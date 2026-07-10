package manga.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import manga.enums.ReviewDecisionType;
import manga.model.ReviewDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for manuscript review decision history.
 * It only inserts and reads decisions so review approvals/rejections remain an
 * immutable timeline for defense/audit purposes.
 */
@Repository
public class ReviewDecisionRepository {
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * Appends a new review decision record; existing decisions are not updated.
     */
    public long create(ReviewDecision decision) {
        String sql = "INSERT INTO ReviewDecision (manuscriptVersionId, reviewerId, decisionType, comment, decisionAt) " +
                    "VALUES (?, ?, ?, ?, GETDATE())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, decision.getManuscriptVersionId());
            ps.setLong(2, decision.getReviewerId());
            ps.setString(3, decision.getDecisionType().name());
            ps.setString(4, decision.getComment());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create review decision", ex);
        }
        throw new RuntimeException("Failed to create review decision");
    }
    
    /**
     * Reads the decision timeline for one manuscript version, newest first.
     */
    public List<ReviewDecision> findByManuscriptVersionId(Long manuscriptVersionId) {
        String sql = "SELECT id, manuscriptVersionId, reviewerId, decisionType, comment, decisionAt " +
                    "FROM ReviewDecision WHERE manuscriptVersionId = ? ORDER BY decisionAt DESC";
        List<ReviewDecision> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, manuscriptVersionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find review decisions", ex);
        }
        return results;
    }
    
    /**
     * Reads the latest decision when the workflow needs the current review outcome.
     */
    public ReviewDecision findLatestByManuscriptVersionId(Long manuscriptVersionId) {
        String sql = "SELECT TOP 1 id, manuscriptVersionId, reviewerId, decisionType, comment, decisionAt " +
                    "FROM ReviewDecision WHERE manuscriptVersionId = ? ORDER BY decisionAt DESC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, manuscriptVersionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find latest review decision", ex);
        }
        return null;
    }
    
    /**
     * Reads all decisions made by one reviewer for audit/debugging.
     */
    public List<ReviewDecision> findByReviewerId(Long reviewerId) {
        String sql = "SELECT id, manuscriptVersionId, reviewerId, decisionType, comment, decisionAt " +
                    "FROM ReviewDecision WHERE reviewerId = ? ORDER BY decisionAt DESC";
        List<ReviewDecision> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reviewerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find review decisions by reviewer", ex);
        }
        return results;
    }
    
    private ReviewDecision map(ResultSet rs) throws SQLException {
        ReviewDecision decision = new ReviewDecision();
        decision.setId(rs.getLong("id"));
        decision.setManuscriptVersionId(rs.getLong("manuscriptVersionId"));
        decision.setReviewerId(rs.getLong("reviewerId"));
        decision.setDecisionType(ReviewDecisionType.valueOf(rs.getString("decisionType")));
        decision.setComment(rs.getString("comment"));
        decision.setDecisionAt(rs.getTimestamp("decisionAt") != null ? rs.getTimestamp("decisionAt").toLocalDateTime() : null);
        return decision;
    }
}
