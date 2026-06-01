package manga.scheduler;

import manga.repository.RankingRepository;
import manga.repository.UserRepository;
import manga.service.ClosePeriodPipelineService;
import manga.service.NotificationService;
import manga.service.RankingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

@Component
public class RankingPeriodScheduler {

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClosePeriodPipelineService closePeriodPipelineService;

    @Autowired
    private RankingService rankingService;

    @Autowired
    private NotificationService notificationService;

    /**
     * Scheduler 1: Monthly Auto-Open Period
     * Runs on day 1 of every month at 00:00
     * Creates a new RankingPeriod with 7-day duration
     * Notifies all Editorial Board members
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    public void autoOpenMonthlyPeriod() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        // Check if period already exists for this month
        Map<String, Object> existingPeriod = rankingRepository.findPeriodByMonthYear(year, month);
        if (existingPeriod != null) {
            // Period already exists, skip creation
            return;
        }

        // Calculate period dates: day 1 to day 7 of current month
        LocalDate startDate = today.withDayOfMonth(1);
        LocalDate endDate = today.withDayOfMonth(7);

        // Create period name
        String periodName = String.format("%s %d", startDate.getMonth().toString(), year);

        // Create period with OPEN status
        long periodId = rankingRepository.createPeriod(periodName, Date.valueOf(startDate), Date.valueOf(endDate), "OPEN");

        // Notify all Editorial Board members
        notifyEditorialBoardPeriodOpened(periodId, periodName, startDate, endDate);
    }

    /**
     * Scheduler 2: Auto-Close Expired Periods
     * Runs every 5 minutes
     * Finds OPEN periods past their endDate and closes them
     * Triggers calculation pipeline for each closed period
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void autoCloseExpiredPeriods() {
        List<Map<String, Object>> expiredPeriods = rankingRepository.findOpenExpiredPeriods();

        for (Map<String, Object> period : expiredPeriods) {
            long periodId = (Long) period.get("id");
            String periodName = (String) period.get("name");

            try {
                // Close the period
                rankingRepository.closePeriod(periodId);

                // Trigger calculation pipeline (reuse existing logic)
                // Note: This requires an AuthenticatedUser, but scheduler runs as system
                // We'll need to handle this - either create a system user or bypass auth
                // For now, we'll use a placeholder approach
                rankingService.closeRankingPeriod(periodId, null);

                // Notify Editorial Board that period has closed and calculation started
                notifyEditorialBoardPeriodClosed(periodId, periodName);

            } catch (Exception e) {
                // Log error but continue with other periods
                System.err.println("Failed to auto-close period " + periodId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Notify all Editorial Board members that a period has closed
     */
    private void notifyEditorialBoardPeriodClosed(long periodId, String periodName) {
        List<Map<String, Object>> boardMembers = userRepository.findByRole("EDITORIAL_BOARD");

        String message = String.format(
            "🔒 Ranking Period '%s' has CLOSED. Ranking calculation in progress. Results will be available shortly.",
            periodName
        );

        for (Map<String, Object> member : boardMembers) {
            long userId = (Long) member.get("id");
            notificationService.notifyUser(userId, "RANKING_PERIOD_CLOSED", message, periodId, "RANKING_PERIOD");
        }
    }

    /**
     * Notify all Editorial Board members that a new period has opened
     */
    private void notifyEditorialBoardPeriodOpened(long periodId, String periodName, LocalDate startDate, LocalDate endDate) {
        // Get all Editorial Board members
        List<Map<String, Object>> boardMembers = userRepository.findByRole("EDITORIAL_BOARD");

        String message = String.format(
            "📊 Monthly Ranking Period '%s' is now OPEN! Upload your ranking CSV before %s.",
            periodName, endDate
        );

        for (Map<String, Object> member : boardMembers) {
            long userId = (Long) member.get("id");
            // Create notification using NotificationService
            notificationService.notifyUser(userId, "RANKING_PERIOD_OPENED", message, periodId, "RANKING_PERIOD");
        }
    }
}
