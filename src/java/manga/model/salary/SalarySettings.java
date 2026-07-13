package manga.model.salary;

import java.math.BigDecimal;

public class SalarySettings {
    private int kpiBonusThreshold;
    private BigDecimal bonusPercent;

    public int getKpiBonusThreshold() {
        return kpiBonusThreshold;
    }

    public void setKpiBonusThreshold(int kpiBonusThreshold) {
        this.kpiBonusThreshold = kpiBonusThreshold;
    }

    public BigDecimal getBonusPercent() {
        return bonusPercent;
    }

    public void setBonusPercent(BigDecimal bonusPercent) {
        this.bonusPercent = bonusPercent;
    }
}
