package manga.service;

import manga.model.ProgressSettings;
import manga.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Admin-configurable thresholds for the chapter/series progress bar and % badge colours:
 * below lowThresholdPercent = red, from there up to (not including) highThresholdPercent = amber,
 * at/above highThresholdPercent = green. Purely a display concern - a chapter's actual
 * "Completed" status/grouping is a separate check on completionPct >= 100 (see
 * ChapterRepository/isChapterDone), unaffected by wherever the green line is set here.
 */
@Service
public class ProgressSettingsService {

    public static final int DEFAULT_LOW_THRESHOLD_PERCENT = 50;
    public static final int DEFAULT_HIGH_THRESHOLD_PERCENT = 100;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    public ProgressSettings getSettings() {
        ProgressSettings settings = new ProgressSettings();
        settings.setLowThresholdPercent(getLowThresholdPercent());
        settings.setHighThresholdPercent(getHighThresholdPercent());
        return settings;
    }

    public int getLowThresholdPercent() {
        return systemSettingRepository.getInt(
                SystemSettingRepository.PROGRESS_LOW_THRESHOLD_PERCENT, DEFAULT_LOW_THRESHOLD_PERCENT);
    }

    public int getHighThresholdPercent() {
        return systemSettingRepository.getInt(
                SystemSettingRepository.PROGRESS_HIGH_THRESHOLD_PERCENT, DEFAULT_HIGH_THRESHOLD_PERCENT);
    }

    public void updateSettings(int lowThresholdPercent, int highThresholdPercent) {
        if (lowThresholdPercent < 1 || lowThresholdPercent > 99) {
            throw new IllegalArgumentException("Red/amber threshold must be between 1 and 99 percent");
        }
        if (highThresholdPercent < 1 || highThresholdPercent > 100) {
            throw new IllegalArgumentException("Amber/green threshold must be between 1 and 100 percent");
        }
        if (lowThresholdPercent >= highThresholdPercent) {
            throw new IllegalArgumentException("Red/amber threshold must be lower than the amber/green threshold");
        }
        systemSettingRepository.setInt(SystemSettingRepository.PROGRESS_LOW_THRESHOLD_PERCENT, lowThresholdPercent);
        systemSettingRepository.setInt(SystemSettingRepository.PROGRESS_HIGH_THRESHOLD_PERCENT, highThresholdPercent);
    }
}
