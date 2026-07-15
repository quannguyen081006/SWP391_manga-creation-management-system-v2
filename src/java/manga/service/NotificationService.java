package manga.service;

import manga.model.NotificationItem;
import manga.repository.NotificationRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    public void notifyUser(long userId, String type, String message, long referenceId, String referenceType) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Notification target user is required");
        }
        if (isBlank(type) || isBlank(message)) {
            throw new IllegalArgumentException("Notification type and message are required");
        }
        // The repository stores the viewUrl at creation time from type/reference metadata.
        notificationRepository.create(userId, type.trim().toUpperCase(), message.trim(), referenceId, referenceType);
    }

    public boolean existsNotification(long userId, String type, long referenceId) {
        return notificationRepository.exists(userId, type, referenceId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
    
    public void markRead(long userId, long id) {
        notificationRepository.markRead(userId, id); 
    }

    public List<NotificationItem> listByUser(long userId) {
        return notificationRepository.listByUser(userId);
    }

    public List<NotificationItem> listByUser(long userId, int limit) {
        return notificationRepository.listByUser(userId, limit);
    }

    public int unreadCount(long userId) {
        return notificationRepository.unreadCount(userId);
    }

    public void markUnread(long userId, long id) {
        notificationRepository.markUnread(userId, id);
    }

    public void delete(long userId, long id) {
        notificationRepository.delete(userId, id);
    }

    public void markAllRead(long userId) {
        notificationRepository.markAllRead(userId);
    }

    public String viewUrlByUser(long userId, long id) {
        return notificationRepository.viewUrlByUser(userId, id);
    }
}
