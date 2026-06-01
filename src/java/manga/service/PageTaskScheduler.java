package manga.service;

import manga.repository.PageTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PageTaskScheduler {

    @Autowired
    private PageTaskRepository pageTaskRepository;

    // BR-TSK-10: dueDate passed and not approved -> mark OVERDUE
    @Scheduled(cron = "0 0 * * * *")
    public void markOverdueTasks() {
        pageTaskRepository.markOverdueTasks();
    }

    // BR-TSK-09: reminder 24h before dueDate
    @Scheduled(cron = "0 0 8 * * *")
    public void remindDueSoonTasks() {
        pageTaskRepository.notifyDueSoonTasks();
    }

    // BR-TSK-08: no update for >= 3 days after assignment -> flag Delayed + notify Mangaka
    @Scheduled(cron = "0 30 8 * * *")
    public void detectDelayedTasks() {
        pageTaskRepository.markDelayedTasks();
    }
}
