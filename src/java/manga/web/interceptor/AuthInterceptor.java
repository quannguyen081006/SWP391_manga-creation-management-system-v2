package manga.web.interceptor;

import manga.model.AuthenticatedUser;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String context = request.getContextPath();

        // Public routes must stay available without an AUTH_USER session.
        if (uri.endsWith("/login") || uri.endsWith("/logout")
                || uri.contains("/assets/") || uri.endsWith("/redirect.jsp")) {
            return true;
        }

        AuthenticatedUser user = getSessionUser(request);
        if (user == null) {
            // API callers get JSON status codes; browser pages redirect to login.
            if (uri.contains("/api/v1/")) {
                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            } else {
                response.sendRedirect(context + "/login");
            }
            return false;
        }

        // Controllers and JSPs can reuse the already-checked session user.
        request.setAttribute("AUTH_USER_CHECKED", user);
        preventCachedAuthenticatedPage(response, uri);

        if (!isAllowed(user, uri, context)) {
            // RBAC denies API callers with 403 but keeps web users inside the app.
            if (uri.contains("/api/v1/")) {
                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            } else {
                response.sendRedirect(context + "/main/dashboard");
            }
            return false;
        }

        return true;
    }

    /**
     * Reads AUTH_USER from HttpSession when it is present and has the expected type.
     */
    private AuthenticatedUser getSessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object auth = session == null ? null : session.getAttribute("AUTH_USER");
        if (auth instanceof AuthenticatedUser) {
            return (AuthenticatedUser) auth;
        }
        return null;
    }

    /**
     * Prevents browser cache from showing protected pages after logout.
     */
    private void preventCachedAuthenticatedPage(HttpServletResponse response, String uri) {
        if (uri.contains("/assets/")) {
            return;
        }
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    /**
     * Sends the small JSON error shape expected by API clients.
     */
    private void writeJsonError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"data\":null,\"errors\":[\"" + message + "\"]}");
        response.getWriter().flush();
    }

    /**
     * Applies route-level RBAC rules after the user is known to be logged in.
     * Rules are kept as clear if statements so a beginner can trace each URL
     * prefix to its allowed roles without learning an extra mapping structure.
     */
    private boolean isAllowed(AuthenticatedUser user, String uri, String context) {
        String path = uri.substring(context.length());
        if (path.startsWith("/api/v1/users")) {
            return user.hasRole("ADMIN");
        }
        if (path.startsWith("/main/users")) {
            return user.hasRole("ADMIN");
        }
        if (path.startsWith("/main/settings")) {
            return user.hasRole("ADMIN");
        }
        if (path.startsWith("/main/proposals")) {
            return user.hasRole("ADMIN") || user.hasRole("MANGAKA") || user.hasRole("TANTOU_EDITOR") || user.hasRole("EDITORIAL_BOARD");
        }
        if (path.startsWith("/main/series") || path.startsWith("/main/chapters")) {
            return user.hasRole("ADMIN") || user.hasRole("MANGAKA") || user.hasRole("TANTOU_EDITOR");
        }
        if (path.startsWith("/main/decisions")) {
            return user.hasRole("ADMIN") || user.hasRole("EDITORIAL_BOARD");
        }
        if (path.startsWith("/main/ranking")) {
            return true;
        }
        if (path.startsWith("/main/salary")) {
            if (path.startsWith("/main/salary/my")) {
                return user.hasRole("ASSISTANT");
            }
            return user.hasRole("MANGAKA");
        }
        if (path.startsWith("/main/profile")) {
            return true;
        }
        if (path.startsWith("/main/tasks")) {
            return user.hasRole("ADMIN") || user.hasRole("MANGAKA") || user.hasRole("ASSISTANT") || user.hasRole("TANTOU_EDITOR");
        }
        if (path.startsWith("/main/manuscripts")) {
            return user.hasRole("ADMIN") || user.hasRole("MANGAKA") || user.hasRole("TANTOU_EDITOR");
        }
        return true;
    }

    /**
     * No-op hook required by HandlerInterceptor; work is done in preHandle.
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
    }

    /**
     * No-op hook required by HandlerInterceptor after request completion.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    }
}
