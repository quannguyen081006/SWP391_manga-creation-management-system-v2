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
 * JDBC access for the Notification table.
 * <p>
 * <b>Two write paths converge on the same title/URL rules:</b>
 * <ol>
 *   <li>{@link manga.service.NotificationService#notifyUser} -> {@link #create(long, String, String, long, String)}
 *       for workflow-driven alerts (proposals, manuscripts, account changes, etc.).</li>
 *   <li>PageTaskRepository scheduler jobs use a parallel bypass
 *       ({@code createNotificationIfAbsent} / {@code createNotificationIfAbsentToday}) with
 *       local title/URL helpers that follow the same rules as {@link #defaultTitle} and
 *       {@link #defaultViewUrl} below. That bypass keeps batch SQL in one transaction.</li>
 * </ol>
 * <p>
 * <b>Dedup patterns:</b> {@link #exists(long, String, long)} supports once-ever checks
 * (e.g. ReviewTaskService uses it before {@code REVIEW_WARNING}). Task schedulers use
 * once-ever for {@code TASK_OVERDUE} and daily dedup for {@code TASK_DUE_SOON} /
 * {@code TASK_DELAYED} via PageTaskRepository helpers that filter on {@code createdAt} date.
 */
@Repository
public class NotificationRepository {

    @Autowired
    private DataSource dataSource;

    /**
     * Creates a notification using the central type-to-title and type-to-URL mapping below.
     * Prefer {@link manga.service.NotificationService#notifyUser} from workflow code; task
     * schedulers may insert through PageTaskRepository when SQL batching requires it.
     */
    public void create(long userId, String type, String message, long referenceId, String referenceType) {
        create(userId, type, defaultTitle(type), message, defaultViewUrl(type, referenceId, referenceType), referenceId, referenceType);
    }

    /**
     * Inserts the final row after title and viewUrl are already known.
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
     * Lists the default notification page/header amount for one user.
     */
    public List<NotificationItem> listByUser(long userId) {
        return listByUser(userId, 100);
    }

    /**
     * Lists notifications newest first; callers choose a limit for page/header use.
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
     * Counts unread rows for the header badge.
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
     * Marks a single user-owned notification as read.
     */
    public void markRead(long userId, long id) {
        String sql = "UPDATE Notification SET isRead = 1 WHERE id = ? AND userId = ?";
        update(sql, id, userId);
    }

    /**
     * Marks a single user-owned notification as unread; used by the JS/API path.
     */
    public void markUnread(long userId, long id) {
        String sql = "UPDATE Notification SET isRead = 0 WHERE id = ? AND userId = ?";
        update(sql, id, userId);
    }

    /**
     * Deletes a single user-owned notification; there is only an API/JS route for this.
     */
    public void delete(long userId, long id) {
        String sql = "DELETE FROM Notification WHERE id = ? AND userId = ?";
        update(sql, id, userId);
    }

    /**
     * Marks every unread notification as read for the current user.
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
     * Once-ever dedup check: true when any row already exists for (user, type, referenceId).
     * Used by NotificationService.existsNotification and by callers that guard before create().
     * Task schedulers use equivalent logic inside PageTaskRepository.createNotificationIfAbsent
     * (e.g. one TASK_OVERDUE per task) or createNotificationIfAbsentToday (e.g. one TASK_DUE_SOON
     * or TASK_DELAYED reminder per calendar day).
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
     * Loads the stored click target for one user-owned notification.
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

    /**
     * Single source of truth for display titles from notification type.
     * Keep this repository as the easy-to-explain source for type -> title mapping.
     * PageTaskRepository's scheduler bypass is documented here because it must stay
     * aligned with this mapping when it creates task reminders in batch SQL.
     */
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

    /**
     * Single source of truth for click targets stored at insert time.
     * NotificationWebController validates these paths again on /click (open-redirect guard).
     */
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
