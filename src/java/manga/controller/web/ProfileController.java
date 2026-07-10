package manga.controller.web;

import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.repository.UserAdminRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/main/profile")
public class ProfileController {

    private static final Logger LOGGER = Logger.getLogger(ProfileController.class.getName());
    private static final long MAX_AVATAR_BYTES = 2L * 1024L * 1024L;
    private static final String FLASH_SUCCESS = "PROFILE_FLASH_SUCCESS";
    private static final String FLASH_ERROR = "PROFILE_FLASH_ERROR";

    @Autowired
    private UserAdminRepository userAdminRepository;

    @RequestMapping(method = RequestMethod.GET)
    public String edit(HttpSession session, Model model) {
        AuthenticatedUser authUser = SessionUserUtil.requireUser(session);
        Map<String, Object> profile = userAdminRepository.getUser(authUser.getId());
        if (profile == null) {
            throw new IllegalArgumentException("User not found");
        }
        syncSessionUser(authUser, profile);
        model.addAttribute("user", profile);
        consumeFlash(session, model);
        return "profile/edit";
    }

    @GetMapping("/change-password")
    public String changePasswordPage(HttpSession session, Model model) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        model.addAttribute("user", user);
        consumeFlash(session, model);
        return "profile/change-password";
    }

    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public String update(
            HttpSession session,
            HttpServletRequest request,
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar) {
        AuthenticatedUser authUser = SessionUserUtil.requireUser(session);
        StoredAvatar storedAvatar = null;
        try {
            validateProfile(fullName, email);
            if (userAdminRepository.emailExistsExcludingUser(email, authUser.getId())) {
                throw new IllegalArgumentException("Email already exists");
            }

            Map<String, Object> currentProfile = userAdminRepository.getUser(authUser.getId());
            if (currentProfile == null) {
                throw new IllegalArgumentException("User not found");
            }
            String avatarUrl = (String) currentProfile.get("avatarUrl");
            if (avatar != null && !avatar.isEmpty()) {
                storedAvatar = storeAvatar(request, authUser.getId(), avatar);
                avatarUrl = storedAvatar.url;
            }

            userAdminRepository.updateProfile(authUser.getId(), fullName, email, avatarUrl);
            authUser.setFullName(fullName.trim());
            authUser.setEmail(email.trim());
            authUser.setAvatarUrl(avatarUrl);
            setFlashSuccess(session, "Profile updated successfully");
        } catch (Exception ex) {
            deleteStoredAvatar(storedAvatar);
            setFlashError(session, messageOf(ex, "Cannot update profile"));
        }
        return "redirect:/main/profile";
    }

    @RequestMapping(value = "/change-password", method = RequestMethod.POST)
    public String changePassword(
            HttpSession session,
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmNewPassword") String confirmNewPassword) {
        AuthenticatedUser authUser = SessionUserUtil.requireUser(session);
        try {
            String currentPasswordHash = userAdminRepository.getPasswordHash(authUser.getId());
            if (currentPasswordHash == null || currentPassword == null || !currentPassword.equals(currentPasswordHash)) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            if (newPassword == null || !newPassword.equals(confirmNewPassword)) {
                throw new IllegalArgumentException("New password confirmation does not match");
            }
            if (newPassword.length() < 5) {
                throw new IllegalArgumentException("Password must be at least 5 characters");
            }

            userAdminRepository.updatePassword(authUser.getId(), newPassword);
            authUser.setPasswordHash(newPassword);
            setFlashSuccess(session, "Password changed successfully");
        } catch (RuntimeException ex) {
            setFlashError(session, messageOf(ex, "Cannot change password"));
        }
        return "redirect:/main/profile";
    }

    private void validateProfile(String fullName, String email) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (email == null || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Email is invalid");
        }
    }

    private StoredAvatar storeAvatar(HttpServletRequest request, long userId, MultipartFile avatar) throws IOException {
        if (avatar.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("Avatar must not exceed 2MB");
        }
        String extension = extensionOf(avatar.getOriginalFilename());
        if (!"jpg".equals(extension) && !"jpeg".equals(extension) && !"png".equals(extension)) {
            throw new IllegalArgumentException("Avatar must be a JPG, JPEG, or PNG file");
        }

        String uploadRoot = request.getServletContext().getRealPath("/assets/uploads/avatars");
        if (uploadRoot == null) {
            throw new IllegalStateException("Avatar upload storage is unavailable");
        }
        Path uploadDirectory = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Files.createDirectories(uploadDirectory);

        String filename = userId + "_" + System.currentTimeMillis() + "." + extension;
        Path target = uploadDirectory.resolve(filename).normalize();
        if (!target.startsWith(uploadDirectory)) {
            throw new IllegalArgumentException("Avatar filename is invalid");
        }
        try {
            avatar.transferTo(target.toFile());
        } catch (IOException ex) {
            Files.deleteIfExists(target);
            throw ex;
        } catch (IllegalStateException ex) {
            Files.deleteIfExists(target);
            throw ex;
        }
        return new StoredAvatar("/assets/uploads/avatars/" + filename, target);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ENGLISH);
    }

    private void deleteStoredAvatar(StoredAvatar storedAvatar) {
        if (storedAvatar == null) {
            return;
        }
        try {
            Files.deleteIfExists(storedAvatar.path);
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "Cannot delete uploaded avatar after profile update failed", ex);
        }
    }

    private void syncSessionUser(AuthenticatedUser authUser, Map<String, Object> profile) {
        authUser.setFullName((String) profile.get("fullName"));
        authUser.setEmail((String) profile.get("email"));
        authUser.setAvatarUrl((String) profile.get("avatarUrl"));
    }

    private void consumeFlash(HttpSession session, Model model) {
        Object success = session.getAttribute(FLASH_SUCCESS);
        Object error = session.getAttribute(FLASH_ERROR);
        session.removeAttribute(FLASH_SUCCESS);
        session.removeAttribute(FLASH_ERROR);
        if (success != null) {
            model.addAttribute("success", success);
            model.addAttribute("flashSuccess", success);
        }
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("flashError", error);
        }
    }

    private void setFlashSuccess(HttpSession session, String message) {
        session.removeAttribute(FLASH_ERROR);
        session.setAttribute(FLASH_SUCCESS, message);
    }

    private void setFlashError(HttpSession session, String message) {
        session.removeAttribute(FLASH_SUCCESS);
        session.setAttribute(FLASH_ERROR, message);
    }

    private String messageOf(Exception ex, String fallback) {
        return ex.getMessage() == null || ex.getMessage().trim().isEmpty() ? fallback : ex.getMessage();
    }

    private static final class StoredAvatar {
        private final String url;
        private final Path path;

        private StoredAvatar(String url, Path path) {
            this.url = url;
            this.path = path;
        }
    }
}
