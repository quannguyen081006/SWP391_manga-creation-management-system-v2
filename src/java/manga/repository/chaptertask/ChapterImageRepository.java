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
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository that manages chapter images (ChapterImage).
 *
 * Table of contents:
 *  1. upload()                    - Upload a new PAGE image (submitted by an ASSISTANT for a task)
 *  2. deactivateActivePageImages() - Soft-delete old PAGE images with the same pageNumber before a new upload
 *  3. listByTask()                - Get task images + fallback to the original page when no task image exists yet
 *  4. syncFinalPageUpload()       - Mangaka syncs a finished page image from outside into ChapterImage
 *  5. backfillFinalPageUploads()  - Backfill all LETTERING pages of the chapter that lack a ChapterImage
 *  6. deactivate()                - Soft-delete one image (only the uploader or the series-owning Mangaka)
 *  7. findById()                  - Find an image by ID
 *  8. findChapterOwnerMangaka()   - Get the mangakaId who owns the chapter's series
 *  9. findChapterTantouEditor()   - Get the tantouEditorId assigned to the chapter
 * 10. findTaskChapterId()         - Get the chapterId of a PageTask
 * 11. findTaskAssistantId()       - Get the assigned assistantId of a PageTask
 *
 * Upload rules:
 *  - ASSISTANT only, must have pageTaskId + pageNumber, the task must belong to the chapter, and the assistant must be the assignee
 *  - Each new upload -> soft-deletes the old image with the same pageNumber (only 1 active image per page)
 *  - Uploading via a task -> updates the PageTask's lastProgressAt
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
     * Upload a new chapter image.
     * - PAGE: deactivate the old image with the same pageNumber first, then insert the new one
     * - After inserting a PAGE via a task -> touch the PageTask's lastProgressAt
     */
    public long upload(long chapterId, Long pageTaskId, long uploadedBy,
            Integer pageNumber, String fileUrl, String originalFileName, long fileSizeBytes, String imagePhash) {
        String insertSql =
            "INSERT INTO ChapterImage (chapterId, pageTaskId, uploadedBy, imageType, pageNumber, fileUrl, originalFileName, fileSizeBytes, uploadedAt, isActive, imagePhash) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), 1, ?)";

        try (Connection conn = dataSource.getConnection()) {
            ensureImageHashColumnReady(conn);
            validateUpload(conn, chapterId, pageTaskId, uploadedBy, pageNumber, fileUrl);
            if (imagePhash != null) {
                checkDuplicateImage(conn, chapterId, imagePhash);
            }
            deactivateActivePageImages(conn, chapterId, pageNumber.intValue());

            long newId;
            try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, chapterId);
                if (pageTaskId == null) {
                    ps.setNull(2, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(2, pageTaskId.longValue());
                }
                ps.setLong(3, uploadedBy);
                ps.setString(4, "PAGE");
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
                // Update task progress when the assistant uploads an image
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

    /** Automatically adds the imagePhash column if missing - avoids having to run a manual migration on teammates' machines. */
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
            dropDeadColumnsIfPresent(conn);
            imageHashColumnReady = Boolean.TRUE;
        }
    }

    /**
     * Drops two dead columns left over from an earlier schema design, on databases created before
     * this cleanup: [pageId] (nullable bigint, never populated or read - superseded by chapterId+pageNumber
     * as the join key) and [imageUrl] (a PERSISTED computed column that just duplicates [fileUrl] byte-for-byte,
     * wasting storage). Neither is referenced anywhere in the application.
     */
    private void dropDeadColumnsIfPresent(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                    "IF COL_LENGTH('dbo.ChapterImage', 'imageUrl') IS NOT NULL "
                    + "ALTER TABLE [dbo].[ChapterImage] DROP COLUMN [imageUrl]");
            st.executeUpdate(
                    "IF COL_LENGTH('dbo.ChapterImage', 'pageId') IS NOT NULL "
                    + "ALTER TABLE [dbo].[ChapterImage] DROP COLUMN [pageId]");
        }
    }

    /**
     * Blocks duplicate image submission: compares the new image's pHash against every image
     * (including ones that were rejected/replaced) ever uploaded across the ENTIRE chapter -
     * every task, every page. Ensures an image already used anywhere in the chapter cannot be reused.
     *
     * Combines 2 hash sources:
     *  - ChapterImage: images submitted by assistants via a task + images the Mangaka synced at the LETTERING stage
     *  - PageRevision: EVERY time the Mangaka uploads an image for a page (including intermediate stages such as
     *    SKETCHING/INKING/COLORING/SCREENTONE) - if this source were skipped, an image the Mangaka uploaded at an
     *    intermediate stage would be "invisible" and an Assistant could resubmit the exact same image for another page.
     */
    private void checkDuplicateImage(Connection conn, long chapterId, String newHash) throws SQLException {
        int threshold = systemSettingRepository.getInt(
                SystemSettingRepository.PAGE_TASK_PHASH_THRESHOLD, DEFAULT_PHASH_HAMMING_THRESHOLD);
        boolean includeRevisionHistory = isPageRevisionTableReady(conn);
        String sql = "SELECT imagePhash FROM ChapterImage WHERE chapterId = ? AND imagePhash IS NOT NULL";
        if (includeRevisionHistory) {
            sql += " UNION SELECT pr.imagePhash FROM [dbo].[PageRevision] pr "
                    + "JOIN [dbo].[Page] p ON p.id = pr.pageId "
                    + "WHERE p.chapterId = ? AND pr.imagePhash IS NOT NULL";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            if (includeRevisionHistory) {
                ps.setLong(2, chapterId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String existing = rs.getString(1);
                    if (ImagePhashUtil.hammingDistance(newHash, existing) <= threshold) {
                        // Kept short so it stays readable in the upload toast.
                        throw new IllegalArgumentException(
                                "This image was already used in this chapter. Please edit it before uploading.");
                    }
                }
            }
        }
    }

    /**
     * Lazy cache: checks whether the PageRevision table AND its imagePhash column are ready
     * (the table is created at runtime by PageRepository). Only caches when = true (once present, it stays present).
     * If = false, always re-checks, because the table/column may be created by PageRepository at any
     * time after the first check (e.g. after the Mangaka's first upload) - caching false forever
     * would defeat the duplicate-prevention logic.
     * Automatically ALTERs to add the imagePhash column if the table already existed but lacked the column
     * (e.g. the table was created by an older code version) - avoids an "Invalid column name" error in the UNION query.
     */
    private volatile boolean pageRevisionTableReady = false;

    private boolean isPageRevisionTableReady(Connection conn) throws SQLException {
        if (pageRevisionTableReady) {
            return true;
        }
        boolean tableExists;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT CASE WHEN OBJECT_ID('dbo.PageRevision', 'U') IS NOT NULL THEN 1 ELSE 0 END");
             ResultSet rs = ps.executeQuery()) {
            tableExists = rs.next() && rs.getInt(1) == 1;
        }
        if (!tableExists) {
            return false;
        }
        boolean columnExists;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT CASE WHEN COL_LENGTH('dbo.PageRevision', 'imagePhash') IS NULL THEN 0 ELSE 1 END");
             ResultSet rs = ps.executeQuery()) {
            columnExists = rs.next() && rs.getInt(1) == 1;
        }
        if (!columnExists) {
            try (Statement alter = conn.createStatement()) {
                alter.execute("ALTER TABLE [dbo].[PageRevision] ADD [imagePhash] [char](16) NULL");
            }
        }
        pageRevisionTableReady = true;
        return true;
    }

    /**
     * Public entry point for the Mangaka upload flow (PageApiController) - checks for duplicate images
     * across the whole chapter, opening its own connection.
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
     * Soft-deletes all active PAGE images with the same pageNumber in the chapter.
     * Called before inserting a new PAGE to ensure only 1 active image per page.
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
     * Public entry point for deactivating a page's active ChapterImage rows without
     * inserting a replacement - used when a rollback lands on a non-LETTERING stage,
     * since ChapterImage only ever represents the page's finalized (LETTERING) image.
     */
    public void deactivatePageImages(long chapterId, int pageNumber) {
        try (Connection conn = dataSource.getConnection()) {
            deactivateActivePageImages(conn, chapterId, pageNumber);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot deactivate page images", ex);
        }
    }

    /**
     * Gets images for a PageTask.
     * UNION of 2 sources:
     *  - Images already uploaded to the task (ChapterImage.pageTaskId = task)
     *  - Fallback: the original page from the Page table within the task's range, without a corresponding task image yet
     * Used to render the full workspace even when the assistant hasn't uploaded yet.
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

    /** Overload without a hash (used for backfilling from an existing Page.imageUrl - no file available to hash). */
    public void syncFinalPageUpload(long chapterId, int pageNumber, long uploadedBy, String fileUrl) {
        syncFinalPageUpload(chapterId, pageNumber, uploadedBy, fileUrl, null);
    }

    /**
     * Mangaka syncs one finished page image into ChapterImage (not through a task).
     * Only the series-owning Mangaka may call this. Deactivates the old image with the same pageNumber first.
     * imagePhash: stored to support chapter-wide duplicate prevention (null if it could not be computed).
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
     * Backfills all LETTERING pages of the chapter that don't yet have an active ChapterImage.
     * Called when the chapter transitions to EDITORIAL_REVIEW to ensure every completed page is recorded.
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

    /** Helper query with 2 long parameters. */
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
     * Soft-deletes one image. Only the uploader or the series-owning Mangaka may delete it.
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

    /** Finds a ChapterImage by ID, returns null if it does not exist. */
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
     * Gets ChapterImages by a list of ids, scoped to a single pageTaskId (safety - avoids leaking another task's images).
     * Does not filter by isActive since it's used to display history, including old replaced/rejected images.
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

    /** Gets the mangakaId who owns the chapter's series. */
    public long findChapterOwnerMangaka(long chapterId) {
        String sql = "SELECT s.mangakaId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        return queryLong(sql, chapterId, "Chapter not found");
    }

    /** Gets the tantouEditorId assigned to the chapter. */
    public long findChapterTantouEditor(long chapterId) {
        String sql = "SELECT s.tantouEditorId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        return queryLong(sql, chapterId, "Chapter not found");
    }

    /** Gets the chapterId of a PageTask. */
    public long findTaskChapterId(long pageTaskId) {
        String sql = "SELECT chapterId FROM PageTask WHERE id = ?";
        return queryLong(sql, pageTaskId, "Task not found");
    }

    /** Gets the assigned assistantId of a PageTask. */
    public long findTaskAssistantId(long pageTaskId) {
        String sql = "SELECT assistantId FROM PageTask WHERE id = ?";
        return queryLong(sql, pageTaskId, "Task not found");
    }

    /**
     * Validates upload permission before inserting.
     * ASSISTANT only, needs pageTaskId + pageNumber, the task must belong to the chapter and be assigned to that person.
     */
    private void validateUpload(Connection conn, long chapterId, Long pageTaskId, long uploadedBy,
            Integer pageNumber, String fileUrl) throws SQLException {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("fileUrl is required");
        }

        ensureChapterExists(conn, chapterId);

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
    }

    /** Maps ResultSet -> ChapterImageItem. pageTaskId and pageNumber are nullable. */
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

    /** Throws an exception if the chapter does not exist. */
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

    /** Checks whether a user has a specific role (queries the DB directly, used during upload validation). */
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

    /** Reads the chapterId and assistantId of a PageTask to validate upload permission. */
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

    /** Helper: a query that returns a single long value, throws an exception if not found. */
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

    /** Internal DTO for reading the chapterId + assistantId of a PageTask in a single query. */
    private static class TaskAccess {
        private long chapterId;
        private long assistantId;
    }
}