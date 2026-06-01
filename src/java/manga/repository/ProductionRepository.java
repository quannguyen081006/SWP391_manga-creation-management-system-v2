package manga.repository;

import manga.model.AuthenticatedUser;
import manga.model.ChapterSummary;
import manga.model.ManuscriptSummary;
import manga.model.SeriesSummary;
import manga.model.TaskSummary;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ProductionRepository {

    private static final int SERIES_CHAPTER_DEADLINE_BUFFER_DAYS = 7;

    @Autowired
    private DataSource dataSource;

    public List<SeriesSummary> listSeries() {
        return listSeriesByMangaka(null);
    }

    public List<SeriesSummary> listSeries(AuthenticatedUser user) {
        if (user != null && user.hasRole("MANGAKA") && !user.hasRole("ADMIN")) {
            return listSeriesByMangaka(Long.valueOf(user.getId()));
        }
        return listSeries();
    }

    private List<SeriesSummary> listSeriesByMangaka(Long mangakaId) {
        String sql =
            "SELECT s.id, s.title, s.genre, s.status, s.mangakaId, s.tantouEditorId, s.publicationDate, "
            + "COUNT(c.id) AS chapterCount, "
            + "SUM(CASE WHEN c.status IN ('PLANNING','IN_PROGRESS','EDITORIAL_REVIEW') THEN 1 ELSE 0 END) AS inProgressChapters, "
            + "AVG(CASE WHEN c.id IS NULL THEN NULL ELSE c.completionPct END) AS progressPct "
            + "FROM Series s "
            + "LEFT JOIN Chapter c ON c.seriesId = s.id "
            + (mangakaId == null ? "" : "WHERE s.mangakaId = ? ")
            + "GROUP BY s.id, s.title, s.genre, s.status, s.mangakaId, s.tantouEditorId, s.publicationDate "
            + "ORDER BY MAX(s.createdAt) DESC";

        List<SeriesSummary> rows = new ArrayList<SeriesSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (mangakaId != null) {
                ps.setLong(1, mangakaId.longValue());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SeriesSummary s = new SeriesSummary();
                    s.setId(rs.getLong("id"));
                    s.setTitle(rs.getString("title"));
                    s.setGenre(rs.getString("genre"));
                    s.setStatus(rs.getString("status"));
                    s.setMangakaId(rs.getLong("mangakaId"));
                    s.setTantouEditorId(rs.getLong("tantouEditorId"));
                    s.setPublicationDate(rs.getDate("publicationDate"));
                    s.setChapterCount(rs.getInt("chapterCount"));
                    s.setInProgressChapters(rs.getInt("inProgressChapters"));
                    double pct = rs.getDouble("progressPct");
                    s.setProgressPct(rs.wasNull() ? 0.0 : pct);
                    rows.add(s);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load series", ex);
        }
        return rows;
    }

    public void updateSeriesDeadline(long seriesId, long tantouEditorId, Date publicationDate) {
        if (publicationDate == null) {
            throw new IllegalArgumentException("publicationDate is required");
        }
        if (publicationDate.before(Date.valueOf(LocalDate.now()))) {
            throw new IllegalArgumentException("Series deadline cannot be in the past");
        }

        String readSql = "SELECT mangakaId, tantouEditorId, title FROM Series WHERE id = ?";
        String chapterDeadlineSql = "SELECT COUNT(1) FROM Chapter WHERE seriesId = ? AND submissionDeadline > DATEADD(DAY, -" + SERIES_CHAPTER_DEADLINE_BUFFER_DAYS + ", ?)";
        String updateSql = "UPDATE Series SET publicationDate = ? WHERE id = ?";
        String notifySql =
            "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, GETDATE())";

        try (Connection conn = dataSource.getConnection()) {
            long mangakaId;
            long assignedTantouId;
            String title;
            try (PreparedStatement read = conn.prepareStatement(readSql)) {
                read.setLong(1, seriesId);
                try (ResultSet rs = read.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Series not found");
                    }
                    mangakaId = rs.getLong("mangakaId");
                    assignedTantouId = rs.getLong("tantouEditorId");
                    title = rs.getString("title");
                }
            }

            if (assignedTantouId != tantouEditorId) {
                throw new IllegalArgumentException("Only assigned Tantou can update series deadline");
            }

            try (PreparedStatement chapterDeadline = conn.prepareStatement(chapterDeadlineSql)) {
                chapterDeadline.setLong(1, seriesId);
                chapterDeadline.setDate(2, publicationDate);
                try (ResultSet rs = chapterDeadline.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        throw new IllegalArgumentException("Series deadline must be at least 7 days after all chapter deadlines");
                    }
                }
            }

            try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setDate(1, publicationDate);
                update.setLong(2, seriesId);
                update.executeUpdate();
            }

            try (PreparedStatement notify = conn.prepareStatement(notifySql)) {
                notify.setLong(1, mangakaId);
                notify.setString(2, "SERIES_DEADLINE_UPDATED");
                notify.setString(3, "Series deadline updated");
                notify.setString(4, "Deadline for series \"" + title + "\" was updated to " + publicationDate + ".");
                notify.setString(5, "/main/series/" + seriesId);
                notify.setLong(6, seriesId);
                notify.setString(7, "SERIES");
                notify.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update series deadline", ex);
        }
    }

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
            throw new RuntimeException("Cannot load series owner", ex);
        }
    }

    public void enrollAssistant(long seriesId, long assistantId) {
        String checkAssistantSql =
            "SELECT COUNT(1) FROM [User] u "
            + "JOIN UserRole ur ON ur.userId = u.id "
            + "JOIN [Role] r ON r.id = ur.roleId "
            + "WHERE u.id = ? AND u.status = 'ACTIVE' AND r.name = 'ASSISTANT'";
        String countSql = "SELECT COUNT(1) FROM MangakaAssistant WHERE mangakaId = ?";
        String insertSql = "INSERT INTO MangakaAssistant (mangakaId, assistantId, enrolledAt) VALUES (?, ?, GETDATE())";

        try (Connection conn = dataSource.getConnection()) {
            long mangakaId = findSeriesOwnerMangaka(seriesId);
            try (PreparedStatement check = conn.prepareStatement(checkAssistantSql)) {
                check.setLong(1, assistantId);
                try (ResultSet rs = check.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        throw new IllegalArgumentException("assistantId must be an ACTIVE ASSISTANT");
                    }
                }
            }

            try (PreparedStatement count = conn.prepareStatement(countSql)) {
                count.setLong(1, mangakaId);
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) >= 4) {
                        throw new IllegalArgumentException("Mangaka already has 4 assistants");
                    }
                }
            }

            try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                insert.setLong(1, mangakaId);
                insert.setLong(2, assistantId);
                insert.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot enroll assistant", ex);
        }
    }

    public void removeAssistant(long seriesId, long assistantId) {
        String countSql = "SELECT COUNT(1) FROM MangakaAssistant WHERE mangakaId = ?";
        String sql = "DELETE FROM MangakaAssistant WHERE mangakaId = ? AND assistantId = ?";
        try (Connection conn = dataSource.getConnection()) {
            long mangakaId = findSeriesOwnerMangaka(seriesId);
            try (PreparedStatement count = conn.prepareStatement(countSql)) {
                count.setLong(1, mangakaId);
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) <= 4) {
                        throw new IllegalArgumentException("Mangaka must have 4 assistants");
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, mangakaId);
                ps.setLong(2, assistantId);
                if (ps.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Mangaka assistant not found");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot remove assistant", ex);
        }
    }

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

    public List<Map<String, Object>> listMangakaAssistantsBySeries(long seriesId) {
        String sql =
            "SELECT u.id, u.username, u.fullName, u.email "
            + "FROM Series s "
            + "JOIN MangakaAssistant ma ON ma.mangakaId = s.mangakaId "
            + "JOIN [User] u ON u.id = ma.assistantId "
            + "WHERE s.id = ? AND u.status = 'ACTIVE' "
            + "ORDER BY u.fullName";
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("id", rs.getLong("id"));
                    row.put("username", rs.getString("username"));
                    row.put("fullName", rs.getString("fullName"));
                    row.put("email", rs.getString("email"));
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load mangaka assistants", ex);
        }
        return rows;
    }
    public List<ChapterSummary> listChapters() {
        String sql = "SELECT id, seriesId, chapterNumber, title, status, submissionDeadline, publicationDate, completionPct, atRisk FROM Chapter ORDER BY createdAt DESC";
        List<ChapterSummary> rows = new ArrayList<ChapterSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
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
                rows.add(c);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load chapters", ex);
        }
        return rows;
    }

    public List<TaskSummary> listTasks() {
        String sql =
            "SELECT t.id, t.chapterId, t.assistantId, t.pageRangeStart, t.pageRangeEnd, t.taskType, t.dueDate, t.status, t.rejectionCount, "
            + "CAST(CASE WHEN t.status IN ('PENDING','IN_PROGRESS','REJECTED') "
            + "AND DATEDIFF(DAY, t.assignedAt, GETDATE()) >= 3 "
            + "AND DATEDIFF(DAY, t.updatedAt, GETDATE()) >= 3 THEN 1 ELSE 0 END AS BIT) AS isDelayed, "
            + "c.title AS chapterTitle, c.chapterNumber, s.title AS seriesTitle, u.fullName AS assistantName "
            + "FROM PageTask t "
            + "JOIN Chapter c ON c.id = t.chapterId "
            + "JOIN Series s ON s.id = c.seriesId "
            + "LEFT JOIN [User] u ON u.id = t.assistantId "
            + "ORDER BY t.updatedAt DESC";

        List<TaskSummary> rows = new ArrayList<TaskSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
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
                t.setChapterTitle(rs.getString("chapterTitle"));
                t.setChapterNumber(rs.getInt("chapterNumber"));
                t.setSeriesTitle(rs.getString("seriesTitle"));
                t.setAssistantName(rs.getString("assistantName"));
                t.setDelayed(rs.getBoolean("isDelayed"));
                rows.add(t);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load tasks", ex);
        }
        return rows;
    }

    public List<ManuscriptSummary> listManuscripts() {
        return listManuscripts(null, null);
    }

    public List<ManuscriptSummary> listManuscripts(AuthenticatedUser user, Long seriesId) {
        String sql =
            "SELECT m.id, m.chapterId, m.version, m.status, m.submittedAt, m.reviewDeadline, m.fileUrl, m.revisionDeadline, "
            + "c.title AS chapterTitle, c.chapterNumber, s.title AS seriesTitle, mangaka.fullName AS mangakaName "
            + "FROM Manuscript m "
            + "JOIN Chapter c ON c.id = m.chapterId "
            + "JOIN Series s ON s.id = c.seriesId "
            + "LEFT JOIN [User] mangaka ON mangaka.id = s.mangakaId ";

        List<Object> params = new ArrayList<Object>();
        List<String> where = new ArrayList<String>();
        if (user != null && user.hasRole("MANGAKA") && !user.hasRole("ADMIN")) {
            where.add("s.mangakaId = ?");
            params.add(Long.valueOf(user.getId()));
        } else if (user != null && user.hasRole("TANTOU_EDITOR") && !user.hasRole("ADMIN")) {
            where.add("s.tantouEditorId = ?");
            params.add(Long.valueOf(user.getId()));
            where.add("m.status IN ('SUBMITTED','UNDER_REVIEW')");
        }
        if (seriesId != null && seriesId.longValue() > 0) {
            where.add("s.id = ?");
            params.add(seriesId);
        }
        if (!where.isEmpty()) {
            sql += "WHERE " + joinWhere(where) + " ";
        }
        sql += "ORDER BY m.submittedAt DESC";

        List<ManuscriptSummary> rows = new ArrayList<ManuscriptSummary>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setLong(i + 1, ((Long) params.get(i)).longValue());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ManuscriptSummary m = new ManuscriptSummary();
                    m.setId(rs.getLong("id"));
                    m.setChapterId(rs.getLong("chapterId"));
                    m.setVersion(rs.getInt("version"));
                    m.setStatus(rs.getString("status"));
                    m.setSubmittedAt(rs.getTimestamp("submittedAt"));
                    m.setReviewDeadline(rs.getTimestamp("reviewDeadline"));
                    m.setFileUrl(rs.getString("fileUrl"));
                    m.setRevisionDeadline(rs.getTimestamp("revisionDeadline"));
                    m.setChapterTitle(rs.getString("chapterTitle"));
                    m.setChapterNumber(rs.getInt("chapterNumber"));
                    m.setSeriesTitle(rs.getString("seriesTitle"));
                    m.setMangakaName(rs.getString("mangakaName"));
                    rows.add(m);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load manuscripts", ex);
        }
        return rows;
    }

    private String joinWhere(List<String> where) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < where.size(); i++) {
            if (i > 0) {
                sb.append(" AND ");
            }
            sb.append(where.get(i));
        }
        return sb.toString();
    }
}


