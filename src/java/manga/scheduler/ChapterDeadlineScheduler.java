package manga.scheduler;

import manga.model.ChapterSummary;
import manga.repository.ChapterRepository;
import manga.repository.PageTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChapterDeadlineScheduler {

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private PageTaskRepository pageTaskRepository;

    // Run daily at 09:00.
    @Scheduled(cron = "0 0 9 * * *")
    public void remindApproachingDeadlines() {
        for (ChapterSummary ch : chapterRepository.findChaptersWithDeadlineInDays(7)) {
            long mangakaId = chapterRepository.findOwnerMangakaByChapter(ch.getId());
            pageTaskRepository.createNotificationIfAbsentToday(
                mangakaId,
                "CHAPTER_DEADLINE_SOON",
                "Chapter #" + ch.getId() + " (Ch." + ch.getChapterNumber() + " - " + ch.getTitle()
                    + ") submission deadline in 7 days (" + ch.getSubmissionDeadline() + "). "
                    + "Current progress: " + ch.getCompletionPct() + "%",
                ch.getId(),
                "CHAPTER"
            );
        }

        for (ChapterSummary ch : chapterRepository.findChaptersWithDeadlineInDays(3)) {
            long mangakaId = chapterRepository.findOwnerMangakaByChapter(ch.getId());
            pageTaskRepository.createNotificationIfAbsentToday(
                mangakaId,
                "CHAPTER_DEADLINE_URGENT",
                "URGENT: Chapter #" + ch.getId() + " (Ch." + ch.getChapterNumber() + " - " + ch.getTitle()
                    + ") deadline in 3 days! Status: " + ch.getStatus()
                    + ", Progress: " + ch.getCompletionPct() + "%",
                ch.getId(),
                "CHAPTER"
            );
        }

        for (ChapterSummary ch : chapterRepository.findMissedSubmissionDeadlineChapters()) {
            long tantouId = chapterRepository.findSeriesTantou(ch.getSeriesId());
            pageTaskRepository.createNotificationIfAbsentToday(
                tantouId,
                "CHAPTER_SUBMISSION_MISSED",
                "Chapter #" + ch.getId() + " (Ch." + ch.getChapterNumber() + " - " + ch.getTitle()
                    + ") missed its submission deadline (" + ch.getSubmissionDeadline() + ").",
                ch.getId(),
                "CHAPTER"
            );
        }
    }
}
