package manga.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import manga.common.exception.BusinessRuleException;
import manga.dto.RevenueDataPoint;
import manga.model.AuthenticatedUser;
import manga.repository.AuditLogRepository;
import manga.repository.DecisionRepository;
import manga.repository.MangakaRankingRepository;
import manga.repository.RankingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClosePeriodPipelineService {

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private MangakaRankingRepository mangakaRankingRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional
    public void executePipeline(long periodId, AuthenticatedUser user) {
        // ADMIN only
        if (user == null || !user.hasRole("ADMIN")) {
            throw new BusinessRuleException("Only ADMIN can close & calculate ranking period");
        }

        // Lock & Validate period is OPEN
        Map<String, Object> period = rankingRepository.findPeriodById(periodId);
        if (period == null) {
            throw new BusinessRuleException("Ranking period not found");
        }
        String status = (String) period.get("status");
        if (!"OPEN".equalsIgnoreCase(status)) {
            throw new BusinessRuleException("Only OPEN period can be closed and calculated");
        }

        // Validate that there is at least one VoteEntry for this period
        List<Map<String, Object>> entries = rankingRepository.listEntries(periodId);
        if (entries == null || entries.isEmpty()) {
            throw new BusinessRuleException("Cannot close period: At least one vote entry is required");
        }

        // PHASE 1: Lock period (separate transaction)
        phase1LockPeriod(periodId, user);

        // PHASE 2: Series Ranking (separate transaction)
        phase2CalculateSeriesRanking(periodId, user);

        // PHASE 3: Mangaka Ranking (separate transaction)
        phase3CalculateMangakaRanking(periodId, user);

        // PHASE 4: Decision Engine (separate transaction)
        phase4RunDecisionEngine(periodId, user);

        // PHASE 5: Finalize period (separate transaction)
        phase5FinalizePeriod(periodId, user, period.get("name").toString());
    }

    // BR-RNK-10: Execute pipeline without user check for scheduler use
    @Transactional
    public void executePipelineAsSystem(long periodId) {
        // Lock & Validate period is OPEN
        Map<String, Object> period = rankingRepository.findPeriodById(periodId);
        if (period == null) {
            throw new BusinessRuleException("Ranking period not found");
        }
        String status = (String) period.get("status");
        if (!"OPEN".equalsIgnoreCase(status)) {
            throw new BusinessRuleException("Only OPEN period can be closed and calculated");
        }

        // Validate that there is at least one VoteEntry for this period
        List<Map<String, Object>> entries = rankingRepository.listEntries(periodId);
        if (entries == null || entries.isEmpty()) {
            throw new BusinessRuleException("Cannot close period: At least one vote entry is required");
        }

        // PHASE 1: Lock period (separate transaction)
        phase1LockPeriod(periodId, null);

        // PHASE 2: Series Ranking (separate transaction)
        phase2CalculateSeriesRanking(periodId, null);

        // PHASE 3: Mangaka Ranking (separate transaction)
        phase3CalculateMangakaRanking(periodId, null);

        // PHASE 4: Decision Engine (separate transaction)
        phase4RunDecisionEngine(periodId, null);

        // PHASE 5: Finalize period (separate transaction)
        phase5FinalizePeriod(periodId, null, period.get("name").toString());
    }

    @Transactional
    void phase1LockPeriod(long periodId, AuthenticatedUser user) {
        try ( Connection conn = dataSource.getConnection()) {
            String closeSql = "UPDATE RankingPeriod SET status = 'CLOSED' WHERE id = ? AND status = 'OPEN'";
            try ( PreparedStatement ps = conn.prepareStatement(closeSql)) {
                ps.setLong(1, periodId);
                if (ps.executeUpdate() == 0) {
                    throw new BusinessRuleException("Failed to close ranking period: status was modified");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database error in phase 1: lock period", ex);
        }
    }

    @Transactional
    void phase2CalculateSeriesRanking(long periodId, AuthenticatedUser user) {
        try ( Connection conn = dataSource.getConnection()) {
            calculateSeriesRanking(conn, periodId);
        } catch (SQLException ex) {
            throw new RuntimeException("Database error in phase 2: series ranking", ex);
        }
    }

    @Transactional
    void phase3CalculateMangakaRanking(long periodId, AuthenticatedUser user) {
        try ( Connection conn = dataSource.getConnection()) {
            calculateMangakaRanking(conn, periodId);
        } catch (SQLException ex) {
            throw new RuntimeException("Database error in phase 3: mangaka ranking", ex);
        }
    }

    @Transactional
    void phase4RunDecisionEngine(long periodId, AuthenticatedUser user) {
        try ( Connection conn = dataSource.getConnection()) {
            runDecisionEngine(conn, periodId, user);
        } catch (SQLException ex) {
            throw new RuntimeException("Database error in phase 4: decision engine", ex);
        }
    }

    @Transactional
    void phase5FinalizePeriod(long periodId, AuthenticatedUser user, String periodName) {
        try ( Connection conn = dataSource.getConnection()) {
            String calculatedSql = "UPDATE RankingPeriod SET status = 'CALCULATED', calculatedAt = GETDATE() WHERE id = ?";
            try ( PreparedStatement ps = conn.prepareStatement(calculatedSql)) {
                ps.setLong(1, periodId);
                ps.executeUpdate();
            }
            // BR-RNK-09: Audit log for ranking calculated and archived
            Long actorId = (user != null) ? user.getId() : null;
            auditLogRepository.insertLog(actorId, "RANKING_CALCULATED", "RANKING_PERIOD", periodId,
                    "Ranking period " + periodId + " calculation completed");
            auditLogRepository.insertLog(actorId, "RANKING_RESULT_ARCHIVED", "RANKING_PERIOD", periodId,
                    "Ranking period " + periodId + " result archived");
        } catch (SQLException ex) {
            throw new RuntimeException("Database error in phase 5: finalize period", ex);
        }
    }

    private void calculateSeriesRanking(Connection conn, long periodId) throws SQLException {
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

    private void calculateMangakaRanking(Connection conn, long periodId) throws SQLException {
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

    private void runDecisionEngine(Connection conn, long periodId, AuthenticatedUser user) throws SQLException {
        // Batch fetch all bottom 20% series with their status and session existence check
        String fetchBottomSeriesDataSql
                = "SELECT rr.id AS rankingRecordId, rr.seriesId, s.status AS seriesStatus, "
                + "   (SELECT COUNT(1) FROM DecisionSession ds WHERE ds.seriesId = rr.seriesId AND ds.status = 'OPEN') AS hasOpenSession "
                + "FROM RankingRecord rr "
                + "JOIN Series s ON s.id = rr.seriesId "
                + "WHERE rr.periodId = ? AND rr.isBottomTwenty = 1";

        List<Long> rankingRecordIds = new ArrayList<>();
        List<Long> seriesIds = new ArrayList<>();

        try ( PreparedStatement ps = conn.prepareStatement(fetchBottomSeriesDataSql)) {
            ps.setLong(1, periodId);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long rankingRecordId = rs.getLong("rankingRecordId");
                    long seriesId = rs.getLong("seriesId");
                    String seriesStatus = rs.getString("seriesStatus");
                    int hasOpenSession = rs.getInt("hasOpenSession");

                    // Skip if already cancelled or has open session
                    if ("CANCELLED".equalsIgnoreCase(seriesStatus) || hasOpenSession > 0) {
                        continue;
                    }

                    rankingRecordIds.add(rankingRecordId);
                    seriesIds.add(seriesId);
                }
            }
        }

        // Defensive logging: report if no bottom 20% series found
        if (seriesIds.isEmpty()) {
            // Check if any RankingRecord exists for this period
            String checkSql = "SELECT COUNT(*) FROM RankingRecord WHERE periodId = ?";
            try ( PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setLong(1, periodId);
                try ( ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int totalRecords = rs.getInt(1);
                        // Log: No bottom 20% series found. Total RankingRecord count: totalRecords
                    }
                }
            }
            return; // No series to process
        }

        // Log number of series to process
        // Log: Processing seriesIds.size() bottom 20% series for decision sessions
        // Build IN clause for series IDs
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < seriesIds.size(); i++) {
            if (i > 0) {
                inClause.append(",");
            }
            inClause.append("?");
        }

        String fetchAllRevenueHistorySql
                = "SELECT ve.seriesId, rp.id AS periodId, rp.name AS periodName, SUM(ve.revenue) AS totalRevenue "
                + "FROM VoteEntry ve "
                + "JOIN RankingPeriod rp ON rp.id = ve.periodId "
                + "WHERE ve.seriesId IN (" + inClause.toString() + ") AND (rp.status = 'CALCULATED' OR rp.id = ?) "
                + "GROUP BY ve.seriesId, rp.id, rp.name, rp.endDate "
                + "ORDER BY rp.endDate DESC";

        // Map seriesId -> list of revenue data points
        Map<Long, List<RevenueDataPoint>> revenueHistoryMap = new HashMap<>();
        for (long seriesId : seriesIds) {
            revenueHistoryMap.put(seriesId, new ArrayList<RevenueDataPoint>());
        }

        try ( PreparedStatement ps = conn.prepareStatement(fetchAllRevenueHistorySql)) {
            int paramIndex = 1;
            for (long seriesId : seriesIds) {
                ps.setLong(paramIndex++, seriesId);
            }
            ps.setLong(paramIndex, periodId);

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long seriesId = rs.getLong("seriesId");
                    revenueHistoryMap.get(seriesId).add(new RevenueDataPoint(
                            rs.getLong("periodId"),
                            rs.getString("periodName"),
                            rs.getBigDecimal("totalRevenue")
                    ));
                }
            }
        }

        // Create decision sessions for each series
        for (int i = 0; i < seriesIds.size(); i++) {
            long seriesId = seriesIds.get(i);
            long rankingRecordId = rankingRecordIds.get(i);
            List<RevenueDataPoint> revenueTrend = revenueHistoryMap.get(seriesId);

            // Reverse to get chronological order
            java.util.Collections.reverse(revenueTrend);
            // Keep only last 6 periods for decision analysis
            if (revenueTrend.size() > 6) {
                revenueTrend = new ArrayList<>(
                        revenueTrend.subList(
                                revenueTrend.size() - 6,
                                revenueTrend.size()
                        )
                );
            }
            String suggestion = calculateSystemSuggestion(revenueTrend);

            // Serialize revenue trend to JSON for snapshot storage (manual serialization)
            StringBuilder jsonBuilder = new StringBuilder("[");
            for (int j = 0; j < revenueTrend.size(); j++) {
                if (j > 0) {
                    jsonBuilder.append(",");
                }
                RevenueDataPoint point = revenueTrend.get(j);
                jsonBuilder.append("{\"periodId\":").append(point.getPeriodId())
                        .append(",\"periodName\":\"").append(escapeJson(point.getPeriodName())).append("\"")
                        .append(",\"revenue\":").append(point.getRevenue()).append("}");
            }
            jsonBuilder.append("]");
            String revenueTrendJson = jsonBuilder.toString();

            // Create DecisionSession with revenue trend snapshot
            Long actorId = (user != null) ? user.getId() : null;
            decisionRepository.createSession(seriesId, rankingRecordId, suggestion, revenueTrendJson, actorId);
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String calculateSystemSuggestion(List<RevenueDataPoint> trend) {
        if (trend == null || trend.size() < 2) {
            return null; // insufficient history
        }

        if (trend.size() == 2) {
            BigDecimal r0 = trend.get(0).getRevenue();
            BigDecimal r1 = trend.get(1).getRevenue();
            return r1.compareTo(r0) >= 0 ? "CONTINUE" : "REVIEW";
        }

        // trend size >= 3
        BigDecimal r0 = trend.get(trend.size() - 3).getRevenue();
        BigDecimal r1 = trend.get(trend.size() - 2).getRevenue();
        BigDecimal r2 = trend.get(trend.size() - 1).getRevenue();

        boolean increasing = r2.compareTo(r1) > 0 && r1.compareTo(r0) > 0;
        boolean decreasing = r2.compareTo(r1) < 0 && r1.compareTo(r0) < 0;

        if (increasing) {
            return "CONTINUE";
        } else if (decreasing) {
            return "CANCEL";
        } else {
            return "REVIEW";
        }
    }
}
