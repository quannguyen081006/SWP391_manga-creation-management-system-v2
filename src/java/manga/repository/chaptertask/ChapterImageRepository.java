package manga.repository.chaptertask;

import manga.common.util.ImagePhashUtil;
import manga.model.chaptertask.ChapterImageItem;
import manga.repository.SystemSettingRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository quản lý ảnh của chapter (ChapterImage).
 *
 * Mục lục:
 *  1. upload()                    - Upload ảnh mới (PAGE/COVER/REFERENCE)
 *  2. deactivateActivePageImages() - Soft-delete các PAGE cũ cùng pageNumber trước khi upload mới
 *  3. listByChapter()             - Lấy danh sách ảnh active của chapter
 *  4. listByTask()                - Lấy ảnh của task + fallback page gốc chưa có ảnh task
 *  5. syncFinalPageUpload()       - Mangaka sync ảnh page hoàn chỉnh từ ngoài vào ChapterImage
 *  6. backfillFinalPageUploads()  - Backfill toàn bộ page LETTERING của chapter chưa có ChapterImage
 *  7. deactivate()                - Soft-delete 1 ảnh (chỉ uploader hoặc Mangaka chủ seri)
 *  8. findById()                  - Tìm ảnh theo ID
 *  9. findChapterOwnerMangaka()   - Lấy mangakaId chủ seri của chapter
 * 10. findChapterTantouEditor()   - Lấy tantouEditorId phụ trách chapter
 * 11. findTaskChapterId()         - Lấy chapterId của một PageTask
 * 12. findTaskAssistantId()       - Lấy assistantId được giao của một PageTask
 * 13. hasAssignedTaskInChapter()  - Kiểm tra assistant có task trong chapter không
 *
 * Quy tắc upload:
 *  - PAGE: chỉ ASSISTANT, phải có pageTaskId + pageNumber, task phải thuộc chapter, assistant phải là người được giao
 *  - COVER/REFERENCE: chỉ MANGAKA chủ seri, không có pageTaskId/pageNumber
 *  - Mỗi lần upload PAGE mới → soft-delete PAGE cũ cùng pageNumber (chỉ 1 ảnh active mỗi trang)
 *  - Upload PAGE qua task → cập nhật lastProgressAt của PageTask
 */
@Repository
public class ChapterImageRepository {

    private static final int DEFAULT_PHASH_HAMMING_THRESHOLD = 10;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    private volatile Boolean imageHashColumnReady = Boolean.FALSE;

    /**
     * Upload ảnh chapter mới.
     * - PAGE: deactivate ảnh cũ cùng pageNumber trước, rồi insert mới
     * - Sau khi insert PAGE qua task → touch lastProgressAt của PageTask
     */
    public long upload(long chapterId, Long pageTaskId, long uploadedBy, String imageType,
            Integer pageNumber, String fileUrl, String originalFileName, long fileSizeBytes, String imagePhash) {
        String insertSql =
            "INSERT INTO ChapterImage (chapterId, pageTaskId, uploadedBy, imageType, pageNumber, fileUrl, originalFileName, fileSizeBytes, uploadedAt, isActive, imagePhash) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), 1, ?)";

        try (Connection conn = dataSource.getConnection()) {
            ensureImageHashColumnReady(conn);
            String normalizedType = normalizeImageType(imageType);
            validateUpload(conn, chapterId, pageTaskId, uploadedBy, normalizedType, pageNumber, fileUrl);
            if ("PAGE".equals(normalizedType)) {
                if (imagePhash != null) {
                    checkDuplicateImage(conn, chapterId, imagePhash);
                }
                deactivateActivePageImages(conn, chapterId, pageNumber.intValue());
            }

            long newId;
            try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, chapterId);
                if (pageTaskId == null) {
                    ps.setNull(2, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(2, pageTaskId.longValue());
                }
                ps.setLong(3, uploadedBy);
                ps.setString(4, normalizedType);
                if (pageNumber == null) {
                    ps.setNull(5, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(5, pageNumber.intValue());
                }
                ps.setString(6, fileUrl.trim());
                ps.setString(7, trimToNull(originalFileName));
                ps.setLong(8, fileSizeBytes);
                ps.setString(9, imagePhash);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Cannot upload chapter image");
                    }
                    newId = rs.getLong(1);
                }
            }

            if (pageTaskId != null) {
                // Cập nhật tiến độ task khi assistant upload ảnh
                String touchSql = "UPDATE PageTask SET lastProgressAt = GETDATE(), updatedAt = GETDATE() WHERE id = ?";
                try (PreparedStatement touch = conn.prepareStatement(touchSql)) {
                    touch.setLong(1, pageTaskId.longValue());
                    touch.executeUpdate();
                }
            }

            return newId;
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot upload chapter image", ex);
        }
    }

    /** Tự thêm cột imagePhash nếu chưa có - tránh phải chạy migration tay trên máy đồng đội. */
    private void ensureImageHashColumnReady(Connection conn) throws SQLException {
        if (Boolean.TRUE.equals(imageHashColumnReady)) {
            return;
        }
        synchronized (this) {
            if (Boolean.TRUE.equals(imageHashColumnReady)) {
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT CASE WHEN COL_LENGTH('dbo.ChapterImage','imagePhash') IS NULL THEN 0 ELSE 1 END");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    try (Statement alter = conn.createStatement()) {
                        alter.execute("ALTER TABLE [dbo].[ChapterImage] ADD [imagePhash] [char](16) NULL");
                    }
                }
            }
            imageHashColumnReady = Boolean.TRUE;
        }
    }

    /**
     * Chặn nộp trùng ảnh: so sánh pHash ảnh mới với toàn bộ ảnh (kể cả đã bị reject/thay)
     * từng upload trong CẢ chapter - mọi task, mọi page. Đảm bảo không tái sử dụng lại
     * một ảnh đã dùng ở bất kỳ đâu trong chapter.
     */
    private void checkDuplicateImage(Connection conn, long chapterId, String newHash) throws SQLException {
        int threshold = systemSettingRepository.getInt(
                SystemSettingRepository.PAGE_TASK_PHASH_THRESHOLD, DEFAULT_PHASH_HAMMING_THRESHOLD);
        String sql = "SELECT imagePhash FROM ChapterImage WHERE chapterId = ? AND imagePhash IS NOT NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String existing = rs.getString(1);
                    if (ImagePhashUtil.hammingDistance(newHash, existing) <= threshold) {
                        throw new IllegalArgumentException(
                                "Ảnh này đã từng được dùng trước đó trong chapter này (kể cả bản bị từ chối/thay thế). "
                                + "Vui lòng chỉnh sửa nội dung trước khi upload lại.");
                    }
                }
            }
        }
    }

    /**
     * Public entry cho luồng Mangaka upload (PageApiController) - kiểm tra trùng ảnh
     * trên phạm vi cả chapter, mở connection riêng.
     */
    public void checkDuplicateImageInChapter(long chapterId, String newHash) {
        if (newHash == null) {
            return;
        }
        try (Connection conn = dataSource.getConnection()) {
            ensureImageHashColumnReady(conn);
            checkDuplicateImage(conn, chapterId, newHash);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check duplicate image", ex);
        }
    }

    /**
     * Soft-delete tất cả PAGE active cùng pageNumber trong chapter.
     * Gọi trước khi insert PAGE mới để đảm bảo chỉ 1 ảnh active mỗi trang.
     */
    private void deactivateActivePageImages(Connection conn, long chapterId, int pageNumber) throws SQLException {
        String sql =
                "UPDATE ChapterImage SET isActive = 0 "
                + "WHERE chapterId = ? AND pageNumber = ? AND imageType = 'PAGE' AND isActive = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            ps.setInt(2, pageNumber);
            ps.executeUpdate();
        }
    }

    /**
     * Lấy tất cả ảnh active của chapter, sắp xếp: có pageNumber trước, rồi theo pageNumber ASC.
     */
    public List<ChapterImageItem> listByChapter(long chapterId) {
        String sql =
            "SELECT id, chapterId, pageTaskId, uploadedBy, imageType, pageNumber, fileUrl, originalFileName, fileSizeBytes, uploadedAt, isActive, note "
            + "FROM ChapterImage WHERE chapterId = ? AND isActive = 1 "
            + "ORDER BY CASE WHEN pageNumber IS NULL THEN 1 ELSE 0 END, pageNumber ASC, uploadedAt ASC";
        return list(sql, chapterId, "Cannot list chapter images");
    }

    /**
     * Lấy ảnh của một PageTask.
     * UNION 2 nguồn:
     *  - Ảnh đã upload vào task (ChapterImage.pageTaskId = task)
     *  - Fallback: page gốc từ bảng Page trong range của task, chưa có ảnh task tương ứng
     * Dùng để hiển thị workspace đầy đủ kể cả khi assistant chưa upload.
     */
    public List<ChapterImageItem> listByTask(long pageTaskId) {
        String sql =
            "SELECT id, chapterId, pageTaskId, uploadedBy, imageType, pageNumber, fileUrl, originalFileName, fileSizeBytes, uploadedAt, isActive, note "
            + "FROM ("
            + "  SELECT ci.id, ci.chapterId, ci.pageTaskId, ci.uploadedBy, ci.imageType, ci.pageNumber, ci.fileUrl, ci.originalFileName, ci.fileSizeBytes, ci.uploadedAt, ci.isActive, ci.note, 0 AS sourceRank "
            + "  FROM ChapterImage ci WHERE ci.pageTaskId = ? AND ci.isActive = 1 "
            + "  UNION ALL "
            + "  SELECT CAST(0 AS bigint) AS id, p.chapterId, pt.id AS pageTaskId, ISNULL(p.uploadedBy, 0) AS uploadedBy, 'PAGE' AS imageType, p.pageNumber, p.imageUrl AS fileUrl, "
            + "         CONCAT('Chapter page ', p.pageNumber) AS originalFileName, CAST(0 AS bigint) AS fileSizeBytes, p.uploadedAt, CAST(1 AS bit) AS isActive, 'CHAPTER_PAGE' AS note, 1 AS sourceRank "
            + "  FROM PageTask pt JOIN [dbo].[Page] p ON p.chapterId = pt.chapterId AND p.pageNumber BETWEEN pt.pageRangeStart AND pt.pageRangeEnd "
            + "  WHERE pt.id = ? AND p.imageUrl IS NOT NULL "
            + "    AND NOT EXISTS (SELECT 1 FROM ChapterImage ci WHERE ci.pageTaskId = pt.id AND ci.pageNumber = p.pageNumber AND ci.isActive = 1) "
            + ") x "
            + "ORDER BY CASE WHEN pageNumber IS NULL THEN 1 ELSE 0 END, pageNumber ASC, sourceRank ASC, uploadedAt ASC";
        return list(sql, pageTaskId, pageTaskId, "Cannot list task images");
    }

    /** Overload không kèm hash (dùng cho backfill từ Page.imageUrl có sẵn - không có file để hash). */
    public void syncFinalPageUpload(long chapterId, int pageNumber, long uploadedBy, String fileUrl) {
        syncFinalPageUpload(chapterId, pageNumber, uploadedBy, fileUrl, null);
    }

    /**
     * Mangaka sync 1 ảnh page hoàn chỉnh vào ChapterImage (không qua task).
     * Chỉ Mangaka chủ seri mới được gọi. Deactivate ảnh cũ cùng pageNumber trước.
     * imagePhash: lưu để phục vụ chống trùng toàn chapter (null nếu không tính được).
     */
    public void syncFinalPageUpload(long chapterId, int pageNumber, long uploadedBy, String fileUrl, String imagePhash) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return;
        }
        String ownerSql =
                "SELECT s.mangakaId "
                + "FROM Chapter c JOIN Series s ON s.id = c.seriesId "
                + "WHERE c.id = ?";
        String insertSql =
                "INSERT INTO ChapterImage (chapterId, pageTaskId, uploadedBy, imageType, pageNumber, fileUrl, originalFileName, fileSizeBytes, uploadedAt, isActive, note, imagePhash) "
                + "VALUES (?, NULL, ?, 'PAGE', ?, ?, ?, NULL, GETDATE(), 1, 'MANGAKA_PAGE_UPLOAD', ?)";
        try (Connection conn = dataSource.getConnection()) {
            ensureImageHashColumnReady(conn);
            long ownerId;
            try (PreparedStatement ps = conn.prepareStatement(ownerSql)) {
                ps.setLong(1, chapterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Chapter not found");
                    }
                    ownerId = rs.getLong(1);
                }
            }
            if (ownerId != uploadedBy) {
                throw new IllegalArgumentException("Only series owner Mangaka can sync final page image");
            }
            deactivateActivePageImages(conn, chapterId, pageNumber);
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setLong(1, chapterId);
                ps.setLong(2, uploadedBy);
                ps.setInt(3, pageNumber);
                ps.setString(4, fileUrl.trim());
                ps.setString(5, "Chapter page " + pageNumber);
                ps.setString(6, imagePhash);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot sync final page image", ex);
        }
    }

    /**
     * Backfill toàn bộ page LETTERING của chapter chưa có ChapterImage active.
     * Gọi khi chapter chuyển sang EDITORIAL_REVIEW để đảm bảo tất cả page đã hoàn thành được ghi nhận.
     */
    public void backfillFinalPageUploads(long chapterId, long uploadedBy) {
        String sql =
                "SELECT p.pageNumber, p.imageUrl "
                + "FROM " + PageRepository.TABLE_PAGE + " p "
                + "WHERE p.chapterId = ? "
                + "AND p.imageUrl IS NOT NULL "
                + "AND UPPER(ISNULL(p.completedStage, '')) = 'LETTERING' "
                + "AND NOT EXISTS ("
                + "  SELECT 1 FROM ChapterImage ci "
                + "  WHERE ci.chapterId = p.chapterId "
                + "    AND ci.pageNumber = p.pageNumber "
                + "    AND ci.imageType = 'PAGE' "
                + "    AND ci.isActive = 1"
                + ") "
                + "ORDER BY p.pageNumber";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    syncFinalPageUpload(chapterId, rs.getInt("pageNumber"), uploadedBy, rs.getString("imageUrl"));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot backfill final page images", ex);
        }
    }

    /** Helper query với 2 tham số long. */
    private List<ChapterImageItem> list(String sql, long firstId, long secondId, String error) {
        List<ChapterImageItem> rows = new ArrayList<ChapterImageItem>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, firstId);
            ps.setLong(2, secondId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(error, ex);
        }
        return rows;
    }

    /**
     * Soft-delete 1 ảnh. Chỉ uploader hoặc Mangaka chủ seri mới được xóa.
     */
    public void deactivate(long imageId, long requestorId) {
        String readSql =
            "SELECT ci.uploadedBy, ci.chapterId, s.mangakaId "
            + "FROM ChapterImage ci "
            + "JOIN Chapter c ON c.id = ci.chapterId "
            + "JOIN Series s ON s.id = c.seriesId "
            + "WHERE ci.id = ? AND ci.isActive = 1";
        String updateSql = "UPDATE ChapterImage SET isActive = 0 WHERE id = ? AND isActive = 1";

        try (Connection conn = dataSource.getConnection()) {
            long uploadedBy;
            long mangakaId;
            try (PreparedStatement read = conn.prepareStatement(readSql)) {
                read.setLong(1, imageId);
                try (ResultSet rs = read.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Chapter image not found");
                    }
                    uploadedBy = rs.getLong("uploadedBy");
                    mangakaId = rs.getLong("mangakaId");
                }
            }

            if (uploadedBy != requestorId && mangakaId != requestorId) {
                throw new IllegalArgumentException("Only image uploader or chapter Mangaka can deactivate image");
            }

            try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setLong(1, imageId);
                if (update.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Chapter image not found");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot deactivate chapter image", ex);
        }
    }

    /** Tìm ChapterImage theo ID, trả null nếu không tồn tại. */
    public ChapterImageItem findById(long imageId) {
        String sql =
            "SELECT id, chapterId, pageTaskId, uploadedBy, imageType, pageNumber, fileUrl, originalFileName, fileSizeBytes, uploadedAt, isActive, note "
            + "FROM ChapterImage WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return map(rs);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load chapter image", ex);
        }
    }

    /**
     * Lấy các ChapterImage theo danh sách id, giới hạn trong 1 pageTaskId (an toàn - tránh lộ ảnh task khác).
     * Không lọc isActive vì dùng để hiển thị lịch sử, bao gồm cả ảnh cũ đã bị thay/reject.
     */
    public List<ChapterImageItem> findByIds(long pageTaskId, List<Long> ids) {
        List<ChapterImageItem> rows = new ArrayList<ChapterImageItem>();
        if (ids == null || ids.isEmpty()) {
            return rows;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
        }
        String sql =
            "SELECT id, chapterId, pageTaskId, uploadedBy, imageType, pageNumber, fileUrl, originalFileName, fileSizeBytes, uploadedAt, isActive, note "
            + "FROM ChapterImage WHERE pageTaskId = ? AND id IN (" + placeholders + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, pageTaskId);
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 2, ids.get(i).longValue());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load chapter images by ids", ex);
        }
        return rows;
    }

    /** Lấy mangakaId chủ seri của chapter (dùng để kiểm tra quyền upload COVER/REFERENCE). */
    public long findChapterOwnerMangaka(long chapterId) {
        String sql = "SELECT s.mangakaId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        return queryLong(sql, chapterId, "Chapter not found");
    }

    /** Lấy tantouEditorId phụ trách chapter. */
    public long findChapterTantouEditor(long chapterId) {
        String sql = "SELECT s.tantouEditorId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        return queryLong(sql, chapterId, "Chapter not found");
    }

    /** Lấy chapterId của một PageTask. */
    public long findTaskChapterId(long pageTaskId) {
        String sql = "SELECT chapterId FROM PageTask WHERE id = ?";
        return queryLong(sql, pageTaskId, "Task not found");
    }

    /** Lấy assistantId được giao của một PageTask. */
    public long findTaskAssistantId(long pageTaskId) {
        String sql = "SELECT assistantId FROM PageTask WHERE id = ?";
        return queryLong(sql, pageTaskId, "Task not found");
    }

    /** Kiểm tra assistant có ít nhất 1 task trong chapter không. */
    public boolean hasAssignedTaskInChapter(long chapterId, long assistantId) {
        String sql = "SELECT COUNT(1) FROM PageTask WHERE chapterId = ? AND assistantId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            ps.setLong(2, assistantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check assistant task", ex);
        }
    }

    /**
     * Validate quyền upload trước khi insert.
     * PAGE  → ASSISTANT, cần pageTaskId + pageNumber, task phải thuộc chapter và được giao đúng người.
     * COVER/REFERENCE → MANGAKA chủ seri, không có pageTaskId/pageNumber.
     */
    private void validateUpload(Connection conn, long chapterId, Long pageTaskId, long uploadedBy,
            String imageType, Integer pageNumber, String fileUrl) throws SQLException {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("fileUrl is required");
        }

        ensureChapterExists(conn, chapterId);

        if ("PAGE".equals(imageType)) {
            if (!hasRole(conn, uploadedBy, "ASSISTANT")) {
                throw new IllegalArgumentException("Only ASSISTANT can upload PAGE image");
            }
            if (pageTaskId == null) {
                throw new IllegalArgumentException("pageTaskId is required for PAGE image");
            }
            if (pageNumber == null || pageNumber.intValue() <= 0) {
                throw new IllegalArgumentException("pageNumber is required for PAGE image");
            }
            TaskAccess task = readTaskAccess(conn, pageTaskId.longValue());
            if (task.chapterId != chapterId) {
                throw new IllegalArgumentException("Task does not belong to this chapter");
            }
            if (task.assistantId != uploadedBy) {
                throw new IllegalArgumentException("ASSISTANT can upload only for assigned task");
            }
            return;
        }

        if (pageTaskId != null) {
            throw new IllegalArgumentException("pageTaskId must be null for COVER/REFERENCE image");
        }
        if (pageNumber != null) {
            throw new IllegalArgumentException("pageNumber must be null for COVER/REFERENCE image");
        }
        if (!hasRole(conn, uploadedBy, "MANGAKA")) {
            throw new IllegalArgumentException("Only MANGAKA can upload COVER or REFERENCE image");
        }
        long ownerId = findChapterOwnerMangaka(conn, chapterId);
        if (ownerId != uploadedBy) {
            throw new IllegalArgumentException("Only series owner Mangaka can upload COVER or REFERENCE image");
        }
    }

    /** Normalize và validate imageType: PAGE / COVER / REFERENCE. */
    private String normalizeImageType(String imageType) {
        if (imageType == null || imageType.trim().isEmpty()) {
            throw new IllegalArgumentException("imageType is required");
        }
        String normalized = imageType.trim().toUpperCase(Locale.ENGLISH);
        if (!"PAGE".equals(normalized) && !"COVER".equals(normalized) && !"REFERENCE".equals(normalized)) {
            throw new IllegalArgumentException("imageType must be PAGE, COVER, or REFERENCE");
        }
        return normalized;
    }

    /** Helper query với 1 tham số long. */
    private List<ChapterImageItem> list(String sql, long id, String error) {
        List<ChapterImageItem> rows = new ArrayList<ChapterImageItem>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(error, ex);
        }
        return rows;
    }

    /** Map ResultSet → ChapterImageItem. pageTaskId và pageNumber nullable. */
    private ChapterImageItem map(ResultSet rs) throws SQLException {
        ChapterImageItem item = new ChapterImageItem();
        item.setId(rs.getLong("id"));
        item.setChapterId(rs.getLong("chapterId"));
        long taskId = rs.getLong("pageTaskId");
        item.setPageTaskId(rs.wasNull() ? null : Long.valueOf(taskId));
        item.setUploadedBy(rs.getLong("uploadedBy"));
        item.setImageType(rs.getString("imageType"));
        int pageNumber = rs.getInt("pageNumber");
        item.setPageNumber(rs.wasNull() ? null : Integer.valueOf(pageNumber));
        item.setFileUrl(rs.getString("fileUrl"));
        item.setOriginalFileName(rs.getString("originalFileName"));
        item.setFileSizeBytes(rs.getLong("fileSizeBytes"));
        item.setUploadedAt(rs.getTimestamp("uploadedAt"));
        item.setActive(rs.getBoolean("isActive"));
        item.setNote(rs.getString("note"));
        return item;
    }

    /** Ném exception nếu chapter không tồn tại. */
    private void ensureChapterExists(Connection conn, long chapterId) throws SQLException {
        String sql = "SELECT COUNT(1) FROM Chapter WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    throw new IllegalArgumentException("Chapter not found");
                }
            }
        }
    }

    /** Kiểm tra user có role cụ thể không (query DB trực tiếp, dùng trong validate upload). */
    private boolean hasRole(Connection conn, long userId, String roleName) throws SQLException {
        String sql = "SELECT COUNT(1) FROM UserRole ur JOIN [Role] r ON r.id = ur.roleId WHERE ur.userId = ? AND r.name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** Đọc chapterId và assistantId của một PageTask để validate quyền upload. */
    private TaskAccess readTaskAccess(Connection conn, long pageTaskId) throws SQLException {
        String sql = "SELECT chapterId, assistantId FROM PageTask WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, pageTaskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Task not found");
                }
                TaskAccess task = new TaskAccess();
                task.chapterId = rs.getLong("chapterId");
                task.assistantId = rs.getLong("assistantId");
                return task;
            }
        }
    }

    /** Overload dùng Connection có sẵn (tránh mở connection mới trong cùng transaction). */
    private long findChapterOwnerMangaka(Connection conn, long chapterId) throws SQLException {
        String sql = "SELECT s.mangakaId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Chapter not found");
                }
                return rs.getLong(1);
            }
        }
    }

    /** Helper: query trả về 1 giá trị long, ném exception nếu không tìm thấy. */
    private long queryLong(String sql, long id, String error) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(error);
                }
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(error, ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    /** DTO nội bộ để đọc chapterId + assistantId của PageTask trong 1 lần query. */
    private static class TaskAccess {
        private long chapterId;
        private long assistantId;
    }
}