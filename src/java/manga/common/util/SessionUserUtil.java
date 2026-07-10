package manga.common.util;

import manga.model.AuthenticatedUser;
import javax.servlet.http.HttpSession;

public final class SessionUserUtil {

    private SessionUserUtil() {
    }

    /**
     * Returns AUTH_USER from the session or stops the caller with Unauthorized.
     * Use this in controllers/services when a feature only needs a logged-in user.
     */
    public static AuthenticatedUser requireUser(HttpSession session) {
        Object auth = session == null ? null : session.getAttribute("AUTH_USER");
        if (auth == null || !(auth instanceof AuthenticatedUser)) {
            throw new IllegalStateException("Unauthorized");
        }
        return (AuthenticatedUser) auth;
    }

    /**
     * Checks that the current user has one required role.
     * Use this after requireUser when an action needs a role-specific guard.
     */
    public static void requireRole(AuthenticatedUser user, String role, String message) {
        if (user == null || !user.hasRole(role)) {
            throw new IllegalArgumentException(message);
        }
    }
}

