package manga.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import manga.model.MangakaRankingRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class MangakaRankingRepository {

    @Autowired
    private DataSource dataSource;

    public void insertBatch(long periodId, List<MangakaRankingRecord> records) {
        String sql = "INSERT INTO MangakaRankingRecord (periodId, mangakaId, totalReads, totalRevenue, totalLikes, rankPosition, calculatedAt)"
                + " VALUES (?, ?, ?, ?, ?, ?, GETDATE())";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (MangakaRankingRecord r : records) {
                    ps.setLong(1, periodId);
                    ps.setLong(2, r.getMangakaId());
                    ps.setLong(3, r.getTotalReads());
                    ps.setBigDecimal(4, r.getTotalRevenue());
                    ps.setLong(5, r.getTotalLikes());
                    ps.setInt(6, r.getRankPosition());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot save mangaka ranking batch", ex);
        }
    }

    public List<Map<String, Object>> findByPeriodId(long periodId) {
        String sql = "SELECT mrr.id, mrr.periodId, mrr.mangakaId, u.fullName AS mangakaName, "
                + "mrr.totalReads, mrr.totalRevenue, mrr.totalLikes, mrr.rankPosition, mrr.calculatedAt "
                + "FROM MangakaRankingRecord mrr "
                + "JOIN [User] u ON u.id = mrr.mangakaId "
                + "WHERE mrr.periodId = ? "
                + "ORDER BY mrr.rankPosition ASC";
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("id", rs.getLong("id"));
                    row.put("periodId", rs.getLong("periodId"));
                    row.put("mangakaId", rs.getLong("mangakaId"));
                    row.put("mangakaName", rs.getString("mangakaName"));
                    row.put("totalReads", rs.getLong("totalReads"));
                    row.put("totalRevenue", rs.getBigDecimal("totalRevenue"));
                    row.put("totalLikes", rs.getLong("totalLikes"));
                    row.put("rankPosition", rs.getInt("rankPosition"));
                    row.put("calculatedAt", rs.getTimestamp("calculatedAt"));
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load mangaka ranking records", ex);
        }
        return rows;
    }

    public boolean existsForPeriod(long periodId) {
        String sql = "SELECT COUNT(1) FROM MangakaRankingRecord WHERE periodId = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check if mangaka ranking exists", ex);
        }
    }
}
