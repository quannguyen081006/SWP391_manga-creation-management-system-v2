package manga.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import manga.enums.ManuscriptStatus;
import manga.model.ManuscriptVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository for ManuscriptVersion entity using JDBC pattern.
 *
 * Provides data access methods for manuscript version management. Supports the
 * new visual workspace workflow with versioning.
 */
@Repository
public class ManuscriptVersionRepository {

    @Autowired
    private DataSource dataSource;

    /**
     * Find all manuscript versions for a chapter, ordered by version descending
     * (latest first).
     */
    public List<ManuscriptVersion> findByChapterIdOrderByVersionDesc(Long chapterId) {
        String sql = "SELECT id, chapterId, version, previousVersionId, status, createdAt, submittedAt, approvedAt, rejectedAt, publishedAt, createdBy, submittedBy, approvedBy, rejectedBy, feedback, revisionNotes, totalPageCount "
                + "FROM ManuscriptVersion WHERE chapterId = ? ORDER BY version DESC";
        List<ManuscriptVersion> results = new ArrayList<>();
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find manuscript versions for chapter", ex);
        }
        return results;
    }

    /**
     * Find the manuscript version currently under review for a chapter. BR-2:
     * Only one UNDER_REVIEW per chapter
     */
    public ManuscriptVersion findByChapterIdAndStatus(Long chapterId, ManuscriptStatus status) {
        String sql = "SELECT id, chapterId, version, previousVersionId, status, createdAt, submittedAt, approvedAt, rejectedAt, publishedAt, createdBy, submittedBy, approvedBy, rejectedBy, feedback, revisionNotes, totalPageCount "
                + "FROM ManuscriptVersion WHERE chapterId = ? AND status = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            ps.setString(2, status.name());
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find manuscript version", ex);
        }
        return null;
    }

    public ManuscriptVersion findLatestByChapterId(long chapterId) {

        String sql = "SELECT TOP 1 * "
                + "FROM ManuscriptVersion "
                + "WHERE chapterId = ? "
                + "ORDER BY version DESC, id DESC";

        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, chapterId);

            try ( ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return map(rs);
                }
            }

        } catch (SQLException ex) {
            throw new RuntimeException(
                    "Cannot find latest manuscript version",
                    ex);
        }

        return null;
    }

    /**
     * Find active workspace for a chapter. Active workspace is the latest
     * manuscript version with status in: DRAFT, IN_PROGRESS,
     * SUBMITTED_FOR_REVIEW, UNDER_REVIEW Returns null if no active workspace
     * exists.
     */
    public ManuscriptVersion findActiveWorkspace(Long chapterId) {
        String sql = "SELECT TOP 1 id, chapterId, version, previousVersionId, status, createdAt, submittedAt, approvedAt, rejectedAt, publishedAt, createdBy, submittedBy, approvedBy, rejectedBy, feedback, revisionNotes, totalPageCount "
                + "FROM ManuscriptVersion "
                + "WHERE chapterId = ? AND status IN ('DRAFT', 'IN_PROGRESS', 'SUBMITTED_FOR_REVIEW', 'UNDER_REVIEW') "
                + "ORDER BY version DESC";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find active workspace", ex);
        }
        return null;
    }

    /**
     * Count active workspaces for a chapter. Used for validation and duplicate
     * prevention.
     */
    public long countActiveWorkspaces(Long chapterId) {
        String sql = "SELECT COUNT(*) FROM ManuscriptVersion "
                + "WHERE chapterId = ? AND status IN ('DRAFT', 'IN_PROGRESS', 'SUBMITTED_FOR_REVIEW', 'UNDER_REVIEW')";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot count active workspaces", ex);
        }
        return 0;
    }

    /**
     * Find active workspace for a chapter with row-level lock (SELECT FOR
     * UPDATE). Used in transactional context to prevent race conditions during
     * workspace creation. Locks the chapter's manuscript versions row to
     * prevent concurrent insertions.
     *
     * This method should be called within a transaction to ensure the lock is
     * held.
     */
    public ManuscriptVersion findActiveWorkspaceForUpdate(Long chapterId) {
        String sql = "SELECT TOP 1 id, chapterId, version, previousVersionId, status, createdAt, submittedAt, approvedAt, rejectedAt, publishedAt, createdBy, submittedBy, approvedBy, rejectedBy, feedback, revisionNotes, totalPageCount "
                + "FROM ManuscriptVersion WITH (UPDLOCK, ROWLOCK) "
                + "WHERE chapterId = ? AND status IN ('DRAFT', 'IN_PROGRESS', 'SUBMITTED_FOR_REVIEW', 'UNDER_REVIEW') "
                + "ORDER BY version DESC";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find active workspace with lock", ex);
        }
        return null;
    }

    /**
     * Find a specific manuscript version by chapter and version number.
     */
    public ManuscriptVersion findByChapterIdAndVersion(Long chapterId, Integer version) {
        String sql = "SELECT id, chapterId, version, previousVersionId, status, createdAt, submittedAt, approvedAt, rejectedAt, publishedAt, createdBy, submittedBy, approvedBy, rejectedBy, feedback, revisionNotes, totalPageCount "
                + "FROM ManuscriptVersion WHERE chapterId = ? AND version = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            ps.setInt(2, version);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find manuscript version", ex);
        }
        return null;
    }

    /**
     * Find manuscript version by ID.
     */
    public ManuscriptVersion findById(Long id) {
        String sql = "SELECT id, chapterId, version, previousVersionId, status, createdAt, submittedAt, approvedAt, rejectedAt, publishedAt, createdBy, submittedBy, approvedBy, rejectedBy, feedback, revisionNotes, totalPageCount "
                + "FROM ManuscriptVersion WHERE id = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find manuscript version", ex);
        }
        return null;
    }

    /**
     * Create new manuscript version.
     */
    public long create(ManuscriptVersion version) {
        String sql = "INSERT INTO ManuscriptVersion (chapterId, version, previousVersionId, status, createdAt, createdBy, totalPageCount) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, version.getChapterId());
            ps.setInt(2, version.getVersion());
            if (version.getPreviousVersionId() != null) {
                ps.setLong(3, version.getPreviousVersionId());
            } else {
                ps.setNull(3, java.sql.Types.BIGINT);
            }
            ps.setString(4, version.getStatus().name());
            ps.setTimestamp(5, new java.sql.Timestamp(System.currentTimeMillis()));
            if (version.getCreatedBy() != null) {
                ps.setLong(6, version.getCreatedBy());
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }
            ps.setInt(7, version.getTotalPageCount() != null ? version.getTotalPageCount() : 0);
            ps.executeUpdate();
            try ( ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create manuscript version", ex);
        }
        throw new RuntimeException("Failed to create manuscript version");
    }

    /**
     * Update manuscript version status.
     */
    public void updateStatus(Long id, ManuscriptStatus status) {
        String sql = "UPDATE ManuscriptVersion SET status = ? WHERE id = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update manuscript version status", ex);
        }
    }

    public void updateTotalPageCount(
            Long id,
            Integer totalPageCount
    ) {

        String sql
                = "UPDATE ManuscriptVersion "
                + "SET totalPageCount = ? "
                + "WHERE id = ?";

        try (
                 Connection conn = dataSource.getConnection();  PreparedStatement ps
                = conn.prepareStatement(sql)) {

            ps.setInt(1, totalPageCount);
            ps.setLong(2, id);

            ps.executeUpdate();

        } catch (SQLException ex) {

            throw new RuntimeException(
                    "Cannot update total page count",
                    ex
            );
        }
    }

    /**
     * Update manuscript version when submitted for review.
     */
    public void updateSubmit(Long id, Long submittedBy) {
        String sql = "UPDATE ManuscriptVersion SET status = 'UNDER_REVIEW', submittedAt = GETDATE(), submittedBy = ? WHERE id = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, submittedBy);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update manuscript version for submission", ex);
        }
    }

    /**
     * Update manuscript version with approval/rejection details.
     */
    public void updateApproval(Long id, ManuscriptStatus status, String feedback, Long userId) {
        String sql = "UPDATE ManuscriptVersion SET status = ?, feedback = ?, approvedAt = CASE WHEN ? = 'APPROVED' THEN GETDATE() ELSE approvedAt END, "
                + "rejectedAt = CASE WHEN ? = 'REJECTED' THEN GETDATE() ELSE rejectedAt END, "
                + "approvedBy = CASE WHEN ? = 'APPROVED' THEN ? ELSE approvedBy END, "
                + "rejectedBy = CASE WHEN ? = 'REJECTED' THEN ? ELSE rejectedBy END WHERE id = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, feedback);
            ps.setString(3, status.name());
            ps.setString(4, status.name());
            ps.setString(5, status.name());
            if (userId != null) {
                ps.setLong(6, userId);
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }
            ps.setString(7, status.name());
            if (userId != null) {
                ps.setLong(8, userId);
            } else {
                ps.setNull(8, java.sql.Types.BIGINT);
            }
            ps.setLong(9, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update manuscript version", ex);
        }
    }

    /**
     * Publish manuscript version.
     */
    public void updatePublish(Long id) {
        String sql = "UPDATE ManuscriptVersion SET status = 'PUBLISHED', publishedAt = GETDATE() WHERE id = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("Manuscript version not found for publish");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot publish manuscript version", ex);
        }
    }

    /**
     * Count manuscript versions by status for a chapter.
     */
    public long countByChapterIdAndStatus(Long chapterId, ManuscriptStatus status) {
        String sql = "SELECT COUNT(*) FROM ManuscriptVersion WHERE chapterId = ? AND status = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            ps.setString(2, status.name());
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot count manuscript versions", ex);
        }
        return 0;
    }

    /**
     * Find the latest version for a chapter (no next version exists).
     */
    public ManuscriptVersion findLatestByChapterId(Long chapterId) {
        String sql = "SELECT TOP 1 id, chapterId, version, previousVersionId, status, createdAt, submittedAt, approvedAt, rejectedAt, publishedAt, createdBy, submittedBy, approvedBy, rejectedBy, feedback, revisionNotes, totalPageCount "
                + "FROM ManuscriptVersion WHERE chapterId = ? ORDER BY version DESC";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find latest manuscript version", ex);
        }
        return null;
    }

    /**
     * Find all manuscript versions with UNDER_REVIEW status, optionally
     * filtered by series assignments. Used for Tantou review inbox.
     */
    public List<ManuscriptVersion> findUnderReviewForTantou(Long tantouUserId, boolean isAdmin) {
        String sql;
        if (isAdmin) {
            sql = "SELECT id, chapterId, version, previousVersionId, status, createdAt, submittedAt, approvedAt, rejectedAt, publishedAt, createdBy, submittedBy, approvedBy, rejectedBy, feedback, revisionNotes, totalPageCount "
                    + "FROM ManuscriptVersion WHERE status = 'UNDER_REVIEW' ORDER BY submittedAt DESC";
        } else {
            sql = "SELECT mv.id, mv.chapterId, mv.version, mv.previousVersionId, mv.status, mv.createdAt, mv.submittedAt, mv.approvedAt, mv.rejectedAt, mv.publishedAt, mv.createdBy, mv.submittedBy, mv.approvedBy, mv.rejectedBy, mv.feedback, mv.revisionNotes, mv.totalPageCount "
                    + "FROM ManuscriptVersion mv "
                    + "JOIN Chapter c ON c.id = mv.chapterId "
                    + "JOIN Series s ON s.id = c.seriesId "
                    + "WHERE mv.status = 'UNDER_REVIEW' AND s.tantouEditorId = ? "
                    + "ORDER BY mv.submittedAt DESC";
        }
        List<ManuscriptVersion> results = new ArrayList<>();
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!isAdmin) {
                ps.setLong(1, tantouUserId);
            }
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find under review manuscripts", ex);
        }
        return results;
    }

    private ManuscriptVersion map(ResultSet rs) throws SQLException {
        ManuscriptVersion version = new ManuscriptVersion();
        version.setId(rs.getLong("id"));
        version.setChapterId(rs.getLong("chapterId"));
        version.setVersion(rs.getInt("version"));
        version.setPreviousVersionId(rs.getObject("previousVersionId") != null ? rs.getLong("previousVersionId") : null);
        version.setStatus(ManuscriptStatus.valueOf(rs.getString("status")));
        version.setCreatedAt(rs.getTimestamp("createdAt") != null ? rs.getTimestamp("createdAt").toLocalDateTime() : null);
        version.setSubmittedAt(rs.getTimestamp("submittedAt") != null ? rs.getTimestamp("submittedAt").toLocalDateTime() : null);
        version.setApprovedAt(rs.getTimestamp("approvedAt") != null ? rs.getTimestamp("approvedAt").toLocalDateTime() : null);
        version.setRejectedAt(rs.getTimestamp("rejectedAt") != null ? rs.getTimestamp("rejectedAt").toLocalDateTime() : null);
        version.setPublishedAt(rs.getTimestamp("publishedAt") != null ? rs.getTimestamp("publishedAt").toLocalDateTime() : null);
        version.setCreatedBy(rs.getObject("createdBy") != null ? rs.getLong("createdBy") : null);
        version.setSubmittedBy(rs.getObject("submittedBy") != null ? rs.getLong("submittedBy") : null);
        version.setApprovedBy(rs.getObject("approvedBy") != null ? rs.getLong("approvedBy") : null);
        version.setRejectedBy(rs.getObject("rejectedBy") != null ? rs.getLong("rejectedBy") : null);
        version.setFeedback(rs.getString("feedback"));
        version.setRevisionNotes(rs.getString("revisionNotes"));
        version.setTotalPageCount(rs.getObject("totalPageCount") != null ? rs.getInt("totalPageCount") : 0);
        return version;
    }
}
