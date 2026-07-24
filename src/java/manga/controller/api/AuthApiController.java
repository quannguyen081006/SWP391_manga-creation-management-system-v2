package manga.controller.api;

import manga.common.ApiResponse;
import manga.model.AuthenticatedUser;
import manga.service.AuthService;
import manga.web.ActiveSessionRegistry;
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

    @Autowired
    private ActiveSessionRegistry activeSessionRegistry;

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
        // Ghi nhận phiên API này là phiên hợp lệ DUY NHẤT của tài khoản, giống hệt
        // login qua web. Không có bước này thì đăng nhập qua API sẽ lách được luật
        // "một tài khoản một phiên": phiên cũ ở nơi khác vẫn được coi là hợp lệ.
        activeSessionRegistry.register(user.getId(), session.getId());

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("fullName", user.getFullName());
        data.put("roles", user.getRoles());

        return ApiResponse.ok(data, "Login successful");
    }

        @RequestMapping(value = "/logout", method = RequestMethod.POST)
    public ApiResponse<Object> logout(HttpSession session) {
        // Gỡ phiên khỏi registry trước khi huỷ, chỉ gỡ đúng phiên của mình để không
        // đụng tới phiên mới hơn vừa đăng nhập ở máy khác.
        Object auth = session.getAttribute("AUTH_USER");
        if (auth instanceof AuthenticatedUser) {
            activeSessionRegistry.unregister(((AuthenticatedUser) auth).getId(), session.getId());
        }
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
