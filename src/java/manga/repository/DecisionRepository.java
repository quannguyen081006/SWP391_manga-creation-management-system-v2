package manga.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import manga.dto.RevenueDataPoint;
import manga.model.AuthenticatedUser;
import manga.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class DecisionRepository {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AuditLogRepository auditLogRepository;

    // ------------------------------------------------------------------ //
    //  listSessions — không thay đổi                                      //
    // ------------------------------------------------------------------ //
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try ( Connection conn = dataSource.getConnection()) {
            boolean hasSystemSuggestion = hasDecisionSessionSystemSuggestionColumn(conn);
            String sql = "SELECT ds.id, ds.seriesId, ds.rankingRecordId, ds.status, ds.result"
                    + (hasSystemSuggestion ? ", ds.systemSuggestion" : "")
                    + ", ds.openedAt, ds.closedAt, s.title AS seriesTitle"
                    + " FROM DecisionSession ds"
                    + " JOIN Series s ON s.id = ds.seriesId"
                    + " ORDER BY ds.openedAt DESC";
            try ( PreparedStatement ps = conn.prepareStatement(sql);  ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapSession(rs, hasSystemSuggestion, false));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list decision sessions", ex);
        }
        return rows;
    }

    // ------------------------------------------------------------------ //
    //  getSessionDetail — không thay đổi                                  //
    // ------------------------------------------------------------------ //
    public Map<String, Object> getSessionDetail(long sessionId) {
        String votesSql = "SELECT id, sessionId, voterId, decision, justification, votedAt"
                + " FROM DecisionVote WHERE sessionId = ? ORDER BY votedAt DESC";

        try ( Connection conn = dataSource.getConnection()) {
            boolean hasSystemSuggestion = hasDecisionSessionSystemSuggestionColumn(conn);
            boolean hasRevenueTrendSnapshot = hasDecisionSessionRevenueTrendSnapshotColumn(conn);
            String sessionSql = "SELECT ds.id, ds.seriesId, ds.rankingRecordId, ds.status, ds.result"
                    + (hasSystemSuggestion ? ", ds.systemSuggestion" : "")
                    + (hasRevenueTrendSnapshot ? ", ds.revenueTrendSnapshot" : "")
                    + ", ds.openedAt, ds.closedAt, s.title AS seriesTitle"
                    + " FROM DecisionSession ds"
                    + " JOIN Series s ON s.id = ds.seriesId"
                    + " WHERE ds.id = ?";
            Map<String, Object> session;
            try ( PreparedStatement ps = conn.prepareStatement(sessionSql)) {
                ps.setLong(1, sessionId);
                try ( ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Decision session not found");
                    }
                    session = mapSession(rs, hasSystemSuggestion, hasRevenueTrendSnapshot);
                }
            }

            List<Map<String, Object>> votes = new ArrayList<Map<String, Object>>();
            try ( PreparedStatement ps = conn.prepareStatement(votesSql)) {
                ps.setLong(1, sessionId);
                try ( ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> vote = new HashMap<String, Object>();
                        vote.put("id", rs.getLong("id"));
                        vote.put("sessionId", rs.getLong("sessionId"));
                        vote.put("voterId", rs.getLong("voterId"));
                        vote.put("decision", rs.getString("decision"));
                        vote.put("justification", rs.getString("justification"));
                        vote.put("votedAt", rs.getTimestamp("votedAt"));
                        votes.add(vote);
                    }
                }
            }

            session.put("votes", votes);
            return session;
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load decision session detail", ex);
        }
    }

    // ------------------------------------------------------------------ //
    //  FIX: castVote                                                      //
    //   - Check 1: validate decision value + CANCEL justification         //
    //   - Check 2: session phải OPEN (BR-64)                              //
    //   - Check 3: conflict of interest — Tantou Editor bị block (BR-60) //
    //   - Check 4: không duplicate vote (BR-61)                           //
    //   - Step 5: insert vote                                             //
    //   - Step 6: resolveIfQuorum — finalize session nếu đủ 3 vote       //
    //  Toàn bộ trong 1 transaction                                        //
    // ------------------------------------------------------------------ //
    public void castVote(long sessionId, long voterId, String decision, String justification) {

        // Check 1: Validate decision value
        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        if (!"CONTINUE".equals(normalized) && !"CANCEL".equals(normalized) && !"CHANGE_TYPE".equals(normalized)) {
            throw new IllegalArgumentException("decision must be CONTINUE, CANCEL, or CHANGE_TYPE");
        }
        // BR-68: CANCEL bắt buộc có justification
        if ("CANCEL".equals(normalized) && (justification == null || justification.trim().isEmpty())) {
            throw new IllegalArgumentException("justification is required when decision is CANCEL (BR-68)");
        }

        String sessionSql = "SELECT status, seriesId FROM DecisionSession WHERE id = ?";
        String conflictSql = "SELECT tantouEditorId FROM Series WHERE id = ?";
        String dupSql = "SELECT COUNT(1) FROM DecisionVote WHERE sessionId = ? AND voterId = ?";
        String voterSql = "SELECT u.status FROM [User] u JOIN UserRole ur ON ur.userId = u.id JOIN [Role] r ON r.id = ur.roleId WHERE u.id = ? AND r.name = 'EDITORIAL_BOARD'";
        String insertSql = "INSERT INTO DecisionVote (sessionId, voterId, decision, justification, votedAt)"
                + " VALUES (?, ?, ?, ?, GETDATE())";

        try ( Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {

                // Check 2: Session phải tồn tại và còn OPEN (BR-64)
                long seriesId;
                try ( PreparedStatement ps = conn.prepareStatement(sessionSql)) {
                    ps.setLong(1, sessionId);
                    try ( ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Decision session not found");
                        }
                        String status = rs.getString("status");
                        if (!"OPEN".equalsIgnoreCase(status)) {
                            throw new IllegalArgumentException(
                                    "Cannot vote on a " + status + " decision session (BR-64)");
                        }
                        seriesId = rs.getLong("seriesId");
                    }
                }

                // Check 2.5: BR-DEC-01 - Verify voter is ACTIVE and has EDITORIAL_BOARD role
                try ( PreparedStatement ps = conn.prepareStatement(voterSql)) {
                    ps.setLong(1, voterId);
                    try ( ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Voter not found or does not have EDITORIAL_BOARD role (BR-DEC-01)");
                        }
                        String voterStatus = rs.getString("status");
                        if (!"ACTIVE".equalsIgnoreCase(voterStatus)) {
                            throw new IllegalArgumentException("Voter is not ACTIVE (BR-DEC-01)");
                        }
                    }
                }

                // Check 3: Conflict of interest — Tantou Editor của series không được vote (BR-60)
                try ( PreparedStatement ps = conn.prepareStatement(conflictSql)) {
                    ps.setLong(1, seriesId);
                    try ( ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            long tantouId = rs.getLong("tantouEditorId");
                            if (!rs.wasNull() && tantouId == voterId) {
                                throw new IllegalArgumentException(
                                        "Tantou Editor of this series cannot vote due to conflict of interest (BR-60)");
                            }
                        }
                    }
                }

                // Check 4: Không vote duplicate (BR-61)
                try ( PreparedStatement ps = conn.prepareStatement(dupSql)) {
                    ps.setLong(1, sessionId);
                    ps.setLong(2, voterId);
                    try ( ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            throw new IllegalArgumentException(
                                    "You have already voted in this decision session (BR-61)");
                        }
                    }
                }

                // Step 5: Insert vote
                try ( PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setLong(1, sessionId);
                    ps.setLong(2, voterId);
                    ps.setString(3, normalized);
                    ps.setString(4, justification == null ? null : justification.trim());
                    ps.executeUpdate();
                }

                // AuditLog is append-only evidence that a board member submitted a vote.
                auditLogRepository.insertLog(voterId, "DECISION_VOTE_SUBMITTED", "DECISION", sessionId,
                        "Voter " + voterId + " submitted decision: " + normalized);

                // Step 6: Kiểm tra quorum và finalize nếu đủ (BR-62)
                resolveIfQuorum(conn, sessionId, seriesId, voterId);

                conn.commit();

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot cast decision vote", ex);
        }
    }

    // ------------------------------------------------------------------ //
    //  resolveIfQuorum (private helper)                                   //
    //  Gọi sau mỗi vote. Nếu tổng vote >= 3 → finalize session.          //
    //  Kết quả: vote nhiều nhất thắng.                                    //
    //  Tie-break: CONTINUE > CHANGE_TYPE > CANCEL                        //
    //  Nếu CANCEL → update Series.status = CANCELLED (BR-69)             //
    //  Gửi DECISION_RESOLVED notification cho tất cả Board members        //
    // ------------------------------------------------------------------ //
    private void resolveIfQuorum(Connection conn, long sessionId, long seriesId, Long triggeringVoterId) throws SQLException {

        String countSql
                = "SELECT"
                + " SUM(CASE WHEN decision = 'CONTINUE'    THEN 1 ELSE 0 END) AS continueVotes,"
                + " SUM(CASE WHEN decision = 'CANCEL'      THEN 1 ELSE 0 END) AS cancelVotes,"
                + " SUM(CASE WHEN decision = 'CHANGE_TYPE' THEN 1 ELSE 0 END) AS changeVotes,"
                + " COUNT(*) AS totalVotes"
                + " FROM DecisionVote WHERE sessionId = ?";

        int continueV, cancelV, changeV, total;
        try ( PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setLong(1, sessionId);
            try ( ResultSet rs = ps.executeQuery()) {
                rs.next();
                continueV = rs.getInt("continueVotes");
                cancelV = rs.getInt("cancelVotes");
                changeV = rs.getInt("changeVotes");
                total = rs.getInt("totalVotes");
            }
        }

        // Chưa đủ quorum → chờ thêm vote (BR-62: min 3)
        if (total < 3) {
            return;
        }

        // Xác định kết quả: vote nhiều nhất thắng
        // Tie-break: CONTINUE > CHANGE_TYPE > CANCEL
        String result;
        if (continueV >= cancelV && continueV >= changeV) {
            result = "CONTINUE";
        } else if (changeV >= cancelV) {
            result = "CHANGE_TYPE";
        } else {
            result = "CANCEL";
        }

        // Đóng session
        String closeSessionSql
                = "UPDATE DecisionSession SET status = 'CLOSED', result = ?, closedAt = GETDATE() WHERE id = ?";
        try ( PreparedStatement ps = conn.prepareStatement(closeSessionSql)) {
            ps.setString(1, result);
            ps.setLong(2, sessionId);
            ps.executeUpdate();
        }
        // AuditLog records the automatic resolution once quorum is reached.
        auditLogRepository.insertLog(triggeringVoterId, "DECISION_SESSION_RESOLVED", "DECISION", sessionId,
                "Decision session " + sessionId + " resolved with result: " + result);

        // Nếu CANCEL → update Series.status = CANCELLED (BR-69)
        if ("CANCEL".equals(result)) {
            String cancelSeriesSql = "UPDATE Series SET status = 'CANCELLED' WHERE id = ?";
            try ( PreparedStatement ps = conn.prepareStatement(cancelSeriesSql)) {
                ps.setLong(1, seriesId);
                ps.executeUpdate();
            }

            // AuditLog records the resulting series cancellation as a separate event.
            auditLogRepository.insertLog(triggeringVoterId, "SERIES_CANCELLED", "SERIES", seriesId,
                    "Series " + seriesId + " cancelled due to decision session " + sessionId);
        }

        // Gửi DECISION_RESOLVED notification cho tất cả Board members active (BR-65)
        // BR-DEC-08: Exclude Tantou Editor from notification
        String notifySql
                = "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt)"
                + " SELECT u.id,"
                + "   'DECISION_RESOLVED',"
                + "   'Decision finalized',"
                + "   'A decision session has been finalized with result: " + result + ".',"
                + "   '/main/decisions/' + CAST(? AS VARCHAR(30)),"
                + "   ?,"
                + "   'DECISION',"
                + "   0,"
                + "   GETDATE()"
                + " FROM [User] u"
                + " JOIN UserRole ur ON ur.userId = u.id"
                + " JOIN [Role] ro ON ro.id = ur.roleId"
                + " JOIN Series s ON s.id = ?"
                + " WHERE u.status = 'ACTIVE'"
                + "   AND ro.name = 'EDITORIAL_BOARD'"
                + "   AND u.id <> s.tantouEditorId";
        try ( PreparedStatement ps = conn.prepareStatement(notifySql)) {
            ps.setLong(1, sessionId);
            ps.setLong(2, sessionId);
            ps.setLong(3, seriesId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ //
    //  createSession — create new OPEN decision session                    //
    // ------------------------------------------------------------------ //
    public long createSession(long seriesId, long rankingRecordId, String systemSuggestion, String revenueTrendSnapshot, Long actorId) {
        try ( Connection conn = dataSource.getConnection()) {
            boolean hasSystemSuggestion = hasDecisionSessionSystemSuggestionColumn(conn);
            boolean hasRevenueTrendSnapshot = hasDecisionSessionRevenueTrendSnapshotColumn(conn);

            StringBuilder sqlBuilder = new StringBuilder("INSERT INTO DecisionSession (seriesId, rankingRecordId, status");
            List<Object> params = new ArrayList<>();
            params.add(seriesId);
            params.add(rankingRecordId);

            if (hasSystemSuggestion) {
                sqlBuilder.append(", systemSuggestion");
                params.add(systemSuggestion);
            }
            if (hasRevenueTrendSnapshot) {
                sqlBuilder.append(", revenueTrendSnapshot");
                params.add(revenueTrendSnapshot);
            }
            sqlBuilder.append(", openedAt) VALUES (?, ?, 'OPEN'");

            if (hasSystemSuggestion) {
                sqlBuilder.append(", ?");
            }
            if (hasRevenueTrendSnapshot) {
                sqlBuilder.append(", ?");
            }
            sqlBuilder.append(", GETDATE())");

            String sql = sqlBuilder.toString();
            conn.setAutoCommit(false);
            try ( PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int paramIndex = 1;
                for (Object param : params) {

                    if (param == null) {
                        ps.setNull(paramIndex++, java.sql.Types.VARCHAR);

                    } else if (param instanceof Long) {
                        ps.setLong(paramIndex++, (Long) param);

                    } else if (param instanceof String) {
                        ps.setString(paramIndex++, (String) param);

                    } else {
                        ps.setObject(paramIndex++, param);
                    }
                }

                ps.executeUpdate();
                try ( ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        long sessionId = rs.getLong(1);
                        notifyEligibleBoardMembers(conn, sessionId, seriesId);
                        // AuditLog records the creation of a decision session for later DB review.
                        auditLogRepository.insertLog(actorId, "DECISION_SESSION_OPENED", "DECISION", sessionId,
                                "Decision session " + sessionId + " opened for series " + seriesId);
                        conn.commit();
                        return sessionId;
                    }
                }
                throw new IllegalStateException("Cannot create decision session");
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            System.out.println("Creating decision session for seriesId = " + seriesId);
            throw new RuntimeException("Cannot create decision session", ex);
        }
    }

    private void notifyEligibleBoardMembers(Connection conn, long sessionId, long seriesId) throws SQLException {
        String sql
                = "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) "
                + "SELECT u.id, 'DECISION_SESSION_OPENED', 'Decision session opened', "
                + "'A new decision session is open for series #' + CAST(? AS VARCHAR(30)) + '.', "
                + "'/main/decisions/' + CAST(? AS VARCHAR(30)), ?, 'DECISION', 0, GETDATE() "
                + "FROM [User] u "
                + "JOIN UserRole ur ON ur.userId = u.id "
                + "JOIN [Role] r ON r.id = ur.roleId "
                + "JOIN Series s ON s.id = ? "
                + "WHERE u.status = 'ACTIVE' "
                + "AND r.name = 'EDITORIAL_BOARD' "
                + "AND u.id <> s.tantouEditorId";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, seriesId);
            ps.setLong(2, sessionId);
            ps.setLong(3, sessionId);
            ps.setLong(4, seriesId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ //
    //  finalizeSession — manually finalize session (for ADMIN)           //
    // ------------------------------------------------------------------ //
    // ------------------------------------------------------------------ //
    //  mapSession helper — không thay đổi                                 //
    // ------------------------------------------------------------------ //
    private Map<String, Object> mapSession(ResultSet rs, boolean hasSystemSuggestion, boolean hasRevenueTrendSnapshot) throws SQLException {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("id", rs.getLong("id"));
        row.put("seriesId", rs.getLong("seriesId"));
        row.put("rankingRecordId", rs.getLong("rankingRecordId"));
        row.put("status", rs.getString("status"));
        row.put("result", rs.getString("result"));
        row.put("systemSuggestion", hasSystemSuggestion ? rs.getString("systemSuggestion") : null);
        row.put("openedAt", rs.getTimestamp("openedAt"));
        row.put("closedAt", rs.getTimestamp("closedAt"));
        row.put("seriesTitle", rs.getString("seriesTitle"));
        if (hasRevenueTrendSnapshot) {
            row.put("revenueTrendSnapshot", rs.getString("revenueTrendSnapshot"));
        }
        return row;
    }

    private boolean hasDecisionSessionSystemSuggestionColumn(Connection conn) throws SQLException {
        String sql = "SELECT COL_LENGTH('DecisionSession', 'systemSuggestion')";
        try ( PreparedStatement ps = conn.prepareStatement(sql);  ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getObject(1) != null;
        }
    }

    private boolean hasDecisionSessionRevenueTrendSnapshotColumn(Connection conn) throws SQLException {
        String sql = "SELECT COL_LENGTH('DecisionSession', 'revenueTrendSnapshot')";
        try ( PreparedStatement ps = conn.prepareStatement(sql);  ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getObject(1) != null;
        }
    }
    
    
    public void runDecisionEngine(Connection conn, long periodId, AuthenticatedUser user) throws SQLException {
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
            createSession(seriesId, rankingRecordId, suggestion, revenueTrendJson, actorId);
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
