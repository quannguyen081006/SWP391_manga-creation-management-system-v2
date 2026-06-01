package manga.common.util;

import manga.model.AuthenticatedUser;
import javax.servlet.http.HttpSession;

public final class SessionUserUtil {

    private SessionUserUtil() {
    }

    public static AuthenticatedUser requireUser(HttpSession session) {
        Object auth = session == null ? null : session.getAttribute("AUTH_USER");
        if (auth == null || !(auth instanceof AuthenticatedUser)) {
            throw new IllegalStateException("Unauthorized");
        }
        return (AuthenticatedUser) auth;
    }

    public static void requireRole(AuthenticatedUser user, String role, String message) {
        if (user == null || !user.hasRole(role)) {
            throw new IllegalArgumentException(message);
        }
    }
}

