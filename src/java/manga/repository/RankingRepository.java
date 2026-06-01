package manga.repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import manga.dto.RankingCsvRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RankingRepository {

    @Autowired
    private DataSource dataSource;

    // ------------------------------------------------------------------ //
    //  listPeriods — không thay đổi                                       //
    // ------------------------------------------------------------------ //
    public List<Map<String, Object>> listPeriods() {
        String sql = "SELECT id, name, startDate, endDate, status, calculatedAt FROM RankingPeriod ORDER BY id DESC";
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql);  ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapPeriod(rs));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list ranking periods", ex);
        }
        return rows;
    }

    // ------------------------------------------------------------------ //
    //  findPeriodById — không thay đổi                                    //
    // ------------------------------------------------------------------ //
    public Map<String, Object> findPeriodById(long periodId) {
        String sql = "SELECT id, name, startDate, endDate, status, calculatedAt FROM RankingPeriod WHERE id = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Ranking period not found");
                }
                return mapPeriod(rs);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load ranking period", ex);
        }
    }

    // ------------------------------------------------------------------ //
    //  createPeriod — không thay đổi                                      //
    // ------------------------------------------------------------------ //
    public long createPeriod(String name, Date startDate, Date endDate) {
        return createPeriod(name, startDate, endDate, "OPEN");
    }

    // ------------------------------------------------------------------ //
    //  createPeriodWithStatus — new method for lifecycle refactor         //
    // ------------------------------------------------------------------ //
    public long createPeriod(String name, Date startDate, Date endDate, String status) {
        String sql = "INSERT INTO RankingPeriod (name, startDate, endDate, status) VALUES (?, ?, ?, ?)";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setDate(2, startDate);
            ps.setDate(3, endDate);
            ps.setString(4, status);
            ps.executeUpdate();
            try ( ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            throw new IllegalStateException("Cannot create ranking period");
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create ranking period", ex);
        }
    }

    // ------------------------------------------------------------------ //
    //  closePeriod — không thay đổi                                       //
    // ------------------------------------------------------------------ //
    public void closePeriod(long periodId) {
        updatePeriodStatus(periodId, "CLOSED", "OPEN");
    }

    // ------------------------------------------------------------------ //
    //  updatePeriodStatus — new method for lifecycle refactor              //
    // ------------------------------------------------------------------ //
    public void updatePeriodStatus(long periodId, String newStatus, String expectedCurrentStatus) {
        String sql = "UPDATE RankingPeriod SET status = ? WHERE id = ? AND status = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setLong(2, periodId);
            ps.setString(3, expectedCurrentStatus);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Cannot update period status from " + expectedCurrentStatus + " to " + newStatus);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update ranking period status", ex);
        }
    }

    // ------------------------------------------------------------------ //
    //  findOpenExpiredPeriods — find OPEN periods past their endDate       //
    // ------------------------------------------------------------------ //
    public List<Map<String, Object>> findOpenExpiredPeriods() {
        String sql = "SELECT id, name, startDate, endDate, status, calculatedAt FROM RankingPeriod WHERE status = 'OPEN' AND endDate < CAST(GETDATE() AS DATE)";
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql);  ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapPeriod(rs));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find expired ranking periods", ex);
        }
        return rows;
    }

    // ------------------------------------------------------------------ //
    //  findPeriodByMonthYear — check if period exists for month/year      //
    // ------------------------------------------------------------------ //
    public Map<String, Object> findPeriodByMonthYear(int year, int month) {
        String sql = "SELECT id, name, startDate, endDate, status, calculatedAt FROM RankingPeriod WHERE YEAR(startDate) = ? AND MONTH(startDate) = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapPeriod(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find ranking period by month/year", ex);
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  FIX: submitEntry — thay MERGE bằng check-then-insert (BR-54)       //
    // ------------------------------------------------------------------ //
    public void submitEntry(long periodId, long seriesId, long boardMemberId, int voteCount, int readerCount, java.math.BigDecimal revenue) {
        // BR-51: voteCount không âm
        if (voteCount < 0) {
            throw new IllegalArgumentException("voteCount must be >= 0 (BR-51)");
        }
        // BR-52: readerCount phải > 0
        if (readerCount <= 0) {
            throw new IllegalArgumentException("readerCount must be > 0 (BR-52)");
        }
        // BR-50: voteCount không vượt readerCount
        if (voteCount > readerCount) {
            throw new IllegalArgumentException("voteCount cannot exceed readerCount (BR-50)");
        }
        if (revenue == null || revenue.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("revenue must be >= 0");
        }

        String periodStatusSql = "SELECT status FROM RankingPeriod WHERE id = ?";
        String duplicateSql = "SELECT COUNT(1) FROM VoteEntry WHERE periodId = ? AND seriesId = ? AND boardMemberId = ?";
        try ( Connection conn = dataSource.getConnection()) {
            boolean hasRevenue = hasVoteEntryRevenueColumn(conn);
            String insertSql = hasRevenue
                    ? "INSERT INTO VoteEntry (periodId, seriesId, boardMemberId, voteCount, readerCount, revenue, submittedAt)"
                    + " VALUES (?, ?, ?, ?, ?, ?, GETDATE())"
                    : "INSERT INTO VoteEntry (periodId, seriesId, boardMemberId, voteCount, readerCount, submittedAt)"
                    + " VALUES (?, ?, ?, ?, ?, GETDATE())";

            // Check 1: period phải OPEN (BR-49)
            try ( PreparedStatement ps = conn.prepareStatement(periodStatusSql)) {
                ps.setLong(1, periodId);
                try ( ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Ranking period not found");
                    }
                    if (!"OPEN".equalsIgnoreCase(rs.getString("status"))) {
                        throw new IllegalArgumentException("Vote entries can only be submitted when period is OPEN (BR-49)");
                    }
                }
            }

            // Check 2: không duplicate (BR-54)
            try ( PreparedStatement ps = conn.prepareStatement(duplicateSql)) {
                ps.setLong(1, periodId);
                ps.setLong(2, seriesId);
                ps.setLong(3, boardMemberId);
                try ( ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        throw new IllegalArgumentException(
                                "You have already submitted a vote entry for this series in this period (BR-54)");
                    }
                }
            }

            // Insert vote entry
            try ( PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setLong(1, periodId);
                ps.setLong(2, seriesId);
                ps.setLong(3, boardMemberId);
                ps.setInt(4, voteCount);
                ps.setInt(5, readerCount);
                if (hasRevenue) {
                    ps.setBigDecimal(6, revenue);
                }
                ps.executeUpdate();
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Cannot submit vote entry", ex);
        }
    }

    public boolean seriesExists(long seriesId) {
        String sql = "SELECT COUNT(1) FROM Series WHERE id = ?";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, seriesId);
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check series", ex);
        }
    }

    public void replaceCsvEntries(long periodId, long adminUserId, List<RankingCsvRow> rows) {
        String statusSql = "SELECT status FROM RankingPeriod WHERE id = ?";
        String deleteSql = "DELETE FROM VoteEntry WHERE periodId = ? AND boardMemberId = ?";
        try ( Connection conn = dataSource.getConnection()) {
            boolean hasRevenue = hasVoteEntryRevenueColumn(conn);
            String insertSql = hasRevenue
                    ? "INSERT INTO VoteEntry (periodId, seriesId, boardMemberId, voteCount, readerCount, revenue, submittedAt)"
                    + " VALUES (?, ?, ?, ?, ?, ?, GETDATE())"
                    : "INSERT INTO VoteEntry (periodId, seriesId, boardMemberId, voteCount, readerCount, submittedAt)"
                    + " VALUES (?, ?, ?, ?, ?, GETDATE())";
            conn.setAutoCommit(false);
            try {
                try ( PreparedStatement ps = conn.prepareStatement(statusSql)) {
                    ps.setLong(1, periodId);
                    try ( ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Ranking period not found");
                        }
                        if (!"OPEN".equalsIgnoreCase(rs.getString("status"))) {
                            throw new IllegalArgumentException("CSV upload is only allowed for OPEN ranking periods");
                        }
                    }
                }

                try ( PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setLong(1, periodId);
                    ps.setLong(2, adminUserId);
                    ps.executeUpdate();
                }

                try ( PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (RankingCsvRow row : rows) {
                        ps.setLong(1, periodId);
                        ps.setLong(2, row.getSeriesId());
                        ps.setLong(3, adminUserId);
                        ps.setInt(4, row.getVoteCount());
                        ps.setInt(5, row.getReaderCount());
                        if (hasRevenue) {
                            ps.setBigDecimal(6, row.getRevenue());
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot import ranking CSV", ex);
        }
    }

    // ------------------------------------------------------------------ //
    //  listEntries — không thay đổi                                       //
    // ------------------------------------------------------------------ //
    public List<Map<String, Object>> listEntries(long periodId) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try ( Connection conn = dataSource.getConnection()) {
            boolean hasRevenue = hasVoteEntryRevenueColumn(conn);
            String sql = "SELECT id, periodId, seriesId, boardMemberId, voteCount, readerCount"
                    + (hasRevenue ? ", revenue" : "")
                    + ", submittedAt FROM VoteEntry WHERE periodId = ? ORDER BY id DESC";
            try ( PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, periodId);
                try ( ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<String, Object>();
                        row.put("id", rs.getLong("id"));
                        row.put("periodId", rs.getLong("periodId"));
                        row.put("seriesId", rs.getLong("seriesId"));
                        row.put("boardMemberId", rs.getLong("boardMemberId"));
                        row.put("voteCount", rs.getInt("voteCount"));
                        row.put("readerCount", rs.getInt("readerCount"));
                        row.put("revenue", hasRevenue ? rs.getBigDecimal("revenue") : java.math.BigDecimal.ZERO);
                        row.put("submittedAt", rs.getTimestamp("submittedAt"));
                        rows.add(row);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list vote entries", ex);
        }
        return rows;
    }

    // ------------------------------------------------------------------ //
    //  FIX: calculatePeriod                                               //
    //   1. Sửa isBottomTwenty edge case (totalRows <= 1)                  //
    //   2. Tự động tạo DecisionSession cho bottom series                  //
    //   3. Gửi notification DECISION_SESSION_OPENED cho Board members     //
    //  Tất cả trong 1 transaction                                         //
    // ------------------------------------------------------------------ //
    @Transactional
    public void calculatePeriod(long periodId) {

// =========================================================
// STEP 1 — CHECK PERIOD STATUS
// =========================================================
        String statusSql
                = "SELECT status FROM RankingPeriod WHERE id = ?";

// =========================================================
// STEP 2 — CALCULATE RANKING
// =========================================================
        String insertRankingSql
                = ";WITH agg AS ("
                + " SELECT ve.seriesId,"
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
                + "   seriesId,"
                + "   rankScore,"
                + "   ROW_NUMBER() OVER (ORDER BY rankScore DESC, seriesId ASC) AS rankPosition,"
                + "   COUNT(*) OVER () AS totalRows"
                + " FROM agg"
                + ")"
                + " INSERT INTO RankingRecord ("
                + "   periodId,"
                + "   seriesId,"
                + "   rankScore,"
                + "   rankPosition,"
                + "   isBottomTwenty,"
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
                + "   GETDATE()"
                + " FROM ranked r";

// =========================================================
// STEP 3 — AUTO CREATE DECISION SESSION
// =========================================================
        String createSessionsSql
                = "INSERT INTO DecisionSession (seriesId, rankingRecordId, status, openedAt)"
                + " SELECT rr.seriesId, rr.id, 'OPEN', GETDATE()"
                + " FROM RankingRecord rr"
                + " JOIN Series s ON s.id = rr.seriesId"
                + " WHERE rr.periodId = ?"
                + "   AND rr.isBottomTwenty = 1"
                + "   AND s.status != 'CANCELLED'"
                + "   AND NOT EXISTS ("
                + "     SELECT 1"
                + "     FROM DecisionSession ds"
                + "     WHERE ds.seriesId = rr.seriesId"
                + "       AND ds.status = 'OPEN'"
                + "   )";

        String notifyDecisionSessionsSql
                = "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) "
                + "SELECT u.id, 'DECISION_SESSION_OPENED', 'Decision session opened', "
                + "'A new decision session is open for series #' + CAST(ds.seriesId AS VARCHAR(30)) + '.', "
                + "'/main/decisions/' + CAST(ds.id AS VARCHAR(30)), ds.id, 'DECISION', 0, GETDATE() "
                + "FROM DecisionSession ds "
                + "JOIN RankingRecord rr ON rr.id = ds.rankingRecordId "
                + "JOIN Series s ON s.id = ds.seriesId "
                + "JOIN [User] u ON u.status = 'ACTIVE' "
                + "JOIN UserRole ur ON ur.userId = u.id "
                + "JOIN [Role] r ON r.id = ur.roleId "
                + "WHERE rr.periodId = ? "
                + "AND ds.status = 'OPEN' "
                + "AND r.name = 'EDITORIAL_BOARD' "
                + "AND u.id <> s.tantouEditorId "
                + "AND NOT EXISTS ( "
                + "  SELECT 1 FROM Notification n "
                + "  WHERE n.userId = u.id "
                + "  AND n.type = 'DECISION_SESSION_OPENED' "
                + "  AND n.referenceId = ds.id "
                + ")";

// =========================================================
// STEP 4 — SEND NOTIFICATION
// =========================================================
// =========================================================
// STEP 5 — UPDATE PERIOD STATUS
// =========================================================
        String updatePeriodSql
                = "UPDATE RankingPeriod "
                + "SET status = 'CALCULATED', "
                + "    calculatedAt = GETDATE() "
                + "WHERE id = ?";

        try ( Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try {

                // =====================================================
                // VALIDATE STATUS
                // =====================================================
                try ( PreparedStatement ps = conn.prepareStatement(statusSql)) {

                    ps.setLong(1, periodId);

                    try ( ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "Ranking period not found"
                            );
                        }

                        String status = rs.getString("status");

                        if ("CALCULATED".equalsIgnoreCase(status)) {
                            throw new IllegalArgumentException(
                                    "Ranking period already calculated"
                            );
                        }

                        if (!"CLOSED".equalsIgnoreCase(status)) {
                            throw new IllegalArgumentException(
                                    "Only CLOSED period can be calculated"
                            );
                        }
                    }
                }

                // =====================================================
                // INSERT RANKING RECORDS
                // =====================================================
                try ( PreparedStatement ps
                        = conn.prepareStatement(insertRankingSql)) {

                    ps.setLong(1, periodId);
                    ps.setLong(2, periodId);

                    ps.executeUpdate();
                }

                // =====================================================
                // CREATE DECISION SESSION
                // =====================================================
                try ( PreparedStatement ps
                        = conn.prepareStatement(createSessionsSql)) {

                    ps.setLong(1, periodId);

                    ps.executeUpdate();
                }

                // =====================================================
                // SEND NOTIFICATIONS
                // =====================================================
                try ( PreparedStatement ps
                        = conn.prepareStatement(notifyDecisionSessionsSql)) {

                    ps.setLong(1, periodId);

                    ps.executeUpdate();
                }

                // =====================================================
                // UPDATE PERIOD STATUS
                // =====================================================
                try ( PreparedStatement ps
                        = conn.prepareStatement(updatePeriodSql)) {

                    ps.setLong(1, periodId);

                    ps.executeUpdate();
                }

                conn.commit();

            } catch (Exception ex) {

                conn.rollback();
                throw ex;

            } finally {

                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(
                    "Cannot calculate ranking period: " + ex.getMessage(),
                    ex
            );
        }

    }
    // ------------------------------------------------------------------ //
    //  results — không thay đổi                                           //
    // ------------------------------------------------------------------ //

    public List<Map<String, Object>> results(long periodId) {
        String sql = "SELECT rr.id, rr.periodId, rr.seriesId, s.title AS seriesTitle, "
                + "rr.rankScore, rr.rankPosition, rr.isBottomTwenty, rr.calculatedAt, "
                + "rr.totalLikes, rr.totalReads "
                + "FROM RankingRecord rr "
                + "JOIN Series s ON s.id = rr.seriesId "
                + "WHERE rr.periodId = ? "
                + "ORDER BY rr.rankPosition";
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("id", rs.getLong("id"));
                    row.put("periodId", rs.getLong("periodId"));
                    row.put("seriesId", rs.getLong("seriesId"));
                    row.put("seriesTitle", rs.getString("seriesTitle"));
                    row.put("rankScore", rs.getBigDecimal("rankScore"));
                    row.put("rankPosition", rs.getInt("rankPosition"));
                    row.put("isBottomTwenty", rs.getBoolean("isBottomTwenty"));
                    row.put("calculatedAt", rs.getTimestamp("calculatedAt"));
                    row.put("totalLikes", rs.getLong("totalLikes"));
                    row.put("totalReads", rs.getLong("totalReads"));
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load ranking results", ex);
        }
        return rows;
    }

    public List<manga.dto.RevenueDataPoint> getRevenueHistory(long seriesId, long currentPeriodId, int limit) {
        List<manga.dto.RevenueDataPoint> points = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            boolean hasRevenue = hasVoteEntryRevenueColumn(conn);
            String revenueExpression = hasRevenue ? "COALESCE(SUM(ve.revenue), 0)" : "CAST(0 AS DECIMAL(15,2))";
            String sql = "SELECT TOP 3 rp.id AS periodId, rp.name AS periodName, " + revenueExpression + " AS totalRevenue "
                    + "FROM RankingPeriod rp "
                    + "LEFT JOIN VoteEntry ve ON ve.periodId = rp.id AND ve.seriesId = ? "
                    + "WHERE rp.status = 'CALCULATED' OR rp.id = ? "
                    + "GROUP BY rp.id, rp.name, rp.endDate "
                    + "ORDER BY rp.endDate DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, seriesId);
                ps.setLong(2, currentPeriodId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        points.add(new manga.dto.RevenueDataPoint(
                            rs.getLong("periodId"),
                            rs.getString("periodName"),
                            rs.getBigDecimal("totalRevenue")
                        ));
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load revenue history", ex);
        }
        java.util.Collections.reverse(points);
        return points;
    }

    // ------------------------------------------------------------------ //
    //  mapPeriod helper — không thay đổi                                  //
    // ------------------------------------------------------------------ //
    public long getSeriesMangakaId(long seriesId) {
        String sql = "SELECT mangakaId FROM Series WHERE id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, seriesId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("mangakaId");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load series mangaka id", ex);
        }
        return -1;
    }

    public long getPeriodIdByRankingRecordId(long rankingRecordId) {
        String sql = "SELECT periodId FROM RankingRecord WHERE id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, rankingRecordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("periodId");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load period id by ranking record id", ex);
        }
        return -1;
    }

    private Map<String, Object> mapPeriod(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("id", rs.getLong("id"));
        row.put("name", rs.getString("name"));
        row.put("startDate", rs.getDate("startDate"));
        row.put("endDate", rs.getDate("endDate"));
        row.put("status", rs.getString("status"));
        row.put("calculatedAt", rs.getTimestamp("calculatedAt"));
        return row;
    }

    private boolean hasVoteEntryRevenueColumn(Connection conn) throws SQLException {
        String sql = "SELECT COL_LENGTH('VoteEntry', 'revenue')";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getObject(1) != null;
        }
    }
}
