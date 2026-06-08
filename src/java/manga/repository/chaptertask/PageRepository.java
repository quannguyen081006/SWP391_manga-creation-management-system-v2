package manga.repository.chaptertask;

import manga.model.chaptertask.PageSlotSummary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository quản lý các page slot trong một chapter.
 *
 * Mỗi "page slot" đại diện cho một trang vẽ trong chapter (vd: trang 1, trang 2...).
 * Page slot gắn với chapter và có thể được assign vào PageTask của assistant.
 *
 * =====================  MỤC LỤC  =====================
 *  1. KIỂM TRA TABLE / COLUMN SẴN SÀNG
 *     - isPageTableReady()          : kiểm tra bảng Page tồn tại chưa (lazy + cache)
 *     - requirePageTableReady()     : throw nếu bảng chưa có
 *     - ensurePageStageColumnReady(): tự động ALTER TABLE thêm cột completedStage nếu thiếu
 *
 *  2. QUERY DANH SÁCH PAGE
 *     - listByChapter(chapterId)    : lấy tất cả page slot của chapter, kèm task đang active
 *     - listEmptySlots(chapterId, limit) : lấy các slot chưa có ảnh (status = EMPTY)
 *     - findById(pageId)            : lấy một page slot theo ID
 *
 *  3. TẠO PAGE SLOT
 *     - create(chapterId, pageNumber)        : tạo 1 slot, trả về ID
 *     - bulkCreate(chapterId, totalPages)    : tạo nhiều slot cùng lúc (dùng khi tạo chapter mới)
 *     - bulkCreate(conn, chapterId, totalPages) : overload dùng connection có sẵn (trong transaction)
 *
 *  4. ĐẾM PAGE
 *     - countByChapter(chapterId)   : tổng số slot trong chapter
 *     - countUploaded(chapterId)    : số slot đã có ảnh upload
 *     - nextPageNumber(chapterId)   : số trang tiếp theo sẽ được tạo
 *
 *  5. CẬP NHẬT TRẠNG THÁI / ẢNH
 *     - markUploaded(...)           : đánh dấu IN_PROGRESS khi assistant upload ảnh
 *     - markApproved(...)           : đánh dấu APPROVED khi tantou duyệt
 *     - promoteTaskImage(...)       : sync ảnh từ task được approve vào page slot
 *     - upsertUploadedByPageNumber(): upsert page theo pageNumber (tạo mới nếu chưa có)
 *
 *  6. XÓA PAGE
 *     - delete(pageId)              : xóa slot, chặn nếu đang có task active
 *
 *  7. STAGE PROGRESSION (pipeline vẽ)
 *     - resolveNextStage()          : tính stage tiếp theo, validate không lùi stage
 *     - resolveTaskCompletionStage(): tính stage hoàn thành dựa theo taskType
 *     - normalizeStage()            : chuẩn hóa tên stage (vd: "INK" -> "INKING")
 *     Thứ tự stage: SKETCHING -> INKING -> COLORING -> SCREENTONE -> LETTERING
 * ======================================================
 */
@Repository
public class PageRepository {

    /** Tên bảng dùng trong SQL, có schema prefix để tránh conflict. */
    public static final String TABLE_PAGE = "[dbo].[Page]";

    /** Các trạng thái task được coi là "đã đóng" - không tính là active khi check block delete. */
    private static final String SQL_CLOSED_TASK_STATUSES = "'APPROVED','DELETED','REASSIGNED','CANCELLED'";

    /** Thứ tự các stage trong pipeline vẽ manga. Dùng để validate stage progression. */
    private static final List<String> PAGE_STAGES = Arrays.asList(
            "SKETCHING", "INKING", "COLORING", "SCREENTONE", "LETTERING");

    /** Cache lazy: null = chưa kiểm tra, true/false = kết quả. Dùng volatile để thread-safe. */
    private volatile Boolean pageTableReady;
    private volatile Boolean pageStageColumnReady;

    @Autowired
    private DataSource dataSource;

    // =====================================================================
    // 1. KIỂM TRA TABLE / COLUMN SẴN SÀNG
    // =====================================================================

    /** Throw IllegalArgumentException nếu bảng Page chưa được tạo. */
    private void requirePageTableReady() {
        if (!isPageTableReady()) {
            throw new IllegalArgumentException(
                    "Page table is missing. Run database/schema.sql and database/seed_v5.sql on MangaEditorialDB first.");
        }
    }

    /**
     * Kiểm tra bảng Page tồn tại chưa.
     * Kết quả được cache vào pageTableReady để không query lại mỗi lần.
     * Double-checked locking để an toàn khi nhiều thread chạy đồng thời.
     */
    private boolean isPageTableReady() {
        if (pageTableReady != null) {
            return pageTableReady.booleanValue();
        }
        synchronized (this) {
            if (pageTableReady != null) {
                return pageTableReady.booleanValue();
            }
            boolean ready = false;
            String sql = "SELECT CASE WHEN OBJECT_ID('dbo.Page', 'U') IS NOT NULL THEN 1 ELSE 0 END";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ready = rs.getInt(1) == 1;
                }
            } catch (SQLException ex) {
                ready = false;
            }
            pageTableReady = Boolean.valueOf(ready);
            return ready;
        }
    }

    /**
     * Đảm bảo cột completedStage tồn tại trong bảng Page.
     * Nếu chưa có thì tự ALTER TABLE thêm vào (migration nhỏ tự động).
     * Cũng dùng double-checked locking + cache.
     */
    public void ensurePageStageColumnReady() {
        if (!isPageTableReady()) {
            return;
        }
        if (Boolean.TRUE.equals(pageStageColumnReady)) {
            return;
        }
        synchronized (this) {
            if (Boolean.TRUE.equals(pageStageColumnReady)) {
                return;
            }
            try (Connection conn = dataSource.getConnection()) {
                boolean exists;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT CASE WHEN COL_LENGTH('dbo.Page', 'completedStage') IS NULL THEN 0 ELSE 1 END");
                     ResultSet rs = ps.executeQuery()) {
                    exists = rs.next() && rs.getInt(1) == 1;
                }
                if (!exists) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "ALTER TABLE " + TABLE_PAGE + " ADD completedStage varchar(30) NULL")) {
                        ps.executeUpdate();
                    }
                }
                pageStageColumnReady = Boolean.TRUE;
            } catch (SQLException ex) {
                throw new RuntimeException("Cannot prepare page stage column", ex);
            }
        }
    }

    // =====================================================================
    // 2. QUERY DANH SÁCH PAGE
    // =====================================================================

    /**
     * SQL lấy danh sách page slot của chapter.
     * Dùng OUTER APPLY để join với PageTask active gần nhất (nếu có),
     * giúp hiển thị thông tin task và assistant đang xử lý trang đó.
     * Task "đã đóng" (APPROVED/DELETED/REASSIGNED/CANCELLED) không được tính.
     */
    private static final String LIST_SQL =
            "SELECT p.id, p.chapterId, p.pageNumber, p.imageUrl, p.uploadedBy, p.uploadedAt, p.status, p.completedStage, "
            + "t.id AS taskId, t.taskType, t.status AS taskStatus, t.assistantId, u.fullName AS assistantName "
            + "FROM " + TABLE_PAGE + " p "
            + "OUTER APPLY ( "
            + "  SELECT TOP 1 pt.id, pt.taskType, pt.status, pt.assistantId "
            + "  FROM PageTask pt "
            + "  WHERE pt.chapterId = p.chapterId "
            + "    AND p.pageNumber BETWEEN pt.pageRangeStart AND pt.pageRangeEnd "
            + "    AND UPPER(pt.status) NOT IN (" + SQL_CLOSED_TASK_STATUSES + ") "
            + "  ORDER BY pt.updatedAt DESC "
            + ") t "
            + "LEFT JOIN [User] u ON u.id = t.assistantId "
            + "WHERE p.chapterId = ? "
            + "ORDER BY p.pageNumber";

    /**
     * Lấy toàn bộ page slot của chapter, kèm task active và assistant tương ứng.
     * Trả về list rỗng nếu bảng Page chưa tồn tại (graceful degradation).
     */
    public List<PageSlotSummary> listByChapter(long chapterId) {
        if (!isPageTableReady()) {
            return new ArrayList<PageSlotSummary>();
        }
        ensurePageStageColumnReady();
        List<PageSlotSummary> rows = new ArrayList<PageSlotSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(LIST_SQL)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            if (isMissingPageTable(ex)) {
                pageTableReady = Boolean.FALSE;
                return new ArrayList<PageSlotSummary>();
            }
            throw new RuntimeException("Cannot list chapter pages", ex);
        }
        return rows;
    }

    /** Lấy page slot theo ID. Trả về null nếu không tìm thấy hoặc bảng chưa sẵn sàng. */
    public PageSlotSummary findById(long pageId) {
        if (!isPageTableReady()) {
            return null;
        }
        ensurePageStageColumnReady();
        String sql = "SELECT id, chapterId, pageNumber, imageUrl, uploadedBy, uploadedAt, status, completedStage "
                + "FROM " + TABLE_PAGE + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PageSlotSummary slot = new PageSlotSummary();
                slot.setId(rs.getLong("id"));
                slot.setChapterId(rs.getLong("chapterId"));
                slot.setPageNumber(rs.getInt("pageNumber"));
                slot.setImageUrl(rs.getString("imageUrl"));
                long uploadedBy = rs.getLong("uploadedBy");
                slot.setUploadedBy(rs.wasNull() ? null : Long.valueOf(uploadedBy));
                slot.setUploadedAt(rs.getTimestamp("uploadedAt"));
                slot.setStatus(rs.getString("status"));
                slot.setCompletedStage(rs.getString("completedStage"));
                return slot;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load page", ex);
        }
    }

    /**
     * Lấy các slot chưa có ảnh (status = EMPTY), giới hạn số lượng trả về.
     * Dùng để tìm trang nào chưa có ai làm để assign task mới.
     */
    public List<PageSlotSummary> listEmptySlots(long chapterId, int limit) {
        if (!isPageTableReady()) {
            return new ArrayList<PageSlotSummary>();
        }
        String sql = "SELECT TOP (?) id, chapterId, pageNumber, imageUrl, uploadedBy, uploadedAt, status "
                + "FROM " + TABLE_PAGE + " WHERE chapterId = ? AND status = 'EMPTY' ORDER BY pageNumber";
        List<PageSlotSummary> rows = new ArrayList<PageSlotSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setLong(2, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PageSlotSummary slot = new PageSlotSummary();
                    slot.setId(rs.getLong("id"));
                    slot.setChapterId(rs.getLong("chapterId"));
                    slot.setPageNumber(rs.getInt("pageNumber"));
                    slot.setStatus(rs.getString("status"));
                    rows.add(slot);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list empty page slots", ex);
        }
        return rows;
    }

    // =====================================================================
    // 3. TẠO PAGE SLOT
    // =====================================================================

    /** Tạo 1 page slot mới với status EMPTY. Trả về ID của slot vừa tạo. */
    public long create(long chapterId, int pageNumber) {
        requirePageTableReady();
        ensurePageStageColumnReady();
        String sql = "INSERT INTO " + TABLE_PAGE + " (chapterId, pageNumber, status, createdAt) VALUES (?, ?, 'EMPTY', GETDATE())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, chapterId);
            ps.setInt(2, pageNumber);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            throw new IllegalStateException("Cannot create page slot");
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create page slot", ex);
        }
    }

    /** Tạo nhiều page slot cùng lúc (1..totalPages). Dùng khi tạo chapter mới. */
    public void bulkCreate(long chapterId, int totalPages) {
        try (Connection conn = dataSource.getConnection()) {
            bulkCreate(conn, chapterId, totalPages);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot bulk create page slots", ex);
        }
    }

    /**
     * Overload dùng connection bên ngoài truyền vào - dùng trong transaction.
     * Dùng batch insert để tối ưu performance khi tạo nhiều trang.
     */
    public void bulkCreate(Connection conn, long chapterId, int totalPages) throws SQLException {
        if (totalPages < 1) {
            return;
        }
        if (!isPageTableReady()) {
            return;
        }
        ensurePageStageColumnReady();
        String sql = "INSERT INTO " + TABLE_PAGE + " (chapterId, pageNumber, status, createdAt) VALUES (?, ?, 'EMPTY', GETDATE())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= totalPages; i++) {
                ps.setLong(1, chapterId);
                ps.setInt(2, i);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // =====================================================================
    // 4. ĐẾM PAGE
    // =====================================================================

    /** Đếm tổng số page slot trong chapter. */
    public int countByChapter(long chapterId) {
        if (!isPageTableReady()) {
            return 0;
        }
        String sql = "SELECT COUNT(1) FROM " + TABLE_PAGE + " WHERE chapterId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot count pages", ex);
        }
    }

    /** Đếm số slot đã được upload ảnh (imageUrl != null). */
    public int countUploaded(long chapterId) {
        if (!isPageTableReady()) {
            return 0;
        }
        String sql = "SELECT COUNT(1) FROM " + TABLE_PAGE + " WHERE chapterId = ? AND imageUrl IS NOT NULL";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot count uploaded pages", ex);
        }
    }

    /** Trả về số trang tiếp theo sẽ được tạo = MAX(pageNumber) + 1. */
    public int nextPageNumber(long chapterId) {
        requirePageTableReady();
        String sql = "SELECT ISNULL(MAX(pageNumber), 0) + 1 FROM " + TABLE_PAGE + " WHERE chapterId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        } catch (SQLException ex) {
            if (isMissingPageTable(ex)) {
                pageTableReady = Boolean.FALSE;
                throw new IllegalArgumentException(
                        "Page table is missing. Run database/schema.sql and database/seed_v5.sql on MangaEditorialDB first.");
            }
            throw new RuntimeException("Cannot resolve next page number", ex);
        }
    }

    // =====================================================================
    // 5. CẬP NHẬT TRẠNG THÁI / ẢNH
    // =====================================================================

    /** Đánh dấu trang IN_PROGRESS khi assistant upload ảnh lên. */
    public void markUploaded(long pageId, String imageUrl, long uploadedBy) {
        markUploaded(pageId, imageUrl, uploadedBy, null);
    }

    /** Overload của markUploaded, kèm thông tin completedStage. */
    public void markUploaded(long pageId, String imageUrl, long uploadedBy, String completedStage) {
        markImage(pageId, imageUrl, uploadedBy, "IN_PROGRESS", completedStage);
    }

    /** Đánh dấu trang APPROVED (không kèm stage). */
    public void markApproved(long pageId, String imageUrl, long uploadedBy) {
        markApproved(pageId, imageUrl, uploadedBy, null);
    }

    /** Đánh dấu trang APPROVED, kèm completedStage. */
    public void markApproved(long pageId, String imageUrl, long uploadedBy, String completedStage) {
        markImage(pageId, imageUrl, uploadedBy, "APPROVED", completedStage);
    }

    /** Hàm trung tâm cập nhật imageUrl, uploadedBy, status, completedStage cho page. */
    private void markImage(long pageId, String imageUrl, long uploadedBy, String status, String completedStage) {
        requirePageTableReady();
        ensurePageStageColumnReady();
        String stage = resolveNextStage(pageId, completedStage);
        String sql = "UPDATE " + TABLE_PAGE + " SET imageUrl = ?, uploadedBy = ?, uploadedAt = GETDATE(), status = ?, completedStage = COALESCE(?, completedStage) WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, imageUrl);
            ps.setLong(2, uploadedBy);
            ps.setString(3, status);
            ps.setString(4, stage);
            ps.setLong(5, pageId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Page not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update page image", ex);
        }
    }

    /**
     * Sync ảnh từ task được approve vào page slot theo (chapterId, pageNumber).
     * Nếu slot chưa tồn tại thì tự tạo mới rồi mới update.
     * Dùng taskType để tính stage hoàn thành tương ứng.
     */
    public void promoteTaskImage(long chapterId, int pageNumber, String imageUrl, long uploadedBy, String taskType) {
        requirePageTableReady();
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return;
        }
        String findSql = "SELECT id FROM " + TABLE_PAGE + " WHERE chapterId = ? AND pageNumber = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement find = conn.prepareStatement(findSql)) {
            find.setLong(1, chapterId);
            find.setInt(2, pageNumber);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    markTaskApproved(rs.getLong("id"), imageUrl.trim(), uploadedBy, taskType);
                    return;
                }
            }
            long pageId = create(chapterId, pageNumber);
            markTaskApproved(pageId, imageUrl.trim(), uploadedBy, taskType);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot sync approved task image to chapter page", ex);
        }
    }

    /** Cập nhật ảnh và stage cho page khi task được approve, dùng taskType để suy stage. */
    private void markTaskApproved(long pageId, String imageUrl, long uploadedBy, String taskType) {
        requirePageTableReady();
        ensurePageStageColumnReady();
        String stage = resolveTaskCompletionStage(pageId, taskType);
        String sql = "UPDATE " + TABLE_PAGE + " SET imageUrl = ?, uploadedBy = ?, uploadedAt = GETDATE(), status = 'APPROVED', completedStage = COALESCE(?, completedStage) WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, imageUrl);
            ps.setLong(2, uploadedBy);
            ps.setString(3, stage);
            ps.setLong(4, pageId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Page not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update page image", ex);
        }
    }

    /**
     * Upsert page slot theo (chapterId, pageNumber): update nếu đã có, tạo mới nếu chưa có.
     * Dùng khi đồng bộ ảnh từ task vào page mà không cần biết pageId trước.
     */
    public void upsertUploadedByPageNumber(long chapterId, int pageNumber, String imageUrl, long uploadedBy) {
        upsertUploadedByPageNumber(chapterId, pageNumber, imageUrl, uploadedBy, null);
    }

    /** Overload của upsertUploadedByPageNumber, kèm completedStage. */
    public void upsertUploadedByPageNumber(long chapterId, int pageNumber, String imageUrl, long uploadedBy, String completedStage) {
        requirePageTableReady();
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return;
        }
        String findSql = "SELECT id FROM " + TABLE_PAGE + " WHERE chapterId = ? AND pageNumber = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement find = conn.prepareStatement(findSql)) {
            find.setLong(1, chapterId);
            find.setInt(2, pageNumber);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    markApproved(rs.getLong("id"), imageUrl.trim(), uploadedBy, completedStage);
                    return;
                }
            }
            long pageId = create(chapterId, pageNumber);
            markApproved(pageId, imageUrl.trim(), uploadedBy, completedStage);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot sync approved task image to chapter page", ex);
        }
    }

    // =====================================================================
    // 6. XÓA PAGE
    // =====================================================================

    /**
     * Xóa page slot.
     * Chặn nếu đang có task active (chưa đóng) cover trang đó.
     * Dùng pageRangeStart/End của PageTask để kiểm tra overlap.
     */
    public void delete(long pageId) {
        requirePageTableReady();
        String readSql = "SELECT chapterId, pageNumber FROM " + TABLE_PAGE + " WHERE id = ?";
        String taskSql = "SELECT COUNT(1) FROM PageTask WHERE chapterId = ? AND ? BETWEEN pageRangeStart AND pageRangeEnd AND UPPER(status) NOT IN (" + SQL_CLOSED_TASK_STATUSES + ")";
        String deleteSql = "DELETE FROM " + TABLE_PAGE + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection()) {
            long chapterId;
            int pageNumber;
            try (PreparedStatement read = conn.prepareStatement(readSql)) {
                read.setLong(1, pageId);
                try (ResultSet rs = read.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Page not found");
                    }
                    chapterId = rs.getLong("chapterId");
                    pageNumber = rs.getInt("pageNumber");
                }
            }
            // Kiểm tra task đang active không - nếu có thì không cho xóa
            try (PreparedStatement task = conn.prepareStatement(taskSql)) {
                task.setLong(1, chapterId);
                task.setInt(2, pageNumber);
                try (ResultSet rs = task.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        throw new IllegalArgumentException("Cannot delete page while it has an active task");
                    }
                }
            }
            try (PreparedStatement delete = conn.prepareStatement(deleteSql)) {
                delete.setLong(1, pageId);
                if (delete.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Page not found");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot delete page", ex);
        }
    }

    // =====================================================================
    // 7. STAGE PROGRESSION
    // Thứ tự: SKETCHING -> INKING -> COLORING -> SCREENTONE -> LETTERING
    // =====================================================================

    /**
     * Tính stage tiếp theo dựa trên stage hiện tại và stage được request.
     * Không cho phép lùi stage hoặc nhảy cóc quá 1 bước.
     */
    private String resolveNextStage(long pageId, String requestedStage) {
        String current = findCurrentCompletedStage(pageId);
        String normalized = normalizeStage(requestedStage);
        if (normalized == null) {
            return null;
        }
        if (current == null) {
            return normalized;
        }
        int currentIndex = PAGE_STAGES.indexOf(current);
        int requestedIndex = PAGE_STAGES.indexOf(normalized);
        if (requestedIndex < currentIndex) {
            throw new IllegalArgumentException("Page stage cannot move backwards");
        }
        if (requestedIndex > currentIndex + 1) {
            throw new IllegalArgumentException("Page stage must follow SKETCHING -> INKING -> COLORING -> SCREENTONE -> LETTERING");
        }
        return normalized;
    }

    /**
     * Tính stage hoàn thành dựa theo taskType khi task được approve.
     * Nếu taskType là MIXED thì tự tính stage tiếp theo từ stage hiện tại.
     */
    private String resolveTaskCompletionStage(long pageId, String taskType) {
        String current = findCurrentCompletedStage(pageId);
        String normalized = null;
        if (taskType != null && !"MIXED".equalsIgnoreCase(taskType.trim())) {
            normalized = normalizeStage(taskType);
        }
        if (current == null) {
            return normalized == null ? PAGE_STAGES.get(0) : normalized;
        }
        int currentIndex = PAGE_STAGES.indexOf(current);
        if (currentIndex >= PAGE_STAGES.size() - 1) {
            return current; // Đã ở stage cuối, không tăng nữa
        }
        int nextIndex = currentIndex + 1;
        if (normalized != null) {
            int requestedIndex = PAGE_STAGES.indexOf(normalized);
            if (requestedIndex <= currentIndex) {
                return current;
            }
            if (requestedIndex == nextIndex) {
                return normalized;
            }
        }
        return PAGE_STAGES.get(nextIndex);
    }

    /** Đọc completedStage hiện tại của page từ DB. */
    private String findCurrentCompletedStage(long pageId) {
        String sql = "SELECT completedStage FROM " + TABLE_PAGE + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Page not found");
                }
                return normalizeStage(rs.getString(1));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load page stage", ex);
        }
    }

    /**
     * Chuẩn hóa tên stage: uppercase, xử lý alias ("INK" -> "INKING", "TONE" -> "SCREENTONE"...).
     * Throw nếu stage không hợp lệ.
     */
    private String normalizeStage(String stage) {
        if (stage == null || stage.trim().isEmpty()) {
            return null;
        }
        String normalized = stage.trim().toUpperCase(Locale.ENGLISH);
        if ("INK".equals(normalized)) {
            normalized = "INKING";
        } else if ("TONE".equals(normalized) || "TONING".equals(normalized)) {
            normalized = "SCREENTONE";
        } else if ("BACKGROUND".equals(normalized)) {
            normalized = "SKETCHING";
        }
        if (!PAGE_STAGES.contains(normalized)) {
            throw new IllegalArgumentException("completedStage must be SKETCHING, INKING, COLORING, SCREENTONE, or LETTERING");
        }
        return normalized;
    }

    // =====================================================================
    // PRIVATE HELPERS
    // =====================================================================

    /**
     * Map ResultSet sang PageSlotSummary đầy đủ (bao gồm thông tin task và assistant).
     * Dùng cho listByChapter có JOIN với PageTask và User.
     */
    private PageSlotSummary map(ResultSet rs) throws SQLException {
        PageSlotSummary slot = new PageSlotSummary();
        slot.setId(rs.getLong("id"));
        slot.setChapterId(rs.getLong("chapterId"));
        slot.setPageNumber(rs.getInt("pageNumber"));
        slot.setImageUrl(rs.getString("imageUrl"));
        long uploadedBy = rs.getLong("uploadedBy");
        slot.setUploadedBy(rs.wasNull() ? null : Long.valueOf(uploadedBy));
        slot.setUploadedAt(rs.getTimestamp("uploadedAt"));
        slot.setStatus(rs.getString("status"));
        slot.setCompletedStage(readOptionalString(rs, "completedStage"));
        long taskId = rs.getLong("taskId");
        slot.setTaskId(rs.wasNull() ? null : Long.valueOf(taskId));
        slot.setTaskType(rs.getString("taskType"));
        slot.setTaskStatus(rs.getString("taskStatus"));
        long assistantId = rs.getLong("assistantId");
        slot.setAssistantId(rs.wasNull() ? null : Long.valueOf(assistantId));
        slot.setAssistantName(rs.getString("assistantName"));
        return slot;
    }

    /**
     * Đọc cột string optional - không throw nếu cột không tồn tại trong ResultSet.
     * Dùng để tương thích với DB cũ chưa có cột completedStage.
     */
    private String readOptionalString(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException ex) {
            return null;
        }
    }

    /** Kiểm tra exception có phải do bảng Page không tồn tại không. */
    private boolean isMissingPageTable(Throwable ex) {
        while (ex != null) {
            String msg = ex.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("invalid object name") && lower.contains("page")) {
                    return true;
                }
            }
            ex = ex.getCause();
        }
        return false;
    }
}