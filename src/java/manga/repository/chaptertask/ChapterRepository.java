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
 * Repository quản lý Chapter.
 *
 * Mục lục:
 *  1.  listAll()                          - Lấy tất cả chapter (không filter)
 *  2.  listAll(user)                      - Lấy chapter theo role: ADMIN=all, MANGAKA=seri mình, TANTOU=seri phụ trách
 *  3.  listBySeries()                     - Lấy chapter theo seriesId
 *  4.  findById()                         - Tìm chapter theo ID
 *  5.  create()                           - Tạo chapter với chapterNumber chỉ định (legacy)
 *  6.  createNext()                       - Tạo chapter tiếp theo, tự tăng chapterNumber + tạo page slots nếu schema sẵn sàng
 *  7.  createNextWithPageSlots()          - Tạo chapter + bulk create Page slots trong 1 transaction
 *  8.  createNextLegacy()                 - Tạo chapter không có Page slots (fallback khi schema chưa có)
 *  9.  isPageSchemaReady()                - Kiểm tra DB có cột totalPages và bảng Page chưa (cache volatile)
 * 10.  updateChapterMetadata()            - Cập nhật title + submissionDeadline + tính lại publicationDate
 * 11.  updateChapterTitle()               - Cập nhật chỉ title
 * 12.  submitForReview()                  - Chuyển chapter sang EDITORIAL_REVIEW (chỉ khi completionPct = 100%)
 * 13.  deleteChapter()                    - Xóa chapter PLANNING chưa có task
 * 14.  findChaptersWithDeadlineInDays()   - Tìm chapter deadline sau N ngày (dùng cho scheduler cảnh báo)
 * 15.  findMissedSubmissionDeadlineChapters() - Tìm chapter trễ deadline chưa hoàn thành
 * 16.  findSeriesOwnerMangaka()           - Lấy mangakaId chủ seri
 * 17.  findOwnerMangakaByChapter()        - Lấy mangakaId chủ seri qua chapterId
 * 18.  getChapterStatus()                 - Lấy status của chapter
 * 19.  updateChapterStatus()              - Cập nhật status chapter (dùng bởi ManuscriptVersionService)
 * 20.  getSeriesStatus()                  - Lấy status của seri qua chapterId
 * 21.  findSeriesTantou()                 - Lấy tantouEditorId của seri
 * 22.  getChapterMangaka()                - Lấy mangakaId qua chapterId (alias ngắn)
 * 23.  getChapterTantou()                 - Lấy tantouEditorId qua chapterId (alias ngắn)
 *
 * Quy tắc deadline:
 *  - submissionDeadline không được là ngày quá khứ
 *  - submissionDeadline phải trước seriesDeadline ít nhất CHAPTER_SERIES_DEADLINE_BUFFER_DAYS (7 ngày)
 *  - publicationDate = submissionDeadline + CHAPTER_PUBLICATION_OFFSET_DAYS (14 ngày), tự tính khi create/update
 */
@Repository
public class ChapterRepository {

    /** Số ngày cộng thêm vào submissionDeadline để tính publicationDate. */
    private static final int CHAPTER_PUBLICATION_OFFSET_DAYS = 14;

    /** Chapter deadline phải trước series deadline ít nhất bao nhiêu ngày. */
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

    /** Cache volatile: null=chưa check, TRUE/FALSE=kết quả đã check. */
    private volatile Boolean pageSchemaReady;

    /** Lấy tất cả chapter, sắp xếp mới nhất trước. */
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
     * Lấy chapter theo role của user:
     *  - ADMIN: toàn bộ chapter
     *  - MANGAKA: chỉ chapter thuộc seri mình sở hữu
     *  - TANTOU_EDITOR: chỉ chapter thuộc seri mình phụ trách
     *  - Khác: trả danh sách rỗng
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

    /** Lấy danh sách chapter theo seriesId, sắp xếp theo chapterNumber ASC. */
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

    /** Tìm chapter theo ID, trả null nếu không tồn tại. */
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
     * Tạo chapter với chapterNumber chỉ định (legacy, không tự tăng).
     * publicationDate tự tính = submissionDeadline + 14 ngày.
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
     * Tạo chapter tiếp theo với chapterNumber tự tăng.
     * Nếu schema sẵn sàng (có bảng Page + cột totalPages) và totalPages > 0 → tạo kèm page slots.
     * Nếu schema chưa sẵn sàng → fallback về createNextLegacy().
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
     * Tạo chapter + bulk create Page slots trong 1 transaction.
     * Dùng UPDLOCK/HOLDLOCK để tránh race condition khi tính chapterNumber.
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
     * Tạo chapter không có Page slots (fallback khi DB chưa có bảng Page/cột totalPages).
     * Dùng UPDLOCK/HOLDLOCK để tránh race condition khi tính chapterNumber.
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
     * Kiểm tra DB có cột totalPages trong Chapter và bảng Page chưa.
     * Kết quả được cache bằng volatile Boolean để chỉ query 1 lần, thread-safe.
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

    /** Kiểm tra exception có phải do thiếu schema Page/totalPages không (để fallback). */
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
     * Trả về danh sách cột SELECT phù hợp với schema hiện tại.
     * tablePrefix != null → thêm prefix vào từng cột (dùng khi JOIN).
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
     * Cập nhật title + submissionDeadline, tính lại publicationDate.
     * Sau khi update → refresh lại completionPct của chapter qua PageTaskRepository.
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

    /** Cập nhật chỉ title của chapter. */
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
     * Chuyển chapter sang EDITORIAL_REVIEW.
     * Điều kiện: chapter phải thuộc seri của mangakaId, completionPct = 100%, status IN_PROGRESS hoặc COMPLETE.
     * Sau khi chuyển → backfill toàn bộ ảnh page đã hoàn thành vào ChapterImage.
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
     * Xóa chapter. Chỉ cho phép khi:
     *  - Người gọi là Mangaka chủ seri
     *  - Chapter ở trạng thái PLANNING
     *  - Chapter chưa có PageTask nào
     */
    public void deleteChapter(long chapterId, long mangakaId) {
        long ownerId = findOwnerMangakaByChapter(chapterId);
        if (ownerId != mangakaId) {
            throw new IllegalArgumentException("Only series owner can delete chapter");
        }

        String statusSql = "SELECT status FROM Chapter WHERE id = ?";
        String taskCountSql = "SELECT COUNT(1) FROM PageTask WHERE chapterId = ?";
        String deleteSql = "DELETE FROM Chapter WHERE id = ?";

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(statusSql)) {
                ps.setLong(1, chapterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Chapter not found");
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
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setLong(1, chapterId);
                if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Chapter not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot delete chapter", ex);
        }
    }

    /**
     * Tìm chapter có deadline đúng bằng N ngày kể từ hôm nay, status PLANNING hoặc IN_PROGRESS.
     * Dùng bởi ChapterDeadlineScheduler để gửi cảnh báo sắp đến hạn.
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
     * Tìm chapter đã trễ deadline nhưng chưa hoàn thành (chưa vào EDITORIAL_REVIEW hoặc COMPLETE).
     * Dùng bởi scheduler SLA để đánh dấu atRisk.
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

    /** Lấy mangakaId chủ seri theo seriesId. */
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

    /** Lấy mangakaId chủ seri qua chapterId (JOIN Chapter → Series). */
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

    /** Lấy status hiện tại của chapter. */
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
     * Cập nhật status chapter.
     * Gọi bởi ManuscriptVersionService khi manuscript được approve (→ APPROVED).
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

    /** Lấy status của seri chứa chapter (JOIN Chapter → Series). */
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

    /** Lấy tantouEditorId của seri theo seriesId. */
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
     * Map ResultSet → ChapterSummary.
     * atRisk = true nếu DB đánh dấu HOẶC deadline đã qua mà chưa đủ 100% (tính real-time).
     * totalPages đọc có điều kiện (chỉ khi schema sẵn sàng).
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

    /** Kiểm tra ResultSet có cột tên label không (dùng để đọc totalPages an toàn). */
    private boolean hasColumn(ResultSet rs, String label) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (label.equalsIgnoreCase(md.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }

    /** Tính publicationDate = submissionDeadline + 14 ngày. */
    private Date publicationDateFor(Date submissionDeadline) {
        if (submissionDeadline == null) {
            throw new IllegalArgumentException("submissionDeadline is required");
        }
        return Date.valueOf(submissionDeadline.toLocalDate().plusDays(CHAPTER_PUBLICATION_OFFSET_DAYS));
    }

    /** Kiểm tra date không null và không phải quá khứ. */
    private void validateNotPast(Date date, String fieldName) {
        if (date == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (date.before(Date.valueOf(LocalDate.now()))) {
            throw new IllegalArgumentException(fieldName + " cannot be in the past");
        }
    }

    /** Validate deadline khi tạo chapter mới: không quá khứ + phải trước series deadline 7 ngày. */
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

    /** Validate deadline khi update chapter: không quá khứ + phải trước series deadline 7 ngày. */
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

    /** Ném exception nếu submissionDeadline không đủ sớm hơn seriesDeadline 7 ngày. */
    private void validateBeforeSeriesDeadline(Date submissionDeadline, Date seriesDeadline) {
        if (seriesDeadline == null) {
            throw new IllegalArgumentException("Series deadline must be set by assigned Tantou before creating or updating chapters");
        }
        Date latestChapterDeadline = Date.valueOf(seriesDeadline.toLocalDate().minusDays(CHAPTER_SERIES_DEADLINE_BUFFER_DAYS));
        if (submissionDeadline.after(latestChapterDeadline)) {
            throw new IllegalArgumentException("Chapter deadline must be at least 7 days before series deadline");
        }
    }

    /** Lấy mangakaId chủ seri qua chapterId (alias ngắn, dùng bởi ManuscriptVersionService). */
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

    /** Lấy tantouEditorId phụ trách chapter (alias ngắn, dùng bởi ManuscriptVersionService). */
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