package manga.service;

import manga.common.exception.BusinessRuleException;
import manga.dto.CreateRankingPeriodRequestDTO;
import manga.dto.SubmitVoteEntryRequest;
import manga.enums.RankingPeriodStatus;
import manga.model.AuthenticatedUser;
import manga.repository.RankingRepository;
import manga.repository.MangakaRankingRepository;
import manga.repository.UserRepository;
import manga.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.List;
import java.util.Map;
import manga.model.RankingCsvUpload;
import manga.repository.RankingCsvUploadRepository;

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

    @Autowired
    private RankingCsvUploadRepository rankingCsvUploadRepository;

    @Autowired
    private UserRepository userRepository;

    public List<Map<String, Object>> listPeriods() {
        return rankingRepository.listPeriods();
    }

    public Map<String, Object> getPeriodById(long periodId) {
        return rankingRepository.findPeriodById(periodId);
    }

    public long createRankingPeriod(CreateRankingPeriodRequestDTO request, AuthenticatedUser user) {
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

        long periodId = rankingRepository.createPeriod(request.getName(), startDate, request.getEndDate(),"OPEN");

        // Notify every real Editorial Board member, same lookup the scheduler
        // uses for the monthly auto-open job. Wrapped in try/catch per the
        // convention elsewhere in the app (see ReviewTaskService): a failed
        // notification must not roll back a period that was already created.
        notifyEditorialBoardPeriodOpened(periodId, request.getName());

        return periodId;
    }

    private void notifyEditorialBoardPeriodOpened(long periodId, String periodName) {
        List<Map<String, Object>> boardMembers = userRepository.findByRole("EDITORIAL_BOARD");
        String message = "New ranking period '" + periodName + "' is now open for vote submissions.";
        for (Map<String, Object> member : boardMembers) {
            long memberId = ((Number) member.get("id")).longValue();
            try {
                notificationService.notifyUser(memberId, "RANKING_PERIOD_OPENED", message, periodId, "RANKING_PERIOD");
            } catch (Exception ex) {
                System.err.println("Warning: Failed to notify Editorial Board member " + memberId
                        + " about ranking period " + periodId + ": " + ex.getMessage());
            }
        }
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

    public List<Map<String, Object>> getRankingResults(long periodId) {
        // Any authenticated user can view results
        return rankingRepository.results(periodId);
    }

    public List<Map<String,Object>> findCsvByPeriod(long periodId){
        //Find csv content based on period id
        return rankingCsvUploadRepository.findByPeriod(periodId);
    }
    
    public RankingCsvUpload findCsvById(long uploadId){
        //Find csv content based on period id
        return rankingCsvUploadRepository.findById(uploadId);
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
    
    public boolean hasSubmittedEntries(long periodId, long userId){
        return rankingRepository.hasSubmittedEntries(periodId, userId);
    }
}
