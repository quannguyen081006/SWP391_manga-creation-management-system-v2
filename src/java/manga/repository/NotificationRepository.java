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
 * All notification creation, deduplication, and title/URL mapping resolves
 * here in exactly one place, regardless of caller:
 * <ol>
 * <li>{@link manga.service.NotificationService#notifyUser} -> {@link #create(long, String, String, long, String)}
 * for workflow-driven alerts (proposals, manuscripts, account changes,
 * etc.).</li>
 * <li>{@code PageTaskRepository} scheduler jobs call {@link #createIfAbsent}
 * / {@link #createIfAbsentToday} directly instead of holding a private
 * copy of this SQL/title/URL logic.</li>
 * </ol>
 * <p>
 * <b>Dedup patterns:</b> {@link #exists(long, String, long)} and
 * {@link #createIfAbsent} support once-ever checks (e.g. {@code TASK_OVERDUE},
 * {@code REVIEW_WARNING}). {@link #createIfAbsentToday} scopes the check to
 * today's date for daily-recurring states ({@code TASK_DUE_SOON},
 * {@code TASK_DELAYED}).
 */
@Repository
public class NotificationRepository {

    @Autowired
    private DataSource dataSource;

    public void create(long userId, String type, String message, long referenceId, String referenceType) {
        create(userId, type, defaultTitle(type), message, defaultViewUrl(type, referenceId, referenceType), referenceId, referenceType);
    }

    /**
     * Creates a notification only if one with the same userId/type/referenceId
     * does not already exist. Returns true when a new row was inserted.
     * Used by scheduler jobs that must not re-notify on every run (e.g. TASK_OVERDUE).
     */
    public boolean createIfAbsent(long userId, String type, String message, long referenceId, String referenceType) {
        if (exists(userId, type, referenceId)) {
            return false;
        }
        create(userId, type, message, referenceId, referenceType);
        return true;
    }

    /**
     * Same as {@link #createIfAbsent} but scoped to today's date, allowing one
     * notification per day for recurring states (e.g. TASK_DUE_SOON, TASK_DELAYED).
     */
    public boolean createIfAbsentToday(long userId, String type, String message, long referenceId, String referenceType) {
        String checkSql = "SELECT COUNT(1) FROM Notification "
                + "WHERE userId = ? AND type = ? AND referenceId = ? "
                + "AND CAST(createdAt AS DATE) = CAST(GETDATE() AS DATE)";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setLong(1, userId);
            check.setString(2, type);
            check.setLong(3, referenceId);
            try ( ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check notification duplication", ex);
        }
        create(userId, type, message, referenceId, referenceType);
        return true;
    }

    public void create(long userId, String type, String title, String message, String viewUrl, long referenceId, String referenceType) {
        String sql = "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, 0, GETDATE())";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
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

    public List<NotificationItem> listByUser(long userId) {
        return listByUser(userId, 100);
    }

    public List<NotificationItem> listByUser(long userId, int limit) {
        // SQL Server TOP is parameterized so the header can request many rows safely.
        String sql = "SELECT TOP (?) id, userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt FROM Notification WHERE userId = ? ORDER BY createdAt DESC";
        List<NotificationItem> rows = new ArrayList<NotificationItem>();
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setLong(2, userId);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load notifications", ex);
        }
        return rows;
    }

    public int unreadCount(long userId) {
        String sql = "SELECT COUNT(*) FROM Notification WHERE userId = ? AND isRead = 0";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot count unread notifications", ex);
        }
    }

    public void markRead(long userId, long id) {
        String sql = "UPDATE Notification SET isRead = 1 WHERE id = ? AND userId = ?";
        update(sql, id, userId);
    }

    public void markUnread(long userId, long id) {
        String sql = "UPDATE Notification SET isRead = 0 WHERE id = ? AND userId = ?";
        update(sql, id, userId);
    }

    public void delete(long userId, long id) {
        String sql = "DELETE FROM Notification WHERE id = ? AND userId = ?";
        update(sql, id, userId);
    }

    public void markAllRead(long userId) {
        String sql = "UPDATE Notification SET isRead = 1 WHERE userId = ? AND isRead = 0";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot mark all notifications", ex);
        }
    }

    public boolean exists(long userId, String type, long referenceId) {
        String sql = "SELECT COUNT(*) FROM Notification WHERE userId = ? AND type = ? AND referenceId = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, type);
            // Match the same NULL storage convention used during create().
            if (referenceId <= 0) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, referenceId);
            }
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check notification existence", ex);
        }
        return false;
    }

    public String viewUrlByUser(long userId, long id) {
        String sql = "SELECT type, viewUrl, referenceId FROM Notification WHERE id = ? AND userId = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            try ( ResultSet rs = ps.executeQuery()) {
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
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
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
        // Specific TASK_* titles (moved here from PageTaskRepository so title/URL
        // mapping lives in exactly one place regardless of caller).
        if ("TASK_ASSIGNED".equals(normalized)) { return "New page task assigned"; }
        if ("TASK_SUBMITTED".equals(normalized)) { return "Task submitted for review"; }
        if ("TASK_APPROVED".equals(normalized)) { return "Task approved"; }
        if ("TASK_REJECTED".equals(normalized)) { return "Task rejected - rework needed"; }
        if ("TASK_DELETED".equals(normalized)) { return "Task deleted"; }
        if ("TASK_REASSIGNED".equals(normalized)) { return "Task reassigned"; }
        if ("TASK_DUE_SOON".equals(normalized)) { return "Task due in 24 hours"; }
        if ("TASK_DELAYED".equals(normalized)) { return "Task delayed"; }
        if ("TASK_OVERDUE".equals(normalized)) { return "Task overdue"; }
        if ("TASK_OVERDUE_ACTION_REQUIRED".equals(normalized)) { return "Task overdue - action required"; }
        if ("TASK_EXTENDED".equals(normalized)) { return "Task deadline extended"; }
        if ("TASK_CANCELLED".equals(normalized)) { return "Task cancelled"; }
        if ("TASK_ESCALATED".equals(normalized)) { return "Task escalated"; }
        if ("CHAPTER_AT_RISK".equals(normalized)) { return "Chapter at risk"; }
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
            return "/main/manuscript-workspace/" + referenceId;
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