package manga.repository;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class DecisionRepository {

    @Autowired
    private DataSource dataSource;

    // ------------------------------------------------------------------ //
    //  listSessions — không thay đổi                                      //
    // ------------------------------------------------------------------ //
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (Connection conn = dataSource.getConnection()) {
            boolean hasSystemSuggestion = hasDecisionSessionSystemSuggestionColumn(conn);
            String sql = "SELECT ds.id, ds.seriesId, ds.rankingRecordId, ds.status, ds.result"
                       + (hasSystemSuggestion ? ", ds.systemSuggestion" : "")
                       + ", ds.openedAt, ds.closedAt, s.title AS seriesTitle"
                       + " FROM DecisionSession ds"
                       + " JOIN Series s ON s.id = ds.seriesId"
                       + " ORDER BY ds.openedAt DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
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
        String votesSql   = "SELECT id, sessionId, voterId, decision, justification, votedAt"
                          + " FROM DecisionVote WHERE sessionId = ? ORDER BY votedAt DESC";

        try (Connection conn = dataSource.getConnection()) {
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
            try (PreparedStatement ps = conn.prepareStatement(sessionSql)) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Decision session not found");
                    }
                    session = mapSession(rs, hasSystemSuggestion, hasRevenueTrendSnapshot);
                }
            }

            List<Map<String, Object>> votes = new ArrayList<Map<String, Object>>();
            try (PreparedStatement ps = conn.prepareStatement(votesSql)) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> vote = new HashMap<String, Object>();
                        vote.put("id",            rs.getLong("id"));
                        vote.put("sessionId",     rs.getLong("sessionId"));
                        vote.put("voterId",       rs.getLong("voterId"));
                        vote.put("decision",      rs.getString("decision"));
                        vote.put("justification", rs.getString("justification"));
                        vote.put("votedAt",       rs.getTimestamp("votedAt"));
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

        String sessionSql  = "SELECT status, seriesId FROM DecisionSession WHERE id = ?";
        String conflictSql = "SELECT tantouEditorId FROM Series WHERE id = ?";
        String dupSql      = "SELECT COUNT(1) FROM DecisionVote WHERE sessionId = ? AND voterId = ?";
        String insertSql   = "INSERT INTO DecisionVote (sessionId, voterId, decision, justification, votedAt)"
                           + " VALUES (?, ?, ?, ?, GETDATE())";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {

                // Check 2: Session phải tồn tại và còn OPEN (BR-64)
                long seriesId;
                try (PreparedStatement ps = conn.prepareStatement(sessionSql)) {
                    ps.setLong(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
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

                // Check 3: Conflict of interest — Tantou Editor của series không được vote (BR-60)
                try (PreparedStatement ps = conn.prepareStatement(conflictSql)) {
                    ps.setLong(1, seriesId);
                    try (ResultSet rs = ps.executeQuery()) {
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
                try (PreparedStatement ps = conn.prepareStatement(dupSql)) {
                    ps.setLong(1, sessionId);
                    ps.setLong(2, voterId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            throw new IllegalArgumentException(
                                "You have already voted in this decision session (BR-61)");
                        }
                    }
                }

                // Step 5: Insert vote
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setLong(1, sessionId);
                    ps.setLong(2, voterId);
                    ps.setString(3, normalized);
                    ps.setString(4, justification == null ? null : justification.trim());
                    ps.executeUpdate();
                }

                // Step 6: Kiểm tra quorum và finalize nếu đủ (BR-62)
                resolveIfQuorum(conn, sessionId, seriesId);

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
    private void resolveIfQuorum(Connection conn, long sessionId, long seriesId) throws SQLException {

        String countSql =
            "SELECT"
            + " SUM(CASE WHEN decision = 'CONTINUE'    THEN 1 ELSE 0 END) AS continueVotes,"
            + " SUM(CASE WHEN decision = 'CANCEL'      THEN 1 ELSE 0 END) AS cancelVotes,"
            + " SUM(CASE WHEN decision = 'CHANGE_TYPE' THEN 1 ELSE 0 END) AS changeVotes,"
            + " COUNT(*) AS totalVotes"
            + " FROM DecisionVote WHERE sessionId = ?";

        int continueV, cancelV, changeV, total;
        try (PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                continueV = rs.getInt("continueVotes");
                cancelV   = rs.getInt("cancelVotes");
                changeV   = rs.getInt("changeVotes");
                total     = rs.getInt("totalVotes");
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
        String closeSessionSql =
            "UPDATE DecisionSession SET status = 'CLOSED', result = ?, closedAt = GETDATE() WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(closeSessionSql)) {
            ps.setString(1, result);
            ps.setLong(2, sessionId);
            ps.executeUpdate();
        }

        // Nếu CANCEL → update Series.status = CANCELLED (BR-69)
        if ("CANCEL".equals(result)) {
            String cancelSeriesSql = "UPDATE Series SET status = 'CANCELLED' WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(cancelSeriesSql)) {
                ps.setLong(1, seriesId);
                ps.executeUpdate();
            }
        }

        // Gửi DECISION_RESOLVED notification cho tất cả Board members active (BR-65)
        String notifySql =
            "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt)"
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
            + " WHERE u.status = 'ACTIVE'"
            + "   AND ro.name = 'EDITORIAL_BOARD'";
        try (PreparedStatement ps = conn.prepareStatement(notifySql)) {
            ps.setLong(1, sessionId);
            ps.setLong(2, sessionId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ //
    //  createSession — create new OPEN decision session                    //
    // ------------------------------------------------------------------ //
    public long createSession(long seriesId, long rankingRecordId, String systemSuggestion, String revenueTrendSnapshot) {
        try (Connection conn = dataSource.getConnection()) {
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
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int paramIndex = 1;
                for (Object param : params) {
                    if (param instanceof Long) {
                        ps.setLong(paramIndex++, (Long) param);
                    } else if (param instanceof String) {
                        ps.setString(paramIndex++, (String) param);
                    }
                }
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        long sessionId = rs.getLong(1);
                        notifyEligibleBoardMembers(conn, sessionId, seriesId);
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
            throw new RuntimeException("Cannot create decision session", ex);
        }
    }

    private void notifyEligibleBoardMembers(Connection conn, long sessionId, long seriesId) throws SQLException {
        String sql =
            "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) "
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
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
    public void finalizeSession(long sessionId) {
        // This method is called by DecisionService.finalizeDecision()
        // The actual finalization logic is in resolveIfQuorum()
        // This is a placeholder for manual finalization if needed
        String sql = "UPDATE DecisionSession SET status = 'CLOSED', closedAt = GETDATE() WHERE id = ? AND status = 'OPEN'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Only OPEN session can be finalized");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot finalize decision session", ex);
        }
    }

    // ------------------------------------------------------------------ //
    //  mapSession helper — không thay đổi                                 //
    // ------------------------------------------------------------------ //
    private Map<String, Object> mapSession(ResultSet rs, boolean hasSystemSuggestion, boolean hasRevenueTrendSnapshot) throws SQLException {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("id",              rs.getLong("id"));
        row.put("seriesId",        rs.getLong("seriesId"));
        row.put("rankingRecordId", rs.getLong("rankingRecordId"));
        row.put("status",          rs.getString("status"));
        row.put("result",          rs.getString("result"));
        row.put("systemSuggestion", hasSystemSuggestion ? rs.getString("systemSuggestion") : null);
        row.put("openedAt",        rs.getTimestamp("openedAt"));
        row.put("closedAt",        rs.getTimestamp("closedAt"));
        row.put("seriesTitle",     rs.getString("seriesTitle"));
        if (hasRevenueTrendSnapshot) {
            row.put("revenueTrendSnapshot", rs.getString("revenueTrendSnapshot"));
        }
        return row;
    }

    private boolean hasDecisionSessionSystemSuggestionColumn(Connection conn) throws SQLException {
        String sql = "SELECT COL_LENGTH('DecisionSession', 'systemSuggestion')";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getObject(1) != null;
        }
    }

    private boolean hasDecisionSessionRevenueTrendSnapshotColumn(Connection conn) throws SQLException {
        String sql = "SELECT COL_LENGTH('DecisionSession', 'revenueTrendSnapshot')";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getObject(1) != null;
        }
    }
}
