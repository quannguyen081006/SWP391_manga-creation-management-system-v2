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
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
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
    
    public void calculateSeriesRanking(Connection conn, long periodId) throws SQLException {
        String insertRankingSql = ";WITH agg AS ("
                + " SELECT ve.seriesId,"
                + "   SUM(CAST(ve.voteCount AS BIGINT)) AS totalLikes,"
                + "   SUM(CAST(ve.readerCount AS BIGINT)) AS totalReads,"
                + "   CAST(("
                + "     SUM(CAST(ve.voteCount AS DECIMAL(18,6)))"
                + "     / NULLIF(SUM(CAST(ve.readerCount AS DECIMAL(18,6))), 0)"
                + "   ) * 100 AS DECIMAL(6,2)) AS rankScore"
                + " FROM VoteEntry ve"
                + " WHERE ve.periodId = ?"
                + "   AND ve.voteCount >= 0"
                + "   AND ve.readerCount > 0"
                + "   AND ve.voteCount <= ve.readerCount"
                + " GROUP BY ve.seriesId"
                + "), ranked AS ("
                + " SELECT"
                + "   a.seriesId,"
                + "   a.totalLikes,"
                + "   a.totalReads,"
                + "   a.rankScore,"
                + "   s.publicationDate,"
                + "   ROW_NUMBER() OVER (ORDER BY a.rankScore DESC, a.totalLikes DESC,"
                + "     CASE WHEN s.publicationDate IS NULL THEN 1 ELSE 0 END ASC,"
                + "     s.publicationDate ASC, a.seriesId ASC) AS rankPosition,"
                + "   COUNT(*) OVER () AS totalRows"
                + " FROM agg a"
                + " JOIN Series s ON s.id = a.seriesId"
                + ")"
                + " INSERT INTO RankingRecord ("
                + "   periodId,"
                + "   seriesId,"
                + "   rankScore,"
                + "   rankPosition,"
                + "   isBottomTwenty,"
                + "   totalLikes,"
                + "   totalReads,"
                + "   calculatedAt"
                + " )"
                + " SELECT"
                + "   ?,"
                + "   r.seriesId,"
                + "   r.rankScore,"
                + "   r.rankPosition,"
                + "   CASE"
                + "     WHEN r.rankPosition > r.totalRows - CEILING(r.totalRows * 0.2)"
                + "       THEN 1"
                + "     ELSE 0"
                + "   END,"
                + "   r.totalLikes,"
                + "   r.totalReads,"
                + "   GETDATE()"
                + " FROM ranked r";

        try ( PreparedStatement ps = conn.prepareStatement(insertRankingSql)) {
            ps.setLong(1, periodId);
            ps.setLong(2, periodId);
            ps.executeUpdate();
        }
    }
    
    public void calculateMangakaRanking(Connection conn, long periodId) throws SQLException {
        String sql = ";WITH mangaka_agg AS ("
                + "  SELECT "
                + "    s.mangakaId,"
                + "    SUM(CAST(ve.readerCount AS BIGINT)) AS totalReads,"
                + "    SUM(ve.revenue) AS totalRevenue,"
                + "    SUM(CAST(ve.voteCount AS BIGINT)) AS totalLikes"
                + "  FROM VoteEntry ve"
                + "  JOIN Series s ON s.id = ve.seriesId"
                + "  WHERE ve.periodId = ?"
                + "  GROUP BY s.mangakaId"
                + "), mangaka_ranked AS ("
                + "  SELECT"
                + "    mangakaId,"
                + "    totalReads,"
                + "    totalRevenue,"
                + "    totalLikes,"
                + "    ROW_NUMBER() OVER (ORDER BY totalReads DESC, totalRevenue DESC, totalLikes DESC, mangakaId ASC) AS rankPosition"
                + "  FROM mangaka_agg"
                + ")"
                + "INSERT INTO MangakaRankingRecord (periodId, mangakaId, totalReads, totalRevenue, totalLikes, rankPosition, calculatedAt)"
                + "SELECT"
                + "  ?,"
                + "  mr.mangakaId,"
                + "  mr.totalReads,"
                + "  mr.totalRevenue,"
                + "  mr.totalLikes,"
                + "  mr.rankPosition,"
                + "  GETDATE()"
                + "FROM mangaka_ranked mr";

        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            ps.setLong(2, periodId);
            ps.executeUpdate();
        }
    }
}
