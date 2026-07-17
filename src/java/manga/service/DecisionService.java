package manga.service;

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
import manga.repository.UserRepository;

@Service
public class DecisionService {

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductionRepository productionRepository;

    public static final String board = "EDITORIAL_BOARD";

    public List<Map<String, Object>> listDecisionSessions(AuthenticatedUser user) {
        // Only ADMIN and EDITORIAL_BOARD can view decisions
        if (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD")) {
            throw new BusinessRuleException("Only ADMIN/EDITORIAL_BOARD can view decisions");
        }
        return decisionRepository.listSessions();
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

        // Validate ranking record exists and is bottom 20%
        // Note: In real implementation, would check if this specific ranking record is for this series and isBottomTwenty
        // Validate no duplicate active session (BR-66)
        // Note: This check would be implemented in repository
        // Create session (repository handles creation)
        // Note: systemSuggestion is null here since this is manual session creation
        // The pipeline auto-generates suggestions for bottom 20% series
        // revenueTrendSnapshot is null for manual sessions
        long sessionId = decisionRepository.createSession(request.getSeriesId(), request.getRankingRecordId(), null, null, user.getId());

        return sessionId;
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
