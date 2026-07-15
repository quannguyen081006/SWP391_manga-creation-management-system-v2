package manga.web;

import manga.model.AuthenticatedUser;
import manga.model.NotificationItem;
import manga.service.NotificationService;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Global MVC model provider for the header notification bell.
 * {@code @ControllerAdvice(annotations = Controller.class)} runs only on page controllers,
 * not REST API controllers, and injects {@code @ModelAttribute} values into every JSP
 * without each controller fetching unread count/list manually.
 */
@ControllerAdvice(annotations = Controller.class)
public class NotificationViewAdvice {

    @Autowired
    private NotificationService notificationService;

    /**
     * Badge count for header.jsp; returns 0 when logged out.
     */
    @ModelAttribute("headerUnreadNotificationCount")
    public int unreadCount(HttpSession session) {
        AuthenticatedUser user = getUser(session);
        if (user == null) {
            return 0;
        }
        return notificationService.unreadCount(user.getId());
    }

    /**
     * Recent rows for the header dropdown; full list page loads its own 100-row query.
     */
    @ModelAttribute("headerNotifications")
    public List<NotificationItem> latestNotifications(HttpSession session) {
        AuthenticatedUser user = getUser(session);
        if (user == null) {
            return java.util.Collections.emptyList();
        }
        return notificationService.listByUser(user.getId());
    }

    private AuthenticatedUser getUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object raw = session.getAttribute("AUTH_USER");
        // Header advice runs on many pages; ignore unexpected session values safely.
        if (!(raw instanceof AuthenticatedUser)) {
            return null;
        }
        return (AuthenticatedUser) raw;
    }
}
