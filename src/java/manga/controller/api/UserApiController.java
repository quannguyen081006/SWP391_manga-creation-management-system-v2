package manga.controller.api;

import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.repository.UserAdminRepository;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserApiController {

    @Autowired
    private UserAdminRepository userAdminRepository;

    /**
     * Returns the admin user list for API clients after the session user is checked.
     */
    @RequestMapping(method = RequestMethod.GET)
    public ApiResponse<List<Map<String, Object>>> list(HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(userAdminRepository.listUsers(), "User list");
    }

    /**
     * Creates a basic account through the API. Role assignment is handled by the
     * dedicated role endpoint so the response shape stays simple.
     */
    @RequestMapping(method = RequestMethod.POST)
    public ApiResponse<Map<String, Object>> create(
            HttpSession session,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email) {
        requireAdmin(session);
        if (password == null || password.trim().isEmpty()) {
            password = "12345";
        }
        long id = userAdminRepository.createUser(username, password, fullName, email);
        return ApiResponse.ok(userAdminRepository.getUser(id), "User created");
    }

    /**
     * Loads one user row for admin screens or API clients.
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public ApiResponse<Map<String, Object>> detail(@PathVariable("id") long id, HttpSession session) {
        requireAdmin(session);
        Map<String, Object> user = userAdminRepository.getUser(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return ApiResponse.ok(user, "User detail");
    }

    /**
     * Updates profile fields only; username and roles are managed by separate flows.
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    public ApiResponse<Object> update(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email) {
        requireAdmin(session);
        userAdminRepository.updateUser(id, fullName, email);
        return ApiResponse.ok(null, "User updated");
    }

    /**
     * Changes account status after a small controller check; repository rules still
     * protect the final active ADMIN account.
     */
    @RequestMapping(value = "/{id}/status", method = RequestMethod.PATCH)
    public ApiResponse<Object> patchStatus(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("status") String status) {
        requireAdmin(session);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        // BR-SYS: only ACTIVE and INACTIVE are valid account states.
        if (!"ACTIVE".equals(normalized) && !"INACTIVE".equals(normalized)) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        userAdminRepository.updateStatus(id, normalized);
        return ApiResponse.ok(null, "User status updated");
    }

    /**
     * Adds a role to a user. UserAdminRepository is the authority for singleton
     * ADMIN and valid role-combination checks.
     */
    @RequestMapping(value = "/{id}/roles", method = RequestMethod.POST)
    public ApiResponse<Object> addRole(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("role") String role) {
        requireAdmin(session);
        String normalized = role == null ? "" : role.trim().toUpperCase();
        userAdminRepository.addRole(id, normalized);
        return ApiResponse.ok(null, "Role assigned");
    }

    /**
     * Removes a role from a user while preserving the repository's ADMIN guard.
     */
    @RequestMapping(value = "/{id}/roles", method = RequestMethod.DELETE)
    public ApiResponse<Object> removeRole(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("role") String role) {
        requireAdmin(session);
        String normalized = role == null ? "" : role.trim().toUpperCase();
        userAdminRepository.removeRole(id, normalized);
        return ApiResponse.ok(null, "Role removed");
    }

    /**
     * Reuses the session helper so every API action requires an ADMIN user.
     */
    private AuthenticatedUser requireAdmin(HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "ADMIN", "Only ADMIN can manage users");
        return user;
    }
}

