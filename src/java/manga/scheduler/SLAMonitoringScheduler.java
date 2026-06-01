package manga.scheduler;

import manga.service.ReviewTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for SLA monitoring.
 * 
 * Checks for overdue review tasks and warning thresholds.
 * Business Rules:
 * - BR-52: 48h review deadline, 36h warning threshold, overdue state
 */
@Component
public class SLAMonitoringScheduler {
    
    @Autowired
    private ReviewTaskService reviewTaskService;
    
    /**
     * Check for overdue review tasks every hour.
     * Runs at minute 0 of every hour.
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkOverdueTasks() {
        reviewTaskService.checkOverdueTasks();
    }
    
    /**
     * Check for warning threshold every hour.
     * Runs at minute 30 of every hour.
     */
    @Scheduled(cron = "0 30 * * * ?")
    public void checkWarningThreshold() {
        reviewTaskService.checkWarningThreshold();
    }
}
