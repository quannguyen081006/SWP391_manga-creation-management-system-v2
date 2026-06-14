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
import org.springframework.web.servlet.view.RedirectView;

/**
 * Handles authenticated notification pages and click-through redirects.
 * This controller owns the web flow for `/main/notifications`.
 */
@Controller
@RequestMapping("/main/notifications")
public class NotificationWebController {

    @Autowired
    private NotificationRepository notificationRepository;

    /**
     * Displays the current user's notification list.
     *
     * @param session current HTTP session containing `AUTH_USER`
     * @param model MVC model that receives notifications and unreadCount
     * @return JSP view name for the notification list
     */
    @RequestMapping(method = RequestMethod.GET)
    public String list(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        model.addAttribute("notifications", notificationRepository.listByUser(user.getId(), 100));
        model.addAttribute("unreadCount", notificationRepository.unreadCount(user.getId()));
        return "notification/list";
    }

    /**
     * Marks one notification as read from the web page form action.
     *
     * @param id notification id
     * @param session current HTTP session containing `AUTH_USER`
     * @return redirect back to the notification list
     */
    @RequestMapping(value = "/{id}/read", method = RequestMethod.POST)
    public String markRead(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = requireUser(session);
        notificationRepository.markRead(user.getId(), id);
        return "redirect:/main/notifications";
    }

    /**
     * Handles clicking a notification by marking it read and redirecting safely.
     *
     * @param id notification id
     * @param session current HTTP session containing `AUTH_USER`
     * @return redirect to a supported target URL or back to notifications
     */
    @RequestMapping(value = "/{id}/click", method = RequestMethod.GET)
    public RedirectView click(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = requireUser(session);
        notificationRepository.markRead(user.getId(), id);
        String viewUrl = notificationRepository.viewUrlByUser(user.getId(), id);
        if (isSupportedViewUrl(viewUrl)) {
            return redirectTo(viewUrl);
        }
        return redirectTo("/main/notifications");
    }

    /**
     * Marks all notifications for the current user as read.
     *
     * @param session current HTTP session containing `AUTH_USER`
     * @return redirect back to the notification list
     */
    @RequestMapping(value = "/mark-all-read", method = RequestMethod.POST)
    public String markAllRead(HttpSession session) {
        AuthenticatedUser user = requireUser(session);
        notificationRepository.markAllRead(user.getId());
        return "redirect:/main/notifications";
    }

    private AuthenticatedUser requireUser(HttpSession session) {
        Object auth = session == null ? null : session.getAttribute("AUTH_USER");
        // Web endpoints depend on the interceptor, but this guard keeps direct calls safe.
        if (!(auth instanceof AuthenticatedUser)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return (AuthenticatedUser) auth;
    }

    private boolean isSupportedViewUrl(String viewUrl) {
        if (viewUrl == null) {
            return false;
        }
        String path = viewUrl.trim();
        if (path.isEmpty() || !path.startsWith("/main/")) {
            return false;
        }
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        // Allow only known web routes so stored notification URLs cannot redirect externally.
        return path.matches("/main/notifications")
                || path.matches("/main/proposals/\\d+")
                || path.matches("/main/proposals/\\d+/vote")
                || path.matches("/main/proposals/\\d+/edit")
                || path.matches("/main/tasks/\\d+")
                || path.matches("/main/chapters")
                || path.matches("/main/chapters/\\d+")
                || path.matches("/main/chapters/detail")
                || path.startsWith("/main/series/")
                || path.matches("/main/decisions/\\d+")
                || path.matches("/main/ranking/periods(/\\d+/(results|mangaka))?");
    }

    private RedirectView redirectTo(String path) {
        RedirectView view = new RedirectView(path, true);
        view.setExposeModelAttributes(false);
        return view;
    }
}
