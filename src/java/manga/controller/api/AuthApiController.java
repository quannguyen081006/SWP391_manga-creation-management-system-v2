package manga.controller.api;

import manga.common.ApiResponse;
import manga.model.AuthenticatedUser;
import manga.repository.UserAdminRepository;
import manga.service.AuthService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private UserAdminRepository userAdminRepository;

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public ApiResponse<Map<String, Object>> login(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpSession session) {
        AuthenticatedUser user = authService.login(username, password);
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
        data.put("roles", currentRoleNames(user.getId()));
        return ApiResponse.ok(data, "Current user");
    }

    @RequestMapping(value = "/roles", method = RequestMethod.GET)
    public ApiResponse<List<Map<String, Object>>> roles(HttpSession session) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (user == null) {
            throw new IllegalStateException("Unauthorized");
        }
        return ApiResponse.ok(userAdminRepository.listRoleSwitchItems(user.getId()), "Current user roles");
    }

    @RequestMapping(value = "/switch-list", method = RequestMethod.GET)
    public ApiResponse<List<Map<String, Object>>> switchList(HttpSession session) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (user == null) {
            throw new IllegalStateException("Unauthorized");
        }
        return ApiResponse.ok(userAdminRepository.listActiveUsersForSwitch(), "Switch list");
    }

    private List<String> currentRoleNames(long userId) {
        List<String> roles = new ArrayList<String>();
        List<Map<String, Object>> items = userAdminRepository.listRoleSwitchItems(userId);
        for (Map<String, Object> item : items) {
            Object roleName = item.get("roleName");
            if (roleName != null) {
                roles.add(roleName.toString());
            }
        }
        return roles;
    }
}



