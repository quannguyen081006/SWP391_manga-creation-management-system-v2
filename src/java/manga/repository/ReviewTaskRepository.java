package manga.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import manga.model.ReviewTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository for ReviewTask entity using JDBC pattern.
 */
@Repository
public class ReviewTaskRepository {
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Create new review task.
     * Throws domain-specific exceptions for different failure scenarios.
     */
    public long create(ReviewTask reviewTask) {
        String sql = "INSERT INTO ReviewTask (versionId, reviewerId, assignedAt, dueAt, reviewStatus) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, reviewTask.getVersionId());
            ps.setLong(2, reviewTask.getReviewerId());
            ps.setTimestamp(3, java.sql.Timestamp.valueOf(reviewTask.getAssignedAt()));
            ps.setTimestamp(4, java.sql.Timestamp.valueOf(reviewTask.getDueAt()));
            ps.setString(5, reviewTask.getReviewStatus());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            // Parse SQL error message to provide domain-specific errors
            String errorMessage = ex.getMessage();
            if (errorMessage != null) {
                String lowerMsg = errorMessage.toLowerCase();
                
                // Table doesn't exist
                if (lowerMsg.contains("invalid object name") || lowerMsg.contains("object 'reviewtask'") || lowerMsg.contains("table 'reviewtask'")) {
                    throw new RuntimeException("REVIEW_TASK_TABLE_MISSING: ReviewTask table does not exist in database. Run migration_add_review_task_table.sql to create it.", ex);
                }
                
                // Foreign key violation - versionId
                if (lowerMsg.contains("foreign key") && lowerMsg.contains("versionid")) {
                    throw new RuntimeException("REVIEW_TASK_FK_VIOLATION_VERSION: ManuscriptVersion with id " + reviewTask.getVersionId() + " does not exist.", ex);
                }
                
                // Foreign key violation - reviewerId
                if (lowerMsg.contains("foreign key") && lowerMsg.contains("reviewerid")) {
                    throw new RuntimeException("REVIEW_TASK_FK_VIOLATION_REVIEWER: User with id " + reviewTask.getReviewerId() + " does not exist or is not a valid reviewer.", ex);
                }
                
                // NOT NULL violation
                if (lowerMsg.contains("cannot insert null") || lowerMsg.contains("not null")) {
                    throw new RuntimeException("REVIEW_TASK_NULL_VIOLATION: Required field is null. versionId=" + reviewTask.getVersionId() + ", reviewerId=" + reviewTask.getReviewerId(), ex);
                }
            }
            
            // Generic SQL error with original message
            throw new RuntimeException("REVIEW_TASK_SQL_ERROR: " + errorMessage, ex);
        }
        throw new RuntimeException("REVIEW_TASK_GENERATION_FAILED: Failed to retrieve generated key after insert");
    }
    
    /**
     * Find review task by version ID.
     */
    public ReviewTask findByVersionId(Long versionId) {
        String sql = "SELECT id, versionId, reviewerId, assignedAt, dueAt, reviewStatus FROM ReviewTask WHERE versionId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, versionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find review task", ex);
        }
        return null;
    }
    
    /**
     * Find review tasks by reviewer ID.
     */
    public List<ReviewTask> findByReviewerId(Long reviewerId) {
        String sql = "SELECT id, versionId, reviewerId, assignedAt, dueAt, reviewStatus FROM ReviewTask WHERE reviewerId = ?";
        List<ReviewTask> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reviewerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find review tasks", ex);
        }
        return results;
    }
    
    /**
     * Update review task status.
     */
    public void updateStatus(Long id, String status) {
        String sql = "UPDATE ReviewTask SET reviewStatus = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update review task status", ex);
        }
    }
    
    /**
     * Delete review task by version ID.
     */
    public void deleteByVersionId(Long versionId) {
        String sql = "DELETE FROM ReviewTask WHERE versionId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, versionId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot delete review task", ex);
        }
    }
    
    /**
     * Find overdue review tasks.
     * Excludes COMPLETED tasks to prevent marking completed reviews as overdue.
     */
    public List<ReviewTask> findOverdueTasks() {
        String sql = "SELECT id, versionId, reviewerId, assignedAt, dueAt, reviewStatus FROM ReviewTask WHERE dueAt < GETDATE() AND reviewStatus != 'OVERDUE' AND reviewStatus != 'COMPLETED'";
        List<ReviewTask> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find overdue tasks", ex);
        }
        return results;
    }
    
    /**
     * Find tasks in warning threshold (36h before due).
     */
    public List<ReviewTask> findWarningThresholdTasks() {
        // Find tasks that are due within the next 12 hours (i.e. assigned ~36 hours ago)
        String sql = "SELECT id, versionId, reviewerId, assignedAt, dueAt, reviewStatus FROM ReviewTask WHERE dueAt BETWEEN GETDATE() AND DATEADD(HOUR, 12, GETDATE()) AND reviewStatus = 'ASSIGNED'";
        List<ReviewTask> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find warning threshold tasks", ex);
        }
        return results;
    }
    
    private ReviewTask map(ResultSet rs) throws SQLException {
        ReviewTask task = new ReviewTask();
        task.setId(rs.getLong("id"));
        task.setVersionId(rs.getLong("versionId"));
        task.setReviewerId(rs.getLong("reviewerId"));
        task.setAssignedAt(rs.getTimestamp("assignedAt").toLocalDateTime());
        task.setDueAt(rs.getTimestamp("dueAt").toLocalDateTime());
        task.setReviewStatus(rs.getString("reviewStatus"));
        return task;
    }
}
