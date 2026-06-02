package manga.service;

import manga.common.exception.BusinessRuleException;
import manga.dto.CreateRankingPeriodRequest;
import manga.dto.SubmitVoteEntryRequest;
import manga.enums.RankingPeriodStatus;
import manga.model.AuthenticatedUser;
import manga.repository.RankingRepository;
import manga.repository.MangakaRankingRepository;
import manga.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.List;
import java.util.Map;

@Service
public class RankingService {

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private ClosePeriodPipelineService closePeriodPipelineService;

    @Autowired
    private MangakaRankingRepository mangakaRankingRepository;

    @Autowired
    private NotificationService notificationService;

    public List<Map<String, Object>> listPeriods() {
        return rankingRepository.listPeriods();
    }

    public Map<String, Object> getPeriodById(long periodId) {
        return rankingRepository.findPeriodById(periodId);
    }

    public long createRankingPeriod(CreateRankingPeriodRequest request, AuthenticatedUser user) {
        // ADMIN only
        if (!user.hasRole("ADMIN")) {
            throw new BusinessRuleException("Only ADMIN can create ranking period");
        }

        // Ranking periods always start on creation date.
        if (request.getEndDate() == null) {
            throw new BusinessRuleException("End date is required");
        }

        Date startDate = new Date(System.currentTimeMillis());
        if (startDate.after(request.getEndDate())) {
            throw new BusinessRuleException("End date must be today or later");
        }

        long periodId = rankingRepository.createPeriod(request.getName(), startDate, request.getEndDate());

        // Notify Editorial Board
        notificationService.notifyUser(
            user.getId(), // In real implementation, would notify all Editorial Board members
            "RANKING_PERIOD_OPENED",
            "New ranking period '" + request.getName() + "' is now open for vote submissions.",
            periodId,
            "RANKING_PERIOD"
        );

        return periodId;
    }

    public void closeRankingPeriod(long periodId, AuthenticatedUser user) {
        closePeriodPipelineService.executePipeline(periodId, user);
    }

    public void submitVoteEntry(long periodId, SubmitVoteEntryRequest request, AuthenticatedUser user) {
        // Only Editorial Board (BR-53)
        if (!user.hasRole("EDITORIAL_BOARD")) {
            throw new BusinessRuleException("Only EDITORIAL_BOARD can submit vote data (BR-53)");
        }

        // Validate period exists and is OPEN (BR-49)
        Map<String, Object> period = rankingRepository.findPeriodById(periodId);
        String status = (String) period.get("status");
        if (!RankingPeriodStatus.OPEN.name().equals(status)) {
            throw new BusinessRuleException("Vote entry only allowed while period OPEN (BR-49)");
        }

        // BR-RNK-01: Validate current date is within period date range
        java.sql.Date startDate = (java.sql.Date) period.get("startDate");
        java.sql.Date endDate = (java.sql.Date) period.get("endDate");
        java.sql.Date currentDate = new java.sql.Date(System.currentTimeMillis());
        
        if (currentDate.before(startDate) || currentDate.after(endDate)) {
            throw new BusinessRuleException("Vote entry only allowed during active period dates (BR-RNK-01)");
        }

        // Validate voteCount (BR-51)
        if (request.getVoteCount() < 0) {
            throw new BusinessRuleException("voteCount cannot be negative (BR-51)");
        }

        // Validate readerCount (BR-52)
        if (request.getReaderCount() <= 0) {
            throw new BusinessRuleException("readerCount must be > 0 (BR-52)");
        }

        // Validate voteCount <= readerCount (BR-50)
        if (request.getVoteCount() > request.getReaderCount()) {
            throw new BusinessRuleException("voteCount cannot exceed readerCount (BR-50)");
        }

        if (request.getRevenue() == null || request.getRevenue().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("revenue cannot be negative");
        }

        // Submit entry (repository handles duplicate check BR-54)
        rankingRepository.submitEntry(periodId, request.getSeriesId(), user.getId(), 
            request.getVoteCount(), request.getReaderCount(), request.getRevenue());
    }

    @Transactional
    public void calculateRanking(long periodId, AuthenticatedUser user) {
        throw new BusinessRuleException("Use close period to trigger pipeline");
    }

    public List<Map<String, Object>> getRankingResults(long periodId, AuthenticatedUser user) {
        // Any authenticated user can view results
        return rankingRepository.results(periodId);
    }

    public List<Map<String, Object>> getMangakaRanking(long periodId, AuthenticatedUser user) {
        return mangakaRankingRepository.findByPeriodId(periodId);
    }

    public List<Map<String, Object>> listVoteEntries(long periodId, AuthenticatedUser user) {
        // Only ADMIN and EDITORIAL_BOARD can view vote entries
        if (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD")) {
            throw new BusinessRuleException("Only ADMIN/EDITORIAL_BOARD can view vote entries");
        }

        return rankingRepository.listEntries(periodId);
    }
}
