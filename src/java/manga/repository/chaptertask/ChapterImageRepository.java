package manga.repository.chaptertask;

import manga.model.chaptertask.ChapterImageItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ChapterImageRepository {

    @Autowired
    private DataSource dataSource;

    public long upload(long chapterId, Long pageTaskId, long uploadedBy, String imageType,
            Integer pageNumber, String fileUrl, String originalFileName, long fileSizeBytes) {
        String insertSql =
            "INSERT INTO ChapterImage (chapterId, pageTaskId, uploadedBy, imageType, pageNumber, fileUrl, originalFileName, fileSizeBytes, uploadedAt, isActive) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), 1)";

        try (Connection conn = dataSource.getConnection()) {
            String normalizedType = normalizeImageType(imageType);
            validateUpload(conn, chapterId, pageTaskId, uploadedBy, normalizedType, pageNumber, fileUrl);

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
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Cannot upload chapter image");
                    }
                    newId = rs.getLong(1);
                }
            }

            if (pageTaskId != null) {
                // Task image upload counts as real assistant progress for delayed-task detection.
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

    public List<ChapterImageItem> listByChapter(long chapterId) {
        String sql =
            "SELECT id, chapterId, pageTaskId, uploadedBy, imageType, pageNumber, fileUrl, originalFileName, fileSizeBytes, uploadedAt, isActive, note "
            + "FROM ChapterImage WHERE chapterId = ? AND isActive = 1 "
            + "ORDER BY CASE WHEN pageNumber IS NULL THEN 1 ELSE 0 END, pageNumber ASC, uploadedAt ASC";
        return list(sql, chapterId, "Cannot list chapter images");
    }

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

    public long findChapterOwnerMangaka(long chapterId) {
        String sql = "SELECT s.mangakaId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        return queryLong(sql, chapterId, "Chapter not found");
    }

    public long findChapterTantouEditor(long chapterId) {
        String sql = "SELECT s.tantouEditorId FROM Chapter c JOIN Series s ON s.id = c.seriesId WHERE c.id = ?";
        return queryLong(sql, chapterId, "Chapter not found");
    }

    public long findTaskChapterId(long pageTaskId) {
        String sql = "SELECT chapterId FROM PageTask WHERE id = ?";
        return queryLong(sql, pageTaskId, "Task not found");
    }

    public long findTaskAssistantId(long pageTaskId) {
        String sql = "SELECT assistantId FROM PageTask WHERE id = ?";
        return queryLong(sql, pageTaskId, "Task not found");
    }

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

    private static class TaskAccess {
        private long chapterId;
        private long assistantId;
    }
}

