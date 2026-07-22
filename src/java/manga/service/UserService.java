package manga.service;

import manga.common.util.BCrypt;
import manga.common.util.RoleCombinationValidator;
import manga.repository.UserAdminRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service layer for user account, profile, and role management. Wraps
 * {@link UserAdminRepository} so controllers (web and API) go through one
 * shared place for user-management business logic, the same
 * Controller -> Service -> Repository pattern used elsewhere in the app
 * (see {@link ProposalService}, {@link DecisionService}, {@link NotificationService}).
 * <p>
 * UserAdminRepository remains the final authority for data-integrity rules
 * (unique username/email, singleton ADMIN, role-combination validation) since
 * those checks must run next to the database write; this service adds the
 * orchestration (early validation, notifications) that used to be duplicated
 * across ModuleWebController, UserApiController, and ProfileController.
 */
@Service
public class UserService {

    @Autowired
    private UserAdminRepository userAdminRepository;

    @Autowired
    private NotificationService notificationService;

    // ------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------

    public List<Map<String, Object>> listUsers() {
        return userAdminRepository.listUsers();
    }

    public Map<String, Object> getUser(long id) {
        return userAdminRepository.getUser(id);
    }

    public boolean hasAnyAdmin() {
        return userAdminRepository.hasAnyAdmin();
    }

    public List<String> listRoles(long userId) {
        return userAdminRepository.listRoles(userId);
    }

    public boolean hasRole(long userId, String roleName) {
        return userAdminRepository.hasRole(userId, roleName);
    }

    public String getFullNameById(long userId) {
        return userAdminRepository.getFullNameById(userId);
    }

    public boolean emailExistsExcludingUser(String email, long userId) {
        return userAdminRepository.emailExistsExcludingUser(email, userId);
    }

    // ------------------------------------------------------------
    // Account management (admin screens / API)
    // ------------------------------------------------------------

    /**
     * Creates a basic account with no roles. Used by the API create endpoint,
     * which assigns roles through the dedicated role endpoint afterward.
     */
    public long createUser(String username, String password, String fullName, String email) {
        long id = userAdminRepository.createUser(username, hashedPassword(password), fullName, email);
        notificationService.notifyUser(id, "ACCOUNT_CREATED", "Your MangaFlow account has been created.", 0, null);
        return id;
    }

    /**
     * Creates an account and assigns the given roles in one step. Used by the
     * admin create-user page, which requires at least one role up front.
     */
    public long createUserWithRoles(String username, String password, String fullName, String email, List<String> roles) {
        validateCreateUser(username, password, fullName, email, roles);
        long id = userAdminRepository.createUser(username, hashedPassword(password), fullName, email);
        for (String role : roles) {
            userAdminRepository.addRole(id, role);
        }
        notificationService.notifyUser(id, "ACCOUNT_CREATED", "Your MangaFlow account has been created.", 0, null);
        return id;
    }

    public void updateUser(long id, String fullName, String email) {
        userAdminRepository.updateUser(id, fullName, email);
    }

    /**
     * Changes ACTIVE/INACTIVE status and notifies the affected user.
     * UserAdminRepository still blocks deactivating the only active ADMIN.
     */
    public void changeStatus(long id, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        userAdminRepository.updateStatus(id, normalized);
        notificationService.notifyUser(id, "ACCOUNT_STATUS_CHANGED", "Your account status changed to " + normalized + ".", 0, null);
    }

    /**
     * Adds one role and notifies the user only if the role is newly assigned.
     */
    public void addRole(long userId, String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        List<String> currentRoles = userAdminRepository.listRoles(userId);
        userAdminRepository.addRole(userId, normalizedRole);
        if (!currentRoles.contains(normalizedRole)) {
            notificationService.notifyUser(userId, "ROLE_ASSIGNED", "Role " + normalizedRole + " was assigned to your account.", 0, null);
        }
    }

    /**
     * Adds several roles at once from the admin list page, with an early
     * combined-role check before touching the database.
     */
    public void assignRoles(long userId, List<String> roles) {
        validateAssignableRoles(userId, roles);
        for (String role : roles) {
            addRole(userId, role);
        }
    }

    /**
     * Removes one role and notifies the user only if the role actually
     * existed. UserAdminRepository blocks removing the only ADMIN role.
     */
    public void removeRole(long userId, String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        List<String> currentRoles = userAdminRepository.listRoles(userId);
        userAdminRepository.removeRole(userId, normalizedRole);
        if (currentRoles.contains(normalizedRole)) {
            notificationService.notifyUser(userId, "ROLE_REMOVED", "Role " + normalizedRole + " was removed from your account.", 0, null);
        }
    }

    // ------------------------------------------------------------
    // Profile (self-service)
    // ------------------------------------------------------------

    /**
     * Updates the current user's profile fields, optionally with a new
     * avatar URL that the caller has already stored on disk.
     */
    public void updateProfile(long userId, String fullName, String email, String avatarUrl) {
        userAdminRepository.updateProfile(userId, fullName, email, avatarUrl);
    }

    /**
     * Verifies the current password, confirms the new password, and saves it.
     * Throws IllegalArgumentException with a user-facing message on any
     * validation failure.
     */
    public void changePassword(long userId, String currentPassword, String newPassword, String confirmNewPassword) {
        String currentPasswordHash = userAdminRepository.getPasswordHash(userId);
        if (currentPasswordHash == null || currentPassword == null
                || !currentPasswordMatches(currentPassword, currentPasswordHash)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (newPassword == null || !newPassword.equals(confirmNewPassword)) {
            throw new IllegalArgumentException("New password confirmation does not match");
        }
        if (newPassword.length() < 5) {
            throw new IllegalArgumentException("Password must be at least 5 characters");
        }
        // The new value is hashed here, so changing a password also migrates a
        // legacy plaintext account off plain text.
        userAdminRepository.updatePassword(userId, BCrypt.hashpw(newPassword));
    }

    /**
     * Compares the submitted current password with the stored value, accepting
     * both BCrypt hashes and legacy plaintext rows for the same reason described
     * on {@link AuthService}.
     */
    private boolean currentPasswordMatches(String submitted, String stored) {
        return BCrypt.looksHashed(stored)
                ? BCrypt.checkpw(submitted, stored)
                : submitted.equals(stored);
    }

    // ------------------------------------------------------------
    // Validation helpers (moved from ModuleWebController)
    // ------------------------------------------------------------

    private String normalizedPassword(String password) {
        return (password == null || password.trim().isEmpty()) ? "12345" : password;
    }

    /**
     * Returns the value to persist in the passwordHash column: the plaintext
     * password run through BCrypt. Accounts are therefore stored hashed from
     * creation onward; only rows predating this change hold plain text, and
     * {@link AuthService} upgrades those on their next successful login.
     * <p>
     * The minimum-length rule is enforced here because UserAdminRepository can no
     * longer check it: that layer now only ever receives the fixed-length hash.
     */
    private String hashedPassword(String password) {
        String effective = normalizedPassword(password);
        if (effective.length() < 5) {
            throw new IllegalArgumentException("Password must be at least 5 characters");
        }
        return BCrypt.hashpw(effective);
    }

    /**
     * Early create-form validation. UserAdminRepository.createUser/addRole and
     * RoleCombinationValidator still enforce the final rules at save time.
     */
    private void validateCreateUser(String username, String password, String fullName, String email, List<String> roles) {
        String effectivePassword = normalizedPassword(password);
        if (isBlank(username) || isBlank(effectivePassword) || isBlank(fullName) || isBlank(email)) {
            throw new IllegalArgumentException("All user fields are required");
        }
        if (effectivePassword.length() < 5) {
            throw new IllegalArgumentException("Password must be at least 5 characters");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Email is invalid");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Select at least one role");
        }
        if (containsRole(roles, "ADMIN") && userAdminRepository.hasAnyAdmin()) {
            throw new IllegalArgumentException("Only one ADMIN account is allowed");
        }
        RoleCombinationValidator.validate(roles);
    }

    /**
     * Early-exit helper before saving several roles at once.
     * UserAdminRepository.addRole and RoleCombinationValidator still enforce
     * the final role-combination rules per addition.
     */
    private void validateAssignableRoles(long userId, List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Select at least one role");
        }
        if (roles.contains("ADMIN")
                && !userAdminRepository.hasRole(userId, "ADMIN")
                && userAdminRepository.hasAnyAdmin()) {
            throw new IllegalArgumentException("Only one ADMIN account is allowed");
        }
        List<String> merged = new ArrayList<String>(userAdminRepository.listRoles(userId));
        for (String role : roles) {
            String normalizedRole = role == null ? "" : role.trim().toUpperCase();
            if (!isBlank(normalizedRole) && !merged.contains(normalizedRole)) {
                merged.add(normalizedRole);
            }
        }
        RoleCombinationValidator.validate(merged);
    }

    private boolean containsRole(List<String> roles, String roleName) {
        if (roles == null) {
            return false;
        }
        for (String role : roles) {
            if (roleName.equalsIgnoreCase(role == null ? "" : role.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
