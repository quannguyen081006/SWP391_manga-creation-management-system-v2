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

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String loginPage() {
        return "auth/login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String login(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletRequest request,
            Model model) {
        try {
            AuthenticatedUser user = authService.login(username, password);
            // Drop any session that existed before authentication so the browser
            // gets a brand-new session id on login. Without this, an attacker who
            // can plant a known JSESSIONID in the victim's browser beforehand would
            // still hold a valid handle on the session after the victim signs in
            // (session fixation).
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }
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

    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login";
    }

}
