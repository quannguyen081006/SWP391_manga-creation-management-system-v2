package manga.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import manga.model.ManuscriptPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository for ManuscriptPage entity using JDBC pattern.
 * 
 * Provides data access methods for manuscript page management.
 * Pages are immutable snapshots of production assets.
 */
@Repository
public class ManuscriptPageRepository {
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Find all pages for a manuscript version, ordered by display order.
     */
    public List<ManuscriptPage> findByManuscriptVersionIdOrderByDisplayOrder(Long manuscriptVersionId) {
        String sql = "SELECT id, manuscriptVersionId, displayOrder, snapshotFileUrl, originalFileUrl, sourceChapterImageId, sourcePageTaskId, pageNumber, snapshotCreatedAt, snapshotChecksum " +
                    "FROM ManuscriptPage WHERE manuscriptVersionId = ? ORDER BY displayOrder ASC";
        List<ManuscriptPage> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, manuscriptVersionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find manuscript pages", ex);
        }
        return results;
    }
    
    /**
     * Find a specific page by manuscript version and display order.
     */
    public ManuscriptPage findByManuscriptVersionIdAndDisplayOrder(Long manuscriptVersionId, Integer displayOrder) {
        String sql = "SELECT id, manuscriptVersionId, displayOrder, snapshotFileUrl, originalFileUrl, sourceChapterImageId, sourcePageTaskId, pageNumber, snapshotCreatedAt, snapshotChecksum " +
                    "FROM ManuscriptPage WHERE manuscriptVersionId = ? AND displayOrder = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, manuscriptVersionId);
            ps.setInt(2, displayOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find manuscript page", ex);
        }
        return null;
    }
    
    /**
     * Verify snapshot integrity by checksum.
     */
    public boolean verifyChecksum(Long pageId, String expectedChecksum) {
        String sql = "SELECT snapshotChecksum FROM ManuscriptPage WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String actualChecksum = rs.getString("snapshotChecksum");
                    return actualChecksum != null && actualChecksum.equals(expectedChecksum);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot verify checksum", ex);
        }
        return false;
    }
    
    /**
     * Find pages by source chapter image ID.
     * Used for tracking which production assets are used in manuscripts.
     */
    public List<ManuscriptPage> findBySourceChapterImageId(Long sourceChapterImageId) {
        String sql = "SELECT id, manuscriptVersionId, displayOrder, snapshotFileUrl, originalFileUrl, sourceChapterImageId, sourcePageTaskId, pageNumber, snapshotCreatedAt, snapshotChecksum " +
                    "FROM ManuscriptPage WHERE sourceChapterImageId = ?";
        List<ManuscriptPage> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sourceChapterImageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find manuscript pages by source image", ex);
        }
        return results;
    }
    
    /**
     * Count pages for a manuscript version.
     */
    public long countByManuscriptVersionId(Long manuscriptVersionId) {
        String sql = "SELECT COUNT(*) FROM ManuscriptPage WHERE manuscriptVersionId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, manuscriptVersionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot count manuscript pages", ex);
        }
        return 0;
    }
    
    /**
     * Create new manuscript page.
     */
    public long create(ManuscriptPage page) {
        String sql = "INSERT INTO ManuscriptPage (manuscriptVersionId, displayOrder, snapshotFileUrl, originalFileUrl, sourceChapterImageId, sourcePageTaskId, pageNumber, snapshotCreatedAt, snapshotChecksum) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, GETDATE(), ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, page.getManuscriptVersionId());
            ps.setInt(2, page.getDisplayOrder());
            ps.setString(3, page.getSnapshotFileUrl());
            ps.setString(4, page.getOriginalFileUrl());
            if (page.getSourceChapterImageId() != null) {
                ps.setLong(5, page.getSourceChapterImageId());
            } else {
                ps.setNull(5, java.sql.Types.BIGINT);
            }
            if (page.getSourcePageTaskId() != null) {
                ps.setLong(6, page.getSourcePageTaskId());
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }
            ps.setInt(7, page.getPageNumber());
            ps.setString(8, page.getSnapshotChecksum());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create manuscript page", ex);
        }
        throw new RuntimeException("Failed to create manuscript page");
    }
    
    /**
     * Delete all pages for a manuscript version.
     * Used when deleting a manuscript version.
     */
    public void deleteByManuscriptVersionId(Long manuscriptVersionId) {
        String sql = "DELETE FROM ManuscriptPage WHERE manuscriptVersionId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, manuscriptVersionId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot delete manuscript pages", ex);
        }
    }
    
    private ManuscriptPage map(ResultSet rs) throws SQLException {
        ManuscriptPage page = new ManuscriptPage();
        page.setId(rs.getLong("id"));
        page.setManuscriptVersionId(rs.getLong("manuscriptVersionId"));
        page.setDisplayOrder(rs.getInt("displayOrder"));
        page.setSnapshotFileUrl(rs.getString("snapshotFileUrl"));
        page.setOriginalFileUrl(rs.getString("originalFileUrl"));
        page.setSourceChapterImageId(rs.getObject("sourceChapterImageId") != null ? rs.getLong("sourceChapterImageId") : null);
        page.setSourcePageTaskId(rs.getObject("sourcePageTaskId") != null ? rs.getLong("sourcePageTaskId") : null);
        page.setPageNumber(rs.getInt("pageNumber"));
        page.setSnapshotCreatedAt(rs.getTimestamp("snapshotCreatedAt") != null ? rs.getTimestamp("snapshotCreatedAt").toLocalDateTime() : null);
        page.setSnapshotChecksum(rs.getString("snapshotChecksum"));
        return page;
    }
}
