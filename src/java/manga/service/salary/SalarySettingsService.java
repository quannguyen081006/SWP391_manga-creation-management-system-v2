package manga.service.salary;

import java.math.BigDecimal;
import manga.model.salary.SalarySettings;
import manga.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SalarySettingsService {

    public static final int DEFAULT_KPI_BONUS_THRESHOLD = 90;
    public static final BigDecimal DEFAULT_BONUS_PERCENT = new BigDecimal("5");
    public static final int DEFAULT_KPI_ON_TIME_WEIGHT = 70;
    public static final int DEFAULT_KPI_QUALITY_WEIGHT = 30;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    public SalarySettings getSettings() {
        SalarySettings settings = new SalarySettings();
        settings.setKpiBonusThreshold(systemSettingRepository.getInt(
                SystemSettingRepository.SALARY_KPI_BONUS_THRESHOLD,
                DEFAULT_KPI_BONUS_THRESHOLD));
        settings.setBonusPercent(systemSettingRepository.getDecimal(
                SystemSettingRepository.SALARY_BONUS_PERCENT,
                DEFAULT_BONUS_PERCENT));
        settings.setKpiOnTimeWeight(systemSettingRepository.getInt(
                SystemSettingRepository.SALARY_KPI_ON_TIME_WEIGHT,
                DEFAULT_KPI_ON_TIME_WEIGHT));
        settings.setKpiQualityWeight(systemSettingRepository.getInt(
                SystemSettingRepository.SALARY_KPI_QUALITY_WEIGHT,
                DEFAULT_KPI_QUALITY_WEIGHT));
        return settings;
    }

    /**
     * Salary is bonus-only: no more late/rejection penalties.
     * Bonus is granted only when KPI reaches kpiBonusThreshold; otherwise bonus is zero.
     */
    public void updateSettings(int kpiBonusThreshold, BigDecimal bonusPercent,
            int kpiOnTimeWeight, int kpiQualityWeight) {
        if (kpiBonusThreshold < 0 || kpiBonusThreshold > 100) {
            throw new IllegalArgumentException("KPI bonus threshold must be between 0 and 100");
        }
        if (bonusPercent == null || bonusPercent.signum() < 0
                || bonusPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Bonus percent must be between 0 and 100");
        }
        if (kpiOnTimeWeight < 0 || kpiQualityWeight < 0
                || kpiOnTimeWeight + kpiQualityWeight != 100) {
            throw new IllegalArgumentException("KPI weights must sum to 100");
        }
        systemSettingRepository.setSalarySettings(
                kpiBonusThreshold, bonusPercent, kpiOnTimeWeight, kpiQualityWeight);
    }
}
