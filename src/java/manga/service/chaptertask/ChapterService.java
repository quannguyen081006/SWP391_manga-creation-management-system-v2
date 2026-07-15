package manga.service.chaptertask;

import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.ChapterSummary;
import manga.repository.chaptertask.ChapterRepository;
import manga.repository.chaptertask.PageTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.List;

/**
 * ============================================================
 * ChapterService - Chapter management business logic
 * ============================================================
 *
 * TABLE OF CONTENTS:
 * ----------------------------------------------------------
 * [1] CHAPTER QUERIES
 *     - listAll()              : List chapters according to user permissions
 *     - listBySeries()         : List chapters belonging to a series
 *     - getDetail()            : Get details of a chapter
 * [2] CREATE & UPDATE CHAPTER (Mangaka)
 *     - create()               : Create a new chapter
 *     - update()               : Update title / submissionDeadline
 * [3] CHAPTER LIFECYCLE
 *     - submitForReview()      : Submit chapter for Tantou Editor review
 *     - delete()               : Delete chapter
 * [4] DEADLINE REMINDERS (Scheduler)
 *     - remindApproachingDeadlines(): Remind at 7 days, 3 days, and missed deadline
 * ============================================================
 */
@Service
public class ChapterService {

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private PageTaskRepository pageTaskRepository;

    // ============================================================
    // [1] CHAPTER QUERIES
    // ============================================================

    /** List chapters visible to the user (permission handling is done in the repository) */
    public List<ChapterSummary> listAll(AuthenticatedUser user) {
        return chapterRepository.listAll(user);
    }

    /** List all chapters belonging to a series */
    public List<ChapterSummary> listBySeries(long seriesId) {
        return chapterRepository.listBySeries(seriesId);
    }

    /** Get chapter details; throws exception if not found */
    public ChapterSummary getDetail(long chapterId) {
        ChapterSummary chapter = chapterRepository.findById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("Chapter not found");
        }
        return chapter;
    }

    // ============================================================
    // [2] CREATE & UPDATE CHAPTER (Mangaka)
    // ============================================================

    /**
     * Create a new chapter within a series.
     * Requirements: the user must be MANGAKA and the owner of the series.
     * totalPages must be >= 1.
     */
    public ChapterSummary create(long seriesId, AuthenticatedUser user, String title, String submissionDeadline, int totalPages) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can create chapter");

        long ownerId = chapterRepository.findSeriesOwnerMangaka(seriesId);
        if (ownerId != user.getId()) {
            throw new IllegalArgumentException("Only series owner can create chapter");
        }

        if (totalPages < 1) {
            throw new IllegalArgumentException("totalPages must be at least 1");
        }

        long id = chapterRepository.createNext(seriesId, title, Date.valueOf(submissionDeadline), totalPages);
        return getDetail(id);
    }

    /**
     * Update the title and/or submissionDeadline of a chapter.
     * If no deadline is passed, only the title is updated.
     */
    public ChapterSummary update(
            long chapterId,
            AuthenticatedUser user,
            String title,
            String submissionDeadline) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can update chapter");

        long ownerId = chapterRepository.findOwnerMangakaByChapter(chapterId);
        if (ownerId != user.getId()) {
            throw new IllegalArgumentException("Only series owner can update chapter");
        }

        ChapterSummary existing = getDetail(chapterId);
        // Keep the existing title if no new title is passed
        String nextTitle = (title == null || title.trim().isEmpty()) ? existing.getTitle() : title;

        if (submissionDeadline == null || submissionDeadline.trim().isEmpty()) {
            // No deadline provided -> only update title
            chapterRepository.updateChapterTitle(chapterId, nextTitle);
            return getDetail(chapterId);
        }

        chapterRepository.updateChapterMetadata(chapterId, nextTitle, Date.valueOf(submissionDeadline));
        return getDetail(chapterId);
    }

    // ============================================================
    // [3] CHAPTER LIFECYCLE
    // ============================================================

    /** Mangaka submits chapter for Tantou Editor review */
    public void submitForReview(long chapterId, AuthenticatedUser user) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can submit chapter for review");
        chapterRepository.submitForReview(chapterId, user.getId());
    }

    /** Mangaka deletes a chapter (can only delete their own chapters) */
    public void delete(long chapterId, AuthenticatedUser user) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can delete chapter");
        chapterRepository.deleteChapter(chapterId, user.getId());
    }

    // ============================================================
    // [4] DEADLINE REMINDERS (Scheduler)
    // ============================================================

    /**
     * Sends chapter deadline reminder notifications at 3 levels, invoked by ChapterDeadlineScheduler (daily at 09:00):
     *
     *   - 7 days before deadline -> CHAPTER_DEADLINE_SOON notification to Mangaka
     *   - 3 days before deadline -> CHAPTER_DEADLINE_URGENT notification to Mangaka
     *   - Missed deadline        -> CHAPTER_SUBMISSION_MISSED notification to Tantou Editor
     *
     * Uses createNotificationIfAbsentToday to avoid sending duplicates on the same day.
     */
    public void remindApproachingDeadlines() {
        // Remind Mangaka: 7 days left
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

        // Remind Mangaka: only 3 days left (urgent)
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

        // Notify Tantou Editor: chapter has missed its submission deadline
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

    public long findOwnerMangakaByChapter(long chapterId){
        return chapterRepository.findOwnerMangakaByChapter(chapterId);
    }

    public long findSeriesTantou(long chapterId){
        return chapterRepository.findSeriesTantou(chapterId);
    }

    /** Gets the chapter's current status (e.g. PLANNING, IN_PROGRESS, EDITORIAL_REVIEW). */
    public String getChapterStatus(long chapterId) {
        return chapterRepository.getChapterStatus(chapterId);
    }
}
