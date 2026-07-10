package manga.service;

import manga.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Workflow-facing entry point for creating notifications.
 * Callers pass type, message, and reference metadata; title and viewUrl are resolved
 * inside {@link NotificationRepository} so every service path shares one mapping.
 */
@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    /**
     * Primary write path for business workflows (proposals, manuscripts, ranking, etc.).
     * Task scheduler jobs may bypass this service and insert via PageTaskRepository when
     * they need dedup helpers in the same JDBC batch; both paths use the same mapping.
     */
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

    /**
     * Once-ever dedup guard before notifyUser(), e.g. REVIEW_WARNING per manuscript version.
     * Daily dedup (TASK_DUE_SOON, TASK_DELAYED) lives in PageTaskRepository schedulers instead.
     */
    public boolean existsNotification(long userId, String type, long referenceId) {
        return notificationRepository.exists(userId, type, referenceId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
