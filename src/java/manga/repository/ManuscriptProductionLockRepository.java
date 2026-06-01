package manga.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import manga.model.ManuscriptProductionLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository for ManuscriptProductionLock entity using JDBC pattern.
 * 
 * Provides data access methods for production lock management.
 * Enforces BR-9: Production assets locked during review.
 */
@Repository
public class ManuscriptProductionLockRepository {
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Find the active lock for a chapter.
     */
    public ManuscriptProductionLock findByChapterId(Long chapterId) {
        String sql = "SELECT id, chapterId, manuscriptVersionId, lockedAt, lockedBy, unlockedAt " +
                    "FROM ManuscriptProductionLock WHERE chapterId = ? AND unlockedAt IS NULL";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find production lock", ex);
        }
        return null;
    }
    
    /**
     * Find lock by manuscript version ID.
     */
    public ManuscriptProductionLock findByManuscriptVersionId(Long manuscriptVersionId) {
        String sql = "SELECT id, chapterId, manuscriptVersionId, lockedAt, lockedBy, unlockedAt " +
                    "FROM ManuscriptProductionLock WHERE manuscriptVersionId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, manuscriptVersionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find production lock", ex);
        }
        return null;
    }
    
    /**
     * Find all expired locks (locked more than 48 hours ago and not unlocked).
     * Used by cleanup job to release stale locks.
     */
    public List<ManuscriptProductionLock> findExpiredLocks() {
        String sql = "SELECT id, chapterId, manuscriptVersionId, lockedAt, lockedBy, unlockedAt " +
                    "FROM ManuscriptProductionLock WHERE unlockedAt IS NULL AND lockedAt < DATEADD(HOUR, -48, GETDATE())";
        List<ManuscriptProductionLock> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find expired locks", ex);
        }
        return results;
    }
    
    /**
     * Create new production lock.
     */
    public long create(ManuscriptProductionLock lock) {
        String sql = "INSERT INTO ManuscriptProductionLock (chapterId, manuscriptVersionId, lockedAt, lockedBy) VALUES (?, ?, GETDATE(), ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, lock.getChapterId());
            ps.setLong(2, lock.getManuscriptVersionId());
            ps.setLong(3, lock.getLockedBy());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create production lock", ex);
        }
        throw new RuntimeException("Failed to create production lock");
    }
    
    /**
     * Unlock production lock.
     * Deletes the lock row to allow new locks to be created for the same chapter.
     */
    public void unlock(Long chapterId) {
        String sql = "DELETE FROM ManuscriptProductionLock WHERE chapterId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot unlock production", ex);
        }
    }
    
    /**
     * Delete lock by chapter ID.
     */
    public void deleteByChapterId(Long chapterId) {
        String sql = "DELETE FROM ManuscriptProductionLock WHERE chapterId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot delete production lock", ex);
        }
    }
    
    private ManuscriptProductionLock map(ResultSet rs) throws SQLException {
        ManuscriptProductionLock lock = new ManuscriptProductionLock();
        lock.setId(rs.getLong("id"));
        lock.setChapterId(rs.getLong("chapterId"));
        lock.setManuscriptVersionId(rs.getLong("manuscriptVersionId"));
        lock.setLockedAt(rs.getTimestamp("lockedAt") != null ? rs.getTimestamp("lockedAt").toLocalDateTime() : null);
        lock.setLockedBy(rs.getLong("lockedBy"));
        lock.setUnlockedAt(rs.getTimestamp("unlockedAt") != null ? rs.getTimestamp("unlockedAt").toLocalDateTime() : null);
        return lock;
    }
}
