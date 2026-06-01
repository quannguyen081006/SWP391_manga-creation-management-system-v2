package manga.controller.web;

import manga.model.AuthenticatedUser;
import manga.repository.NotificationRepository;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/main/notifications")
public class NotificationWebController {

    @Autowired
    private NotificationRepository notificationRepository;

    @RequestMapping(method = RequestMethod.GET)
    public String list(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        model.addAttribute("notifications", notificationRepository.listByUser(user.getId(), 100));
        model.addAttribute("unreadCount", notificationRepository.unreadCount(user.getId()));
        return "notification/list";
    }

    @RequestMapping(value = "/{id}/read", method = RequestMethod.POST)
    public String markRead(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = requireUser(session);
        notificationRepository.markRead(user.getId(), id);
        return "redirect:/main/notifications";
    }

    @RequestMapping(value = "/mark-all-read", method = RequestMethod.POST)
    public String markAllRead(HttpSession session) {
        AuthenticatedUser user = requireUser(session);
        notificationRepository.markAllRead(user.getId());
        return "redirect:/main/notifications";
    }

    private AuthenticatedUser requireUser(HttpSession session) {
        Object auth = session == null ? null : session.getAttribute("AUTH_USER");
        if (!(auth instanceof AuthenticatedUser)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return (AuthenticatedUser) auth;
    }
}
