package manga.repository;

import manga.model.RankingCsvUpload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class RankingCsvUploadRepository {

    @Autowired
    private DataSource dataSource;

    public RankingCsvUpload saveOrUpdate(RankingCsvUpload upload) {
        String checkSql = "SELECT id FROM RankingCsvUpload WHERE periodId = ? AND boardMemberId = ?";
        String insertSql = "INSERT INTO RankingCsvUpload (periodId, boardMemberId, csvFileName, csvContent, uploadedAt) VALUES (?, ?, ?, ?, GETDATE())";
        String updateSql = "UPDATE RankingCsvUpload SET csvFileName = ?, csvContent = ?, uploadedAt = GETDATE() WHERE periodId = ? AND boardMemberId = ?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check if record exists
                Long existingId = null;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setLong(1, upload.getPeriodId());
                    ps.setLong(2, upload.getBoardMemberId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existingId = rs.getLong("id");
                        }
                    }
                }

                if (existingId != null) {
                    // Update existing record
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, upload.getCsvFileName());
                        ps.setString(2, upload.getCsvContent());
                        ps.setLong(3, upload.getPeriodId());
                        ps.setLong(4, upload.getBoardMemberId());
                        ps.executeUpdate();
                    }
                    upload.setId(existingId);
                } else {
                    // Insert new record
                    try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                        ps.setLong(1, upload.getPeriodId());
                        ps.setLong(2, upload.getBoardMemberId());
                        ps.setString(3, upload.getCsvFileName());
                        ps.setString(4, upload.getCsvContent());
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) {
                                upload.setId(rs.getLong(1));
                            }
                        }
                    }
                }

                conn.commit();
                return upload;
            } catch (SQLException ex) {
                conn.rollback();
                throw new RuntimeException("Failed to save or update CSV upload", ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to save or update CSV upload", ex);
        }
    }

    public List<Map<String, Object>> findByPeriod(long periodId) {
        String sql = "SELECT rcu.id, rcu.periodId, rcu.boardMemberId, rcu.csvFileName, rcu.uploadedAt, u.username " +
                     "FROM RankingCsvUpload rcu " +
                     "JOIN [User] u ON u.id = rcu.boardMemberId " +
                     "WHERE rcu.periodId = ? " +
                     "ORDER BY rcu.uploadedAt DESC";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("periodId", rs.getLong("periodId"));
                    row.put("boardMemberId", rs.getLong("boardMemberId"));
                    row.put("csvFileName", rs.getString("csvFileName"));
                    row.put("uploadedAt", rs.getTimestamp("uploadedAt"));
                    row.put("username", rs.getString("username"));
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to find CSV uploads by period", ex);
        }
        return rows;
    }

    public RankingCsvUpload findById(long id) {
        String sql = "SELECT id, periodId, boardMemberId, csvFileName, csvContent, uploadedAt FROM RankingCsvUpload WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RankingCsvUpload upload = new RankingCsvUpload();
                    upload.setId(rs.getLong("id"));
                    upload.setPeriodId(rs.getLong("periodId"));
                    upload.setBoardMemberId(rs.getLong("boardMemberId"));
                    upload.setCsvFileName(rs.getString("csvFileName"));
                    upload.setCsvContent(rs.getString("csvContent"));
                    upload.setUploadedAt(rs.getTimestamp("uploadedAt"));
                    return upload;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to find CSV upload by id", ex);
        }
        return null;
    }
}
