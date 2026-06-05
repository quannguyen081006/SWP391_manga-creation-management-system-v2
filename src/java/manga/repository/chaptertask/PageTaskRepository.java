package manga.repository.chaptertask;

import manga.model.AuthenticatedUser;
import manga.model.chaptertask.ChapterImageItem;
import manga.model.chaptertask.TaskSummary;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class PageTaskRepository {

    private static final int TASK_REJECT_SERIES_DEADLINE_BUFFER_DAYS = 3;
    private static final String SQL_CLOSED_TASK_STATUSES = "'APPROVED','DELETED','REASSIGNED','CANCELLED'";
    private static final String SQL_OVERDUE_SKIP_STATUSES = "'APPROVED','OVERDUE','CANCELLED','DELETED','REASSIGNED'";

    /*
     * Delayed is only a warning flag, not a task status transition.
     * It is based on work progress (lastProgressAt), while OVERDUE is based on dueDate.
     */
    private static final String SQL_IS_DELAYED =
            "CAST(CASE WHEN t.status IN ('PENDING','IN_PROGRESS','REJECTED') "
            + "AND DATEDIFF(DAY, t.assignedAt, GETDATE()) >= 3 "
            + "AND DATEDIFF(DAY, COALESCE(t.lastProgressAt, t.assignedAt), GETDATE()) >= 3 "
            + "THEN 1 ELSE 0 END AS BIT) AS isDelayed";

    private static final String SQL_TASK_COLUMNS_BASE =
            "t.id, t.chapterId, t.assistantId, t.pageRangeStart, t.pageRangeEnd, t.taskType, t.dueDate, t.status, t.rejectionCount, ";

    private volatile Boolean taskSchemaExtended;
    private volatile Boolean taskLifecycleSchemaReady;

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ENGLISH);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ChapterImageRepository chapterImageRepository;

    @Autowired
    private PageRepository pageRepository;

    private String taskSelectColumns() {
        ensureTaskLifecycleSchemaReady();
        if (isTaskSchemaExtended()) {
            return SQL_TASK_COLUMNS_BASE
                    + "ISNULL(t.priority, 'NORMAL') AS priority, t.notes, t.rejectionReason, t.approvalComment, t.actionReason, t.previousAssistantId, "
                    + SQL_IS_DELAYED;
        }
        return SQL_TASK_COLUMNS_BASE
                + "'NORMAL' AS priority, CAST(NULL AS NVARCHAR(500)) AS notes, "
                + "CAST(NULL AS NVARCHAR(300)) AS rejectionReason, CAST(NULL AS NVARCHAR(300)) AS approvalComment, "
                + "CAST(NULL AS NVARCHAR(300)) AS actionReason, CAST(NULL AS BIGINT) AS previousAssistantId, "
                + SQL_IS_DELAYED;
    }

    private void ensureTaskLifecycleSchemaReady() {
        if (Boolean.TRUE.equals(taskLifecycleSchemaReady)) {
            return;
        }
        synchronized (this) {
            if (Boolean.TRUE.equals(taskLifecycleSchemaReady)) {
                return;
            }
            try (Connection conn = dataSource.getConnection()) {
                /*
                 * Runtime schema guard for older local databases.
                 * schema.sql also contains these columns/statuses for clean DB rebuilds.
                 */
                addColumnIfMissing(conn, "actionReason", "nvarchar(300) NULL");
                addColumnIfMissing(conn, "previousAssistantId", "bigint NULL");
                addColumnIfMissing(conn, "lastProgressAt", "datetime NULL");
                String dropConstraint =
                        "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_PageTask_status' AND parent_object_id = OBJECT_ID('dbo.PageTask')) "
                        + "ALTER TABLE [dbo].[PageTask] DROP CONSTRAINT [CK_PageTask_status]";
                try (PreparedStatement ps = conn.prepareStatement(dropConstraint)) {
                    ps.executeUpdate();
                }
                String addConstraint =
                        "IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_PageTask_status' AND parent_object_id = OBJECT_ID('dbo.PageTask')) "
                        + "ALTER TABLE [dbo].[PageTask] WITH CHECK ADD CONSTRAINT [CK_PageTask_status] CHECK "
                        + "([status] IN ('PENDING','IN_PROGRESS','SUBMITTED','APPROVED','REJECTED','OVERDUE','DELETED','REASSIGNED','CANCELLED'))";
                try (PreparedStatement ps = conn.prepareStatement(addConstraint)) {
                    ps.executeUpdate();
                }
                taskLifecycleSchemaReady = Boolean.TRUE;
            } catch (SQLException ex) {
                throw new RuntimeException("Cannot prepare task lifecycle schema", ex);
            }
        }
    }

    private void addColumnIfMissing(Connection conn, String column, String definition) throws SQLException {
        String sql = "IF COL_LENGTH('dbo.PageTask', '" + column + "') IS NULL ALTER TABLE [dbo].[PageTask] ADD " + column + " " + definition;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private boolean isTaskSchemaExtended() {
        if (taskSchemaExtended != null) {
            return taskSchemaExtended.booleanValue();
        }
        synchronized (this) {
            if (taskSchemaExtended != null) {
                return taskSchemaExtended.booleanValue();
            }
            boolean ready = false;
            String sql = "SELECT CASE WHEN COL_LENGTH('dbo.PageTask', 'priority') IS NOT NULL "
                    + "AND COL_LENGTH('dbo.PageTask', 'notes') IS NOT NULL THEN 1 ELSE 0 END";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ready = rs.getInt(1) == 1;
                }
            } catch (SQLException ex) {
                ready = false;
            }
            taskSchemaExtended = Boolean.valueOf(ready);
            return ready;
        }
    }

    private boolean hasColumn(ResultSet rs, String label) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (label.equalsIgnoreCase(md.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }

    public List<TaskSummary> listVisible(AuthenticatedUser user) {
        return listVisible(user, null, null);
    }

    public List<TaskSummary> listVisible(AuthenticatedUser user, String status, Long chapterId) {
        String baseSql =
            "SELECT " + taskSelectColumns() + ", "
            + "c.title AS chapterTitle, c.chapterNumber, s.title AS seriesTitle, u.fullName AS assistantName "
            + "FROM PageTask t "
            + "JOIN Chapter c ON c.id = t.chapterId "
            + "JOIN Series s ON s.id = c.seriesId "
            + "LEFT JOIN [User] u ON u.id = t.assistantId";

        List<TaskSummary> rows = new ArrayList<TaskSummary>();
        List<String> conditions = new ArrayList<String>();
        List<Object> params = new ArrayList<Object>();
        if (!user.hasRole("ADMIN")) {
            if (user.hasRole("MANGAKA")) {
                conditions.add("s.mangakaId = ?");
                params.add(user.getId());
            }
            if (user.hasRole("TANTOU_EDITOR")) {
                conditions.add("s.tantouEditorId = ?");
                params.add(user.getId());
            }
            if (user.hasRole("ASSISTANT")) {
                conditions.add("t.assistantId = ?");
                params.add(user.getId());
            }
            if (conditions.isEmpty()) {
                return rows;
            }
        }

        StringBuilder sql = new StringBuilder(baseSql);
        if (!user.hasRole("ADMIN")) {
            sql.append(" WHERE (");
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                sql.append(conditions.get(i));
            }
            sql.append(")");
        }
        boolean hasWhere = !user.hasRole("ADMIN");
        if (status != null && !status.trim().isEmpty()) {
            sql.append(hasWhere ? " AND" : " WHERE");
            sql.append(" t.status = ?");
            params.add(status.trim().toUpperCase(Locale.ENGLISH));
            hasWhere = true;
        }
        if (chapterId != null) {
            sql.append(hasWhere ? " AND" : " WHERE");
            sql.append(" t.chapterId = ?");
            params.add(chapterId);
        }
        sql.append(" ORDER BY t.updatedAt DESC");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Long) {
                    ps.setLong(i + 1, ((Long) param).longValue());
                } else {
                    ps.setString(i + 1, String.valueOf(param));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapDetailed(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list tasks", ex);
        }
        return rows;
    }
    public List<TaskSummary> listByChapter(long chapterId) {
        String sql =
            "SELECT " + taskSelectColumns() + ", "
            + "c.title AS chapterTitle, c.chapterNumber, s.title AS seriesTitle, u.fullName AS assistantName "
            + "FROM PageTask t "
            + "JOIN Chapter c ON c.id = t.chapterId "
            + "JOIN Series s ON s.id = c.seriesId "
            + "LEFT JOIN [User] u ON u.id = t.assistantId "
            + "WHERE t.chapterId = ? "
            + "ORDER BY t.id DESC";
        List<TaskSummary> rows = new ArrayList<TaskSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapDetailed(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list tasks", ex);
        }
        return rows;
    }

    public TaskSummary findById(long taskId) {
        String sql =
            "SELECT " + taskSelectColumns() + ", "
            + "c.title AS chapterTitle, c.chapterNumber, s.title AS seriesTitle, u.fullName AS assistantName "
            + "FROM PageTask t "
            + "JOIN Chapter c ON c.id = t.chapterId "
            + "JOIN Series s ON s.id = c.seriesId "
            + "LEFT JOIN [User] u ON u.id = t.assistantId "
            + "WHERE t.id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapDetailed(rs);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load task", ex);
        }
    }

    public long create(long chapterId, long assistantId, int start, int end, String taskType, Date dueDate) {
        return create(chapterId, assistantId, start, end, taskType, dueDate, "NORMAL", null);
    }

    public long create(long chapterId, long assistantId, int start, int end, String taskType, Date dueDate, String priority, String notes) {
        ensureTaskLifecycleSchemaReady();
        // Only active tasks block page-range reuse; closed tasks do not block new assignment.
        String overlapSql = "SELECT COUNT(1) FROM PageTask WHERE chapterId = ? AND UPPER(status) NOT IN (" + SQL_CLOSED_TASK_STATUSES + ") AND NOT (pageRangeEnd < ? OR pageRangeStart > ?)";
        String chapterSql = "SELECT c.submissionDeadline, c.seriesId, s.mangakaId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        String enrollmentSql = "SELECT COUNT(1) FROM MangakaAssistant WHERE mangakaId = ? AND assistantId = ?";
        String insertExtendedSql = "INSERT INTO PageTask (chapterId, assistantId, pageRangeStart, pageRangeEnd, taskType, dueDate, status, rejectionCount, priority, notes, assignedAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', 0, ?, ?, GETDATE(), GETDATE())";
        String insertLegacySql = "INSERT INTO PageTask (chapterId, assistantId, pageRangeStart, pageRangeEnd, taskType, dueDate, status, rejectionCount, assignedAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', 0, GETDATE(), GETDATE())";

        try (Connection conn = dataSource.getConnection()) {
            taskType = normalizeTaskType(taskType);
            String normalizedPriority = normalizePriority(priority);
            validateTaskAssignment(conn, 0L, chapterId, assistantId, start, end, taskType, dueDate, overlapSql, chapterSql, enrollmentSql);

            long newId;
            boolean extended = isTaskSchemaExtended();
            String insertSql = extended ? insertExtendedSql : insertLegacySql;
            try (PreparedStatement insert = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, chapterId);
                insert.setLong(2, assistantId);
                insert.setInt(3, start);
                insert.setInt(4, end);
                insert.setString(5, taskType);
                insert.setDate(6, dueDate);
                if (extended) {
                    insert.setString(7, normalizedPriority);
                    insert.setString(8, notes == null ? null : notes.trim());
                }
                insert.executeUpdate();
                try (ResultSet rs = insert.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Cannot create task");
                    }
                    newId = rs.getLong(1);
                }
            }

            refreshChapterProgress(chapterId);
            createNotification(
                    assistantId,
                    "TASK_ASSIGNED",
                    "You have been assigned task #" + newId + ".",
                    newId,
                    "TASK");
            return newId;
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create task", ex);
        }
    }

    public void updateTaskByMangaka(long taskId, long mangakaId, long assistantId, int start, int end, String taskType, Date dueDate) {
        String taskInfoSql = "SELECT t.chapterId, t.assistantId, t.status FROM PageTask t WHERE t.id = ?";
        String updateSql = "UPDATE PageTask SET assistantId = ?, pageRangeStart = ?, pageRangeEnd = ?, taskType = ?, dueDate = ?, status = 'IN_PROGRESS', rejectionCount = 0, updatedAt = GETDATE(), assignedAt = GETDATE() WHERE id = ?";

        try (Connection conn = dataSource.getConnection()) {
            taskType = normalizeTaskType(taskType);
            long chapterId;
            long currentAssistantId;
            String currentStatus;
            try (PreparedStatement ps = conn.prepareStatement(taskInfoSql)) {
                ps.setLong(1, taskId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Task not found");
                    }
                    chapterId = rs.getLong("chapterId");
                    currentAssistantId = rs.getLong("assistantId");
                    currentStatus = rs.getString("status");
                }
            }

            long ownerId = findChapterOwnerMangaka(chapterId);
            if (ownerId != mangakaId) {
                throw new IllegalArgumentException("Only chapter owner can update task");
            }

            if ("APPROVED".equalsIgnoreCase(currentStatus)) {
                throw new IllegalArgumentException("Approved task cannot be edited. Create a new task instead (BR-TSK-06)");
            }

            validateTaskAssignment(
                    conn,
                    taskId,
                    chapterId,
                    assistantId,
                    start,
                    end,
                    taskType,
                    dueDate,
                    "SELECT COUNT(1) FROM PageTask WHERE chapterId = ? AND id <> ? AND UPPER(status) NOT IN (" + SQL_CLOSED_TASK_STATUSES + ") AND NOT (pageRangeEnd < ? OR pageRangeStart > ?)",
                    "SELECT c.submissionDeadline, c.seriesId, s.mangakaId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?",
                    "SELECT COUNT(1) FROM MangakaAssistant WHERE mangakaId = ? AND assistantId = ?");

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setLong(1, assistantId);
                ps.setInt(2, start);
                ps.setInt(3, end);
                ps.setString(4, taskType);
                ps.setDate(5, dueDate);
                ps.setLong(6, taskId);
                if (ps.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Task not found");
                }
            }

            refreshChapterProgress(chapterId);
            boolean reassigned = currentAssistantId != assistantId;
            createNotification(
                    assistantId,
                    reassigned ? "TASK_REASSIGNED" : "TASK_UPDATED",
                    reassigned
                            ? "Task #" + taskId + " has been assigned to you."
                            : "Task #" + taskId + " has been updated.",
                    taskId,
                    "TASK");
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update task", ex);
        }
    }

    public long reassignByMangaka(long taskId, long mangakaId, long newAssistantId, String reason) {
        return reassignByMangaka(taskId, mangakaId, newAssistantId, reason, null);
    }

    public long reassignByMangaka(long taskId, long mangakaId, long newAssistantId, String reason, Date newDueDate) {
        ensureTaskLifecycleSchemaReady();
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("Reassign reason must be at least 5 characters");
        }
        TaskSummary task = findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found");
        }
        if (findChapterOwnerMangaka(task.getChapterId()) != mangakaId) {
            throw new IllegalArgumentException("Only chapter owner can reassign task");
        }
        String currentStatusForReassign = normalizeStatus(task.getStatus());
        if (!"IN_PROGRESS".equals(currentStatusForReassign) && !"OVERDUE".equals(currentStatusForReassign)) {
            throw new IllegalArgumentException("Only IN_PROGRESS or OVERDUE task can be reassigned");
        }
        if (task.getAssistantId() == newAssistantId) {
            throw new IllegalArgumentException("Choose a different assistant");
        }
        Date dueDateForNewTask = task.getDueDate();
        if ("OVERDUE".equals(currentStatusForReassign)) {
            if (newDueDate == null) {
                throw new IllegalArgumentException("newDueDate is required when reassigning an overdue task");
            }
            validateOverdueDecisionDueDate(task.getChapterId(), newDueDate);
            dueDateForNewTask = newDueDate;
        }

        String closeSql = "UPDATE PageTask SET status = 'REASSIGNED', actionReason = ?, updatedAt = GETDATE() WHERE id = ?";
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement close = conn.prepareStatement(closeSql)) {
                close.setString(1, reason.trim());
                close.setLong(2, taskId);
                close.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot reassign task", ex);
        }

        long newTaskId = create(
                task.getChapterId(),
                newAssistantId,
                task.getPageRangeStart(),
                task.getPageRangeEnd(),
                task.getTaskType(),
                dueDateForNewTask,
                task.getPriority(),
                task.getNotes());
        setPreviousAssistantAndReason(newTaskId, task.getAssistantId(), reason.trim());
        createNotification(
                task.getAssistantId(),
                "TASK_REASSIGNED",
                "Task #" + taskId + " was reassigned. Reason: " + reason.trim(),
                taskId,
                "TASK");
        return newTaskId;
    }

    public void deleteByMangaka(long taskId, long mangakaId, String reason) {
        ensureTaskLifecycleSchemaReady();
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("Delete reason must be at least 5 characters");
        }
        TaskSummary task = findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found");
        }
        if (findChapterOwnerMangaka(task.getChapterId()) != mangakaId) {
            throw new IllegalArgumentException("Only chapter owner can delete task");
        }
        String currentStatusForDelete = normalizeStatus(task.getStatus());
        if (!"IN_PROGRESS".equals(currentStatusForDelete) && !"OVERDUE".equals(currentStatusForDelete)) {
            throw new IllegalArgumentException("Only IN_PROGRESS or OVERDUE task can be deleted");
        }

        String sql = "UPDATE PageTask SET status = 'DELETED', actionReason = ?, updatedAt = GETDATE() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason.trim());
            ps.setLong(2, taskId);
            ps.executeUpdate();
            refreshChapterProgress(task.getChapterId());
            createNotification(
                    task.getAssistantId(),
                    "TASK_DELETED",
                    "Task #" + taskId + " was deleted. Reason: " + reason.trim(),
                    taskId,
                    "TASK");
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot delete task", ex);
        }
    }

    private void setPreviousAssistantAndReason(long taskId, long previousAssistantId, String reason) {
        String sql = "UPDATE PageTask SET previousAssistantId = ?, actionReason = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, previousAssistantId);
            ps.setString(2, reason);
            ps.setLong(3, taskId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update reassignment metadata", ex);
        }
    }

    private String normalizeTaskType(String taskType) {
        if (taskType == null || taskType.trim().isEmpty()) {
            throw new IllegalArgumentException("taskType is required");
        }
        String normalized = taskType.trim().toUpperCase(Locale.ENGLISH);
        if (!"SKETCHING".equals(normalized)
                && !"INKING".equals(normalized)
                && !"COLORING".equals(normalized)
                && !"LETTERING".equals(normalized)
                && !"SCREENTONE".equals(normalized)
                && !"MIXED".equals(normalized)) {
            throw new IllegalArgumentException("taskType must be SKETCHING, INKING, COLORING, SCREENTONE, LETTERING, or MIXED");
        }
        return normalized;
    }
    private void validateTaskAssignment(
            Connection conn,
            long taskId,
            long chapterId,
            long assistantId,
            int start,
            int end,
            String taskType,
            Date dueDate,
            String overlapSql,
            String chapterSql,
            String enrollmentSql) throws SQLException {

        if (assistantId <= 0) {
            throw new IllegalArgumentException("assistantId is required");
        }
        if (taskType == null || taskType.trim().isEmpty()) {
            throw new IllegalArgumentException("taskType is required");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("dueDate is required");
        }
        if (dueDate.before(Date.valueOf(LocalDate.now()))) {
            throw new IllegalArgumentException("Task dueDate cannot be in the past");
        }
        if (end < start) {
            throw new IllegalArgumentException("pageRangeEnd must be >= pageRangeStart");
        }

        String completePageSql = "SELECT TOP 1 pageNumber FROM " + PageRepository.TABLE_PAGE
                + " WHERE chapterId = ? AND pageNumber BETWEEN ? AND ? "
                + "AND UPPER(ISNULL(completedStage, '')) = 'LETTERING' ORDER BY pageNumber";
        try (PreparedStatement ps = conn.prepareStatement(completePageSql)) {
            ps.setLong(1, chapterId);
            ps.setInt(2, start);
            ps.setInt(3, end);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("Page " + rs.getInt("pageNumber") + " is already complete and cannot be assigned");
                }
            }
        }

        if (taskId == 0L) {
            try (PreparedStatement overlap = conn.prepareStatement(overlapSql)) {
                overlap.setLong(1, chapterId);
                overlap.setInt(2, start);
                overlap.setInt(3, end);
                try (ResultSet rs = overlap.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        throw new IllegalArgumentException("Page range overlaps existing task (BR-33)");
                    }
                }
            }
        } else {
            try (PreparedStatement overlap = conn.prepareStatement(overlapSql)) {
                overlap.setLong(1, chapterId);
                overlap.setLong(2, taskId);
                overlap.setInt(3, start);
                overlap.setInt(4, end);
                try (ResultSet rs = overlap.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        throw new IllegalArgumentException("Page range overlaps existing task (BR-33)");
                    }
                }
            }
        }

        long mangakaId;
        Date submissionDeadline;
        try (PreparedStatement chapter = conn.prepareStatement(chapterSql)) {
            chapter.setLong(1, chapterId);
            try (ResultSet rs = chapter.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Chapter not found");
                }
                submissionDeadline = rs.getDate("submissionDeadline");
                mangakaId = rs.getLong("mangakaId");
            }
        }

        if (assistantId == mangakaId) {
            throw new IllegalArgumentException("Mangaka cannot self-assign page task (BR-35)");
        }

        Date deadline3DaysBefore = new Date(submissionDeadline.getTime() - (3L * 24L * 60L * 60L * 1000L));
        if (dueDate.after(deadline3DaysBefore)) {
            throw new IllegalArgumentException("Task dueDate must be at least 3 days before chapter submissionDeadline (BR-34)");
        }

        try (PreparedStatement enrollment = conn.prepareStatement(enrollmentSql)) {
            enrollment.setLong(1, mangakaId);
            enrollment.setLong(2, assistantId);
            try (ResultSet rs = enrollment.executeQuery()) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    throw new IllegalArgumentException("Assistant must be assigned to mangaka (BR-36)");
                }
            }
        }
    }

    public void updateStatusByAssistant(long taskId, long assistantId, String status) {
        ensureTaskLifecycleSchemaReady();
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!"SUBMITTED".equals(normalized)) {
            throw new IllegalArgumentException("Assistant can only submit task for review");
        }

        String readSql = "SELECT chapterId, assistantId, pageRangeStart, pageRangeEnd, status, taskType FROM PageTask WHERE id = ?";
        String updateSql = "UPDATE PageTask SET status = ?, updatedAt = GETDATE(), lastProgressAt = GETDATE() WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement read = conn.prepareStatement(readSql)) {
            read.setLong(1, taskId);
            long chapterId;
            long ownerAssistantId;
            int pageRangeStart;
            int pageRangeEnd;
            String currentStatus;
            try (ResultSet rs = read.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Task not found");
                }
                chapterId = rs.getLong("chapterId");
                ownerAssistantId = rs.getLong("assistantId");
                pageRangeStart = rs.getInt("pageRangeStart");
                pageRangeEnd = rs.getInt("pageRangeEnd");
                currentStatus = rs.getString("status");
            }

            if (ownerAssistantId != assistantId) {
                throw new IllegalArgumentException("Task not assigned to this assistant (BR-42)");
            }

            String current = normalizeStatus(currentStatus);
            // Assistants may still submit late while Mangaka is deciding what to do with an OVERDUE task.
            if (!("IN_PROGRESS".equals(current)
                    || "REJECTED".equals(current)
                    || "OVERDUE".equals(current))) {
                throw new IllegalArgumentException("Assistant can submit only from active/rework task state (BR-TSK-01)");
            }

            validateSubmittedTaskImages(conn, taskId, pageRangeStart, pageRangeEnd);

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, normalized);
                ps.setLong(2, taskId);
                ps.executeUpdate();
            }

            refreshChapterProgress(chapterId);
            long mangakaId = findChapterOwnerMangaka(chapterId);
            createNotification(
                    mangakaId,
                    "TASK_SUBMITTED",
                    "Task #" + taskId + " has been submitted for your review by assistant.",
                    taskId,
                    "TASK");
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update task status", ex);
        }
    }

    private void validateSubmittedTaskImages(Connection conn, long taskId, int pageRangeStart, int pageRangeEnd) throws SQLException {
        int expected = pageRangeEnd - pageRangeStart + 1;
        String sql = "SELECT COUNT(DISTINCT pageNumber) "
                + "FROM ChapterImage "
                + "WHERE pageTaskId = ? "
                + "AND isActive = 1 "
                + "AND imageType = 'PAGE' "
                + "AND pageNumber BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ps.setInt(2, pageRangeStart);
            ps.setInt(3, pageRangeEnd);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int uploaded = rs.getInt(1);
                if (uploaded < expected) {
                    throw new IllegalArgumentException("Upload all task pages to ChapterImage before submitting (" + uploaded + "/" + expected + ")");
                }
            }
        }
    }

    public void approveByMangaka(long taskId, long mangakaId, String comment) {
        String readSql = "SELECT chapterId, assistantId, status, taskType FROM PageTask WHERE id = ?";
        String updateExtendedSql = "UPDATE PageTask SET status = 'APPROVED', approvalComment = ?, updatedAt = GETDATE() WHERE id = ?";
        String updateLegacySql = "UPDATE PageTask SET status = 'APPROVED', updatedAt = GETDATE() WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement read = conn.prepareStatement(readSql)) {
            read.setLong(1, taskId);
            long chapterId;
            long assistantId;
            String currentStatus;
            String taskType;
            try (ResultSet rs = read.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Task not found");
                }
                chapterId = rs.getLong("chapterId");
                assistantId = rs.getLong("assistantId");
                currentStatus = rs.getString("status");
                taskType = rs.getString("taskType");
            }

            long ownerId = findChapterOwnerMangaka(chapterId);
            if (ownerId != mangakaId) {
                throw new IllegalArgumentException("Only chapter owner Mangaka can approve (BR-39)");
            }

            if (!"SUBMITTED".equals(normalizeStatus(currentStatus))) {
                throw new IllegalArgumentException("Only SUBMITTED task can be approved (BR-39)");
            }

            boolean extended = isTaskSchemaExtended();
            if (extended) {
                try (PreparedStatement ps = conn.prepareStatement(updateExtendedSql)) {
                    ps.setString(1, comment == null ? null : comment.trim());
                    ps.setLong(2, taskId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(updateLegacySql)) {
                    ps.setLong(1, taskId);
                    ps.executeUpdate();
                }
            }

            promoteTaskImagesToChapter(taskId, chapterId, mangakaId, taskType);
            refreshChapterProgress(chapterId);

            String approveMsg = "Task #" + taskId + " has been approved.";
            if (comment != null && !comment.trim().isEmpty()) {
                approveMsg += " Comment: " + comment.trim();
            }
            createNotification(assistantId, "TASK_APPROVED", approveMsg, taskId, "TASK");
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot approve task", ex);
        }
    }

    private void promoteTaskImagesToChapter(long taskId, long chapterId, long approvedBy, String completedStage) {
        List<ChapterImageItem> images = chapterImageRepository.listByTask(taskId);
        for (ChapterImageItem image : images) {
            if (image.getPageNumber() == null || !"PAGE".equalsIgnoreCase(image.getImageType())) {
                continue;
            }
            pageRepository.promoteTaskImage(
                    chapterId,
                    image.getPageNumber().intValue(),
                    image.getFileUrl(),
                    approvedBy,
                    completedStage);
        }
    }

    public int rejectByMangaka(long taskId, long mangakaId, String reason) {
        String readSql =
                "SELECT t.chapterId, t.assistantId, t.status, t.rejectionCount, t.dueDate, s.publicationDate AS seriesDeadline "
                + "FROM PageTask t "
                + "JOIN Chapter c ON c.id = t.chapterId "
                + "JOIN Series s ON s.id = c.seriesId "
                + "WHERE t.id = ?";
        String updateExtendedSql = "UPDATE PageTask SET status = 'IN_PROGRESS', rejectionCount = ?, rejectionReason = ?, dueDate = ?, updatedAt = GETDATE() WHERE id = ?";
        String updateLegacySql = "UPDATE PageTask SET status = 'IN_PROGRESS', rejectionCount = ?, dueDate = ?, updatedAt = GETDATE() WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement read = conn.prepareStatement(readSql)) {
            read.setLong(1, taskId);
            long chapterId;
            long assistantId;
            String currentStatus;
            int currentReject;
            Date currentDueDate;
            Date seriesDeadline;
            try (ResultSet rs = read.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Task not found");
                }
                chapterId = rs.getLong("chapterId");
                assistantId = rs.getLong("assistantId");
                currentStatus = rs.getString("status");
                currentReject = rs.getInt("rejectionCount");
                currentDueDate = rs.getDate("dueDate");
                seriesDeadline = rs.getDate("seriesDeadline");
            }

            long ownerId = findChapterOwnerMangaka(chapterId);
            if (ownerId != mangakaId) {
                throw new IllegalArgumentException("Only chapter owner Mangaka can reject (BR-38)");
            }

            if (!"SUBMITTED".equals(normalizeStatus(currentStatus))) {
                throw new IllegalArgumentException("Only SUBMITTED task can be rejected (BR-38)");
            }

            int next = currentReject + 1;
            Date nextDueDate = extendedRejectDueDate(currentDueDate, seriesDeadline);
            boolean extended = isTaskSchemaExtended();
            if (extended) {
                try (PreparedStatement update = conn.prepareStatement(updateExtendedSql)) {
                    update.setInt(1, next);
                    update.setString(2, reason == null ? null : reason.trim());
                    update.setDate(3, nextDueDate);
                    update.setLong(4, taskId);
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement update = conn.prepareStatement(updateLegacySql)) {
                    update.setInt(1, next);
                    update.setDate(2, nextDueDate);
                    update.setLong(3, taskId);
                    update.executeUpdate();
                }
            }

            refreshChapterProgress(chapterId);

            String feedback = reason == null ? "" : reason.trim();
            createNotification(
                    assistantId,
                    "TASK_REJECTED",
                    "Task #" + taskId + " needs rework."
                            + (feedback.isEmpty() ? "" : " Reason: " + feedback),
                    taskId,
                    "TASK");

            return next;
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot reject task", ex);
        }
    }

    private Date extendedRejectDueDate(Date currentDueDate, Date seriesDeadline) {
        if (currentDueDate == null || seriesDeadline == null) {
            return currentDueDate;
        }
        LocalDate proposed = currentDueDate.toLocalDate().plusDays(1);
        LocalDate latestAllowed = seriesDeadline.toLocalDate().minusDays(TASK_REJECT_SERIES_DEADLINE_BUFFER_DAYS);
        if (proposed.isAfter(latestAllowed)) {
            return currentDueDate;
        }
        return Date.valueOf(proposed);
    }

    public long findChapterOwnerMangaka(long chapterId) {
        String sql = "SELECT s.mangakaId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Chapter not found");
                }
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load chapter owner", ex);
        }
    }

    public long findChapterTantouEditor(long chapterId) {
        String sql = "SELECT s.tantouEditorId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Chapter not found");
                }
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load chapter tantou", ex);
        }
    }

    public long findTaskAssistantId(long taskId) {
        String sql = "SELECT assistantId FROM PageTask WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Task not found");
                }
                return rs.getLong("assistantId");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load task assistant", ex);
        }
    }

    public long findTaskChapterId(long taskId) {
        String sql = "SELECT chapterId FROM PageTask WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Task not found");
                }
                return rs.getLong("chapterId");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load task chapter", ex);
        }
    }

    public long getTaskOwnerMangaka(long taskId) {
        return findChapterOwnerMangaka(findTaskChapterId(taskId));
    }

    public long getTaskTantouEditor(long taskId) {
        return findChapterTantouEditor(findTaskChapterId(taskId));
    }

    public int markOverdueTasks() {
        /*
         * OVERDUE is a lifecycle state: due date has passed and task still needs action.
         * It is separate from the delayed warning flag.
         */
        String selectSql =
            "SELECT t.id, t.chapterId, t.assistantId, s.mangakaId "
            + "FROM PageTask t "
            + "JOIN Chapter c ON c.id = t.chapterId "
            + "JOIN Series s ON s.id = c.seriesId "
            + "WHERE t.dueDate < CAST(GETDATE() AS DATE) AND t.status NOT IN (" + SQL_OVERDUE_SKIP_STATUSES + ")";
        String updateSql = "UPDATE PageTask SET status = 'OVERDUE', updatedAt = GETDATE() WHERE id = ?";

        int changed = 0;
        Set<Long> chapters = new HashSet<Long>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement select = conn.prepareStatement(selectSql);
             ResultSet rs = select.executeQuery()) {

            List<long[]> rows = new ArrayList<long[]>();
            while (rs.next()) {
                rows.add(new long[] { rs.getLong("id"), rs.getLong("chapterId"), rs.getLong("assistantId"), rs.getLong("mangakaId") });
            }

            for (long[] row : rows) {
                try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                    update.setLong(1, row[0]);
                    if (update.executeUpdate() > 0) {
                        changed++;
                        chapters.add(row[1]);
                        // Notify mangaka: action required
                        createNotificationIfAbsentToday(
                                row[3],
                                "TASK_OVERDUE_ACTION_REQUIRED",
                                "Task #" + row[0] + " is overdue. Please decide: extend, reassign, or cancel.",
                                row[0],
                                "TASK");
                        // Notify assistant
                        createNotificationIfAbsentToday(
                                row[2],
                                "TASK_OVERDUE",
                                "Task #" + row[0] + " is overdue. Please contact your Mangaka.",
                                row[0],
                                "TASK");
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot mark overdue tasks", ex);
        }

        for (Long chapterId : chapters) {
            refreshChapterProgress(chapterId.longValue());
        }
        return changed;
    }

    /**
     * Auto-cancel tasks that have been OVERDUE for 3+ days with no mangaka decision.
     * Runs daily at 09:00 via PageTaskScheduler.
     */
    public int escalatePendingOverdueDecisions() {
        ensureTaskLifecycleSchemaReady();
        String selectSql =
            "SELECT t.id, t.chapterId, t.assistantId "
            + "FROM PageTask t "
            + "WHERE t.status = 'OVERDUE' "
            + "AND DATEDIFF(DAY, t.updatedAt, GETDATE()) >= 3";
        String updateSql =
            "UPDATE PageTask SET status = 'CANCELLED', actionReason = 'Auto-cancelled: no mangaka decision within 3 days of overdue', "
            + "updatedAt = GETDATE() WHERE id = ? AND status = 'OVERDUE'";

        int cancelled = 0;
        Set<Long> chapters = new HashSet<Long>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement select = conn.prepareStatement(selectSql);
             ResultSet rs = select.executeQuery()) {

            List<long[]> rows = new ArrayList<long[]>();
            while (rs.next()) {
                rows.add(new long[] { rs.getLong("id"), rs.getLong("chapterId"), rs.getLong("assistantId") });
            }

            for (long[] row : rows) {
                try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                    update.setLong(1, row[0]);
                    if (update.executeUpdate() > 0) {
                        cancelled++;
                        chapters.add(row[1]);
                        createNotificationIfAbsentToday(
                                row[2],
                                "TASK_CANCELLED",
                                "Task #" + row[0] + " has been auto-cancelled: no mangaka decision within 3 days of overdue.",
                                row[0],
                                "TASK");
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot escalate overdue decisions", ex);
        }

        for (Long chapterId : chapters) {
            refreshChapterProgress(chapterId.longValue());
        }
        return cancelled;
    }

    /**
     * Extend an OVERDUE task: set new dueDate, reset status to IN_PROGRESS.
     * Only the chapter owner Mangaka can call this.
     */
    public void extendOverdueTask(long taskId, long mangakaId, java.sql.Date newDueDate, String reason) {
        ensureTaskLifecycleSchemaReady();
        if (newDueDate == null) {
            throw new IllegalArgumentException("newDueDate is required");
        }
        if (newDueDate.before(java.sql.Date.valueOf(java.time.LocalDate.now()))) {
            throw new IllegalArgumentException("newDueDate cannot be in the past");
        }
        TaskSummary task = findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found");
        }
        if (findChapterOwnerMangaka(task.getChapterId()) != mangakaId) {
            throw new IllegalArgumentException("Only chapter owner can extend task");
        }
        if (!"OVERDUE".equals(normalizeStatus(task.getStatus()))) {
            throw new IllegalArgumentException("Only OVERDUE task can be extended");
        }
        validateOverdueDecisionDueDate(task.getChapterId(), newDueDate);

        String sql = "UPDATE PageTask SET dueDate = ?, status = 'IN_PROGRESS', updatedAt = GETDATE() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, newDueDate);
            ps.setLong(2, taskId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Task not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot extend task", ex);
        }

        refreshChapterProgress(task.getChapterId());
        String msg = "Task #" + taskId + " deadline extended to " + newDueDate + ".";
        if (reason != null && !reason.trim().isEmpty()) {
            msg += " Reason: " + reason.trim();
        }
        createNotification(task.getAssistantId(), "TASK_EXTENDED", msg, taskId, "TASK");
    }

    private void validateOverdueDecisionDueDate(long chapterId, Date dueDate) {
        if (dueDate.before(Date.valueOf(LocalDate.now()))) {
            throw new IllegalArgumentException("Task dueDate cannot be in the past");
        }
        Date latestDueDate = findLatestTaskDueDate(chapterId);
        if (dueDate.after(latestDueDate)) {
            throw new IllegalArgumentException("Task dueDate must be at least 3 days before chapter submissionDeadline (BR-34)");
        }
    }

    private Date findLatestTaskDueDate(long chapterId) {
        String sql = "SELECT submissionDeadline FROM Chapter WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Chapter not found");
                }
                Date submissionDeadline = rs.getDate("submissionDeadline");
                return Date.valueOf(submissionDeadline.toLocalDate().minusDays(TASK_REJECT_SERIES_DEADLINE_BUFFER_DAYS));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot validate task due date", ex);
        }
    }

    public int notifyDueSoonTasks() {
        String sql =
            "SELECT id, assistantId FROM PageTask "
            + "WHERE dueDate = DATEADD(DAY, 1, CAST(GETDATE() AS DATE)) "
            + "AND status IN ('PENDING','IN_PROGRESS','REJECTED','SUBMITTED')";
        int sent = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long taskId = rs.getLong("id");
                long assistantId = rs.getLong("assistantId");
                if (createNotificationIfAbsentToday(
                        assistantId,
                        "TASK_DUE_SOON",
                        "Task #" + taskId + " is due within 24 hours.",
                        taskId,
                        "TASK")) {
                    sent++;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot send due-soon reminders", ex);
        }
        return sent;
    }

    /** BR-TSK-08: flag delayed tasks (computed on read) and notify Mangaka once per day. */
    public int markDelayedTasks() {
        String sql =
            "SELECT t.id, s.mangakaId "
            + "FROM PageTask t "
            + "JOIN Chapter c ON c.id = t.chapterId "
            + "JOIN Series s ON s.id = c.seriesId "
            + "WHERE t.status IN ('PENDING','IN_PROGRESS','REJECTED') "
            + "AND DATEDIFF(DAY, t.assignedAt, GETDATE()) >= 3 "
            + "AND DATEDIFF(DAY, COALESCE(t.lastProgressAt, t.assignedAt), GETDATE()) >= 3";

        int sent = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long taskId = rs.getLong("id");
                long mangakaId = rs.getLong("mangakaId");
                if (createNotificationIfAbsentToday(
                        mangakaId,
                        "TASK_DELAYED",
                        "Task #" + taskId + " is delayed (no update for 3+ days since assignment).",
                        taskId,
                        "TASK")) {
                    sent++;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot mark delayed tasks", ex);
        }
        return sent;
    }

    public void refreshChapterProgress(long chapterId) {
        pageRepository.ensurePageStageColumnReady();
        String readRiskSql = "SELECT atRisk FROM Chapter WHERE id = ?";
        String updateSql =
            "UPDATE c SET "
            + "completionPct = stats.completionPct, "
            + "status = CASE "
            + "  WHEN stats.totalPages = 0 AND c.status IN ('PLANNING','IN_PROGRESS','COMPLETE') THEN 'PLANNING' "
            + "  WHEN stats.totalPages > 0 AND stats.completionPct >= 100 AND c.status IN ('PLANNING','IN_PROGRESS','COMPLETE') THEN 'COMPLETE' "
            + "  WHEN stats.totalPages > 0 AND stats.completionPct < 100 AND c.status IN ('PLANNING','IN_PROGRESS','COMPLETE') THEN 'IN_PROGRESS' "
            + "  ELSE c.status END, "
            + "atRisk = CASE "
            + "  WHEN c.submissionDeadline < CAST(GETDATE() AS DATE) AND stats.completionPct < 100 THEN 1 "
            + "  WHEN DATEDIFF(DAY, CAST(c.createdAt AS DATE), c.submissionDeadline) > 0 "
            + "       AND stats.completionPct < 50 "
            + "       AND (100.0 * DATEDIFF(DAY, CAST(c.createdAt AS DATE), CAST(GETDATE() AS DATE)) / DATEDIFF(DAY, CAST(c.createdAt AS DATE), c.submissionDeadline)) > 70 "
            + "  THEN 1 ELSE 0 END "
            + "FROM Chapter c "
            + "CROSS APPLY ( "
            + "  SELECT "
            + "    COUNT(1) AS totalPages, "
            + "    CAST(ROUND(CASE WHEN COUNT(1)=0 THEN 0 ELSE (100.0 * SUM(CASE UPPER(ISNULL(p.completedStage, '')) "
            + "      WHEN 'SKETCHING' THEN 1 WHEN 'INKING' THEN 2 WHEN 'COLORING' THEN 3 "
            + "      WHEN 'SCREENTONE' THEN 4 WHEN 'LETTERING' THEN 5 ELSE 0 END) / (COUNT(1) * 5)) END, 2) AS DECIMAL(5,2)) AS completionPct "
            + "  FROM " + PageRepository.TABLE_PAGE + " p WHERE p.chapterId = c.id "
            + ") stats "
            + "WHERE c.id = ?";

        try (Connection conn = dataSource.getConnection()) {
            boolean wasAtRisk = false;
            try (PreparedStatement read = conn.prepareStatement(readRiskSql)) {
                read.setLong(1, chapterId);
                try (ResultSet rs = read.executeQuery()) {
                    if (rs.next()) {
                        wasAtRisk = rs.getBoolean("atRisk");
                    }
                }
            }

            try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setLong(1, chapterId);
                update.executeUpdate();
            }

            boolean nowAtRisk = false;
            try (PreparedStatement read = conn.prepareStatement(readRiskSql)) {
                read.setLong(1, chapterId);
                try (ResultSet rs = read.executeQuery()) {
                    if (rs.next()) {
                        nowAtRisk = rs.getBoolean("atRisk");
                    }
                }
            }

            if (!wasAtRisk && nowAtRisk) {
                long tantouId = findChapterTantouEditor(chapterId);
                createNotificationIfAbsentToday(
                        tantouId,
                        "CHAPTER_AT_RISK",
                        "Chapter #" + chapterId + " is at risk (progress below expected timeline).",
                        chapterId,
                        "CHAPTER");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot refresh chapter progress", ex);
        }
    }

    public void createNotification(long userId, String type, String message, long referenceId, String referenceType) {
        String sql = "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, 0, GETDATE())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, type);
            ps.setString(3, notificationTitle(type));
            ps.setString(4, message);
            ps.setString(5, notificationViewUrl(type, referenceId, referenceType));
            ps.setLong(6, referenceId);
            ps.setString(7, referenceType);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create notification", ex);
        }
    }

    public boolean areAllTasksApproved(long chapterId) {
        // Closed tasks are excluded from the active denominator so they do not block completion.
        String sql = "SELECT "
                + "SUM(CASE WHEN UPPER(status) = 'APPROVED' THEN 1 ELSE 0 END) AS approvedCount, "
                + "SUM(CASE WHEN UPPER(status) NOT IN ('DELETED','REASSIGNED','CANCELLED') THEN 1 ELSE 0 END) AS totalCount "
                + "FROM PageTask WHERE chapterId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int approvedCount = rs.getInt("approvedCount");
                    int totalCount = rs.getInt("totalCount");
                    return totalCount > 0 && approvedCount == totalCount;
                }
                return false;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check chapter task approval status", ex);
        }
    }

    public boolean areAllPagesFullyCompleted(long chapterId) {
        pageRepository.ensurePageStageColumnReady();
        String sql = "SELECT "
                + "COUNT(1) AS totalCount, "
                + "SUM(CASE WHEN UPPER(ISNULL(p.completedStage, '')) = 'LETTERING' THEN 1 ELSE 0 END) AS completedCount "
                + "FROM " + PageRepository.TABLE_PAGE + " WHERE chapterId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int totalCount = rs.getInt("totalCount");
                    int completedCount = rs.getInt("completedCount");
                    return totalCount > 0 && completedCount == totalCount;
                }
                return false;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check chapter page completion status", ex);
        }
    }

    public boolean createNotificationIfAbsentToday(long userId, String type, String message, long referenceId, String referenceType) {
        String checkSql =
            "SELECT COUNT(1) FROM Notification "
            + "WHERE userId = ? AND type = ? AND referenceId = ? "
            + "AND CAST(createdAt AS DATE) = CAST(GETDATE() AS DATE)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setLong(1, userId);
            check.setString(2, type);
            check.setLong(3, referenceId);
            try (ResultSet rs = check.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    return false;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check notification duplication", ex);
        }

        createNotification(userId, type, message, referenceId, referenceType);
        return true;
    }

    private String notificationTitle(String type) {
        if (type == null) {
            return "Notification";
        }
        String normalized = type.trim().toUpperCase(Locale.ENGLISH);
        if ("TASK_ASSIGNED".equals(normalized)) {
            return "New page task assigned";
        }
        if ("TASK_SUBMITTED".equals(normalized)) {
            return "Task submitted for review";
        }
        if ("TASK_APPROVED".equals(normalized)) {
            return "Task approved";
        }
        if ("TASK_REJECTED".equals(normalized)) {
            return "Task rejected - rework needed";
        }
        if ("TASK_DELETED".equals(normalized)) {
            return "Task deleted";
        }
        if ("TASK_REASSIGNED".equals(normalized)) {
            return "Task reassigned";
        }
        if ("TASK_DUE_SOON".equals(normalized)) {
            return "Task due in 24 hours";
        }
        if ("TASK_DELAYED".equals(normalized)) {
            return "Task delayed";
        }
        if ("TASK_OVERDUE".equals(normalized)) {
            return "Task overdue";
        }
        if ("TASK_OVERDUE_ACTION_REQUIRED".equals(normalized)) {
            return "Task overdue - action required";
        }
        if ("TASK_EXTENDED".equals(normalized)) {
            return "Task deadline extended";
        }
        if ("TASK_CANCELLED".equals(normalized)) {
            return "Task cancelled";
        }
        if ("TASK_ESCALATED".equals(normalized)) {
            return "Task escalated";
        }
        if ("CHAPTER_AT_RISK".equals(normalized)) {
            return "Chapter at risk";
        }
        if (normalized.startsWith("MANUSCRIPT")) {
            return "Manuscript update";
        }
        if (normalized.startsWith("CHAPTER")) {
            return "Chapter update";
        }
        return "Notification";
    }

    private String notificationViewUrl(String type, long referenceId, String referenceType) {
        if (referenceId <= 0 || referenceType == null) {
            return null;
        }
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ENGLISH);
        String normalizedRef = referenceType.trim().toUpperCase(Locale.ENGLISH);
        if ("TASK".equals(normalizedRef) || "PAGETASK".equals(normalizedRef)) {
            if ("TASK_ESCALATED".equals(normalizedType)) {
                return "/main/tasks/" + referenceId + "?tab=history";
            }
            return "/main/tasks/" + referenceId;
        }
        if ("CHAPTER".equals(normalizedRef)) {
            return "/main/chapters/" + referenceId;
        }
        if ("MANUSCRIPT".equals(normalizedRef)) {
            return "/main/notifications";
        }
        if ("DECISION".equals(normalizedRef) || "DECISION_SESSION".equals(normalizedRef)) {
            return "/main/decisions/" + referenceId;
        }
        if ("PROPOSAL".equals(normalizedRef)) {
            return "/main/proposals/" + referenceId;
        }
        if ("SERIES".equals(normalizedRef)) {
            return "/main/notifications";
        }
        return null;
    }

    private TaskSummary mapDetailed(ResultSet rs) throws SQLException {
        TaskSummary t = map(rs);
        t.setChapterTitle(rs.getString("chapterTitle"));
        t.setChapterNumber(rs.getInt("chapterNumber"));
        t.setSeriesTitle(rs.getString("seriesTitle"));
        t.setAssistantName(rs.getString("assistantName"));
        t.setDelayed(rs.getBoolean("isDelayed"));
        return t;
    }
    private TaskSummary map(ResultSet rs) throws SQLException {
        TaskSummary t = new TaskSummary();
        t.setId(rs.getLong("id"));
        t.setChapterId(rs.getLong("chapterId"));
        t.setAssistantId(rs.getLong("assistantId"));
        t.setPageRangeStart(rs.getInt("pageRangeStart"));
        t.setPageRangeEnd(rs.getInt("pageRangeEnd"));
        t.setTaskType(rs.getString("taskType"));
        t.setDueDate(rs.getDate("dueDate"));
        t.setStatus(rs.getString("status"));
        t.setRejectionCount(rs.getInt("rejectionCount"));
        if (hasColumn(rs, "priority")) {
            t.setPriority(rs.getString("priority"));
        } else {
            t.setPriority("NORMAL");
        }
        if (hasColumn(rs, "notes")) {
            t.setNotes(rs.getString("notes"));
        }
        if (hasColumn(rs, "rejectionReason")) {
            t.setRejectionReason(rs.getString("rejectionReason"));
        }
        if (hasColumn(rs, "approvalComment")) {
            t.setApprovalComment(rs.getString("approvalComment"));
        }
        if (hasColumn(rs, "actionReason")) {
            t.setActionReason(rs.getString("actionReason"));
        }
        if (hasColumn(rs, "previousAssistantId")) {
            long previousAssistantId = rs.getLong("previousAssistantId");
            t.setPreviousAssistantId(rs.wasNull() ? null : Long.valueOf(previousAssistantId));
        }
        return t;
    }

    public void updateTaskProgress(long taskId, long mangakaId, Date dueDate, String priority, String notes) {
        String readSql = "SELECT chapterId, status FROM PageTask WHERE id = ?";
        String updateExtendedSql = "UPDATE PageTask SET dueDate = ?, priority = ?, notes = ?, updatedAt = GETDATE() WHERE id = ?";
        String updateLegacySql = "UPDATE PageTask SET dueDate = ?, updatedAt = GETDATE() WHERE id = ?";
        try (Connection conn = dataSource.getConnection()) {
            long chapterId;
            String status;
            try (PreparedStatement ps = conn.prepareStatement(readSql)) {
                ps.setLong(1, taskId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Task not found");
                    }
                    chapterId = rs.getLong("chapterId");
                    status = rs.getString("status");
                }
            }
            long ownerId = findChapterOwnerMangaka(chapterId);
            if (ownerId != mangakaId) {
                throw new IllegalArgumentException("Only chapter owner can update task");
            }
            if ("APPROVED".equalsIgnoreCase(status)) {
                throw new IllegalArgumentException("Approved task cannot be edited. Create a new task instead (BR-TSK-06)");
            }
            if (isTaskSchemaExtended()) {
                String normalizedPriority = normalizePriority(priority);
                try (PreparedStatement ps = conn.prepareStatement(updateExtendedSql)) {
                    ps.setDate(1, dueDate);
                    ps.setString(2, normalizedPriority);
                    ps.setString(3, notes == null ? null : notes.trim());
                    ps.setLong(4, taskId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(updateLegacySql)) {
                    ps.setDate(1, dueDate);
                    ps.setLong(2, taskId);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update task progress", ex);
        }
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.trim().isEmpty()) {
            return "NORMAL";
        }
        String normalized = priority.trim().toUpperCase(Locale.ENGLISH);
        if (!"NORMAL".equals(normalized) && !"HIGH".equals(normalized) && !"URGENT".equals(normalized)) {
            throw new IllegalArgumentException("priority must be NORMAL, HIGH, or URGENT");
        }
        return normalized;
    }
}

