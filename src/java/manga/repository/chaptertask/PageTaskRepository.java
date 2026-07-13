package manga.repository.chaptertask;

import manga.model.AuthenticatedUser;
import manga.model.chaptertask.ChapterImageItem;
import manga.model.chaptertask.TaskReviewHistoryEntry;
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
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * ============================================================
 * PageTaskRepository - Quản lý Page Task (nhiệm vụ trang)
 * ============================================================
 *
 * MỤC LỤC:
 * ----------------------------------------------------------
 * [1] HẰNG SỐ & TRẠNG THÁI
 * [2] SCHEMA GUARD - Kiểm tra & tự cập nhật cấu trúc DB
 * [3] TRUY VẤN TASK
 *     - listVisible()       : Liệt kê task theo quyền người dùng
 *     - listByChapter()     : Liệt kê task theo chapter
 *     - findById()          : Tìm task theo ID
 * [4] TẠO & CẬP NHẬT TASK (Mangaka)
 *     - create()            : Tạo task mới
 *     - updateTaskProgress() : Cập nhật due date / priority / notes
 * [5] VÒNG ĐỜI TASK - Nộp / Duyệt / Từ chối
 *     - updateStatusByAssistant(): Assistant nộp task
 *     - approveByMangaka()  : Mangaka duyệt task
 *     - rejectByMangaka()   : Mangaka từ chối task
 * [6] VÒNG ĐỜI TASK - Phân công lại / Xoá / Huỷ
 *     - reassignByMangaka() : Phân công lại cho assistant khác
 *     - deleteByMangaka()   : Xoá task
 *     - escalatePendingOverdueDecisions(): Tự huỷ task OVERDUE sau 3 ngày không có quyết định
 * [7] QUẢN LÝ OVERDUE
 *     - markOverdueTasks()  : Đánh dấu task quá hạn
 *     - extendOverdueTask() : Gia hạn task OVERDUE
 * [8] NHẮC NHỞ & THÔNG BÁO
 *     - notifyDueSoonTasks(): Nhắc task sắp đến hạn (24h)
 *     - markDelayedTasks()  : Nhắc task bị trễ tiến độ (3+ ngày)
 * [9] TIẾN ĐỘ CHAPTER
 *     - refreshChapterProgress(): Tính lại % hoàn thành chapter
 *     - areAllTasksApproved()  : Kiểm tra tất cả task đã duyệt chưa
 *     - areAllPagesFullyCompleted(): Kiểm tra tất cả trang đã hoàn tất chưa
 * [10] THÔNG BÁO (Notification)
 *     - createNotification()
 *     - createNotificationIfAbsentToday()
 * [11] HELPER / UTILITY
 * ============================================================
 */
@Repository
public class PageTaskRepository {

    // ============================================================
    // [1] HẰNG SỐ & TRẠNG THÁI
    // ============================================================

    /** Số ngày buffer tối thiểu giữa dueDate của task và submissionDeadline của chapter (BR-34) */
    private static final int TASK_REJECT_SERIES_DEADLINE_BUFFER_DAYS = 3;

    /** Các trạng thái "đã đóng" — task ở các trạng thái này không còn block việc tái sử dụng page range */
    private static final String SQL_CLOSED_TASK_STATUSES = "'APPROVED','DELETED','REASSIGNED','CANCELLED'";

    /** Các trạng thái bị bỏ qua khi chạy job đánh dấu OVERDUE */
    private static final String SQL_OVERDUE_SKIP_STATUSES = "'APPROVED','OVERDUE','CANCELLED','DELETED','REASSIGNED','SUBMITTED'";

    /**
     * Cờ DELAYED (chỉ cảnh báo, không thay đổi status):
     * Task bị coi là "chậm" nếu không có cập nhật tiến độ trong 3+ ngày kể từ lúc được giao.
     * Khác với OVERDUE (dựa vào dueDate), DELAYED dựa vào lastProgressAt.
     */
    private static final String SQL_IS_DELAYED =
            "CAST(CASE WHEN t.status IN ('PENDING','IN_PROGRESS','REJECTED') "
            + "AND DATEDIFF(DAY, t.assignedAt, GETDATE()) >= 3 "
            + "AND DATEDIFF(DAY, COALESCE(t.lastProgressAt, t.assignedAt), GETDATE()) >= 3 "
            + "THEN 1 ELSE 0 END AS BIT) AS isDelayed";

    /** Các cột cơ bản của PageTask dùng trong SELECT */
    private static final String SQL_TASK_COLUMNS_BASE =
            "t.id, t.chapterId, t.assistantId, t.pageRangeStart, t.pageRangeEnd, "
            + "STUFF((SELECT ',' + pts.taskTypeCode FROM PageTaskStage pts "
            + "WHERE pts.taskId = t.id ORDER BY pts.taskTypeCode "
            + "FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 1, '') AS taskTypes, "
            + "t.dueDate, t.status, t.rejectionCount, ";

    /** Cache: DB có các cột mở rộng (priority, notes, ...) không? */
    private volatile Boolean taskSchemaExtended;

    /** Cache: DB đã sẵn sàng cho lifecycle schema (actionReason, previousAssistantId, ...) chưa? */
    private volatile Boolean taskLifecycleSchemaReady;

    /** Cache: bảng TaskReviewHistory (lịch sử submit/review) đã tồn tại chưa? */
    private volatile Boolean taskReviewHistoryTableReady;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ChapterImageRepository chapterImageRepository;

    @Autowired
    private PageRepository pageRepository;

    // ============================================================
    // [2] SCHEMA GUARD - Kiểm tra & tự cập nhật cấu trúc DB
    // ============================================================

    /** Chuẩn hóa status string: trim + uppercase */
    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ENGLISH);
    }

    /**
     * Trả về danh sách cột SELECT tùy thuộc DB có schema mở rộng hay không.
     * Schema mở rộng: có cột priority, notes, rejectionReason, approvalComment, actionReason, previousAssistantId.
     */
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

    /**
     * Đảm bảo DB có đủ các cột cho task lifecycle (actionReason, previousAssistantId, lastProgressAt)
     * và constraint status hợp lệ. Chạy một lần, thread-safe.
     */
    private void ensureTaskLifecycleSchemaReady() {
        if (Boolean.TRUE.equals(taskLifecycleSchemaReady)) {
            return;
        }
        synchronized (this) {
            if (Boolean.TRUE.equals(taskLifecycleSchemaReady)) {
                return;
            }
            try (Connection conn = dataSource.getConnection()) {
                addColumnIfMissing(conn, "actionReason", "nvarchar(300) NULL");
                addColumnIfMissing(conn, "previousAssistantId", "bigint NULL");
                addColumnIfMissing(conn, "lastProgressAt", "datetime NULL");

                // Xoá constraint cũ rồi thêm lại với đầy đủ trạng thái mới
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

    /**
     * Đảm bảo bảng TaskReviewHistory (lịch sử từng round submit/review) tồn tại.
     * Cần bảng riêng vì PageTask.rejectionReason/approvalComment bị ghi đè mỗi vòng,
     * không đủ để dựng lại lịch sử đầy đủ.
     */
    private void ensureTaskReviewHistoryTableReady() {
        if (Boolean.TRUE.equals(taskReviewHistoryTableReady)) {
            return;
        }
        synchronized (this) {
            if (Boolean.TRUE.equals(taskReviewHistoryTableReady)) {
                return;
            }
            String createSql =
                    "IF OBJECT_ID('dbo.TaskReviewHistory','U') IS NULL "
                    + "CREATE TABLE [dbo].[TaskReviewHistory] ("
                    + "[id] [bigint] IDENTITY(1,1) NOT NULL, "
                    + "[taskId] [bigint] NOT NULL, "
                    + "[roundNumber] [int] NOT NULL, "
                    + "[submittedAt] [datetime] NOT NULL, "
                    + "[submittedBy] [bigint] NOT NULL, "
                    + "[decision] [varchar](20) NULL, "
                    + "[reviewedAt] [datetime] NULL, "
                    + "[reviewedBy] [bigint] NULL, "
                    + "[reviewComment] [nvarchar](300) NULL, "
                    + "[imageIdsSnapshot] [nvarchar](500) NULL, "
                    + "CONSTRAINT [PK_TaskReviewHistory] PRIMARY KEY CLUSTERED ([id] ASC))";
            String indexSql =
                    "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_TaskReviewHistory_taskId' "
                    + "AND object_id = OBJECT_ID('dbo.TaskReviewHistory')) "
                    + "CREATE INDEX [IX_TaskReviewHistory_taskId] ON [dbo].[TaskReviewHistory]([taskId] ASC, [roundNumber] DESC)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement create = conn.prepareStatement(createSql);
                 PreparedStatement index = conn.prepareStatement(indexSql)) {
                create.executeUpdate();
                index.executeUpdate();
                taskReviewHistoryTableReady = Boolean.TRUE;
            } catch (SQLException ex) {
                throw new RuntimeException("Cannot prepare task review history schema", ex);
            }
        }
    }

    /** Thêm cột vào bảng PageTask nếu chưa tồn tại */
    private void addColumnIfMissing(Connection conn, String column, String definition) throws SQLException {
        String sql = "IF COL_LENGTH('dbo.PageTask', '" + column + "') IS NULL ALTER TABLE [dbo].[PageTask] ADD " + column + " " + definition;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    /** Kiểm tra DB có cột mở rộng (priority, notes) không — cache kết quả */
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

    /** Kiểm tra ResultSet có cột tên `label` không */
    private boolean hasColumn(ResultSet rs, String label) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (label.equalsIgnoreCase(md.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // [3] TRUY VẤN TASK
    // ============================================================

    /** Liệt kê task theo quyền người dùng (không lọc status/chapter) */
    public List<TaskSummary> listVisible(AuthenticatedUser user) {
        return listVisible(user, null, null);
    }

    /**
     * Liệt kê task hiển thị với người dùng, có thể lọc thêm theo status và/hoặc chapterId.
     * Quy tắc phân quyền:
     *   - ADMIN: thấy tất cả
     *   - MANGAKA: chỉ thấy task thuộc series của mình
     *   - TANTOU_EDITOR: chỉ thấy task thuộc series mình phụ trách
     *   - ASSISTANT: chỉ thấy task được giao cho mình
     */
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

    /** Liệt kê tất cả task thuộc một chapter, sắp xếp theo ID giảm dần */
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

    /** Tìm một task theo ID, trả về null nếu không tồn tại */
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

    public List<Map<String, Object>> findApprovedTasksForSalary(
            long periodId, long assistantId) {
        String sql = "SELECT t.id, t.pageRangeStart, t.pageRangeEnd, "
                + "t.rejectionCount, s.title AS seriesTitle, c.chapterNumber, "
                + "pps.pageNumber, pps.taskTypeCode, t.dueDate, "
                + "t.updatedAt AS approvedAt, tt.ratePerPage, "
                + "CASE WHEN t.updatedAt <= DATEADD(DAY, 1, CAST(t.dueDate AS DATETIME)) "
                + "THEN 1 ELSE 0 END AS onTime, "
                + "CASE WHEN t.updatedAt > DATEADD(DAY, 1, CAST(t.dueDate AS DATETIME)) "
                + "THEN DATEDIFF(DAY, t.dueDate, t.updatedAt) ELSE 0 END AS daysLate "
                + "FROM PageTask t "
                + "JOIN Chapter c ON c.id = t.chapterId "
                + "JOIN Series s ON s.id = c.seriesId "
                + "JOIN SalaryPeriod sp ON sp.id = ? AND sp.mangakaId = s.mangakaId "
                + "JOIN PageTaskPageStage pps ON pps.taskId = t.id "
                + "LEFT JOIN TaskType tt ON tt.code = pps.taskTypeCode "
                + "WHERE t.assistantId = ? AND UPPER(t.status) = 'APPROVED' "
                + "AND ((sp.status = 'OPEN' AND t.isSalaried = 0) "
                + "OR (sp.status = 'SETTLED' AND t.isSalaried = 1 "
                + "AND t.updatedAt <= sp.settledAt "
                + "AND NOT EXISTS (SELECT 1 FROM SalaryPeriod earlier "
                + "JOIN AssistantSalaryRecord er ON er.periodId = earlier.id "
                + "AND er.assistantId = t.assistantId "
                + "WHERE earlier.mangakaId = sp.mangakaId "
                + "AND earlier.status = 'SETTLED' "
                + "AND earlier.settledAt < sp.settledAt "
                + "AND earlier.settledAt >= t.updatedAt))) "
                + "ORDER BY s.title ASC, c.chapterNumber ASC, t.id ASC, pps.pageNumber ASC";
        Map<Long, Map<String, Object>> tasks =
                new LinkedHashMap<Long, Map<String, Object>>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
              ps.setLong(2, assistantId);
              try (ResultSet rs = ps.executeQuery()) {
                  while (rs.next()) {
                      long taskId = rs.getLong("id");
                      Map<String, Object> task = tasks.get(taskId);
                      if (task == null) {
                          task = new HashMap<String, Object>();
                          task.put("id", taskId);
                          task.put("seriesTitle", rs.getString("seriesTitle"));
                          task.put("chapterNumber", rs.getInt("chapterNumber"));
                          task.put("pageRangeStart", rs.getInt("pageRangeStart"));
                          task.put("pageRangeEnd", rs.getInt("pageRangeEnd"));
                          task.put("dueDate", rs.getDate("dueDate"));
                          task.put("approvedAt", rs.getTimestamp("approvedAt"));
                          task.put("onTime", rs.getBoolean("onTime"));
                          task.put("daysLate", rs.getInt("daysLate"));
                          task.put("rejectionCount", rs.getInt("rejectionCount"));
                          task.put("amount", BigDecimal.ZERO);
                          task.put("pages", new ArrayList<Map<String, Object>>());
                          tasks.put(taskId, task);
                      }

                      BigDecimal rate = rs.getBigDecimal("ratePerPage");
                      if (rate == null) {
                          rate = BigDecimal.ZERO;
                      }
                      Map<String, Object> page = new HashMap<String, Object>();
                      page.put("pageNumber", rs.getInt("pageNumber"));
                      page.put("taskType", rs.getString("taskTypeCode"));
                      page.put("ratePerPage", rate);
                      page.put("amount", rate);
                      @SuppressWarnings("unchecked")
                      List<Map<String, Object>> pages =
                              (List<Map<String, Object>>) task.get("pages");
                      pages.add(page);
                      task.put("amount", ((BigDecimal) task.get("amount")).add(rate));
                  }
              }
          } catch (SQLException ex) {
              throw new RuntimeException("Cannot load approved salary tasks", ex);
          }
          return new ArrayList<Map<String, Object>>(tasks.values());
      }

    // ============================================================
    // [4] TẠO & CẬP NHẬT TASK (Mangaka)
    // ============================================================

    /** Tạo task với priority mặc định NORMAL, không có notes */
    public long create(long chapterId, long assistantId, int start, int end, List<String> taskTypes, Date dueDate) {
        return create(chapterId, assistantId, start, end, taskTypes, dueDate, "NORMAL", null);
    }

    /**
     * Tạo task mới, validate đầy đủ trước khi insert:
     * - Không trùng page range với task đang active (BR-33)
     * - dueDate phải trước submissionDeadline ít nhất 3 ngày (BR-34)
     * - Assistant phải thuộc danh sách của Mangaka (BR-36)
     * - Mangaka không được tự giao cho chính mình (BR-35)
     * Sau khi tạo: gửi thông báo cho assistant và cập nhật tiến độ chapter.
     */
    public long create(long chapterId, long assistantId, int start, int end, List<String> taskTypes, Date dueDate, String priority, String notes) {
        return create(chapterId, assistantId, start, end, taskTypes, dueDate, priority, notes, true);
    }

    private long create(long chapterId, long assistantId, int start, int end, List<String> taskTypes, Date dueDate,
            String priority, String notes, boolean notifyAssignment) {
        ensureTaskLifecycleSchemaReady();
        // Chỉ task đang active mới block tái sử dụng page range; task đã đóng thì không
        String overlapSql = "SELECT COUNT(1) FROM PageTask WHERE chapterId = ? AND UPPER(status) NOT IN (" + SQL_CLOSED_TASK_STATUSES + ") AND NOT (pageRangeEnd < ? OR pageRangeStart > ?)";
        String chapterSql = "SELECT c.submissionDeadline, c.seriesId, s.mangakaId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        String enrollmentSql = "SELECT COUNT(1) FROM MangakaAssistant WHERE mangakaId = ? AND assistantId = ?";
        String insertExtendedSql = "INSERT INTO PageTask (chapterId, assistantId, pageRangeStart, pageRangeEnd, dueDate, status, rejectionCount, priority, notes, assignedAt, updatedAt) VALUES (?, ?, ?, ?, ?, 'IN_PROGRESS', 0, ?, ?, GETDATE(), GETDATE())";
        String insertLegacySql = "INSERT INTO PageTask (chapterId, assistantId, pageRangeStart, pageRangeEnd, dueDate, status, rejectionCount, assignedAt, updatedAt) VALUES (?, ?, ?, ?, ?, 'IN_PROGRESS', 0, GETDATE(), GETDATE())";

        try (Connection conn = dataSource.getConnection()) {
            Map<Integer, String> pageStages = derivePageStagesForRange(conn, chapterId, start, end);
            List<String> normalizedTaskTypes = summarizePageStages(pageStages);
            String normalizedPriority = normalizePriority(priority);
            validateTaskAssignment(conn, 0L, chapterId, assistantId, start, end, normalizedTaskTypes, dueDate, overlapSql, chapterSql, enrollmentSql);

            long newId;
            conn.setAutoCommit(false);
            boolean extended = isTaskSchemaExtended();
            String insertSql = extended ? insertExtendedSql : insertLegacySql;
            try (PreparedStatement insert = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, chapterId);
                insert.setLong(2, assistantId);
                insert.setInt(3, start);
                insert.setInt(4, end);
                insert.setDate(5, dueDate);
                if (extended) {
                    insert.setString(6, normalizedPriority);
                    insert.setString(7, notes == null ? null : notes.trim());
                }
                insert.executeUpdate();
                try (ResultSet rs = insert.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Cannot create task");
                    }
                    newId = rs.getLong(1);
                }
            }
            replaceTaskStages(conn, newId, normalizedTaskTypes);
            replaceTaskPageStages(conn, newId, pageStages);
            conn.commit();

            refreshChapterProgress(chapterId);
            if (notifyAssignment) {
                createNotification(
                        assistantId,
                        "TASK_ASSIGNED",
                        "You have been assigned task #" + newId + ".",
                        newId,
                        "TASK");
            }
            return newId;
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create task", ex);
        }
    }

    // ============================================================
    // [5] VÒNG ĐỜI TASK - Nộp / Duyệt / Từ chối
    // ============================================================

    /**
     * Mangaka phân công lại task cho assistant khác.
     * Yêu cầu: task đang IN_PROGRESS hoặc OVERDUE; phải cung cấp lý do (≥5 ký tự).
     * Nếu task OVERDUE, bắt buộc phải truyền newDueDate hợp lệ.
     * Quy trình: đóng task cũ (REASSIGNED) → tạo task mới → ghi previousAssistantId.
     */
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
                task.getTaskTypes(),
                dueDateForNewTask,
                task.getPriority(),
                task.getNotes(),
                false);
        setPreviousAssistantAndReason(newTaskId, task.getAssistantId(), reason.trim());
        createNotification(
                newAssistantId,
                "TASK_REASSIGNED",
                "Task #" + newTaskId + " has been reassigned to you.",
                newTaskId,
                "TASK");
        createNotification(
                task.getAssistantId(),
                "TASK_REASSIGNED",
                "Task #" + taskId + " was reassigned. Reason: " + reason.trim(),
                taskId,
                "TASK");
        return newTaskId;
    }

    // ============================================================
    // [6] VÒNG ĐỜI TASK - Phân công lại / Xoá / Huỷ
    // ============================================================

    /**
     * Mangaka xoá task (chuyển sang DELETED).
     * Chỉ xoá được task đang IN_PROGRESS hoặc OVERDUE; phải có lý do ≥5 ký tự.
     */
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

    /** Ghi lại previousAssistantId và lý do phân công lại vào task mới */
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

    private void replaceTaskStages(Connection conn, long taskId, List<String> taskTypes) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM PageTaskStage WHERE taskId = ?")) {
            delete.setLong(1, taskId);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO PageTaskStage (taskId, taskTypeCode) VALUES (?, ?)")) {
            for (String taskType : taskTypes) {
                insert.setLong(1, taskId);
                insert.setString(2, taskType);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void replaceTaskPageStages(Connection conn, long taskId,
            Map<Integer, String> pageStages) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM PageTaskPageStage WHERE taskId = ?")) {
            delete.setLong(1, taskId);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO PageTaskPageStage (taskId, pageNumber, taskTypeCode) VALUES (?, ?, ?)")) {
            for (Map.Entry<Integer, String> entry : pageStages.entrySet()) {
                insert.setLong(1, taskId);
                insert.setInt(2, entry.getKey().intValue());
                insert.setString(3, entry.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private List<String> splitTaskTypes(String value) {
        List<String> taskTypes = new ArrayList<String>();
        if (value == null || value.trim().isEmpty()) {
            return taskTypes;
        }
        for (String taskType : value.split(",")) {
            if (!taskType.trim().isEmpty()) {
                taskTypes.add(taskType.trim());
            }
        }
        return taskTypes;
    }

    private Map<Integer, String> derivePageStagesForRange(
            Connection conn, long chapterId, int start, int end) throws SQLException {
        String sql = "SELECT pageNumber, completedStage FROM " + PageRepository.TABLE_PAGE
                + " WHERE chapterId = ? AND pageNumber BETWEEN ? AND ? ORDER BY pageNumber";
        Map<Integer, String> pageStages = new java.util.LinkedHashMap<Integer, String>();
        int pageCount = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            ps.setInt(2, start);
            ps.setInt(3, end);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pageCount++;
                    String nextStage = nextStageAfter(rs.getString("completedStage"));
                    pageStages.put(Integer.valueOf(rs.getInt("pageNumber")), nextStage);
                }
            }
        }
        int expectedCount = end - start + 1;
        if (pageCount != expectedCount) {
            throw new IllegalArgumentException("Every page in the selected range must exist before assigning a task");
        }
        return pageStages;
    }

    private List<String> summarizePageStages(Map<Integer, String> pageStages) {
        String requiredStage = null;
        boolean mixedStages = false;
        for (String stage : pageStages.values()) {
            if (requiredStage == null) {
                requiredStage = stage;
            } else if (!requiredStage.equals(stage)) {
                mixedStages = true;
            }
        }
        List<String> stages = new ArrayList<String>();
        stages.add(mixedStages ? "MIXED" : requiredStage);
        return stages;
    }

    private String nextStageAfter(String completedStage) {
        String current = completedStage == null ? "" : completedStage.trim().toUpperCase(Locale.ENGLISH);
        if (current.isEmpty()) {
            return "SKETCHING";
        }
        if ("SKETCHING".equals(current)) {
            return "INKING";
        }
        if ("INKING".equals(current)) {
            return "COLORING";
        }
        if ("COLORING".equals(current)) {
            return "SCREENTONE";
        }
        if ("SCREENTONE".equals(current)) {
            return "LETTERING";
        }
        if ("LETTERING".equals(current)) {
            throw new IllegalArgumentException("Completed pages cannot be assigned another task");
        }
        throw new IllegalArgumentException("Unknown completed stage: " + completedStage);
    }

    private Map<Integer, String> findTaskPageStages(Connection conn, long taskId) throws SQLException {
        String sql = "SELECT chapterId, pageRangeStart, pageRangeEnd FROM PageTask WHERE id = ?";
        long chapterId;
        int start;
        int end;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Task not found");
                }
                chapterId = rs.getLong("chapterId");
                start = rs.getInt("pageRangeStart");
                end = rs.getInt("pageRangeEnd");
            }
        }
        Map<Integer, String> stages = new java.util.LinkedHashMap<Integer, String>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pageNumber, taskTypeCode FROM PageTaskPageStage "
                + "WHERE taskId = ? ORDER BY pageNumber")) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stages.put(Integer.valueOf(rs.getInt("pageNumber")),
                            rs.getString("taskTypeCode"));
                }
            }
        }
        if (stages.size() != end - start + 1) {
            stages = derivePageStagesForRange(conn, chapterId, start, end);
            replaceTaskPageStages(conn, taskId, stages);
        }
        return stages;
    }

    /**
     * Validate toàn bộ điều kiện trước khi tạo hoặc cập nhật task:
     * - Các trường bắt buộc không được null/rỗng
     * - dueDate không được trong quá khứ
     * - pageRangeEnd >= pageRangeStart
     * - Không có trang nào trong range đã hoàn thành (completedStage = LETTERING)
     * - Không trùng page range với task active khác (BR-33)
     * - dueDate phải trước submissionDeadline ≥ 3 ngày (BR-34)
     * - Mangaka không tự giao cho mình (BR-35)
     * - Assistant phải trong danh sách của Mangaka (BR-36)
     */
    private void validateTaskAssignment(
            Connection conn,
            long taskId,
            long chapterId,
            long assistantId,
            int start,
            int end,
            List<String> taskTypes,
            Date dueDate,
            String overlapSql,
            String chapterSql,
            String enrollmentSql) throws SQLException {

        if (assistantId <= 0) {
            throw new IllegalArgumentException("assistantId is required");
        }
        if (taskTypes == null || taskTypes.isEmpty()) {
            throw new IllegalArgumentException("taskTypes is required");
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

        // Kiểm tra trang đã hoàn thành trong range
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

        // Kiểm tra trùng page range với task active khác
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

        // BR-35: Mangaka không được tự giao cho mình
        if (assistantId == mangakaId) {
            throw new IllegalArgumentException("Mangaka cannot self-assign page task (BR-35)");
        }

        // BR-34: dueDate phải trước submissionDeadline ít nhất 3 ngày
        Date deadline3DaysBefore = new Date(submissionDeadline.getTime() - (3L * 24L * 60L * 60L * 1000L));
        if (dueDate.after(deadline3DaysBefore)) {
            throw new IllegalArgumentException("Task dueDate must be at least 3 days before chapter submissionDeadline (BR-34)");
        }

        // BR-36: Assistant phải trong danh sách của Mangaka
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

    /**
     * Assistant nộp task để Mangaka review (chuyển sang SUBMITTED).
     * Chỉ nộp được từ trạng thái IN_PROGRESS, REJECTED, hoặc OVERDUE (BR-TSK-01).
     * Bắt buộc phải upload đủ ảnh cho tất cả trang trong range trước khi nộp.
     */
    public void updateStatusByAssistant(long taskId, long assistantId, String status) {
        ensureTaskLifecycleSchemaReady();
        ensureTaskReviewHistoryTableReady();
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!"SUBMITTED".equals(normalized)) {
            throw new IllegalArgumentException("Assistant can only submit task for review");
        }

        String readSql = "SELECT chapterId, assistantId, pageRangeStart, pageRangeEnd, status, rejectionCount FROM PageTask WHERE id = ?";
        String updateSql = "UPDATE PageTask SET status = ?, updatedAt = GETDATE(), lastProgressAt = GETDATE() WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement read = conn.prepareStatement(readSql)) {
            read.setLong(1, taskId);
            long chapterId;
            long ownerAssistantId;
            int pageRangeStart;
            int pageRangeEnd;
            String currentStatus;
            int rejectionCount;
            try (ResultSet rs = read.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Task not found");
                }
                chapterId = rs.getLong("chapterId");
                ownerAssistantId = rs.getLong("assistantId");
                pageRangeStart = rs.getInt("pageRangeStart");
                pageRangeEnd = rs.getInt("pageRangeEnd");
                currentStatus = rs.getString("status");
                rejectionCount = rs.getInt("rejectionCount");
            }

            if (ownerAssistantId != assistantId) {
                throw new IllegalArgumentException("Task not assigned to this assistant (BR-42)");
            }

            String current = normalizeStatus(currentStatus);
            // Assistant vẫn có thể nộp khi task OVERDUE (Mangaka chưa quyết định)
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

            insertReviewHistoryRound(conn, taskId, assistantId, rejectionCount + 1);

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

    /**
     * Kiểm tra assistant đã upload đủ ảnh cho tất cả trang trong range chưa.
     * Mỗi pageNumber trong [pageRangeStart, pageRangeEnd] phải có ít nhất 1 ảnh active loại PAGE.
     */
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

    /**
     * Ghi nhận 1 round submit mới vào TaskReviewHistory, snapshot lại id các ảnh PAGE đang active.
     * Gọi ngay sau khi status chuyển thành SUBMITTED, trong cùng connection/transaction.
     */
    private void insertReviewHistoryRound(Connection conn, long taskId, long submittedBy, int roundNumber) throws SQLException {
        String snapshot;
        String imageIdsSql = "SELECT id FROM ChapterImage WHERE pageTaskId = ? AND imageType = 'PAGE' AND isActive = 1 ORDER BY pageNumber";
        try (PreparedStatement ps = conn.prepareStatement(imageIdsSql)) {
            ps.setLong(1, taskId);
            StringBuilder ids = new StringBuilder();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (ids.length() > 0) {
                        ids.append(',');
                    }
                    ids.append(rs.getLong(1));
                }
            }
            snapshot = ids.length() == 0 ? null : ids.toString();
        }

        String insertSql =
                "INSERT INTO TaskReviewHistory (taskId, roundNumber, submittedAt, submittedBy, decision, imageIdsSnapshot) "
                + "VALUES (?, ?, GETDATE(), ?, NULL, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setLong(1, taskId);
            ps.setInt(2, roundNumber);
            ps.setLong(3, submittedBy);
            ps.setString(4, snapshot);
            ps.executeUpdate();
        }
    }

    /**
     * Cập nhật quyết định (APPROVED/REJECTED) cho round đang chờ review (decision IS NULL) mới nhất của task.
     */
    private void closeReviewHistoryRound(Connection conn, long taskId, String decision, long reviewedBy, String reviewComment) throws SQLException {
        String sql =
                "UPDATE TaskReviewHistory "
                + "SET decision = ?, reviewedAt = GETDATE(), reviewedBy = ?, reviewComment = ? "
                + "WHERE id = (SELECT TOP 1 id FROM TaskReviewHistory WHERE taskId = ? AND decision IS NULL ORDER BY roundNumber DESC)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, decision);
            ps.setLong(2, reviewedBy);
            ps.setString(3, reviewComment == null ? null : reviewComment.trim());
            ps.setLong(4, taskId);
            ps.executeUpdate();
        }
    }

    /**
     * Mangaka duyệt task SUBMITTED (BR-39).
     * Sau khi duyệt: ảnh task được promote lên Chapter, cập nhật tiến độ, gửi thông báo.
     */
    public void approveByMangaka(long taskId, long mangakaId, String comment) {
        ensureTaskReviewHistoryTableReady();
        String readSql = "SELECT chapterId, assistantId, status FROM PageTask WHERE id = ?";
        String updateExtendedSql = "UPDATE PageTask SET status = 'APPROVED', approvalComment = ?, updatedAt = GETDATE() WHERE id = ?";
        String updateLegacySql = "UPDATE PageTask SET status = 'APPROVED', updatedAt = GETDATE() WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement read = conn.prepareStatement(readSql)) {
            read.setLong(1, taskId);
            long chapterId;
            long assistantId;
            String currentStatus;
            try (ResultSet rs = read.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Task not found");
                }
                chapterId = rs.getLong("chapterId");
                assistantId = rs.getLong("assistantId");
                currentStatus = rs.getString("status");
            }

            long ownerId = findChapterOwnerMangaka(chapterId);
            if (ownerId != mangakaId) {
                throw new IllegalArgumentException("Only chapter owner Mangaka can approve (BR-39)");
            }

            if (!"SUBMITTED".equals(normalizeStatus(currentStatus))) {
                throw new IllegalArgumentException("Only SUBMITTED task can be approved (BR-39)");
            }

            Map<Integer, String> pageStages = findTaskPageStages(conn, taskId);

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

            closeReviewHistoryRound(conn, taskId, "APPROVED", mangakaId, comment);

            replaceTaskStages(conn, taskId, summarizePageStages(pageStages));
            promoteTaskImagesToChapter(taskId, chapterId, mangakaId, pageStages);
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

    /** Promote ảnh PAGE của task vào Chapter (cập nhật bảng Page với completedStage tương ứng) */
    private void promoteTaskImagesToChapter(long taskId, long chapterId, long approvedBy,
            Map<Integer, String> pageStages) {
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
                    pageStages.get(image.getPageNumber()));
        }
    }

    /**
     * Mangaka từ chối task SUBMITTED (BR-38), trả về số lần bị từ chối.
     * Task chuyển lại IN_PROGRESS; dueDate được gia hạn thêm 1 ngày nếu còn đủ buffer trước deadline.
     */
    public int rejectByMangaka(long taskId, long mangakaId, String reason) {
        ensureTaskReviewHistoryTableReady();
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

            closeReviewHistoryRound(conn, taskId, "REJECTED", mangakaId, reason);

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

    /**
     * Tính dueDate mới sau khi reject: cộng thêm 1 ngày, nhưng không vượt quá
     * (seriesDeadline - 3 ngày). Nếu không còn đủ buffer, giữ nguyên dueDate cũ.
     */
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

    /**
     * Lấy toàn bộ lịch sử submit/review của 1 task, mới nhất trước.
     * Mỗi round kèm theo thumbnail các ảnh đã nộp tại thời điểm đó (kể cả ảnh đã bị thay/reject sau này).
     */
    public List<TaskReviewHistoryEntry> listReviewHistory(long taskId) {
        ensureTaskReviewHistoryTableReady();
        String sql =
                "SELECT h.roundNumber, h.submittedAt, su.fullName AS submittedByName, "
                + "h.decision, h.reviewedAt, ru.fullName AS reviewedByName, h.reviewComment, h.imageIdsSnapshot "
                + "FROM TaskReviewHistory h "
                + "LEFT JOIN [User] su ON su.id = h.submittedBy "
                + "LEFT JOIN [User] ru ON ru.id = h.reviewedBy "
                + "WHERE h.taskId = ? "
                + "ORDER BY h.roundNumber DESC";
        List<TaskReviewHistoryEntry> entries = new ArrayList<TaskReviewHistoryEntry>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TaskReviewHistoryEntry entry = new TaskReviewHistoryEntry();
                    entry.setRoundNumber(rs.getInt("roundNumber"));
                    entry.setSubmittedAt(rs.getTimestamp("submittedAt"));
                    entry.setSubmittedByName(rs.getString("submittedByName"));
                    entry.setDecision(rs.getString("decision"));
                    entry.setReviewedAt(rs.getTimestamp("reviewedAt"));
                    entry.setReviewedByName(rs.getString("reviewedByName"));
                    entry.setReviewComment(rs.getString("reviewComment"));
                    entry.setImages(chapterImageRepository.findByIds(taskId, parseIdList(rs.getString("imageIdsSnapshot"))));
                    entries.add(entry);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load task review history", ex);
        }
        return entries;
    }

    /** Parse chuỗi "12,13,14" thành List<Long>, trả list rỗng nếu null/empty. */
    private List<Long> parseIdList(String csv) {
        List<Long> ids = new ArrayList<Long>();
        if (csv == null || csv.trim().isEmpty()) {
            return ids;
        }
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ids.add(Long.valueOf(trimmed));
            }
        }
        return ids;
    }

    // ============================================================
    // [7] QUẢN LÝ OVERDUE
    // ============================================================

    /**
     * Job hàng ngày: đánh dấu OVERDUE các task đã quá dueDate nhưng chưa hoàn tất.
     * Gửi thông báo cho cả Mangaka (action required) và Assistant.
     * Trả về số task vừa được cập nhật.
     */
    public int markOverdueTasks() {
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
                        createNotificationIfAbsent(
                                row[3],
                                "TASK_OVERDUE_ACTION_REQUIRED",
                                "Task #" + row[0] + " is overdue. Please decide: extend, reassign, or cancel.",
                                row[0],
                                "TASK");
                        createNotificationIfAbsent(
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
     * Job hàng ngày (09:00): tự động huỷ (CANCELLED) các task OVERDUE mà Mangaka
     * chưa có quyết định trong 3 ngày. Gửi thông báo cho assistant.
     * Trả về số task bị huỷ.
     */
    public int escalatePendingOverdueDecisions() {
        ensureTaskLifecycleSchemaReady();
        String selectSql =
            "SELECT t.id, t.chapterId, t.assistantId, s.mangakaId "
            + "FROM PageTask t "
            + "JOIN Chapter c ON c.id = t.chapterId "
            + "JOIN Series s ON s.id = c.seriesId "
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
                rows.add(new long[] {
                    rs.getLong("id"),
                    rs.getLong("chapterId"),
                    rs.getLong("assistantId"),
                    rs.getLong("mangakaId")
                });
            }

            for (long[] row : rows) {
                try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                    update.setLong(1, row[0]);
                    if (update.executeUpdate() > 0) {
                        cancelled++;
                        chapters.add(row[1]);
                        createNotificationIfAbsent(
                                row[3],
                                "TASK_CANCELLED",
                                "Task #" + row[0] + " has been auto-cancelled: no action was taken within 3 days of overdue.",
                                row[0],
                                "TASK");
                        createNotificationIfAbsent(
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
     * Mangaka gia hạn task OVERDUE: đặt dueDate mới, reset status về IN_PROGRESS.
     * dueDate mới phải hợp lệ (không quá khứ, đủ buffer trước submissionDeadline).
     */
    public void extendOverdueTask(long taskId, long mangakaId, java.sql.Date newDueDate, String reason) {
        ensureTaskLifecycleSchemaReady();
        if (newDueDate == null) {
            throw new IllegalArgumentException("newDueDate is required");
        }
        if (!newDueDate.toLocalDate().isAfter(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("newDueDate must be after today");
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

    /**
     * Kiểm tra dueDate cho quyết định trên task OVERDUE:
     * không được quá khứ, không được vượt quá (submissionDeadline - 3 ngày).
     */
    private void validateOverdueDecisionDueDate(long chapterId, Date dueDate) {
        if (!dueDate.toLocalDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Task dueDate must be after today");
        }
        Date latestDueDate = findLatestTaskDueDate(chapterId);
        if (dueDate.after(latestDueDate)) {
            throw new IllegalArgumentException("Task dueDate must be at least 3 days before chapter submissionDeadline (BR-34)");
        }
    }

    /** Trả về ngày tối đa cho dueDate của task: submissionDeadline - 3 ngày */
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

    // ============================================================
    // [8] NHẮC NHỞ & THÔNG BÁO
    // ============================================================

    /**
     * Job hàng ngày: gửi nhắc nhở cho assistant về task sắp đến hạn trong 24 giờ.
     * Chỉ gửi 1 lần/ngày/task (createNotificationIfAbsentToday).
     */
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

    /**
     * BR-TSK-08: Nhắc Mangaka về task bị trễ tiến độ (không có cập nhật 3+ ngày kể từ khi giao).
     * DELAYED chỉ là cờ cảnh báo, không thay đổi status của task.
     */
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

    // ============================================================
    // [9] TIẾN ĐỘ CHAPTER
    // ============================================================

    /**
     * Tính lại completionPct và status của Chapter dựa trên completedStage của từng Page.
     * Thang điểm hoàn thành: SKETCHING=1/5, INKING=2/5, COLORING=3/5, SCREENTONE=4/5, LETTERING=5/5.
     * Cũng cập nhật cờ atRisk:
     *   - Đã qua submissionDeadline mà chưa xong: atRisk = true
     *   - Đã qua 70% thời gian mà mới hoàn thành < 50%: atRisk = true
     * Nếu chapter mới trở thành atRisk, gửi thông báo cho Tantou Editor.
     */
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

            // Gửi thông báo cho Tantou Editor khi chapter lần đầu trở thành at-risk
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

    /**
     * Kiểm tra tất cả task active của chapter đã được APPROVED chưa.
     * Task đã đóng (DELETED, REASSIGNED, CANCELLED) không tính vào denominator.
     */
    public boolean areAllTasksApproved(long chapterId) {
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

    /** Kiểm tra tất cả trang của chapter đã đạt completedStage = LETTERING (giai đoạn cuối) chưa */
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

    // ============================================================
    // [10] THÔNG BÁO (Notification)
    // ============================================================

    /** Tạo thông báo cho người dùng */
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

    /** Creates one notification per user/type/reference across scheduler runs. */
    public boolean createNotificationIfAbsent(long userId, String type, String message, long referenceId, String referenceType) {
        String checkSql =
            "SELECT COUNT(1) FROM Notification "
            + "WHERE userId = ? AND type = ? AND referenceId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setLong(1, userId);
            check.setString(2, type);
            check.setLong(3, referenceId);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check notification duplication", ex);
        }

        createNotification(userId, type, message, referenceId, referenceType);
        return true;
    }

    /**
     * Tạo thông báo nhưng chỉ khi chưa có thông báo cùng loại cho cùng referenceId trong ngày hôm nay.
     * Trả về true nếu thông báo được tạo, false nếu đã tồn tại.
     */
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

    // ============================================================
    // [11] HELPER / UTILITY
    // ============================================================

    /** Map notification type sang tiêu đề hiển thị */
    private String notificationTitle(String type) {
        if (type == null) {
            return "Notification";
        }
        String normalized = type.trim().toUpperCase(Locale.ENGLISH);
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
        if (normalized.startsWith("MANUSCRIPT")) { return "Manuscript update"; }
        if (normalized.startsWith("CHAPTER")) { return "Chapter update"; }
        return "Notification";
    }

    /** Tạo URL điều hướng cho thông báo dựa vào referenceType và type */
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
        if ("CHAPTER".equals(normalizedRef)) { return "/main/chapters/" + referenceId; }
        if ("MANUSCRIPT".equals(normalizedRef)) { return "/main/notifications"; }
        if ("DECISION".equals(normalizedRef) || "DECISION_SESSION".equals(normalizedRef)) { return "/main/decisions/" + referenceId; }
        if ("PROPOSAL".equals(normalizedRef)) { return "/main/proposals/" + referenceId; }
        if ("SERIES".equals(normalizedRef)) { return "/main/notifications"; }
        return null;
    }

    /** Map đầy đủ ResultSet → TaskSummary kèm thông tin chapter/series/assistant */
    private TaskSummary mapDetailed(ResultSet rs) throws SQLException {
        TaskSummary t = map(rs);
        t.setChapterTitle(rs.getString("chapterTitle"));
        t.setChapterNumber(rs.getInt("chapterNumber"));
        t.setSeriesTitle(rs.getString("seriesTitle"));
        t.setAssistantName(rs.getString("assistantName"));
        t.setDelayed(rs.getBoolean("isDelayed"));
        return t;
    }

    /** Map các cột cơ bản của PageTask từ ResultSet */
    private TaskSummary map(ResultSet rs) throws SQLException {
        TaskSummary t = new TaskSummary();
        t.setId(rs.getLong("id"));
        t.setChapterId(rs.getLong("chapterId"));
        t.setAssistantId(rs.getLong("assistantId"));
        t.setPageRangeStart(rs.getInt("pageRangeStart"));
        t.setPageRangeEnd(rs.getInt("pageRangeEnd"));
        t.setTaskTypes(splitTaskTypes(rs.getString("taskTypes")));
        t.setDueDate(rs.getDate("dueDate"));
        t.setStatus(rs.getString("status"));
        t.setRejectionCount(rs.getInt("rejectionCount"));
        if (hasColumn(rs, "priority")) {
            t.setPriority(rs.getString("priority"));
        } else {
            t.setPriority("NORMAL");
        }
        if (hasColumn(rs, "notes")) { t.setNotes(rs.getString("notes")); }
        if (hasColumn(rs, "rejectionReason")) { t.setRejectionReason(rs.getString("rejectionReason")); }
        if (hasColumn(rs, "approvalComment")) { t.setApprovalComment(rs.getString("approvalComment")); }
        if (hasColumn(rs, "actionReason")) { t.setActionReason(rs.getString("actionReason")); }
        if (hasColumn(rs, "previousAssistantId")) {
            long previousAssistantId = rs.getLong("previousAssistantId");
            t.setPreviousAssistantId(rs.wasNull() ? null : Long.valueOf(previousAssistantId));
        }
        return t;
    }

    /** Lấy ID Mangaka sở hữu chapter (qua series) */
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

    /** Lấy ID Tantou Editor phụ trách chapter (qua series) */
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

    /** Lấy assistantId của task */
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

    /** Lấy chapterId của task */
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

    /** Lấy ID Mangaka sở hữu task (qua chapter → series) */
    public long getTaskOwnerMangaka(long taskId) {
        return findChapterOwnerMangaka(findTaskChapterId(taskId));
    }

    /** Lấy ID Tantou Editor phụ trách task (qua chapter → series) */
    public long getTaskTantouEditor(long taskId) {
        return findChapterTantouEditor(findTaskChapterId(taskId));
    }

    /**
     * Mangaka cập nhật dueDate, priority, notes của task (không thay đổi assignment).
     * Task APPROVED không được chỉnh sửa (BR-TSK-06).
     */
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

    /**
     * Validate và chuẩn hóa priority.
     * Các giá trị hợp lệ: NORMAL, HIGH, URGENT. Mặc định là NORMAL nếu null/rỗng.
     */
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
