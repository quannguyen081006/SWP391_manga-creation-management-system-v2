package manga.web;

import manga.model.AuthenticatedUser;
import manga.model.NotificationItem;
import manga.repository.NotificationRepository;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds notification summary attributes to every MVC controller model.
 * The shared header uses these attributes for the unread badge and dropdown.
 */
@ControllerAdvice(annotations = Controller.class)
public class NotificationViewAdvice {

    @Autowired
    private NotificationRepository notificationRepository;

    @ModelAttribute("headerUnreadNotificationCount")
    public int unreadCount(HttpSession session) {
        AuthenticatedUser user = getUser(session);
        if (user == null) {
            return 0;
        }
        return notificationRepository.unreadCount(user.getId());
    }

    /**
     * Provides notifications for the header dropdown.
     *
     * @param session current HTTP session
     * @return notifications for the authenticated user, or an empty list
     */
    @ModelAttribute("headerNotifications")
    public List<NotificationItem> latestNotifications(HttpSession session) {
        AuthenticatedUser user = getUser(session);
        if (user == null) {
            return java.util.Collections.emptyList();
        }
        return notificationRepository.listByUser(user.getId());
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
