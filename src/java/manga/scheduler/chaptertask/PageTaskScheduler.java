package manga.scheduler.chaptertask;

// Chapter/task scheduler group: task lifecycle jobs call repository methods so rules stay in one place.
import manga.service.chaptertask.PageTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PageTaskScheduler {

    @Autowired
    private PageTaskService pageTaskService;

    // BR-TSK-10: dueDate passed and not approved -> mark OVERDUE
    @Scheduled(cron = "0 0 * * * *")
    public void markOverdueTasks() {
        pageTaskService.markOverdueTasks();
    }

    // BR-TSK-09: reminder 24h before dueDate
    @Scheduled(cron = "0 0 8 * * *")
    public void remindDueSoonTasks() {
        pageTaskService.remindDueSoonTasks();
    }

    // BR-TSK-08: no update for >= 3 days after assignment -> flag Delayed + notify Mangaka
    @Scheduled(cron = "0 30 8 * * *")
    public void detectDelayedTasks() {
        pageTaskService.markDelayedTasks();
    }

    // Auto-cancel OVERDUE tasks with no mangaka decision for 3+ days
    @Scheduled(cron = "0 0 9 * * *")
    public void escalatePendingOverdueDecisions() {
        pageTaskService.escalatePendingOverdueDecisions();
    }
}

