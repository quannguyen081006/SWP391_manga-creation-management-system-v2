package manga.service;

import java.util.ArrayList;
import manga.common.exception.BusinessRuleException;
import manga.dto.OpenDecisionSessionRequest;
import manga.dto.SubmitDecisionVoteRequest;
import manga.enums.DecisionResult;
import manga.model.AuthenticatedUser;
import manga.model.SeriesSummary;
import manga.repository.DecisionRepository;
import manga.repository.ProductionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import manga.repository.RankingRepository;
import manga.repository.UserRepository;

@Service
public class DecisionService {

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductionRepository productionRepository;

    @Autowired
    private RankingRepository rankingRepository;

    public static final String board = "EDITORIAL_BOARD";

    public List<Map<String, Object>> listDecisionSessions(AuthenticatedUser user) {
        // Only ADMIN and EDITORIAL_BOARD can view decisions
        if (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD")) {
            throw new BusinessRuleException("Only ADMIN/EDITORIAL_BOARD can view decisions");
        }
        return decisionRepository.listSessions();
    }

    public List<Map<String, Object>> listDecisionSessionsGroupedBySeries(AuthenticatedUser user) {
        // Only ADMIN and EDITORIAL_BOARD can view decisions
        if (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD")) {
            throw new BusinessRuleException("Only ADMIN/EDITORIAL_BOARD can view decisions");
        }
        return decisionRepository.listSessionsGroupedBySeries();
    }

    public Map<String, Object> getDecisionSession(long sessionId, AuthenticatedUser user) {
        // Only ADMIN and EDITORIAL_BOARD can view decision detail
        if (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD")) {
            throw new BusinessRuleException("Only ADMIN/EDITORIAL_BOARD can view decision detail");
        }
        return decisionRepository.getSessionDetail(sessionId);
    }

    public long openDecisionSession(OpenDecisionSessionRequest request, AuthenticatedUser user) {
        // ADMIN only
        if (!user.hasRole("ADMIN")) {
            throw new BusinessRuleException("Only ADMIN can open decision session");
        }

        // Validate series exists
        List<SeriesSummary> seriesList = productionRepository.listSeries();
        SeriesSummary series = null;
        for (SeriesSummary s : seriesList) {
            if (s.getId() == request.getSeriesId()) {
                series = s;
                break;
            }
        }
        if (series == null) {
            throw new BusinessRuleException("Series not found");
        }

        // Validate series not CANCELLED (BR-66)
        String seriesStatus = series.getStatus();
        if ("CANCELLED".equals(seriesStatus)) {
            throw new BusinessRuleException("Cancelled series cannot re-enter review session (BR-66)");
        }

        // BR-DEC-NEW-04: Validate no duplicate session for same ranking record
        if (decisionRepository.hasExistingSessionForRankingRecord(request.getRankingRecordId())) {
            throw new BusinessRuleException("Decision Session already exists for this ranking record (BR-DEC-NEW-04)");
        }

        // Create session (repository handles creation)
        // Note: systemSuggestion is null here since this is manual session creation
        // The pipeline auto-generates suggestions for bottom 20% series
        // revenueTrendSnapshot is null for manual sessions
        long sessionId = decisionRepository.createSession(request.getSeriesId(), request.getRankingRecordId(), null, null, user.getId());

        return sessionId;
    }

    public long openDecisionSessionFromRankingRecord(long rankingRecordId, long seriesId, AuthenticatedUser user) {
        // ADMIN only
        if (!user.hasRole("ADMIN")) {
            throw new BusinessRuleException("Only ADMIN can open decision session");
        }

        // Validate series exists
        List<SeriesSummary> seriesList = productionRepository.listSeries();
        SeriesSummary series = null;
        for (SeriesSummary s : seriesList) {
            if (s.getId() == seriesId) {
                series = s;
                break;
            }
        }
        if (series == null) {
            throw new BusinessRuleException("Series not found");
        }

        // Validate series not CANCELLED (BR-66)
        String seriesStatus = series.getStatus();
        if ("CANCELLED".equals(seriesStatus)) {
            throw new BusinessRuleException("Cancelled series cannot re-enter review session (BR-66)");
        }

        // BR-DEC-NEW-04: Validate no duplicate session for same ranking record
        if (decisionRepository.hasExistingSessionForRankingRecord(rankingRecordId)) {
            throw new BusinessRuleException("Decision Session already exists for this ranking record (BR-DEC-NEW-04)");
        }

        // Create session with revenue trend snapshot calculation
        // Calculate revenue trend for this series
        List<manga.dto.RevenueDataPoint> revenueHistory = rankingRepository.getRevenueHistoryForSeries(seriesId, rankingRecordId);
        String revenueTrendJson = null;
        String systemSuggestion = null;

        if (revenueHistory != null && !revenueHistory.isEmpty()) {
            // Keep only last 6 periods
            if (revenueHistory.size() > 6) {
                revenueHistory = new ArrayList<>(revenueHistory.subList(0, 6));
            }
            systemSuggestion = calculateSystemSuggestionFromTrend(revenueHistory);

            // Serialize revenue trend to JSON
            StringBuilder jsonBuilder = new StringBuilder("[");
            for (int j = 0; j < revenueHistory.size(); j++) {
                if (j > 0) {
                    jsonBuilder.append(",");
                }
                manga.dto.RevenueDataPoint point = revenueHistory.get(j);
                jsonBuilder.append("{\"periodId\":").append(point.getPeriodId())
                        .append(",\"periodName\":\"").append(escapeJson(point.getPeriodName())).append("\"")
                        .append(",\"revenue\":").append(point.getRevenue()).append("}");
            }
            jsonBuilder.append("]");
            revenueTrendJson = jsonBuilder.toString();
        }

        long sessionId = decisionRepository.createSession(seriesId, rankingRecordId, systemSuggestion, revenueTrendJson, user.getId());

        return sessionId;
    }

    private String calculateSystemSuggestionFromTrend(List<manga.dto.RevenueDataPoint> trend) {
        if (trend == null || trend.size() < 2) {
            return null;
        }

        if (trend.size() == 2) {
            java.math.BigDecimal r0 = trend.get(0).getRevenue();
            java.math.BigDecimal r1 = trend.get(1).getRevenue();
            return r1.compareTo(r0) >= 0 ? "CONTINUE" : "REVIEW";
        }

        java.math.BigDecimal r0 = trend.get(2).getRevenue();
        java.math.BigDecimal r1 = trend.get(1).getRevenue();
        java.math.BigDecimal r2 = trend.get(0).getRevenue();

        boolean increasing = r2.compareTo(r1) >= 0 && r1.compareTo(r0) >= 0;
        boolean decreasing = r2.compareTo(r1) < 0 && r1.compareTo(r0) < 0;

        if (increasing) {
            return "CONTINUE";
        } else if (decreasing) {
            return "CANCEL";
        } else {
            return "REVIEW";
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Transactional
    public void submitDecisionVote(long sessionId, SubmitDecisionVoteRequest request, AuthenticatedUser user) {
        // Only Editorial Board (BR-59)
        if (!user.hasRole("EDITORIAL_BOARD")) {
            throw new BusinessRuleException("Only EDITORIAL_BOARD can vote (BR-59)");
        }

        // Validate decision value
        String normalized = request.getDecision() == null ? "" : request.getDecision().trim().toUpperCase();
        if (!DecisionResult.CONTINUE.name().equals(normalized)
                && !DecisionResult.CANCEL.name().equals(normalized)
                && !DecisionResult.CHANGE_TYPE.name().equals(normalized)) {
            throw new BusinessRuleException("decision must be CONTINUE, CANCEL, or CHANGE_TYPE");
        }

        // CANCEL requires justification (BR-68)
        if (DecisionResult.CANCEL.name().equals(normalized)) {
            if (request.getJustification() == null || request.getJustification().trim().isEmpty()) {
                throw new BusinessRuleException("justification is required when decision is CANCEL (BR-68)");
            }
        }
        int requiredMajority = calculateQuorum();

        // Submit vote (repository handles BR-60, BR-61, BR-64, BR-62, BR-69)
        decisionRepository.castVote(sessionId, user.getId(), normalized,
                request.getJustification() == null ? null : request.getJustification().trim(), requiredMajority);
    }

    public int getTotalBoardMembers() {
        return userRepository.countUsersByRole(board);
    }

    public int calculateQuorum() {
        int totalBoard = getTotalBoardMembers();
        return totalBoard / 2 + 1;
    }
    // finalizeDecision removed - quorum-based finalization in resolveIfQuorum handles this automatically
}
