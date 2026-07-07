package manga.scheduler.salary;

import manga.service.salary.SalaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Owns the full salary period lifecycle - there is no manual "Generate period" or
 * "Settle period" action anywhere in the app. On the 5th of every month this settles
 * each Mangaka's currently OPEN period (final calculation, lock, mark tasks paid,
 * notify assistants) and immediately opens a fresh period for the new cycle.
 */
@Component
public class SalaryScheduler {

    @Autowired
    private SalaryService salaryService;

    @Scheduled(cron = "0 0 0 5 * *")
    public void autoMonthlyRotation() {
        salaryService.autoRotatePeriods();
    }
}
