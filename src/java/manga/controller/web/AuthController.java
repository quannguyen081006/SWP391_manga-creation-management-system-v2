package manga.controller.web;

import manga.model.AuthenticatedUser;
import manga.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * Shows the login page for users who do not yet have an AUTH_USER in session.
     */
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String loginPage() {
        return "auth/login";
    }

    /**
     * Checks submitted credentials and stores the authenticated user in HttpSession.
     * This app uses server-side session auth because JSP pages are rendered by the
     * server, so controllers and views can read one shared AUTH_USER instead of
     * passing a JWT through every page request.
     */
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String login(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletRequest request,
            Model model) {
        try {
            AuthenticatedUser user = authService.login(username, password);
            HttpSession session = request.getSession(true);
            // AUTH_USER is the single session key used by controllers, JSPs, and RBAC.
            session.setAttribute("AUTH_USER", user);
            return "redirect:/main/dashboard";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("username", username);
            return "auth/login";
        }
    }

    /**
     * Ends the login session by invalidating HttpSession, then returns to login.
     */
    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login";
    }

}
