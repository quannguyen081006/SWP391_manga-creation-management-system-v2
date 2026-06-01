package manga.repository;

import manga.common.exception.ForbiddenException;
import manga.model.AuthenticatedUser;
import manga.model.Proposal;
import manga.model.ProposalHistory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ProposalRepository {

    @Autowired
    private DataSource dataSource;

    private Boolean boardVotingSchemaReady;

    private static final String BOARD_REVOTE_NOTE = "Board vote tied across approve, revise, and reject. Revote requested.";
    private static final String BOARD_QUORUM_NOT_MET_NOTE = "Board voting round closed without the minimum 3 valid votes. A new round was opened.";
    private static final String BOARD_VOTE_ACTIONS = "'APPROVED','REVISE_REQUESTED','REJECTED'";
    private static final String CURRENT_BOARD_ROUND_ID =
            "(SELECT TOP 1 br.id FROM ProposalBoardRound br WHERE br.proposalId = p.id "
            + "AND br.submitAttemptNumber = p.submitAttemptCount ORDER BY br.roundNumber DESC, br.id DESC)";

    private static final String BASE_SELECT_COLUMNS =
            "p.id, p.mangakaId, p.title, p.genre, p.synopsis, p.sampleFilePath, p.originalFileName, "
            + "p.approximateChapter, p.status, p.submittedAt, p.rejectedAt, p.assignedEditorId, p.submitAttemptCount, ";

    private static final String BOARD_SELECT_COLUMNS =
            "(SELECT COUNT(1) FROM ProposalHistory h WHERE h.proposalId = p.id "
            + "AND h.actorRole = 'EDITORIAL_BOARD' AND h.boardRoundId = " + CURRENT_BOARD_ROUND_ID + " "
            + "AND h.actionType = 'APPROVED') AS boardApproveVotes, "
            + "(SELECT COUNT(1) FROM ProposalHistory h WHERE h.proposalId = p.id "
            + "AND h.actorRole = 'EDITORIAL_BOARD' AND h.boardRoundId = " + CURRENT_BOARD_ROUND_ID + " "
            + "AND h.actionType = 'REVISE_REQUESTED') AS boardReviseVotes, "
            + "(SELECT COUNT(1) FROM ProposalHistory h WHERE h.proposalId = p.id "
            + "AND h.actorRole = 'EDITORIAL_BOARD' AND h.boardRoundId = " + CURRENT_BOARD_ROUND_ID + " "
            + "AND h.actionType = 'REJECTED') AS boardRejectVotes, "
            + "(SELECT COUNT(1) FROM ProposalHistory h WHERE h.proposalId = p.id "
            + "AND h.actorRole = 'EDITORIAL_BOARD' AND h.boardRoundId = " + CURRENT_BOARD_ROUND_ID + " "
            + "AND h.actionType IN (" + BOARD_VOTE_ACTIONS + ")) AS boardTotalVotes, "
            + CURRENT_BOARD_ROUND_ID + " AS boardRoundId, "
            + "(SELECT TOP 1 br.roundNumber FROM ProposalBoardRound br WHERE br.proposalId = p.id "
            + "AND br.submitAttemptNumber = p.submitAttemptCount ORDER BY br.roundNumber DESC, br.id DESC) AS boardRoundNumber, "
            + "(SELECT TOP 1 br.status FROM ProposalBoardRound br WHERE br.proposalId = p.id "
            + "AND br.submitAttemptNumber = p.submitAttemptCount ORDER BY br.roundNumber DESC, br.id DESC) AS boardRoundStatus, "
            + "(SELECT TOP 1 br.openedAt FROM ProposalBoardRound br WHERE br.proposalId = p.id "
            + "AND br.submitAttemptNumber = p.submitAttemptCount ORDER BY br.roundNumber DESC, br.id DESC) AS boardVotingOpenedAt, "
            + "(SELECT TOP 1 br.closesAt FROM ProposalBoardRound br WHERE br.proposalId = p.id "
            + "AND br.submitAttemptNumber = p.submitAttemptCount ORDER BY br.roundNumber DESC, br.id DESC) AS boardVotingClosesAt, "
            + "(SELECT TOP 1 br.closedAt FROM ProposalBoardRound br WHERE br.proposalId = p.id "
            + "AND br.submitAttemptNumber = p.submitAttemptCount ORDER BY br.roundNumber DESC, br.id DESC) AS boardVotingClosedAt, "
            + "(SELECT COUNT(1) FROM ProposalBoardRoundVoter bv WHERE bv.roundId = " + CURRENT_BOARD_ROUND_ID + ") AS boardEligibleVoterCount ";

    private static final String LEGACY_BOARD_SELECT_COLUMNS =
            "0 AS boardApproveVotes, 0 AS boardReviseVotes, 0 AS boardRejectVotes, 0 AS boardTotalVotes, "
            + "CAST(NULL AS BIGINT) AS boardRoundId, CAST(NULL AS INT) AS boardRoundNumber, "
            + "CAST(NULL AS VARCHAR(20)) AS boardRoundStatus, CAST(NULL AS DATETIME) AS boardVotingOpenedAt, "
            + "CAST(NULL AS DATETIME) AS boardVotingClosesAt, CAST(NULL AS DATETIME) AS boardVotingClosedAt, "
            + "0 AS boardEligibleVoterCount ";

    public List<Proposal> findForMangaka(long mangakaId) {
        String sql = "SELECT " + selectColumns()
            + "FROM Proposal p WHERE p.mangakaId = ? ORDER BY p.createdAt DESC";
        return queryMany(sql, Long.valueOf(mangakaId));
    }

    public List<Proposal> findForBoardAndEditor() {
        String sql = "SELECT " + selectColumns()
            + "FROM Proposal p WHERE p.status IN ('UNDER_REVIEW','BOARD_REVIEW','REVISION_REQUESTED','APPROVED','REJECTED') "
            + "ORDER BY p.createdAt DESC";
        return queryMany(sql, null);
    }

    public List<Proposal> findForAssignedEditor(long editorId) {
        String sql = "SELECT " + selectColumns()
            + "FROM Proposal p "
            + "WHERE p.assignedEditorId = ? "
            + "AND p.status IN ('UNDER_REVIEW','BOARD_REVIEW','REVISION_REQUESTED','APPROVED','REJECTED') "
            + "ORDER BY p.createdAt DESC";
        return queryMany(sql, Long.valueOf(editorId));
    }

    public Proposal findById(long id) {
        String sql = "SELECT " + selectColumns() + "FROM Proposal p WHERE p.id = ?";
        List<Proposal> rows = queryMany(sql, Long.valueOf(id));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long createDraft(AuthenticatedUser actor, String title, String genre, String synopsis,
            String sampleFilePath, String originalFileName, int approximateChapter) {
        String sql =
            "INSERT INTO Proposal (mangakaId, title, genre, synopsis, sampleFilePath, originalFileName, approximateChapter, status, createdAt, updatedAt) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', GETDATE(), GETDATE())";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (hasActiveDraft(conn, actor.getId(), 0L)) {
                    throw new IllegalArgumentException("You already have an active draft proposal");
                }
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, actor.getId());
                ps.setString(2, title);
                ps.setString(3, genre);
                ps.setString(4, synopsis);
                ps.setString(5, sampleFilePath);
                ps.setString(6, originalFileName);
                ps.setInt(7, approximateChapter);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Cannot get generated proposal id");
                    }
                    long id = rs.getLong(1);
                    insertHistory(conn, id, actor, "CREATED", "Draft proposal created.", 0);
                    conn.commit();
                    return id;
                }
                }
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create proposal", ex);
        }
    }

    public void updateDraft(AuthenticatedUser actor, long proposalId, String title, String genre, String synopsis,
            String sampleFilePath, String originalFileName, int approximateChapter) {
        StringBuilder sql = new StringBuilder("UPDATE Proposal SET title = ?, genre = ?, synopsis = ?");
        if (sampleFilePath != null) {
            sql.append(", sampleFilePath = ?, originalFileName = ?");
        }
        sql.append(", approximateChapter = ?, updatedAt = GETDATE() ")
           .append("WHERE id = ? AND mangakaId = ? AND status IN ('DRAFT','REVISION_REQUESTED')");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int i = 1;
                ps.setString(i++, title);
                ps.setString(i++, genre);
                ps.setString(i++, synopsis);
                if (sampleFilePath != null) {
                    ps.setString(i++, sampleFilePath);
                    ps.setString(i++, originalFileName);
                }
                ps.setInt(i++, approximateChapter);
                ps.setLong(i++, proposalId);
                ps.setLong(i++, actor.getId());
                if (ps.executeUpdate() == 0) {
                    throw new IllegalArgumentException("Only editable proposal owner can update proposal");
                }
                Proposal p = findById(conn, proposalId);
                insertHistory(conn, proposalId, actor, "UPDATED", "Proposal content updated.", p.getSubmitAttemptCount());
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update proposal draft", ex);
        }
    }

    public void submitForTantouReview(AuthenticatedUser actor, long proposalId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Proposal p = lockProposal(conn, proposalId);
                if (p.getMangakaId() != actor.getId() || !isEditableStatus(p.getStatus())) {
                    throw new IllegalArgumentException("Only editable proposal owner can submit");
                }
                if (p.getSubmitAttemptCount() >= 2) {
                    throw new IllegalArgumentException("Proposal submit attempt limit reached");
                }
                long editorId = findLeastAssignedTantouEditor(conn);
                int nextAttempt = p.getSubmitAttemptCount() + 1;
                String action = p.getSubmitAttemptCount() == 0 ? "SUBMITTED" : "RESUBMITTED";
                String sql = isTantouReviewOverdueSchemaReady()
                    ? "UPDATE Proposal SET status = 'UNDER_REVIEW', submittedAt = GETDATE(), assignedEditorId = ?, "
                    + "submitAttemptCount = ?, tantouReviewOverdue = 0, updatedAt = GETDATE() WHERE id = ?"
                    : "UPDATE Proposal SET status = 'UNDER_REVIEW', submittedAt = GETDATE(), assignedEditorId = ?, "
                    + "submitAttemptCount = ?, updatedAt = GETDATE() WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, editorId);
                    ps.setInt(2, nextAttempt);
                    ps.setLong(3, proposalId);
                    ps.executeUpdate();
                }
                insertHistory(conn, proposalId, actor, action, "Submitted to Tantou Editor review.", nextAttempt);
                insertSystemHistory(conn, proposalId, "ASSIGNED_EDITOR", "Auto-assigned Tantou Editor #" + editorId + ".", nextAttempt);
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot submit proposal", ex);
        }
    }

    public void reviewByTantou(AuthenticatedUser actor, long proposalId, String decision, String note) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Proposal p = lockProposal(conn, proposalId);
                if (p.getAssignedEditorId() == null || p.getAssignedEditorId().longValue() != actor.getId()) {
                    throw new IllegalArgumentException("Only assigned Tantou Editor can review this proposal");
                }
                if (!"UNDER_REVIEW".equalsIgnoreCase(p.getStatus())) {
                    throw new IllegalArgumentException("Proposal is not under Tantou review");
                }
                if ("APPROVE".equals(decision)) {
                    updateProposalStatus(conn, proposalId, "BOARD_REVIEW", false);
                    insertHistory(conn, proposalId, actor, "APPROVED", note, p.getSubmitAttemptCount());
                    openBoardVotingRound(conn, proposalId, p.getSubmitAttemptCount());
                    notifyBoardReviewOpened(conn, p);
                } else if ("REJECT".equals(decision)) {
                    updateProposalStatus(conn, proposalId, "REJECTED", true);
                    insertHistory(conn, proposalId, actor, "REJECTED", note, p.getSubmitAttemptCount());
                } else if ("REVISE".equals(decision)) {
                    updateProposalStatus(conn, proposalId, "REVISION_REQUESTED", false);
                    insertHistory(conn, proposalId, actor, "REVISE_REQUESTED", note, p.getSubmitAttemptCount());
                } else {
                    throw new IllegalArgumentException("Review decision must be APPROVE, REJECT, or REVISE");
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot review proposal", ex);
        }
    }

    public boolean hasBoardVote(long proposalId, long actorId) {
        try (Connection conn = dataSource.getConnection()) {
            Proposal p = findById(conn, proposalId);
            if (p == null) {
                return false;
            }
            Long roundId = findCurrentBoardRoundId(conn, proposalId, p.getSubmitAttemptCount());
            return roundId != null && hasBoardVote(conn, roundId.longValue(), actorId);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check board vote", ex);
        }
    }

    public ProposalHistory findLatestBoardVote(long proposalId, long actorId) {
        try (Connection conn = dataSource.getConnection()) {
            Proposal p = findById(conn, proposalId);
            if (p == null) {
                return null;
            }
            Long roundId = findCurrentBoardRoundId(conn, proposalId, p.getSubmitAttemptCount());
            if (roundId == null) {
                return null;
            }
            String sql =
                "SELECT TOP 1 h.id, h.proposalId, h.actorId, u.fullName AS actorName, h.actorRole, h.actionType, "
                + "h.note, h.createdAt, h.submitAttemptNumber "
                + "FROM ProposalHistory h LEFT JOIN [User] u ON u.id = h.actorId "
                + "WHERE h.proposalId = ? AND h.boardRoundId = ? AND h.actorId = ? "
                + "AND h.actorRole = 'EDITORIAL_BOARD' AND h.submitAttemptNumber = ? "
                + "AND h.actionType IN (" + BOARD_VOTE_ACTIONS + ") ORDER BY h.id DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, proposalId);
                ps.setLong(2, roundId.longValue());
                ps.setLong(3, actorId);
                ps.setInt(4, p.getSubmitAttemptCount());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    ProposalHistory h = new ProposalHistory();
                    h.setId(rs.getLong("id"));
                    h.setProposalId(rs.getLong("proposalId"));
                    long historyActorId = rs.getLong("actorId");
                    h.setActorId(rs.wasNull() ? null : Long.valueOf(historyActorId));
                    h.setActorName(rs.getString("actorName"));
                    h.setActorRole(rs.getString("actorRole"));
                    h.setActionType(rs.getString("actionType"));
                    h.setNote(rs.getString("note"));
                    h.setCreatedAt(rs.getTimestamp("createdAt"));
                    h.setSubmitAttemptNumber(rs.getInt("submitAttemptNumber"));
                    return h;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load board vote", ex);
        }
    }

    public void undoBoardVote(AuthenticatedUser actor, long proposalId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Proposal p = lockProposal(conn, proposalId);
                if (!"BOARD_REVIEW".equalsIgnoreCase(p.getStatus())) {
                    throw new ForbiddenException("Undo window has expired. Vote is final.");
                }
                ProposalHistory vote = findLatestBoardVote(conn, proposalId, actor.getId(), p.getSubmitAttemptCount());
                if (vote == null) {
                    throw new IllegalArgumentException("No vote to undo");
                }
                long elapsedMs = System.currentTimeMillis() - vote.getCreatedAt().getTime();
                if (elapsedMs > 60_000L) {
                    throw new ForbiddenException("Undo window has expired. Vote is final.");
                }
                String deleteSql = "DELETE FROM ProposalHistory WHERE id = ? AND proposalId = ? AND actorId = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setLong(1, vote.getId());
                    ps.setLong(2, proposalId);
                    ps.setLong(3, actor.getId());
                    if (ps.executeUpdate() == 0) {
                        throw new IllegalArgumentException("No vote to undo");
                    }
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot undo board vote", ex);
        }
    }

    private ProposalHistory findLatestBoardVote(Connection conn, long proposalId, long actorId, int attempt)
            throws SQLException {
        Long roundId = findCurrentBoardRoundId(conn, proposalId, attempt);
        if (roundId == null) {
            return null;
        }
        String sql =
            "SELECT TOP 1 h.id, h.proposalId, h.actorId, u.fullName AS actorName, h.actorRole, h.actionType, "
            + "h.note, h.createdAt, h.submitAttemptNumber "
            + "FROM ProposalHistory h LEFT JOIN [User] u ON u.id = h.actorId "
            + "WHERE h.proposalId = ? AND h.boardRoundId = ? AND h.actorId = ? "
            + "AND h.actorRole = 'EDITORIAL_BOARD' AND h.submitAttemptNumber = ? "
            + "AND h.actionType IN (" + BOARD_VOTE_ACTIONS + ") ORDER BY h.id DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, proposalId);
            ps.setLong(2, roundId.longValue());
            ps.setLong(3, actorId);
            ps.setInt(4, attempt);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ProposalHistory h = new ProposalHistory();
                h.setId(rs.getLong("id"));
                h.setProposalId(rs.getLong("proposalId"));
                long historyActorId = rs.getLong("actorId");
                h.setActorId(rs.wasNull() ? null : Long.valueOf(historyActorId));
                h.setActorName(rs.getString("actorName"));
                h.setActorRole(rs.getString("actorRole"));
                h.setActionType(rs.getString("actionType"));
                h.setNote(rs.getString("note"));
                h.setCreatedAt(rs.getTimestamp("createdAt"));
                h.setSubmitAttemptNumber(rs.getInt("submitAttemptNumber"));
                return h;
            }
        }
    }

    public void voteByEditorialBoard(AuthenticatedUser actor, long proposalId, String decision, String note) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Proposal p = lockProposal(conn, proposalId);
                if (!"BOARD_REVIEW".equalsIgnoreCase(p.getStatus())) {
                    throw new IllegalArgumentException("Proposal is not waiting for Editorial Board review");
                }
                Long roundId = findOpenBoardRoundId(conn, proposalId, p.getSubmitAttemptCount());
                if (roundId == null) {
                    throw new IllegalArgumentException("Proposal does not have an open Editorial Board voting round");
                }
                if (isBoardRoundExpired(conn, roundId.longValue())) {
                    closeBoardRoundAndResolveOrReopen(conn, p, roundId.longValue(), "EXPIRED");
                    throw new IllegalArgumentException("The voting window expired. A new voting round has been opened.");
                }
                if (!isEligibleBoardVoter(conn, roundId.longValue(), actor.getId())) {
                    throw new IllegalArgumentException("You are not eligible to vote in this voting round");
                }
                if (hasBoardVote(conn, roundId.longValue(), actor.getId())) {
                    throw new IllegalArgumentException("You have already voted on this proposal");
                }

                String action = "APPROVE".equals(decision)
                        ? "APPROVED"
                        : ("REJECT".equals(decision) ? "REJECTED" : "REVISE_REQUESTED");
                insertHistory(conn, proposalId, actor, action, note, p.getSubmitAttemptCount(), roundId);
                if (allEligibleBoardMembersVoted(conn, roundId.longValue())) {
                    closeBoardRoundAndResolveOrReopen(conn, p, roundId.longValue(), "ALL_VOTED");
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot vote on proposal", ex);
        }
    }

    public void closeExpiredBoardVotingRounds() {
        String sql =
            "SELECT br.id, br.proposalId FROM ProposalBoardRound br "
            + "JOIN Proposal p ON p.id = br.proposalId "
            + "WHERE br.status = 'OPEN' AND br.closesAt <= GETDATE() AND p.status = 'BOARD_REVIEW'";
        List<Long> roundIds = new ArrayList<Long>();
        List<Long> proposalIds = new ArrayList<Long>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                roundIds.add(Long.valueOf(rs.getLong("id")));
                proposalIds.add(Long.valueOf(rs.getLong("proposalId")));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find expired board voting rounds", ex);
        }

        for (int i = 0; i < roundIds.size(); i++) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    Proposal p = lockProposal(conn, proposalIds.get(i).longValue());
                    if ("BOARD_REVIEW".equalsIgnoreCase(p.getStatus())) {
                        closeBoardRoundAndResolveOrReopen(conn, p, roundIds.get(i).longValue(), "EXPIRED");
                    }
                    conn.commit();
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Cannot close expired board voting round", ex);
            }
        }
    }

    public void notifyBoardVotingClosingSoon() {
        String sql =
            "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) "
            + "SELECT bv.voterId, 'PROPOSAL_BOARD_VOTE_CLOSING_SOON', 'Board vote closing soon', "
            + "'Proposal \"' + p.title + '\" board voting round #' + CAST(br.roundNumber AS VARCHAR(10)) "
            + "+ ' closes within 24 hours. Please cast your vote if you have not voted yet.', "
            + "'/main/proposals/' + CAST(p.id AS VARCHAR(20)) + '/vote', p.id, 'PROPOSAL', 0, GETDATE() "
            + "FROM ProposalBoardRound br "
            + "JOIN Proposal p ON p.id = br.proposalId "
            + "JOIN ProposalBoardRoundVoter bv ON bv.roundId = br.id "
            + "WHERE p.status = 'BOARD_REVIEW' AND br.status = 'OPEN' "
            + "AND br.closesAt > GETDATE() AND br.closesAt <= DATEADD(HOUR, 24, GETDATE()) "
            + "AND NOT EXISTS (SELECT 1 FROM ProposalHistory h WHERE h.boardRoundId = br.id "
            + "AND h.actorId = bv.voterId AND h.actorRole = 'EDITORIAL_BOARD' "
            + "AND h.actionType IN (" + BOARD_VOTE_ACTIONS + ")) "
            + "AND NOT EXISTS (SELECT 1 FROM Notification n WHERE n.userId = bv.voterId "
            + "AND n.type = 'PROPOSAL_BOARD_VOTE_CLOSING_SOON' AND n.referenceId = p.id "
            + "AND CAST(n.createdAt AS DATE) = CAST(GETDATE() AS DATE))";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot notify board voting closing soon", ex);
        }
    }

    public void markOverdueTantouReviews() {
        if (!isTantouReviewOverdueSchemaReady()) {
            return;
        }
        String sql =
            "UPDATE p SET tantouReviewOverdue = 1, updatedAt = GETDATE() "
            + "OUTPUT inserted.assignedEditorId, inserted.id, inserted.title "
            + "INTO #OverdueProposalReviewNotifications(editorId, proposalId, proposalTitle) "
            + "FROM Proposal p "
            + "WHERE p.status = 'UNDER_REVIEW' AND p.assignedEditorId IS NOT NULL "
            + "AND p.submittedAt <= DATEADD(HOUR, -48, GETDATE()) "
            + "AND ISNULL(p.tantouReviewOverdue, 0) = 0";
        String createTemp =
            "CREATE TABLE #OverdueProposalReviewNotifications (editorId BIGINT, proposalId BIGINT, proposalTitle VARCHAR(255))";
        String notifySql =
            "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) "
            + "SELECT editorId, 'PROPOSAL_TANTOU_REVIEW_OVERDUE', 'Proposal review overdue', "
            + "'Proposal \"' + proposalTitle + '\" has been waiting for Tantou review for more than 48 hours.', "
            + "'/main/proposals/' + CAST(proposalId AS VARCHAR(20)), proposalId, 'PROPOSAL', 0, GETDATE() "
            + "FROM #OverdueProposalReviewNotifications o "
            + "WHERE NOT EXISTS (SELECT 1 FROM Notification n WHERE n.userId = o.editorId "
            + "AND n.type = 'PROPOSAL_TANTOU_REVIEW_OVERDUE' AND n.referenceId = o.proposalId "
            + "AND CAST(n.createdAt AS DATE) = CAST(GETDATE() AS DATE))";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(createTemp);
            st.executeUpdate(sql);
            st.executeUpdate(notifySql);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot mark overdue Tantou proposal reviews", ex);
        }
    }

    public List<Map<String, Object>> listBoardRoundVoters(long proposalId) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (!isBoardVotingSchemaReady()) {
            return rows;
        }
        String sql =
            "SELECT u.id, u.fullName, "
            + "CASE WHEN h.actionType IS NOT NULL THEN 1 ELSE 0 END AS hasVoted, "
            + "h.actionType AS voteDecision, "
            + "h.note AS voteNote, "
            + "CASE WHEN p.assignedEditorId = u.id THEN 1 ELSE 0 END AS isConflict "
            + "FROM ProposalBoardRoundVoter bv "
            + "JOIN [User] u ON u.id = bv.voterId "
            + "JOIN Proposal p ON p.id = ? "
            + "JOIN ProposalBoardRound br ON br.id = bv.roundId "
            + "AND br.proposalId = p.id "
            + "AND br.submitAttemptNumber = p.submitAttemptCount "
            + "AND br.id = (SELECT TOP 1 br2.id FROM ProposalBoardRound br2 "
            + "WHERE br2.proposalId = p.id AND br2.submitAttemptNumber = p.submitAttemptCount "
            + "ORDER BY br2.roundNumber DESC, br2.id DESC) "
            + "LEFT JOIN ProposalHistory h ON h.boardRoundId = br.id "
            + "AND h.actorId = u.id AND h.actorRole = 'EDITORIAL_BOARD' "
            + "AND h.actionType IN ('APPROVED','REVISE_REQUESTED','REJECTED') "
            + "ORDER BY u.id";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, proposalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("userId", Long.valueOf(rs.getLong("id")));
                    row.put("fullName", rs.getString("fullName"));
                    row.put("hasVoted", Boolean.valueOf(rs.getInt("hasVoted") == 1));
                    row.put("voteDecision", rs.getString("voteDecision"));
                    row.put("voteNote", rs.getString("voteNote"));
                    row.put("isConflict", Boolean.valueOf(rs.getInt("isConflict") == 1));
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list board round voters", ex);
        }
        return rows;
    }

    public List<ProposalHistory> listHistory(long proposalId) {
        String sql =
            "SELECT h.id, h.proposalId, h.actorId, u.fullName AS actorName, h.actorRole, h.actionType, "
            + "h.note, h.createdAt, h.submitAttemptNumber "
            + "FROM ProposalHistory h LEFT JOIN [User] u ON u.id = h.actorId "
            + "WHERE h.proposalId = ? ORDER BY h.createdAt DESC, h.id DESC";
        List<ProposalHistory> rows = new ArrayList<ProposalHistory>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, proposalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProposalHistory h = new ProposalHistory();
                    h.setId(rs.getLong("id"));
                    h.setProposalId(rs.getLong("proposalId"));
                    long actorId = rs.getLong("actorId");
                    h.setActorId(rs.wasNull() ? null : Long.valueOf(actorId));
                    h.setActorName(rs.getString("actorName"));
                    h.setActorRole(rs.getString("actorRole"));
                    h.setActionType(rs.getString("actionType"));
                    h.setNote(rs.getString("note"));
                    h.setCreatedAt(rs.getTimestamp("createdAt"));
                    h.setSubmitAttemptNumber(rs.getInt("submitAttemptNumber"));
                    rows.add(h);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list proposal history", ex);
        }
        return rows;
    }

    public boolean hasActiveProposal(long mangakaId, long excludingProposalId) {
        String sql = "SELECT COUNT(1) FROM Proposal WHERE mangakaId = ? AND id <> ? AND status IN ('UNDER_REVIEW','BOARD_REVIEW','REVISION_REQUESTED')";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, mangakaId);
            ps.setLong(2, excludingProposalId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check active proposal", ex);
        }
    }

    public boolean hasActiveDraft(long mangakaId, long excludingProposalId) {
        try (Connection conn = dataSource.getConnection()) {
            return hasActiveDraft(conn, mangakaId, excludingProposalId);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check active draft proposal", ex);
        }
    }

    private boolean hasActiveDraft(Connection conn, long mangakaId, long excludingProposalId) throws SQLException {
        String sql = "SELECT COUNT(1) FROM Proposal WHERE mangakaId = ? AND id <> ? AND status = 'DRAFT'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, mangakaId);
            ps.setLong(2, excludingProposalId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public boolean canVoteInCurrentBoardRound(long proposalId, long actorId) {
        String sql =
            "SELECT COUNT(1) FROM Proposal p "
            + "JOIN ProposalBoardRound br ON br.proposalId = p.id AND br.submitAttemptNumber = p.submitAttemptCount "
            + "JOIN ProposalBoardRoundVoter bv ON bv.roundId = br.id AND bv.voterId = ? "
            + "WHERE p.id = ? AND p.status = 'BOARD_REVIEW' AND br.status = 'OPEN' AND br.closesAt > GETDATE() "
            + "AND br.id = (SELECT TOP 1 br2.id FROM ProposalBoardRound br2 WHERE br2.proposalId = p.id "
            + "AND br2.submitAttemptNumber = p.submitAttemptCount ORDER BY br2.roundNumber DESC, br2.id DESC) "
            + "AND NOT EXISTS (SELECT 1 FROM ProposalHistory h WHERE h.boardRoundId = br.id "
            + "AND h.actorId = ? AND h.actorRole = 'EDITORIAL_BOARD' AND h.actionType IN (" + BOARD_VOTE_ACTIONS + "))";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, actorId);
            ps.setLong(2, proposalId);
            ps.setLong(3, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check board voting eligibility", ex);
        }
    }

    private Proposal findById(Connection conn, long id) throws SQLException {
        String sql = "SELECT " + selectColumns() + "FROM Proposal p WHERE p.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapBaseProposal(rs) : null;
            }
        }
    }

    private Proposal lockProposal(Connection conn, long proposalId) throws SQLException {
        String sql = "SELECT " + selectColumns() + "FROM Proposal p WITH (UPDLOCK, ROWLOCK) WHERE p.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, proposalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Proposal not found");
                }
                return mapBaseProposal(rs);
            }
        }
    }

    private boolean isEditableStatus(String status) {
        return "DRAFT".equalsIgnoreCase(status) || "REVISION_REQUESTED".equalsIgnoreCase(status);
    }

    private long ensureSeriesExistsOnApprove(Connection conn, long proposalId) throws SQLException {
        String checkSeries = "SELECT COUNT(1) FROM Series WHERE proposalId = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSeries)) {
            ps.setLong(1, proposalId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    return findSeriesIdByProposal(conn, proposalId);
                }
            }
        }
        Proposal p = findById(conn, proposalId);
        long editorId = p.getAssignedEditorId() == null ? findLeastAssignedTantouEditor(conn) : p.getAssignedEditorId().longValue();
        String insertSeries =
            "INSERT INTO Series (proposalId, mangakaId, tantouEditorId, title, genre, status, createdAt) "
            + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', GETDATE())";
        try (PreparedStatement ps = conn.prepareStatement(insertSeries, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, proposalId);
            ps.setLong(2, p.getMangakaId());
            ps.setLong(3, editorId);
            ps.setString(4, p.getTitle());
            ps.setString(5, p.getGenre());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return findSeriesIdByProposal(conn, proposalId);
    }

    private long findSeriesIdByProposal(Connection conn, long proposalId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM Series WHERE proposalId = ?")) {
            ps.setLong(1, proposalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        throw new IllegalStateException("Cannot resolve approved series");
    }

    private Long findOpenBoardRoundId(Connection conn, long proposalId, int attempt) throws SQLException {
        String sql =
            "SELECT TOP 1 id FROM ProposalBoardRound "
            + "WHERE proposalId = ? AND submitAttemptNumber = ? AND status = 'OPEN' "
            + "ORDER BY roundNumber DESC, id DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, proposalId);
            ps.setInt(2, attempt);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Long.valueOf(rs.getLong("id")) : null;
            }
        }
    }

    private Long findCurrentBoardRoundId(Connection conn, long proposalId, int attempt) throws SQLException {
        String sql =
            "SELECT TOP 1 id FROM ProposalBoardRound "
            + "WHERE proposalId = ? AND submitAttemptNumber = ? "
            + "ORDER BY roundNumber DESC, id DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, proposalId);
            ps.setInt(2, attempt);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Long.valueOf(rs.getLong("id")) : null;
            }
        }
    }

    private boolean isBoardRoundExpired(Connection conn, long roundId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(1) FROM ProposalBoardRound WHERE id = ? AND status = 'OPEN' AND closesAt <= GETDATE()")) {
            ps.setLong(1, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean isEligibleBoardVoter(Connection conn, long roundId, long voterId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(1) FROM ProposalBoardRoundVoter WHERE roundId = ? AND voterId = ?")) {
            ps.setLong(1, roundId);
            ps.setLong(2, voterId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean hasBoardVote(Connection conn, long roundId, long actorId) throws SQLException {
        String sql =
            "SELECT COUNT(1) FROM ProposalHistory "
            + "WHERE boardRoundId = ? AND actorId = ? AND actorRole = 'EDITORIAL_BOARD' "
            + "AND actionType IN (" + BOARD_VOTE_ACTIONS + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, roundId);
            ps.setLong(2, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean allEligibleBoardMembersVoted(Connection conn, long roundId) throws SQLException {
        String sql =
            "SELECT "
            + "(SELECT COUNT(1) FROM ProposalBoardRoundVoter WHERE roundId = ?) AS eligibleCount, "
            + "(SELECT COUNT(1) FROM ProposalHistory WHERE boardRoundId = ? AND actorRole = 'EDITORIAL_BOARD' "
            + "AND actionType IN (" + BOARD_VOTE_ACTIONS + ")) AS voteCount";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, roundId);
            ps.setLong(2, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int eligibleCount = rs.getInt("eligibleCount");
                int voteCount = rs.getInt("voteCount");
                return eligibleCount > 0 && voteCount >= eligibleCount;
            }
        }
    }

    private long openBoardVotingRound(Connection conn, long proposalId, int attempt) throws SQLException {
        int roundNumber = 1;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ISNULL(MAX(roundNumber), 0) + 1 FROM ProposalBoardRound WHERE proposalId = ? AND submitAttemptNumber = ?")) {
            ps.setLong(1, proposalId);
            ps.setInt(2, attempt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                roundNumber = rs.getInt(1);
            }
        }

        long roundId;
        String insertRound =
            "INSERT INTO ProposalBoardRound (proposalId, submitAttemptNumber, roundNumber, status, openedAt, closesAt) "
            + "VALUES (?, ?, ?, 'OPEN', GETDATE(), DATEADD(DAY, 3, GETDATE()))";
        try (PreparedStatement ps = conn.prepareStatement(insertRound, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, proposalId);
            ps.setInt(2, attempt);
            ps.setInt(3, roundNumber);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Cannot create board voting round");
                }
                roundId = rs.getLong(1);
            }
        }

        String insertVoters =
            "INSERT INTO ProposalBoardRoundVoter (roundId, voterId) "
            + "SELECT ?, u.id FROM [User] u "
            + "JOIN UserRole ur ON ur.userId = u.id "
            + "JOIN Role r ON r.id = ur.roleId "
            + "WHERE u.status = 'ACTIVE' AND r.name = 'EDITORIAL_BOARD'";
        try (PreparedStatement ps = conn.prepareStatement(insertVoters)) {
            ps.setLong(1, roundId);
            ps.executeUpdate();
        }

        insertSystemHistory(conn, proposalId, "UPDATED",
                "Editorial Board voting round #" + roundNumber + " opened for 3 days. Minimum quorum is 3 valid votes.",
                attempt);
        return roundId;
    }

    private void closeBoardRoundAndResolveOrReopen(Connection conn, Proposal proposal, long roundId, String closeReason) throws SQLException {
        String closeSql =
            "UPDATE ProposalBoardRound SET status = 'CLOSED', closedAt = GETDATE(), closeReason = ? "
            + "WHERE id = ? AND status = 'OPEN'";
        try (PreparedStatement ps = conn.prepareStatement(closeSql)) {
            ps.setString(1, closeReason);
            ps.setLong(2, roundId);
            ps.executeUpdate();
        }
        resolveClosedBoardRound(conn, proposal, roundId);
    }

    private void resolveClosedBoardRound(Connection conn, Proposal proposal, long roundId) throws SQLException {
        String countSql =
            "SELECT "
            + "SUM(CASE WHEN actionType = 'APPROVED' THEN 1 ELSE 0 END) AS approveVotes, "
            + "SUM(CASE WHEN actionType = 'REVISE_REQUESTED' THEN 1 ELSE 0 END) AS reviseVotes, "
            + "SUM(CASE WHEN actionType = 'REJECTED' THEN 1 ELSE 0 END) AS rejectVotes, "
            + "COUNT(1) AS totalVotes "
            + "FROM ProposalHistory "
            + "WHERE boardRoundId = ? AND actorRole = 'EDITORIAL_BOARD' "
            + "AND actionType IN (" + BOARD_VOTE_ACTIONS + ")";
        int approveVotes;
        int reviseVotes;
        int rejectVotes;
        int totalVotes;
        try (PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setLong(1, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                approveVotes = rs.getInt("approveVotes");
                reviseVotes = rs.getInt("reviseVotes");
                rejectVotes = rs.getInt("rejectVotes");
                totalVotes = rs.getInt("totalVotes");
            }
        }
        if (totalVotes < 3) {
            insertSystemHistory(conn, proposal.getId(), "UPDATED", BOARD_QUORUM_NOT_MET_NOTE, proposal.getSubmitAttemptCount());
            openBoardVotingRound(conn, proposal.getId(), proposal.getSubmitAttemptCount());
            return;
        }

        if (approveVotes == 1 && reviseVotes == 1 && rejectVotes == 1) {
            insertSystemHistory(conn, proposal.getId(), "UPDATED", BOARD_REVOTE_NOTE, proposal.getSubmitAttemptCount());
            openBoardVotingRound(conn, proposal.getId(), proposal.getSubmitAttemptCount());
            return;
        }

        if (approveVotes > reviseVotes && approveVotes > rejectVotes) {
            long seriesId = ensureSeriesExistsOnApprove(conn, proposal.getId());
            updateProposalStatus(conn, proposal.getId(), "APPROVED", false);
            insertSystemHistory(conn, proposal.getId(), "APPROVED", "Editorial Board approved publication.", proposal.getSubmitAttemptCount());
            notifyProposalApproved(conn, proposal, seriesId);
        } else if (reviseVotes > approveVotes && reviseVotes > rejectVotes) {
            updateProposalStatus(conn, proposal.getId(), "REVISION_REQUESTED", false);
            insertSystemHistory(conn, proposal.getId(), "REVISE_REQUESTED", "Editorial Board requested revisions before publication.", proposal.getSubmitAttemptCount());
            notifyProposalRevisionRequested(conn, proposal);
        } else if (rejectVotes > approveVotes && rejectVotes > reviseVotes) {
            updateProposalStatus(conn, proposal.getId(), "REJECTED", true);
            insertSystemHistory(conn, proposal.getId(), "REJECTED", "Editorial Board rejected publication.", proposal.getSubmitAttemptCount());
        } else {
            insertSystemHistory(conn, proposal.getId(), "UPDATED", BOARD_REVOTE_NOTE, proposal.getSubmitAttemptCount());
            openBoardVotingRound(conn, proposal.getId(), proposal.getSubmitAttemptCount());
        }
    }

    private void notifyProposalApproved(Connection conn, Proposal proposal, long seriesId) throws SQLException {
        String sql =
            "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) "
            + "VALUES (?, 'PROPOSAL_APPROVED_SERIES_CREATED', 'Series created', ?, ?, ?, 'SERIES', 0, GETDATE())";
        String message = "Proposal \"" + proposal.getTitle() + "\" was approved and series #" + seriesId + " was created.";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, proposal.getMangakaId());
            ps.setString(2, message);
            ps.setString(3, "/main/series/" + seriesId);
            ps.setLong(4, seriesId);
            ps.executeUpdate();
        }
        if (proposal.getAssignedEditorId() != null) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, proposal.getAssignedEditorId().longValue());
                ps.setString(2, message);
                ps.setString(3, "/main/series/" + seriesId);
                ps.setLong(4, seriesId);
                ps.executeUpdate();
            }
        }
    }

    private void notifyBoardReviewOpened(Connection conn, Proposal proposal) throws SQLException {
        String sql =
            "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) "
            + "SELECT u.id, 'PROPOSAL_BOARD_REVIEW_OPENED', 'Proposal ready for board review', "
            + "?, ?, ?, 'PROPOSAL', 0, GETDATE() "
            + "FROM [User] u "
            + "JOIN UserRole ur ON ur.userId = u.id "
            + "JOIN [Role] r ON r.id = ur.roleId "
            + "WHERE u.status = 'ACTIVE' AND r.name = 'EDITORIAL_BOARD'";
        String message = "Proposal \"" + proposal.getTitle() + "\" passed Tantou review and is ready for Editorial Board vote.";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, message);
            ps.setString(2, "/main/proposals/" + proposal.getId());
            ps.setLong(3, proposal.getId());
            ps.executeUpdate();
        }
    }

    private void notifyProposalRevisionRequested(Connection conn, Proposal proposal) throws SQLException {
        String sql =
            "INSERT INTO Notification (userId, type, title, message, viewUrl, referenceId, referenceType, isRead, createdAt) "
            + "VALUES (?, 'PROPOSAL_BOARD_REVISION_REQUESTED', 'Revision requested', ?, ?, ?, 'PROPOSAL', 0, GETDATE())";
        String message = "Editorial Board requested revisions for proposal \"" + proposal.getTitle() + "\".";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, proposal.getMangakaId());
            ps.setString(2, message);
            ps.setString(3, "/main/proposals/" + proposal.getId() + "/edit");
            ps.setLong(4, proposal.getId());
            ps.executeUpdate();
        }
    }

    private long findLeastAssignedTantouEditor(Connection conn) throws SQLException {
        String sql =
            "SELECT TOP 1 u.id "
            + "FROM [User] u JOIN UserRole ur ON ur.userId = u.id JOIN [Role] r ON r.id = ur.roleId "
            + "LEFT JOIN Proposal p ON p.assignedEditorId = u.id AND p.status IN ('UNDER_REVIEW','BOARD_REVIEW','REVISION_REQUESTED') "
            + "WHERE r.name = 'TANTOU_EDITOR' AND u.status = 'ACTIVE' "
            + "GROUP BY u.id ORDER BY COUNT(p.id), u.id";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new IllegalStateException("No active Tantou Editor available for assignment");
    }

    private void updateProposalStatus(Connection conn, long proposalId, String status, boolean setRejectedAt) throws SQLException {
        String sql = setRejectedAt
            ? "UPDATE Proposal SET status = ?, rejectedAt = GETDATE(), updatedAt = GETDATE() WHERE id = ?"
            : "UPDATE Proposal SET status = ?, updatedAt = GETDATE() WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, proposalId);
            ps.executeUpdate();
        }
    }

    private void insertHistory(Connection conn, long proposalId, AuthenticatedUser actor, String actionType, String note, int attempt) throws SQLException {
        String role = actor.getRoles().isEmpty() ? "USER" : actor.getRoles().iterator().next();
        insertHistory(conn, proposalId, Long.valueOf(actor.getId()), role, actionType, note, attempt);
    }

    private void insertHistory(Connection conn, long proposalId, AuthenticatedUser actor, String actionType, String note, int attempt, Long boardRoundId) throws SQLException {
        String role = actor.getRoles().isEmpty() ? "USER" : actor.getRoles().iterator().next();
        insertHistory(conn, proposalId, Long.valueOf(actor.getId()), role, actionType, note, attempt, boardRoundId);
    }

    private void insertSystemHistory(Connection conn, long proposalId, String actionType, String note, int attempt) throws SQLException {
        insertHistory(conn, proposalId, null, "SYSTEM", actionType, note, attempt);
    }

    private void insertHistory(Connection conn, long proposalId, Long actorId, String actorRole, String actionType, String note, int attempt) throws SQLException {
        insertHistory(conn, proposalId, actorId, actorRole, actionType, note, attempt, null);
    }

    private void insertHistory(Connection conn, long proposalId, Long actorId, String actorRole, String actionType, String note, int attempt, Long boardRoundId) throws SQLException {
        String sql =
            "INSERT INTO ProposalHistory (proposalId, actorId, actorRole, actionType, note, submitAttemptNumber, boardRoundId, createdAt) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, GETDATE())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, proposalId);
            if (actorId == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, actorId.longValue());
            }
            ps.setString(3, actorRole);
            ps.setString(4, actionType);
            ps.setString(5, note);
            ps.setInt(6, attempt);
            if (boardRoundId == null) {
                ps.setNull(7, java.sql.Types.BIGINT);
            } else {
                ps.setLong(7, boardRoundId.longValue());
            }
            ps.executeUpdate();
        }
    }

    private List<Proposal> queryMany(String sql, Long param) {
        List<Proposal> rows = new ArrayList<Proposal>();
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) {
                ps.setLong(1, param.longValue());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapBaseProposal(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot query proposals", ex);
        }
        return rows;
    }

    private String selectColumns() {
        return BASE_SELECT_COLUMNS + (isBoardVotingSchemaReady() ? BOARD_SELECT_COLUMNS : LEGACY_BOARD_SELECT_COLUMNS);
    }

    private boolean isBoardVotingSchemaReady() {
        if (boardVotingSchemaReady != null) {
            return boardVotingSchemaReady.booleanValue();
        }
        String sql =
            "SELECT CASE WHEN OBJECT_ID('dbo.ProposalBoardRound', 'U') IS NOT NULL "
            + "AND OBJECT_ID('dbo.ProposalBoardRoundVoter', 'U') IS NOT NULL "
            + "AND COL_LENGTH('dbo.ProposalHistory', 'boardRoundId') IS NOT NULL "
            + "THEN 1 ELSE 0 END";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            boardVotingSchemaReady = Boolean.valueOf(rs.next() && rs.getInt(1) == 1);
            return boardVotingSchemaReady.booleanValue();
        } catch (SQLException ex) {
            boardVotingSchemaReady = Boolean.FALSE;
            return false;
        }
    }

    private Proposal mapBaseProposal(ResultSet rs) throws SQLException {
        Proposal p = new Proposal();
        p.setId(rs.getLong("id"));
        p.setMangakaId(rs.getLong("mangakaId"));
        p.setTitle(rs.getString("title"));
        p.setGenre(rs.getString("genre"));
        p.setSynopsis(rs.getString("synopsis"));
        p.setSampleFilePath(rs.getString("sampleFilePath"));
        p.setOriginalFileName(rs.getString("originalFileName"));
        int chapters = rs.getInt("approximateChapter");
        p.setApproximateChapter(rs.wasNull() ? null : Integer.valueOf(chapters));
        p.setStatus(rs.getString("status"));
        p.setSubmittedAt(rs.getTimestamp("submittedAt"));
        p.setRejectedAt(rs.getTimestamp("rejectedAt"));
        long editorId = rs.getLong("assignedEditorId");
        p.setAssignedEditorId(rs.wasNull() ? null : Long.valueOf(editorId));
        p.setSubmitAttemptCount(rs.getInt("submitAttemptCount"));
        p.setBoardApproveVotes(readOptionalInt(rs, "boardApproveVotes"));
        p.setBoardReviseVotes(readOptionalInt(rs, "boardReviseVotes"));
        p.setBoardRejectVotes(readOptionalInt(rs, "boardRejectVotes"));
        p.setBoardTotalVotes(readOptionalInt(rs, "boardTotalVotes"));
        p.setBoardRoundId(readOptionalLong(rs, "boardRoundId"));
        p.setBoardRoundNumber(readOptionalInteger(rs, "boardRoundNumber"));
        p.setBoardRoundStatus(readOptionalString(rs, "boardRoundStatus"));
        p.setBoardVotingOpenedAt(readOptionalTimestamp(rs, "boardVotingOpenedAt"));
        p.setBoardVotingClosesAt(readOptionalTimestamp(rs, "boardVotingClosesAt"));
        p.setBoardVotingClosedAt(readOptionalTimestamp(rs, "boardVotingClosedAt"));
        p.setBoardEligibleVoterCount(readOptionalInt(rs, "boardEligibleVoterCount"));
        return p;
    }

    private int readOptionalInt(ResultSet rs, String columnName) throws SQLException {
        try {
            return rs.getInt(columnName);
        } catch (SQLException ex) {
            return 0;
        }
    }

    private Integer readOptionalInteger(ResultSet rs, String columnName) throws SQLException {
        try {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : Integer.valueOf(value);
        } catch (SQLException ex) {
            return null;
        }
    }

    private Long readOptionalLong(ResultSet rs, String columnName) throws SQLException {
        try {
            long value = rs.getLong(columnName);
            return rs.wasNull() ? null : Long.valueOf(value);
        } catch (SQLException ex) {
            return null;
        }
    }

    private String readOptionalString(ResultSet rs, String columnName) throws SQLException {
        try {
            return rs.getString(columnName);
        } catch (SQLException ex) {
            return null;
        }
    }

    private Timestamp readOptionalTimestamp(ResultSet rs, String columnName) throws SQLException {
        try {
            return rs.getTimestamp(columnName);
        } catch (SQLException ex) {
            return null;
        }
    }

    private boolean isTantouReviewOverdueSchemaReady() {
        String sql = "SELECT CASE WHEN COL_LENGTH('dbo.Proposal', 'tantouReviewOverdue') IS NOT NULL THEN 1 ELSE 0 END";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (SQLException ex) {
            return false;
        }
    }
}
