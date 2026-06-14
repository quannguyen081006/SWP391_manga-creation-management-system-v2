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
 * Persists notification records and maps database rows to {@link NotificationItem}.
 * It is the single data-access point used by the header dropdown, full
 * notification page, and notification API actions.
 */
@Repository
public class NotificationRepository {

    @Autowired
    private DataSource dataSource;

    /**
     * Creates a notification and derives its title and stored view URL.
     *
     * @param userId target user id
     * @param type notification type
     * @param message user-facing message
     * @param referenceId related entity id, or a non-positive value when absent
     * @param referenceType related entity type used for view URL mapping
     * @return nothing; the notification row is inserted as a side effect
     */
    public void create(long userId, String type, String message, long referenceId, String referenceType) {
        create(userId, type, defaultTitle(type), message, defaultViewUrl(type, referenceId, referenceType), referenceId, referenceType);
    }

    /**
     * Creates a notification with a caller-provided title and stored view URL.
     *
     * @param userId target user id
     * @param type notification type
     * @param title user-facing notification title
     * @param message user-facing notification message
     * @param viewUrl stored redirect target captured at creation time
     * @param referenceId related entity id, or a non-positive value when absent
     * @param referenceType related entity type
     * @return nothing; the notification row is inserted as a side effect
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
            // Store absent references as NULL so duplicate checks and redirects stay explicit.
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
     * Lists the default number of notifications for a user.
     *
     * @param userId owner user id
     * @return up to 100 notifications ordered newest first
     */
    public List<NotificationItem> listByUser(long userId) {
        return listByUser(userId, 100);
    }

    /**
     * Lists notifications for a user with an explicit row limit.
     *
     * @param userId owner user id
     * @param limit maximum number of notifications to return
     * @return notifications ordered newest first
     */
    public List<NotificationItem> listByUser(long userId, int limit) {
        // SQL Server TOP is parameterized so the header can request many rows safely.
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
     * Counts unread notifications for a user.
     *
     * @param userId owner user id
     * @return unread notification count
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
     * Marks one notification as read for its owner.
     *
     * @param userId owner user id
     * @param id notification id
     * @return nothing; the row is updated as a side effect
     */
    public void markRead(long userId, long id) {
        String sql = "UPDATE Notification SET isRead = 1 WHERE id = ? AND userId = ?";
        update(sql, id, userId);
    }

    /**
     * Marks one notification as unread for its owner.
     *
     * @param userId owner user id
     * @param id notification id
     * @return nothing; the row is updated as a side effect
     */
    public void markUnread(long userId, long id) {
        String sql = "UPDATE Notification SET isRead = 0 WHERE id = ? AND userId = ?";
        update(sql, id, userId);
    }

    /**
     * Deletes one notification when it belongs to the user.
     *
     * @param userId owner user id
     * @param id notification id
     * @return nothing; the row is deleted as a side effect
     */
    public void delete(long userId, long id) {
        String sql = "DELETE FROM Notification WHERE id = ? AND userId = ?";
        update(sql, id, userId);
    }

    /**
     * Marks every unread notification for a user as read.
     *
     * @param userId owner user id
     * @return nothing; matching rows are updated as a side effect
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
     * Checks whether a notification of a given type/reference already exists.
     *
     * @param userId owner user id
     * @param type notification type
     * @param referenceId related entity id, or a non-positive value when absent
     * @return {@code true} when a duplicate notification exists
     */
    public boolean exists(long userId, String type, long referenceId) {
        String sql = "SELECT COUNT(*) FROM Notification WHERE userId = ? AND type = ? AND referenceId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, type);
            // Match the same NULL storage convention used during create().
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
     * Loads the stored view URL for a notification owned by a user.
     *
     * @param userId owner user id
     * @param id notification id
     * @return stored view URL, a legacy fallback URL, or {@code null}
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
                String viewUrl = rs.getString("viewUrl");
                /*
                 * Notification viewUrl should be stored at creation time. This branch keeps
                 * older SERIES_DEADLINE_UPDATED rows usable when they were saved before the
                 * series detail route was supported.
                 */
                if ("SERIES_DEADLINE_UPDATED".equalsIgnoreCase(type)
                        && (viewUrl == null || viewUrl.trim().isEmpty())) {
                    long referenceId = rs.getLong("referenceId");
                    return rs.wasNull() ? "/main/notifications" : "/main/series/" + referenceId;
                }
                return viewUrl;
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
        // Keep titles generic by module so BR-PRO/BR-VOT/BR-TSK/BR-MAN events share UI language.
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
        // Store the redirect target at creation time so click handling only validates it.
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
            return "/main/series/" + referenceId;
        }
        return null;
    }
}
