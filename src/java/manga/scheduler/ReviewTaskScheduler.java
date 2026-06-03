package manga.scheduler;

import manga.service.ReviewTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for review task reminders and overdue checks.
 * Runs periodically and delegates to ReviewTaskService.
 */
@Component
public class ReviewTaskScheduler {

    @Autowired
    private ReviewTaskService reviewTaskService;

    // Run every 5 minutes to detect warning threshold and overdue tasks
    @Scheduled(cron = "0 */5 * * * ?")
    public void runRemindersAndOverdueChecks() {
        try {
            reviewTaskService.checkWarningThreshold();
        } catch (Exception ex) {
            System.err.println("ReviewTaskScheduler: warning check failed: " + ex.getMessage());
        }

        try {
            reviewTaskService.checkOverdueTasks();
        } catch (Exception ex) {
            System.err.println("ReviewTaskScheduler: overdue check failed: " + ex.getMessage());
        }
    }
}
