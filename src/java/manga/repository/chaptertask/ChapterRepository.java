package manga.repository.chaptertask;

import manga.model.AuthenticatedUser;
import manga.model.chaptertask.ChapterSummary;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository that manages Chapter.
 *
 * Table of contents:
 *  1.  listAll()                          - Gets all chapters (no filter)
 *  2.  listAll(user)                      - Gets chapters by role: ADMIN=all, MANGAKA=own series, TANTOU=assigned series
 *  3.  listBySeries()                     - Gets chapters by seriesId
 *  4.  findById()                         - Finds a chapter by ID
 *  5.  create()                           - Creates a chapter with a specified chapterNumber (legacy)
 *  6.  createNext()                       - Creates the next chapter, auto-incrementing chapterNumber + creating page slots if the schema is ready
 *  7.  createNextWithPageSlots()          - Creates a chapter + bulk creates Page slots in 1 transaction
 *  8.  createNextLegacy()                 - Creates a chapter without Page slots (fallback when the schema isn't ready)
 *  9.  isPageSchemaReady()                - Checks whether the DB has the totalPages column and the Page table (volatile cache)
 * 10.  updateChapterMetadata()            - Updates title + submissionDeadline + recomputes publicationDate
 * 11.  updateChapterTitle()               - Updates only the title
 * 12.  submitForReview()                  - Transitions the chapter to EDITORIAL_REVIEW (only when completionPct = 100%)
 * 13.  deleteChapter()                    - Deletes a PLANNING chapter with no tasks
 * 14.  findChaptersWithDeadlineInDays()   - Finds chapters whose deadline is in N days (used by the warning scheduler)
 * 15.  findMissedSubmissionDeadlineChapters() - Finds chapters past their deadline that aren't complete
 * 16.  findSeriesOwnerMangaka()           - Gets the mangakaId who owns the series
 * 17.  findOwnerMangakaByChapter()        - Gets the mangakaId who owns the series, via chapterId
 * 18.  getChapterStatus()                 - Gets the chapter's status
 * 19.  updateChapterStatus()              - Updates the chapter status (used by ManuscriptVersionService)
 * 20.  getSeriesStatus()                  - Gets the series status via chapterId
 * 21.  findSeriesTantou()                 - Gets the tantouEditorId of the series
 * 22.  getChapterMangaka()                - Gets the mangakaId via chapterId (short alias)
 * 23.  getChapterTantou()                 - Gets the tantouEditorId via chapterId (short alias)
 *
 * Deadline rules:
 *  - submissionDeadline must not be a past date
 *  - submissionDeadline must be at least CHAPTER_SERIES_DEADLINE_BUFFER_DAYS (7 days) before the series deadline
 *  - publicationDate = submissionDeadline + CHAPTER_PUBLICATION_OFFSET_DAYS (14 days), auto-computed on create/update
 */
@Repository
public class ChapterRepository {

    /** Number of days added to submissionDeadline to compute publicationDate. */
    private static final int CHAPTER_PUBLICATION_OFFSET_DAYS = 14;

    /** Minimum number of days the chapter deadline must be before the series deadline. */
    private static final int CHAPTER_SERIES_DEADLINE_BUFFER_DAYS = 7;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PageTaskRepository pageTaskRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private ChapterImageRepository chapterImageRepository;

    private static final String CHAPTER_COLUMNS_LEGACY =
            "id, seriesId, chapterNumber, title, status, submissionDeadline, publicationDate, completionPct, atRisk";
    private static final String CHAPTER_COLUMNS_EXTENDED =
            CHAPTER_COLUMNS_LEGACY + ", totalPages";

    /** Volatile cache: null=not checked yet, TRUE/FALSE=already checked result. */
    private volatile Boolean pageSchemaReady;

    /** Gets all chapters, ordered newest first. */
    public List<ChapterSummary> listAll() {
        String sql = "SELECT " + chapterSelectColumns(null) + " FROM Chapter ORDER BY createdAt DESC";
        List<ChapterSummary> rows = new ArrayList<ChapterSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapChapter(rs));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list chapters", ex);
        }
        return rows;
    }

    /**
     * Gets chapters based on the user's role:
     *  - ADMIN: all chapters
     *  - MANGAKA: only chapters belonging to series they own
     *  - TANTOU_EDITOR: only chapters belonging to series they're assigned to
     *  - Other: returns an empty list
     */
    public List<ChapterSummary> listAll(AuthenticatedUser user) {
        String sql;
        List<Object> params = new ArrayList<Object>();

        if (user.hasRole("ADMIN")) {
            sql = "SELECT " + chapterSelectColumns(null) + " FROM Chapter ORDER BY createdAt DESC";
        } else if (user.hasRole("MANGAKA")) {
            sql = "SELECT " + chapterSelectColumns("c")
                + " FROM Chapter c JOIN Series s ON s.id = c.seriesId "
                + "WHERE s.mangakaId = ? ORDER BY c.createdAt DESC";
            params.add(user.getId());
        } else if (user.hasRole("TANTOU_EDITOR")) {
            sql = "SELECT " + chapterSelectColumns("c")
                + " FROM Chapter c JOIN Series s ON s.id = c.seriesId "
                + "WHERE s.tantouEditorId = ? ORDER BY c.createdAt DESC";
            params.add(user.getId());
        } else {
            return new ArrayList<ChapterSummary>();
        }

        List<ChapterSummary> rows = new ArrayList<ChapterSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setLong(i + 1, (Long) params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapChapter(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list chapters", ex);
        }
        return rows;
    }

    /** Gets the list of chapters by seriesId, ordered by chapterNumber ASC. */
    public List<ChapterSummary> listBySeries(long seriesId) {
        String sql = "SELECT " + chapterSelectColumns(null) + " FROM Chapter WHERE seriesId = ? ORDER BY chapterNumber";
        List<ChapterSummary> rows = new ArrayList<ChapterSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapChapter(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list chapters", ex);
        }
        return rows;
    }

    /** Finds a chapter by ID, returns null if it does not exist. */
    public ChapterSummary findById(long chapterId) {
        String sql = "SELECT " + chapterSelectColumns(null) + " FROM Chapter WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapChapter(rs);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load chapter", ex);
        }
    }

    /**
     * Creates a chapter with a specified chapterNumber (legacy, no auto-increment).
     * publicationDate is auto-computed = submissionDeadline + 14 days.
     */
    public long create(long seriesId, int chapterNumber, String title, Date submissionDeadline) {
        String sql = "INSERT INTO Chapter (seriesId, chapterNumber, title, status, submissionDeadline, publicationDate, completionPct, atRisk, createdAt) VALUES (?, ?, ?, 'PLANNING', ?, ?, 0.00, 0, GETDATE())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            validateChapterDeadlineForSeries(conn, seriesId, submissionDeadline);
            ps.setLong(1, seriesId);
            ps.setInt(2, chapterNumber);
            ps.setString(3, title);
            ps.setDate(4, submissionDeadline);
            ps.setDate(5, publicationDateFor(submissionDeadline));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            throw new IllegalStateException("Cannot create chapter");
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create chapter", ex);
        }
    }

    /**
     * Creates the next chapter with an auto-incremented chapterNumber.
     * If the schema is ready (has the Page table + totalPages column) and totalPages > 0 -> creates page slots too.
     * If the schema isn't ready -> falls back to createNextLegacy().
     */
    public long createNext(long seriesId, String title, Date submissionDeadline, int totalPages) {
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages cannot be negative");
        }
        int slots = totalPages < 1 ? 0 : totalPages;
        try {
            if (isPageSchemaReady() && slots > 0) {
                return createNextWithPageSlots(seriesId, title, submissionDeadline, slots);
            }
            return createNextLegacy(seriesId, title, submissionDeadline);
        } catch (RuntimeException ex) {
            if (isMissingPageSchema(ex)) {
                pageSchemaReady = Boolean.FALSE;
                return createNextLegacy(seriesId, title, submissionDeadline);
            }
            throw ex;
        }
    }

    /**
     * Creates a chapter + bulk creates Page slots in 1 transaction.
     * Uses UPDLOCK/HOLDLOCK to avoid a race condition when computing chapterNumber.
     */
    private long createNextWithPageSlots(long seriesId, String title, Date submissionDeadline, int totalPages) {
        String nextSql = "SELECT ISNULL(MAX(chapterNumber), 0) + 1 FROM Chapter WITH (UPDLOCK, HOLDLOCK) WHERE seriesId = ?";
        String insertSql = "INSERT INTO Chapter (seriesId, chapterNumber, title, status, submissionDeadline, publicationDate, completionPct, atRisk, totalPages, createdAt) VALUES (?, ?, ?, 'PLANNING', ?, ?, 0.00, 0, ?, GETDATE())";

        try (Connection conn = dataSource.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                validateChapterDeadlineForSeries(conn, seriesId, submissionDeadline);

                int nextChapterNumber;
                try (PreparedStatement ps = conn.prepareStatement(nextSql)) {
                    ps.setLong(1, seriesId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Series not found");
                        }
                        nextChapterNumber = rs.getInt(1);
                    }
                }

                long newId;
                try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, seriesId);
                    ps.setInt(2, nextChapterNumber);
                    ps.setString(3, title);
                    ps.setDate(4, submissionDeadline);
                    ps.setDate(5, publicationDateFor(submissionDeadline));
                    ps.setInt(6, totalPages);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Cannot create chapter");
                        }
                        newId = rs.getLong(1);
                    }
                }
                pageRepository.bulkCreate(conn, newId, totalPages);
                conn.commit();
                return newId;
            } catch (RuntimeException ex) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw ex;
            } catch (SQLException ex) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw new RuntimeException("Cannot create chapter", ex);
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create chapter", ex);
        }
    }

    /**
     * Creates a chapter without Page slots (fallback when the DB lacks the Page table/totalPages column).
     * Uses UPDLOCK/HOLDLOCK to avoid a race condition when computing chapterNumber.
     */
    private long createNextLegacy(long seriesId, String title, Date submissionDeadline) {
        String nextSql = "SELECT ISNULL(MAX(chapterNumber), 0) + 1 FROM Chapter WITH (UPDLOCK, HOLDLOCK) WHERE seriesId = ?";
        String insertSql = "INSERT INTO Chapter (seriesId, chapterNumber, title, status, submissionDeadline, publicationDate, completionPct, atRisk, createdAt) VALUES (?, ?, ?, 'PLANNING', ?, ?, 0.00, 0, GETDATE())";

        try (Connection conn = dataSource.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                validateChapterDeadlineForSeries(conn, seriesId, submissionDeadline);

                int nextChapterNumber;
                try (PreparedStatement ps = conn.prepareStatement(nextSql)) {
                    ps.setLong(1, seriesId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Series not found");
                        }
                        nextChapterNumber = rs.getInt(1);
                    }
                }

                long newId;
                try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, seriesId);
                    ps.setInt(2, nextChapterNumber);
                    ps.setString(3, title);
                    ps.setDate(4, submissionDeadline);
                    ps.setDate(5, publicationDateFor(submissionDeadline));
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Cannot create chapter");
                        }
                        newId = rs.getLong(1);
                    }
                }
                conn.commit();
                return newId;
            } catch (RuntimeException ex) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw ex;
            } catch (SQLException ex) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw new RuntimeException("Cannot create chapter", ex);
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create chapter", ex);
        }
    }

    /**
     * Checks whether the DB has the totalPages column in Chapter and the Page table.
     * The result is cached with a volatile Boolean so it's queried only once, thread-safe.
     */
    private boolean isPageSchemaReady() {
        if (pageSchemaReady != null) {
            return pageSchemaReady.booleanValue();
        }
        synchronized (this) {
            if (pageSchemaReady != null) {
                return pageSchemaReady.booleanValue();
            }
            boolean ready = false;
            String sql = "SELECT CASE WHEN COL_LENGTH('dbo.Chapter', 'totalPages') IS NOT NULL "
                    + "AND OBJECT_ID('dbo.Page', 'U') IS NOT NULL THEN 1 ELSE 0 END";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ready = rs.getInt(1) == 1;
                }
            } catch (SQLException ex) {
                ready = false;
            }
            pageSchemaReady = Boolean.valueOf(ready);
            return ready;
        }
    }

    /** Checks whether the exception is caused by a missing Page/totalPages schema (for fallback). */
    private boolean isMissingPageSchema(Throwable ex) {
        while (ex != null) {
            String msg = ex.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("totalpages") || lower.contains("invalid object name 'page'")) {
                    return true;
                }
            }
            ex = ex.getCause();
        }
        return false;
    }

    /**
     * Returns the list of SELECT columns appropriate for the current schema.
     * tablePrefix != null -> prefixes each column (used when JOINing).
     */
    private String chapterSelectColumns(String tablePrefix) {
        String cols = isPageSchemaReady() ? CHAPTER_COLUMNS_EXTENDED : CHAPTER_COLUMNS_LEGACY;
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            return cols;
        }
        String[] parts = cols.split(", ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(tablePrefix).append('.').append(parts[i]);
        }
        return sb.toString();
    }

    /**
     * Updates title + submissionDeadline, recomputes publicationDate.
     * After updating -> refreshes the chapter's completionPct via PageTaskRepository.
     */
    public void updateChapterMetadata(long chapterId, String title, Date submissionDeadline) {
        String sql = "UPDATE Chapter SET title = ?, publicationDate = ?, submissionDeadline = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            validateChapterDeadlineForChapter(conn, chapterId, submissionDeadline);
            ps.setString(1, title);
            ps.setDate(2, publicationDateFor(submissionDeadline));
            ps.setDate(3, submissionDeadline);
            ps.setLong(4, chapterId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Chapter not found");
            }
            pageTaskRepository.refreshChapterProgress(chapterId);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update chapter", ex);
        }
    }

    /** Updates only the chapter's title. */
    public void updateChapterTitle(long chapterId, String title) {
        String sql = "UPDATE Chapter SET title = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setLong(2, chapterId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Chapter not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update chapter", ex);
        }
    }

    /**
     * Transitions the chapter to EDITORIAL_REVIEW.
     * Conditions: the chapter must belong to mangakaId's series, completionPct = 100%, status IN_PROGRESS or COMPLETE.
     * After transitioning -> backfills all completed page images into ChapterImage.
     */
    public void submitForReview(long chapterId, long mangakaId) {
        String sql =
            "UPDATE c SET c.status = 'EDITORIAL_REVIEW' "
            + "FROM Chapter c "
            + "JOIN Series s ON s.id = c.seriesId "
            + "WHERE c.id = ? AND s.mangakaId = ? AND c.completionPct >= 100.00 AND c.status IN ('IN_PROGRESS','COMPLETE')";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            ps.setLong(2, mangakaId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Chapter must be owner-managed and 100% complete before submit-review");
            }
            chapterImageRepository.backfillFinalPageUploads(chapterId, mangakaId);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot submit chapter for review", ex);
        }
    }

    /**
     * Deletes a chapter. Only allowed when:
     *  - The caller is the series-owning Mangaka
     *  - The chapter is in PLANNING status
     *  - The chapter has no PageTask yet
     */
    public void deleteChapter(long chapterId, long mangakaId) {
        long ownerId = findOwnerMangakaByChapter(chapterId);
        if (ownerId != mangakaId) {
            throw new IllegalArgumentException("Only series owner can delete chapter");
        }

        String statusSql = "SELECT status FROM Chapter WHERE id = ?";
        String taskCountSql = "SELECT COUNT(1) FROM PageTask WHERE chapterId = ?";
        String deletePagesSql = "DELETE FROM [dbo].[Page] WHERE chapterId = ?";
        String deleteSql = "DELETE FROM Chapter WHERE id = ?";

        try (Connection conn = dataSource.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(statusSql)) {
                    ps.setLong(1, chapterId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Chapter not found");
                        }
                        if (!"PLANNING".equalsIgnoreCase(rs.getString("status"))) {
                            throw new IllegalArgumentException("Only PLANNING chapters can be deleted");
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(taskCountSql)) {
                    ps.setLong(1, chapterId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            throw new IllegalArgumentException("Cannot delete chapter with existing tasks");
                        }
                    }
                }
                if (isPageSchemaReady()) {
                    try (PreparedStatement ps = conn.prepareStatement(deletePagesSql)) {
                        ps.setLong(1, chapterId);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setLong(1, chapterId);
                    if (ps.executeUpdate() == 0) {
                        throw new IllegalArgumentException("Chapter not found");
                    }
                }
                conn.commit();
            } catch (RuntimeException ex) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw ex;
            } catch (SQLException ex) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw new RuntimeException("Cannot delete chapter", ex);
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot delete chapter", ex);
        }
    }

    /**
     * Finds chapters whose deadline is exactly N days from today, with status PLANNING or IN_PROGRESS.
     * Used by ChapterDeadlineScheduler to send upcoming-deadline warnings.
     */
    public List<ChapterSummary> findChaptersWithDeadlineInDays(int days) {
        String sql =
            "SELECT " + chapterSelectColumns(null) + " "
            + "FROM Chapter "
            + "WHERE submissionDeadline = CAST(DATEADD(DAY, ?, GETDATE()) AS DATE) "
            + "  AND status IN ('PLANNING', 'IN_PROGRESS')";
        List<ChapterSummary> rows = new ArrayList<ChapterSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapChapter(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot query deadline chapters", ex);
        }
        return rows;
    }

    /**
     * Finds chapters that are past their deadline but not yet complete (not yet in EDITORIAL_REVIEW or COMPLETE).
     * Used by the SLA scheduler to mark atRisk.
     */
    public List<ChapterSummary> findMissedSubmissionDeadlineChapters() {
        String sql =
            "SELECT " + chapterSelectColumns(null) + " "
            + "FROM Chapter "
            + "WHERE submissionDeadline < CAST(GETDATE() AS DATE) "
            + "  AND status NOT IN ('EDITORIAL_REVIEW', 'COMPLETE')";
        List<ChapterSummary> rows = new ArrayList<ChapterSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapChapter(rs));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot query missed deadline chapters", ex);
        }
        return rows;
    }

    /** Gets the mangakaId who owns the series, by seriesId. */
    public long findSeriesOwnerMangaka(long seriesId) {
        String sql = "SELECT mangakaId FROM Series WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Series not found");
                }
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot verify series owner", ex);
        }
    }

    /** Gets the mangakaId who owns the series, via chapterId (JOIN Chapter -> Series). */
    public long findOwnerMangakaByChapter(long chapterId) {
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
            throw new RuntimeException("Cannot verify chapter owner", ex);
        }
    }

    /** Gets the chapter's current status. */
    public String getChapterStatus(long chapterId) {
        String sql = "SELECT status FROM Chapter WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Chapter not found");
                }
                return rs.getString("status");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot get chapter status", ex);
        }
    }

    /**
     * Updates the chapter status.
     * Called by ManuscriptVersionService when the manuscript is approved (-> APPROVED).
     */
    public void updateChapterStatus(long chapterId, String status) {
        String sql = "UPDATE Chapter SET status = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, chapterId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Chapter not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update chapter status", ex);
        }
    }

    /** Gets the status of the series containing the chapter (JOIN Chapter -> Series). */
    public String getSeriesStatus(long chapterId) {
        String sql = "SELECT s.status FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Chapter not found");
                }
                return rs.getString("status");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot get series status", ex);
        }
    }

    /** Gets the tantouEditorId of the series, by seriesId. */
    public long findSeriesTantou(long seriesId) {
        String sql = "SELECT tantouEditorId FROM Series WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Series not found");
                }
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load series tantou", ex);
        }
    }

    /**
     * Maps ResultSet -> ChapterSummary.
     * atRisk = true if the DB flags it OR the deadline has passed without reaching 100% (computed in real time).
     * totalPages is read conditionally (only when the schema is ready).
     */
    private ChapterSummary mapChapter(ResultSet rs) throws SQLException {
        ChapterSummary c = new ChapterSummary();
        c.setId(rs.getLong("id"));
        c.setSeriesId(rs.getLong("seriesId"));
        c.setChapterNumber(rs.getInt("chapterNumber"));
        c.setTitle(rs.getString("title"));
        c.setStatus(rs.getString("status"));
        c.setSubmissionDeadline(rs.getDate("submissionDeadline"));
        c.setPublicationDate(rs.getDate("publicationDate"));
        c.setCompletionPct(rs.getDouble("completionPct"));
        boolean storedAtRisk = rs.getBoolean("atRisk");
        boolean missedDeadline = c.getSubmissionDeadline() != null
                && c.getSubmissionDeadline().before(Date.valueOf(LocalDate.now()))
                && c.getCompletionPct() < 100.0
                && ("PLANNING".equalsIgnoreCase(c.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(c.getStatus()));
        c.setAtRisk(storedAtRisk || missedDeadline);
        if (hasColumn(rs, "totalPages")) {
            int totalPages = rs.getInt("totalPages");
            c.setTotalPages(rs.wasNull() ? null : Integer.valueOf(totalPages));
        } else {
            c.setTotalPages(null);
        }
        return c;
    }

    /** Checks whether the ResultSet has a column named label (used to read totalPages safely). */
    private boolean hasColumn(ResultSet rs, String label) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (label.equalsIgnoreCase(md.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }

    /** Computes publicationDate = submissionDeadline + 14 days. */
    private Date publicationDateFor(Date submissionDeadline) {
        if (submissionDeadline == null) {
            throw new IllegalArgumentException("submissionDeadline is required");
        }
        return Date.valueOf(submissionDeadline.toLocalDate().plusDays(CHAPTER_PUBLICATION_OFFSET_DAYS));
    }

    /** Checks that the date is not null and not in the past. */
    private void validateNotPast(Date date, String fieldName) {
        if (date == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (date.before(Date.valueOf(LocalDate.now()))) {
            throw new IllegalArgumentException(fieldName + " cannot be in the past");
        }
    }

    /** Validates the deadline when creating a new chapter: not in the past + must be 7 days before the series deadline. */
    private void validateChapterDeadlineForSeries(Connection conn, long seriesId, Date submissionDeadline) throws SQLException {
        validateNotPast(submissionDeadline, "submissionDeadline");
        String sql = "SELECT publicationDate FROM Series WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Series not found");
                }
                validateBeforeSeriesDeadline(submissionDeadline, rs.getDate("publicationDate"));
            }
        }
    }

    /** Validates the deadline when updating a chapter: not in the past + must be 7 days before the series deadline. */
    private void validateChapterDeadlineForChapter(Connection conn, long chapterId, Date submissionDeadline) throws SQLException {
        validateNotPast(submissionDeadline, "submissionDeadline");
        String sql = "SELECT s.publicationDate FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Chapter not found");
                }
                validateBeforeSeriesDeadline(submissionDeadline, rs.getDate("publicationDate"));
            }
        }
    }

    /** Throws an exception if submissionDeadline isn't at least 7 days earlier than seriesDeadline. */
    private void validateBeforeSeriesDeadline(Date submissionDeadline, Date seriesDeadline) {
        if (seriesDeadline == null) {
            throw new IllegalArgumentException("Series deadline must be set by assigned Tantou before creating or updating chapters");
        }
        Date latestChapterDeadline = Date.valueOf(seriesDeadline.toLocalDate().minusDays(CHAPTER_SERIES_DEADLINE_BUFFER_DAYS));
        if (submissionDeadline.after(latestChapterDeadline)) {
            throw new IllegalArgumentException("Chapter deadline must be at least 7 days before series deadline");
        }
    }

    /** Gets the mangakaId who owns the series, via chapterId (short alias, used by ManuscriptVersionService). */
    public long getChapterMangaka(long chapterId) {
        String sql = "SELECT s.mangakaId FROM Chapter c JOIN Series s ON s.id=c.seriesId WHERE c.id = ?";
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
            throw new RuntimeException("Cannot resolve chapter owner", ex);
        }
    }

    /** Gets the tantouEditorId assigned to the chapter (short alias, used by ManuscriptVersionService). */
    public long getChapterTantou(long chapterId) {
        String sql = "SELECT s.tantouEditorId FROM Chapter c JOIN Series s ON s.id=c.seriesId WHERE c.id = ?";
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
            throw new RuntimeException("Cannot resolve chapter tantou", ex);
        }
    }
}
