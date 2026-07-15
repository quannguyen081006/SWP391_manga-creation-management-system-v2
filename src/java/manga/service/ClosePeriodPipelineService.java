package manga.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import manga.common.exception.BusinessRuleException;
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
            mangakaRankingRepository.calculateSeriesRanking(conn, periodId);
        } catch (SQLException ex) {
            throw new RuntimeException("Database error in phase 2: series ranking", ex);
        }
    }

    @Transactional
    void phase3CalculateMangakaRanking(long periodId, AuthenticatedUser user) {
        try ( Connection conn = dataSource.getConnection()) {
            mangakaRankingRepository.calculateMangakaRanking(conn, periodId);
        } catch (SQLException ex) {
            throw new RuntimeException("Database error in phase 3: mangaka ranking", ex);
        }
    }

    @Transactional
    void phase4RunDecisionEngine(long periodId, AuthenticatedUser user) {
        try ( Connection conn = dataSource.getConnection()) {
            decisionRepository.runDecisionEngine(conn, periodId, user);
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
}
