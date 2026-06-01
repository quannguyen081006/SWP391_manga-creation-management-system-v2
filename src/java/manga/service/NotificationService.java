package manga.service;

import manga.repository.NotificationRepository;
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
        notificationRepository.create(userId, type.trim().toUpperCase(), message.trim(), referenceId, referenceType);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
