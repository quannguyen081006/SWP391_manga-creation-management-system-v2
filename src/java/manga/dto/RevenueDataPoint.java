package manga.dto;

import java.math.BigDecimal;

public class RevenueDataPoint {
    private long periodId;
    private String periodName;
    private BigDecimal revenue;

    public RevenueDataPoint() {
    }

    public RevenueDataPoint(long periodId, String periodName, BigDecimal revenue) {
        this.periodId = periodId;
        this.periodName = periodName;
        this.revenue = revenue;
    }

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

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }
}
