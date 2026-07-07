package manga.service.salary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import manga.common.exception.BusinessRuleException;
import manga.common.util.SessionUserUtil;
import manga.model.salary.AssistantSalaryRecord;
import manga.model.AuthenticatedUser;
import manga.model.salary.SalarySettings;
import manga.repository.salary.AssistantSalaryRecordRepository;
import manga.repository.salary.SalaryPeriodRepository;
import manga.repository.chaptertask.PageTaskRepository;
import manga.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalaryService {

    @Autowired
    private SalaryPeriodRepository salaryPeriodRepository;

    @Autowired
    private AssistantSalaryRecordRepository assistantSalaryRecordRepository;

    @Autowired
    private SalarySettingsService salarySettingsService;

    @Autowired
    private PageTaskRepository pageTaskRepository;

    @Autowired
    private NotificationService notificationService;

    public List<Map<String, Object>> listMyPeriods(AuthenticatedUser user) {
        requireMangaka(user, "Only MANGAKA can view salary periods");
        return salaryPeriodRepository.listPeriodsByMangaka(user.getId());
    }

    public Map<String, Object> getPeriodOwnedByUser(long periodId, AuthenticatedUser user) {
        Map<String, Object> period = salaryPeriodRepository.findById(periodId);
        if (period == null) {
            throw new BusinessRuleException("Salary period not found");
        }
        Number ownerId = (Number) period.get("mangakaId");
        if (user == null || !user.hasRole("MANGAKA")
                || ownerId == null || ownerId.longValue() != user.getId()) {
            throw new BusinessRuleException("You do not own this salary period");
        }
        return period;
    }

    /**
     * Salary is bonus-only: no late/rejection penalties are applied.
     * A bonus is granted only when KPI reaches the configured threshold; otherwise bonus is zero.
     */
    private void calculateForPeriod(long periodId, long mangakaId) {
        SalarySettings settings = salarySettingsService.getSettings();
        List<AssistantSalaryRecord> rows = assistantSalaryRecordRepository.calculatePreview(
                periodId, mangakaId,
                settings.getKpiOnTimeWeight(), settings.getKpiQualityWeight());
        for (AssistantSalaryRecord row : rows) {
            BigDecimal suggestedBonus = calculateSuggestedBonus(
                    row.getKpiScore(), row.getGrossSalary(), settings);
            row.setSuggestedBonus(suggestedBonus);
            row.setBonus(suggestedBonus);
            row.setDeduction(BigDecimal.ZERO);
        }
        assistantSalaryRecordRepository.upsertCalculated(periodId, rows);
    }

    public List<Map<String, Object>> getRecords(long periodId, AuthenticatedUser user) {
        getPeriodOwnedByUser(periodId, user);
        SalarySettings settings = salarySettingsService.getSettings();
        List<Map<String, Object>> rows = assistantSalaryRecordRepository.findByPeriodId(periodId);
        for (Map<String, Object> row : rows) {
            long assistantId = ((Number) row.get("assistantId")).longValue();
            BigDecimal kpiScore = (BigDecimal) row.get("kpiScore");
            BigDecimal grossSalary = (BigDecimal) row.get("grossSalary");
            row.put("suggestedBonus", calculateSuggestedBonus(kpiScore, grossSalary, settings));
            row.put("tasks", loadTaskBreakdown(periodId, assistantId));
        }
        return rows;
    }

    public List<AssistantSalaryRecord> getMySettledSalaryRecords(AuthenticatedUser user) {
        try {
            SessionUserUtil.requireRole(user, "ASSISTANT", "Only ASSISTANT can view this salary page");
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ex.getMessage());
        }
        List<AssistantSalaryRecord> rows =
                assistantSalaryRecordRepository.findSettledByAssistant(user.getId());
        for (AssistantSalaryRecord row : rows) {
            row.setTasks(loadTaskBreakdown(row.getPeriodId(), user.getId()));
        }
        return rows;
    }

    /** Task-level breakdown shown under each salary row (page, stage, rate, amount, on-time status). */
    private List<Map<String, Object>> loadTaskBreakdown(long periodId, long assistantId) {
        return pageTaskRepository.findApprovedTasksForSalary(periodId, assistantId);
    }

    /**
     * Automatic monthly rotation, run only by {@link manga.scheduler.salary.SalaryScheduler}
     * on the 5th of every month. There is no manual "Generate period" / "Settle period" action
     * anymore - the system fully owns the salary period lifecycle:
     *   1. Every Mangaka's currently OPEN period gets a final calculation and is settled
     *      (locked, tasks marked paid, assistants notified).
     *   2. A fresh OPEN period is created right away so next month's approved tasks have
     *      somewhere to accumulate into.
     */
    public void autoRotatePeriods() {
        List<Long> mangakaIds = salaryPeriodRepository.listMangakaIdsWithAssistants();
        for (Long mangakaId : mangakaIds) {
            rotatePeriodForMangaka(mangakaId.longValue());
        }
    }

    @Transactional
    void rotatePeriodForMangaka(long mangakaId) {
        Long openPeriodId = salaryPeriodRepository.findOpenPeriodId(mangakaId);
        if (openPeriodId != null) {
            settlePeriod(openPeriodId.longValue(), mangakaId);
        }
        salaryPeriodRepository.createPeriod(mangakaId, currentAutoPeriodName());
    }

    /** Final calculation + lock + mark tasks paid + notify assistants. No manual trigger exists for this. */
    private void settlePeriod(long periodId, long mangakaId) {
        calculateForPeriod(periodId, mangakaId);
        List<Long> assistantIds = assistantSalaryRecordRepository.findAssistantIdsByPeriod(periodId);
        salaryPeriodRepository.markSettled(periodId);
        assistantSalaryRecordRepository.markPeriodTasksSalaried(periodId);
        Map<String, Object> period = salaryPeriodRepository.findById(periodId);
        String periodName = String.valueOf(period.get("name"));
        for (Long assistantId : assistantIds) {
            notificationService.notifyUser(
                    assistantId.longValue(),
                    "SALARY_SETTLED",
                    "Salary period \"" + periodName + "\" has been settled. "
                            + "View details on your Salary page.",
                    periodId,
                    "SALARY_PERIOD");
        }
    }

    private String currentAutoPeriodName() {
        LocalDate today = LocalDate.now();
        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return "Auto - " + month + " " + today.getYear();
    }

    /** Live preview shown whenever a Mangaka opens their OPEN period's detail page - no button needed. */
    public void refreshOpenPeriod(long periodId, AuthenticatedUser user) {
        Map<String, Object> period = getPeriodOwnedByUser(periodId, user);
        if ("OPEN".equals(period.get("status"))) {
            calculateForPeriod(periodId, user.getId());
        }
    }

    private BigDecimal calculateSuggestedBonus(BigDecimal kpiScore,
            BigDecimal grossSalary, SalarySettings settings) {
        if (kpiScore == null || grossSalary == null
                || kpiScore.compareTo(new BigDecimal(settings.getKpiBonusThreshold())) < 0) {
            return BigDecimal.ZERO;
        }
        return grossSalary.multiply(settings.getBonusPercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private void requireMangaka(AuthenticatedUser user, String message) {
        try {
            SessionUserUtil.requireRole(user, "MANGAKA", message);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ex.getMessage());
        }
    }
}
