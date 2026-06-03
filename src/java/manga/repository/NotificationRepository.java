package manga.repository;

import manga.model.NotificationItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository thao tac bang Notification va map du lieu sang NotificationItem.
 */
@Repository
public class NotificationRepository {

    @Autowired
    private DataSource dataSource;

    /**
     * Tao notification voi title va viewUrl mac dinh theo loai tham chieu.
     */
    public void create(long userId, String type, String message, long referenceId, String referenceType) {
        create(userId, type, defaultTitle(type), message, defaultViewUrl(type, referenceId, referenceType), referenceId, referenceType);
    }

    /**
     * Tao notification voi day du title, message va viewUrl da tinh san.
     */
    public void create(long userId, String type, String title, String message, String viewUrl, long referenceId, String referenceType) {
        String sql = "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, 0, GETDATE())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, type);
            ps.setString(3, title);
            ps.setString(4, message);
            ps.setString(5, viewUrl);
            if (referenceId <= 0) {
                ps.setNull(6, java.sql.Types.BIGINT);
            } else {
                ps.setLong(6, referenceId);
            }
            ps.setString(7, referenceType);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create notification", ex);
        }
    }

    /**
     * Lay danh sach notification mac dinh cua user.
     */
    public List<NotificationItem> listByUser(long userId) {
        return listByUser(userId, 100);
    }

    /**
     * Lay notification cua user voi gioi han so dong.
     */
    public List<NotificationItem> listByUser(long userId, int limit) {
        String sql = "SELECT TOP (?) id, userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt FROM Notification WHERE userId = ? ORDER BY createdAt DESC";
        List<NotificationItem> rows = new ArrayList<NotificationItem>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load notifications", ex);
        }
        return rows;
    }

    /**
     * Dem so notification chua doc cua user.
     */
    public int unreadCount(long userId) {
        String sql = "SELECT COUNT(*) FROM Notification WHERE userId = ? AND isRead = 0";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot count unread notifications", ex);
        }
    }

    /**
     * Danh dau notification cua user la da doc.
     */
    public void markRead(long userId, long id) {
        String sql = "UPDATE Notification SET isRead = 1 WHERE id = ? AND userId = ?";
        update(sql, id, userId);
    }

    /**
     * Danh dau toan bo notification cua user la da doc.
     */
    public void markAllRead(long userId) {
        String sql = "UPDATE Notification SET isRead = 1 WHERE userId = ? AND isRead = 0";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot mark all notifications", ex);
        }
    }

    /**
     * Check if a notification of a given type and reference already exists for a user.
     * Used to prevent duplicate reminders.
     */
    public boolean exists(long userId, String type, long referenceId) {
        String sql = "SELECT COUNT(*) FROM Notification WHERE userId = ? AND type = ? AND referenceId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, type);
            if (referenceId <= 0) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, referenceId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check notification existence", ex);
        }
        return false;
    }

    /**
     * Lay viewUrl cho notification cua user, co sua fallback cho URL cu.
     */
    public String viewUrlByUser(long userId, long id) {
        String sql = "SELECT type, viewUrl, referenceId FROM Notification WHERE id = ? AND userId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String type = rs.getString("type");
                if ("SERIES_DEADLINE_UPDATED".equalsIgnoreCase(type)) {
                    long referenceId = rs.getLong("referenceId");
                    return rs.wasNull() ? "/main/notifications" : "/main/chapters?seriesId=" + referenceId;
                }
                return rs.getString("viewUrl");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load notification view URL", ex);
        }
    }

    private void update(String sql, long id, long userId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update notification", ex);
        }
    }

    private NotificationItem map(ResultSet rs) throws SQLException {
        NotificationItem n = new NotificationItem();
        n.setId(rs.getLong("id"));
        n.setUserId(rs.getLong("userId"));
        n.setType(rs.getString("type"));
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        n.setViewUrl(rs.getString("viewUrl"));
        long referenceId = rs.getLong("referenceId");
        n.setReferenceId(rs.wasNull() ? null : referenceId);
        n.setReferenceType(rs.getString("referenceType"));
        n.setRead(rs.getBoolean("isRead"));
        n.setCreatedAt(rs.getTimestamp("createdAt"));
        return n;
    }

    private String defaultTitle(String type) {
        if (type == null) {
            return "Notification";
        }
        String normalized = type.trim().toUpperCase();
        if (normalized.contains("TASK")) {
            return "Task update";
        }
        if (normalized.contains("CHAPTER")) {
            return "Chapter update";
        }
        if (normalized.contains("MANUSCRIPT")) {
            return "Manuscript update";
        }
        if (normalized.contains("DECISION")) {
            return "Decision update";
        }
        if (normalized.contains("PROPOSAL")) {
            return "Proposal update";
        }
        if (normalized.contains("SERIES")) {
            return "Series update";
        }
        return "Notification";
    }

    private String defaultViewUrl(String type, long referenceId, String referenceType) {
        if (referenceId <= 0 || type == null) {
            return null;
        }
        String normalized = type.trim().toUpperCase();
        String ref = referenceType == null ? "" : referenceType.trim().toUpperCase();
        if (ref.equals("TASK") || ref.equals("PAGETASK")) {
            if ("TASK_ESCALATED".equals(normalized)) {
                return "/main/tasks/" + referenceId + "?tab=history";
            }
            return "/main/tasks/" + referenceId;
        }
        if (ref.equals("CHAPTER")) {
            return "/main/chapters/" + referenceId;
        }
        if (ref.equals("MANUSCRIPT")) {
            return "/main/notifications";
        }
        if (ref.equals("PROPOSAL")) {
            if (normalized.contains("VOTE")) {
                return "/main/proposals/" + referenceId + "/vote";
            }
            return "/main/proposals/" + referenceId;
        }
        if (ref.equals("DECISION") || ref.equals("DECISION_SESSION")) {
            return "/main/decisions/" + referenceId;
        }
        if (ref.equals("SERIES")) {
            return "/main/notifications";
        }
        return null;
    }
}
