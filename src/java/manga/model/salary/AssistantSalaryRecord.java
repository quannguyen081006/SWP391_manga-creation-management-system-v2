package manga.model.salary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AssistantSalaryRecord {
    private long periodId;
    private String periodName;
    private long assistantId;
    private int totalTasksApproved;
    private int totalPagesCompleted;
    private BigDecimal onTimeRate;
    private BigDecimal kpiScore;
    private BigDecimal grossSalary;
    private BigDecimal bonus;
    private BigDecimal deduction;
    private BigDecimal netSalary;
    private BigDecimal suggestedBonus;
    private List<Map<String, Object>> tasks;

    public long getPeriodId() {
        return periodId;
    }

    public void setPeriodId(long periodId) {
        this.periodId = periodId;
    }

    public String getPeriodName() {
        return periodName;
    }

    public void setPeriodName(String periodName) {
        this.periodName = periodName;
    }

    public long getAssistantId() {
        return assistantId;
    }

    public void setAssistantId(long assistantId) {
        this.assistantId = assistantId;
    }

    public int getTotalTasksApproved() {
        return totalTasksApproved;
    }

    public void setTotalTasksApproved(int totalTasksApproved) {
        this.totalTasksApproved = totalTasksApproved;
    }

    public int getTotalPagesCompleted() {
        return totalPagesCompleted;
    }

    public void setTotalPagesCompleted(int totalPagesCompleted) {
        this.totalPagesCompleted = totalPagesCompleted;
    }

    public BigDecimal getOnTimeRate() {
        return onTimeRate;
    }

    public void setOnTimeRate(BigDecimal onTimeRate) {
        this.onTimeRate = onTimeRate;
    }

    public BigDecimal getKpiScore() {
        return kpiScore;
    }

    public void setKpiScore(BigDecimal kpiScore) {
        this.kpiScore = kpiScore;
    }

    public BigDecimal getGrossSalary() {
        return grossSalary;
    }

    public void setGrossSalary(BigDecimal grossSalary) {
        this.grossSalary = grossSalary;
    }

    public BigDecimal getBonus() {
        return bonus;
    }

    public void setBonus(BigDecimal bonus) {
        this.bonus = bonus;
    }

    public BigDecimal getDeduction() {
        return deduction;
    }

    public void setDeduction(BigDecimal deduction) {
        this.deduction = deduction;
    }

    public BigDecimal getNetSalary() {
        return netSalary;
    }

    public void setNetSalary(BigDecimal netSalary) {
        this.netSalary = netSalary;
    }

    public BigDecimal getSuggestedBonus() {
        return suggestedBonus;
    }

    public void setSuggestedBonus(BigDecimal suggestedBonus) {
        this.suggestedBonus = suggestedBonus;
    }

    public List<Map<String, Object>> getTasks() {
        return tasks;
    }

    public void setTasks(List<Map<String, Object>> tasks) {
        this.tasks = tasks;
    }
}
