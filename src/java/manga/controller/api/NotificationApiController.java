package manga.controller.api;

import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.NotificationItem;
import manga.repository.NotificationRepository;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationApiController {

    @Autowired
    private NotificationRepository notificationRepository;

    @RequestMapping(method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public String list(HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ok(notificationRepository.listByUser(user.getId()), "Notifications");
    }

    @RequestMapping(value = "/{id}/read", method = RequestMethod.PATCH, produces = "application/json;charset=UTF-8")
    public String markRead(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        notificationRepository.markRead(user.getId(), id);
        return okEmpty("Notification marked as read");
    }

    @RequestMapping(value = "/mark-all-read", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public String markAllRead(HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        notificationRepository.markAllRead(user.getId());
        return okEmpty("All notifications marked as read");
    }

    private String ok(List<NotificationItem> items, String message) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"success\":true,\"message\":").append(json(message)).append(",\"data\":[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            appendItem(builder, items.get(i));
        }
        builder.append("]}");
        return builder.toString();
    }

    private String okEmpty(String message) {
        return "{\"success\":true,\"message\":" + json(message) + ",\"data\":null}";
    }

    private void appendItem(StringBuilder builder, NotificationItem item) {
        builder.append("{");
        builder.append("\"id\":").append(item.getId()).append(",");
        builder.append("\"userId\":").append(item.getUserId()).append(",");
        builder.append("\"recipientId\":").append(json(String.valueOf(item.getUserId()))).append(",");
        builder.append("\"type\":").append(json(item.getType())).append(",");
        builder.append("\"title\":").append(json(item.getTitle())).append(",");
        builder.append("\"message\":").append(json(item.getMessage())).append(",");
        builder.append("\"body\":").append(json(item.getMessage())).append(",");
        builder.append("\"viewUrl\":").append(json(item.getViewUrl())).append(",");
        builder.append("\"referenceId\":").append(item.getReferenceId() == null ? "null" : item.getReferenceId()).append(",");
        builder.append("\"referenceType\":").append(json(item.getReferenceType())).append(",");
        builder.append("\"relatedEntityId\":").append(item.getReferenceId() == null ? "null" : json(String.valueOf(item.getReferenceId()))).append(",");
        builder.append("\"relatedEntityType\":").append(json(item.getReferenceType())).append(",");
        builder.append("\"read\":").append(item.isRead()).append(",");
        builder.append("\"createdAt\":").append(json(item.getCreatedAt() == null ? null : item.getCreatedAt().toString()));
        builder.append("}");
    }

    private String json(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (ch < 32) {
                        String hex = Integer.toHexString(ch);
                        builder.append("\\u");
                        for (int j = hex.length(); j < 4; j++) {
                            builder.append("0");
                        }
                        builder.append(hex);
                    } else {
                        builder.append(ch);
                    }
                    break;
            }
        }
        builder.append("\"");
        return builder.toString();
    }
}





