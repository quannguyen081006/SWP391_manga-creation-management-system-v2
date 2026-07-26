package manga.service;

import manga.model.DeadlineSettings;
import manga.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Admin-configurable deadline buffers, backed by SystemSetting.
 * Read by ChapterRepository (chapter-vs-series) and PageTaskRepository (task-vs-chapter, BR-34)
 * on every validation call - no caching, so a change here takes effect immediately.
 */
@Service
public class DeadlineSettingsService {

    public static final int DEFAULT_TASK_CHAPTER_BUFFER_DAYS = 1;
    public static final int DEFAULT_CHAPTER_SERIES_BUFFER_DAYS = 7;

    private static final int MAX_BUFFER_DAYS = 60;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    public DeadlineSettings getSettings() {
        DeadlineSettings settings = new DeadlineSettings();
        settings.setTaskChapterBufferDays(getTaskChapterBufferDays());
        settings.setChapterSeriesBufferDays(getChapterSeriesBufferDays());
        return settings;
    }

    public int getTaskChapterBufferDays() {
        return systemSettingRepository.getInt(
                SystemSettingRepository.TASK_CHAPTER_DEADLINE_BUFFER_DAYS, DEFAULT_TASK_CHAPTER_BUFFER_DAYS);
    }

    public int getChapterSeriesBufferDays() {
        return systemSettingRepository.getInt(
                SystemSettingRepository.CHAPTER_SERIES_DEADLINE_BUFFER_DAYS, DEFAULT_CHAPTER_SERIES_BUFFER_DAYS);
    }

    public void updateSettings(int taskChapterBufferDays, int chapterSeriesBufferDays) {
        if (taskChapterBufferDays < 0 || taskChapterBufferDays > MAX_BUFFER_DAYS) {
            throw new IllegalArgumentException("Task-to-chapter buffer must be between 0 and " + MAX_BUFFER_DAYS + " days");
        }
        if (chapterSeriesBufferDays < 0 || chapterSeriesBufferDays > MAX_BUFFER_DAYS) {
            throw new IllegalArgumentException("Chapter-to-series buffer must be between 0 and " + MAX_BUFFER_DAYS + " days");
        }
        systemSettingRepository.setInt(SystemSettingRepository.TASK_CHAPTER_DEADLINE_BUFFER_DAYS, taskChapterBufferDays);
        systemSettingRepository.setInt(SystemSettingRepository.CHAPTER_SERIES_DEADLINE_BUFFER_DAYS, chapterSeriesBufferDays);
    }
}
