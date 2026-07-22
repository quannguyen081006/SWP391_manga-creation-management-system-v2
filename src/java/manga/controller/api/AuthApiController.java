package manga.controller.api;

import manga.common.ApiResponse;
import manga.model.AuthenticatedUser;
import manga.service.AuthService;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthApiController {

    @Autowired
    private AuthService authService;

        @RequestMapping(value = "/login", method = RequestMethod.POST)
    public ApiResponse<Map<String, Object>> login(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletRequest request) {
        AuthenticatedUser user = authService.login(username, password);

        // Same session-fixation guard as the web login: discard any pre-login
        // session so the authenticated user is bound to a freshly issued id.
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("AUTH_USER", user);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("fullName", user.getFullName());
        data.put("roles", user.getRoles());

        return ApiResponse.ok(data, "Login successful");
    }

        @RequestMapping(value = "/logout", method = RequestMethod.POST)
    public ApiResponse<Object> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.ok(null, "Logout successful");
    }

        @RequestMapping(value = "/me", method = RequestMethod.GET)
    public ApiResponse<Map<String, Object>> me(HttpSession session) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (user == null) {
            throw new IllegalStateException("Unauthorized");
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("fullName", user.getFullName());
        data.put("roles", user.getRoles());
        return ApiResponse.ok(data, "Current user");
    }
}
