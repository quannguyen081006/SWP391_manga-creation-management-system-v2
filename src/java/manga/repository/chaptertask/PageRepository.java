package manga.repository.chaptertask;

import manga.model.chaptertask.PageRevisionEntry;
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
 * Repository managing page slots within a chapter.
 *
 * Each "page slot" represents a drawn page within a chapter (e.g. page 1, page 2...).
 * A page slot belongs to a chapter and can be assigned to an assistant's PageTask.
 *
 * =====================  TABLE OF CONTENTS  =====================
 *  1. TABLE / COLUMN READINESS CHECKS
 *     - isPageTableReady()          : checks whether the Page table exists (lazy + cached)
 *     - requirePageTableReady()     : throws if the table doesn't exist
 *     - ensurePageStageColumnReady(): automatically ALTER TABLE to add the completedStage column if missing
 *
 *  2. PAGE LIST QUERIES
 *     - listByChapter(chapterId)    : gets all page slots for a chapter, along with the active task
 *     - listEmptySlots(chapterId, limit) : gets slots without an image (status = EMPTY)
 *     - findById(pageId)            : gets a single page slot by ID
 *
 *  3. CREATE PAGE SLOT
 *     - create(chapterId, pageNumber)        : creates a single slot, returns its ID
 *     - bulkCreate(chapterId, totalPages)    : creates multiple slots at once (used when creating a new chapter)
 *     - bulkCreate(conn, chapterId, totalPages) : overload using an existing connection (within a transaction)
 *
 *  4. PAGE COUNTS
 *     - countByChapter(chapterId)   : total number of slots in the chapter
 *     - countUploaded(chapterId)    : number of slots that already have an uploaded image
 *     - nextPageNumber(chapterId)   : the next page number to be created
 *
 *  5. STATUS / IMAGE UPDATES
 *     - markUploaded(...)           : marks IN_PROGRESS when the assistant uploads an image
 *     - markApproved(...)           : marks APPROVED when the tantou approves
 *     - promoteTaskImage(...)       : syncs the image from an approved task into the page slot
 *     - upsertUploadedByPageNumber(): upserts a page by pageNumber (creates a new one if not present)
 *
 *  6. DELETE PAGE
 *     - delete(pageId)              : deletes a slot, blocked if there is an active task
 *
 *  7. STAGE PROGRESSION (drawing pipeline)
 *     - resolveNextStage()          : computes the next stage, validates that it doesn't move backwards
 *     - resolveTaskCompletionStage(): computes the completion stage based on taskType
 *     - normalizeStage()            : normalizes the stage name (e.g. "INK" -> "INKING")
 *     Stage order: SKETCHING -> INKING -> COLORING -> SCREENTONE -> LETTERING
 * ======================================================
 */
@Repository
public class PageRepository {

    /** Table name used in SQL, with schema prefix to avoid conflicts. */
    public static final String TABLE_PAGE = "[dbo].[Page]";

    /** Task statuses considered "closed" - not counted as active when checking delete blocks. */
    private static final String SQL_CLOSED_TASK_STATUSES = "'APPROVED','DELETED','REASSIGNED','CANCELLED'";

    /** Order of stages in the manga drawing pipeline. Used to validate stage progression. */
    private static final List<String> PAGE_STAGES = Arrays.asList(
            "SKETCHING", "INKING", "COLORING", "SCREENTONE", "LETTERING");

    /** Lazy cache: null = not checked yet, true/false = the result. Uses volatile for thread safety. */
    private volatile Boolean pageTableReady;
    private volatile Boolean pageStageColumnReady;
    private volatile Boolean pageRevisionTableReady;

    @Autowired
    private DataSource dataSource;

    // =====================================================================
    // 1. TABLE / COLUMN READINESS CHECKS
    // =====================================================================

    /** Throws IllegalArgumentException if the Page table has not been created. */
    private void requirePageTableReady() {
        if (!isPageTableReady()) {
            throw new IllegalArgumentException(
                    "Page table is missing. Run database/schema.sql and database/seed_v5.sql on MangaEditorialDB first.");
        }
    }

    /**
     * Checks whether the Page table exists.
     * The result is cached in pageTableReady so it isn't queried again each time.
     * Double-checked locking for safety when multiple threads run concurrently.
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
     * Ensures the completedStage column exists in the Page table.
     * If it doesn't exist yet, automatically runs ALTER TABLE to add it (a small automatic migration).
     * Also uses double-checked locking + caching.
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

    /**
     * Ensures the PageRevision table (history of image/stage changes for a page) exists.
     * Automatically runs CREATE TABLE if it doesn't exist - avoids having to run a manual migration on teammates' machines.
     */
    private void ensurePageRevisionTableReady() {
        if (!isPageTableReady()) {
            return;
        }
        if (Boolean.TRUE.equals(pageRevisionTableReady)) {
            return;
        }
        synchronized (this) {
            if (Boolean.TRUE.equals(pageRevisionTableReady)) {
                return;
            }
            String createSql =
                    "IF OBJECT_ID('dbo.PageRevision','U') IS NULL "
                    + "CREATE TABLE [dbo].[PageRevision] ("
                    + "[id] [bigint] IDENTITY(1,1) NOT NULL, "
                    + "[pageId] [bigint] NOT NULL, "
                    + "[imageUrl] [varchar](512) NULL, "
                    + "[completedStage] [varchar](30) NULL, "
                    + "[changedBy] [bigint] NULL, "
                    + "[changedAt] [datetime] NOT NULL, "
                    + "[source] [varchar](20) NOT NULL, "
                    + "[imagePhash] [char](16) NULL, "
                    + "CONSTRAINT [PK_PageRevision] PRIMARY KEY CLUSTERED ([id] ASC))";
            String indexSql =
                    "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_PageRevision_pageId' "
                    + "AND object_id = OBJECT_ID('dbo.PageRevision')) "
                    + "CREATE INDEX [IX_PageRevision_pageId] ON [dbo].[PageRevision]([pageId] ASC, [id] DESC)";
            String columnSql =
                    "IF OBJECT_ID('dbo.PageRevision','U') IS NOT NULL "
                    + "AND COL_LENGTH('dbo.PageRevision', 'imagePhash') IS NULL "
                    + "ALTER TABLE [dbo].[PageRevision] ADD [imagePhash] [char](16) NULL";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement create = conn.prepareStatement(createSql);
                 PreparedStatement index = conn.prepareStatement(indexSql);
                 PreparedStatement column = conn.prepareStatement(columnSql)) {
                create.executeUpdate();
                index.executeUpdate();
                column.executeUpdate();
                pageRevisionTableReady = Boolean.TRUE;
            } catch (SQLException ex) {
                throw new RuntimeException("Cannot prepare page revision table", ex);
            }
        }
    }

    /** Records one history entry for a page's image/stage change. Called after each successful page update. */
    private void recordRevision(long pageId, String imageUrl, String stage, long changedBy, String source, String imagePhash) {
        ensurePageRevisionTableReady();
        String sql = "INSERT INTO [dbo].[PageRevision] (pageId, imageUrl, completedStage, changedBy, changedAt, source, imagePhash) "
                + "VALUES (?, ?, ?, ?, GETDATE(), ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, pageId);
            ps.setString(2, imageUrl);
            ps.setString(3, stage);
            ps.setLong(4, changedBy);
            ps.setString(5, source);
            ps.setString(6, imagePhash);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot record page revision", ex);
        }
    }

    /**
     * Gets the full history of image/stage changes for a page, newest first.
     */
    public List<PageRevisionEntry> listRevisions(long pageId) {
        ensurePageRevisionTableReady();
        String sql =
                "SELECT r.id, r.imageUrl, r.completedStage, r.changedAt, r.source, u.fullName AS changedByName "
                + "FROM [dbo].[PageRevision] r "
                + "LEFT JOIN [User] u ON u.id = r.changedBy "
                + "WHERE r.pageId = ? "
                + "ORDER BY r.id DESC";
        List<PageRevisionEntry> entries = new ArrayList<PageRevisionEntry>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PageRevisionEntry entry = new PageRevisionEntry();
                    entry.setId(rs.getLong("id"));
                    entry.setImageUrl(rs.getString("imageUrl"));
                    entry.setCompletedStage(rs.getString("completedStage"));
                    entry.setChangedAt(rs.getTimestamp("changedAt"));
                    entry.setSource(rs.getString("source"));
                    entry.setChangedByName(rs.getString("changedByName"));
                    entries.add(entry);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load page revisions", ex);
        }
        return entries;
    }

    /**
     * Rolls a page back to the exact image + stage of a previous history entry.
     * Writes directly (bypassing resolveNextStage) since a rollback intentionally moves the stage backwards.
     * Then appends a new entry with source=ROLLBACK to keep the timeline continuous.
     */
    public void rollbackToRevision(long pageId, long revisionId, long changedBy) {
        requirePageTableReady();
        ensurePageStageColumnReady();
        ensurePageRevisionTableReady();

        String readSql = "SELECT imageUrl, completedStage, imagePhash FROM [dbo].[PageRevision] WHERE id = ? AND pageId = ?";
        String imageUrl;
        String stage;
        String imagePhash;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(readSql)) {
            ps.setLong(1, revisionId);
            ps.setLong(2, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Revision not found for this page");
                }
                imageUrl = rs.getString("imageUrl");
                stage = rs.getString("completedStage");
                imagePhash = rs.getString("imagePhash");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load page revision", ex);
        }

        String status = (imageUrl == null || imageUrl.trim().isEmpty()) ? "EMPTY" : "IN_PROGRESS";
        String updateSql = "UPDATE " + TABLE_PAGE
                + " SET imageUrl = ?, completedStage = ?, uploadedBy = ?, uploadedAt = GETDATE(), status = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, imageUrl);
            ps.setString(2, stage);
            ps.setLong(3, changedBy);
            ps.setString(4, status);
            ps.setLong(5, pageId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Page not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot rollback page", ex);
        }

        recordRevision(pageId, imageUrl, stage, changedBy, "ROLLBACK", imagePhash);
    }

    // =====================================================================
    // 2. PAGE LIST QUERIES
    // =====================================================================

    /**
     * SQL to fetch the list of page slots for a chapter.
     * Uses OUTER APPLY to join with the most recent active PageTask (if any),
     * to show the task and assistant information currently working on that page.
     * "Closed" tasks (APPROVED/DELETED/REASSIGNED/CANCELLED) are not counted.
     */
    private static final String LIST_SQL =
            "SELECT p.id, p.chapterId, p.pageNumber, p.imageUrl, p.uploadedBy, p.uploadedAt, p.status, p.completedStage, "
            + "t.id AS taskId, p.completedStage AS taskType, t.status AS taskStatus, t.assistantId, u.fullName AS assistantName "
            + "FROM " + TABLE_PAGE + " p "
            + "OUTER APPLY ( "
            + "  SELECT TOP 1 pt.id, pt.status, pt.assistantId "
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
     * Gets all page slots of a chapter, along with the corresponding active task and assistant.
     * Returns an empty list if the Page table does not exist yet (graceful degradation).
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

    /** Gets a page slot by ID. Returns null if not found or the table isn't ready. */
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
     * Gets slots without an image (status = EMPTY), limited to a number of results.
     * Used to find pages nobody is working on yet so a new task can be assigned.
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
    // 3. CREATE PAGE SLOT
    // =====================================================================

    /** Creates a new page slot with status EMPTY. Returns the ID of the newly created slot. */
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

    /** Creates multiple page slots at once (1..totalPages). Used when creating a new chapter. */
    public void bulkCreate(long chapterId, int totalPages) {
        try (Connection conn = dataSource.getConnection()) {
            bulkCreate(conn, chapterId, totalPages);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot bulk create page slots", ex);
        }
    }

    /**
     * Overload accepting an externally provided connection - used within a transaction.
     * Uses batch insert to optimize performance when creating many pages.
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
    // 4. PAGE COUNTS
    // =====================================================================

    /** Counts the total number of page slots in a chapter. */
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

    /** Counts the number of slots that already have an uploaded image (imageUrl != null). */
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

    /** Returns the next page number to be created = MAX(pageNumber) + 1. */
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
    // 5. STATUS / IMAGE UPDATES
    // =====================================================================

    /** Marks a page IN_PROGRESS when the assistant uploads an image. */
    public void markUploaded(long pageId, String imageUrl, long uploadedBy) {
        markUploaded(pageId, imageUrl, uploadedBy, null, null);
    }

    /** Overload of markUploaded, including completedStage information. */
    public void markUploaded(long pageId, String imageUrl, long uploadedBy, String completedStage) {
        markUploaded(pageId, imageUrl, uploadedBy, completedStage, null);
    }

    /** Overload of markUploaded, including completedStage + imagePhash (to prevent duplicate images across the whole chapter). */
    public void markUploaded(long pageId, String imageUrl, long uploadedBy, String completedStage, String imagePhash) {
        markImage(pageId, imageUrl, uploadedBy, "IN_PROGRESS", completedStage, imagePhash);
    }

    /** Marks a page APPROVED (without a stage). */
    public void markApproved(long pageId, String imageUrl, long uploadedBy) {
        markApproved(pageId, imageUrl, uploadedBy, null);
    }

    /** Marks a page APPROVED, including completedStage. */
    public void markApproved(long pageId, String imageUrl, long uploadedBy, String completedStage) {
        markImage(pageId, imageUrl, uploadedBy, "APPROVED", completedStage, null);
    }

    /** Central function that updates imageUrl, uploadedBy, status, and completedStage for a page. */
    private void markImage(long pageId, String imageUrl, long uploadedBy, String status, String completedStage, String imagePhash) {
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
        // stage may be null (keep the old stage) -> re-read the actual stage to record history correctly
        String effectiveStage = stage != null ? stage : findCurrentCompletedStage(pageId);
        recordRevision(pageId, imageUrl, effectiveStage, uploadedBy, "MANGAKA_UPLOAD", imagePhash);
    }

    /**
     * Syncs the image from an approved task into the page slot by (chapterId, pageNumber).
     * If the slot doesn't exist yet, creates it first then updates it.
     * Uses taskType to compute the corresponding completion stage.
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

    /** Updates the image and stage for a page when a task is approved, using taskType to derive the stage. */
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
        String effectiveStage = stage != null ? stage : findCurrentCompletedStage(pageId);
        // imagePhash = null: this image came from a task already uploaded by the assistant via ChapterImageRepository,
        // the hash was already saved there so there's no need to duplicate it here.
        recordRevision(pageId, imageUrl, effectiveStage, uploadedBy, "TASK_APPROVED", null);
    }

    /**
     * Upserts a page slot by (chapterId, pageNumber): updates if it exists, creates if not.
     * Used when syncing an image from a task into a page without knowing the pageId in advance.
     */
    public void upsertUploadedByPageNumber(long chapterId, int pageNumber, String imageUrl, long uploadedBy) {
        upsertUploadedByPageNumber(chapterId, pageNumber, imageUrl, uploadedBy, null);
    }

    /** Overload of upsertUploadedByPageNumber, including completedStage. */
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
    // 6. DELETE PAGE
    // =====================================================================

    /**
     * Deletes a page slot.
     * Blocked if there is an active (not closed) task covering that page.
     * Uses PageTask's pageRangeStart/End to check for overlap.
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
            // Check whether there is an active task - if so, deletion is not allowed
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
    // Order: SKETCHING -> INKING -> COLORING -> SCREENTONE -> LETTERING
    // =====================================================================

    /**
     * Computes the next stage based on the current stage and the requested stage.
     * Does not allow moving the stage backwards or skipping more than one step.
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
     * Computes the completion stage based on taskType when a task is approved.
     * If taskType is MIXED, automatically computes the next stage from the current stage.
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
            return current; // Already at the final stage, don't advance further
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

    /** Reads the page's current completedStage from the DB. */
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
     * Normalizes the stage name: uppercase, handles aliases ("INK" -> "INKING", "TONE" -> "SCREENTONE"...).
     * Throws if the stage is invalid.
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
     * Maps a ResultSet to a full PageSlotSummary (including task and assistant information).
     * Used by listByChapter which JOINs with PageTask and User.
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
     * Reads an optional string column - does not throw if the column doesn't exist in the ResultSet.
     * Used for compatibility with older DBs that don't have the completedStage column.
     */
    private String readOptionalString(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException ex) {
            return null;
        }
    }

    /** Checks whether the exception is caused by the Page table not existing. */
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
