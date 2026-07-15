package manga.service.chaptertask;

import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.PageRevisionEntry;
import manga.model.chaptertask.PageSlotSummary;
import manga.repository.chaptertask.PageRepository;
import manga.repository.chaptertask.PageTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ============================================================
 * PageService - Chapter page slot business logic
 * ============================================================
 *
 * TABLE OF CONTENTS:
 * ----------------------------------------------------------
 * [1] listByChapter()  : List page slots by chapter
 * [2] create()         : Create a new page slot (Mangaka)
 * [3] uploadImage()    : Upload an already-saved image for a page slot (Mangaka)
 * [4] history()        : List a page's revision history
 * [5] rollback()       : Roll back a page to a history point (Mangaka)
 * [6] delete()         : Delete a page slot (Mangaka)
 * ============================================================
 */
@Service
public class PageService {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private ChapterImageService chapterImageService;

    @Autowired
    private PageTaskRepository pageTaskRepository;

    // ============================================================
    // [1] LIST PAGE SLOTS BY CHAPTER
    // ============================================================

    public List<PageSlotSummary> listByChapter(long chapterId) {
        return pageRepository.listByChapter(chapterId);
    }

    // ============================================================
    // [2] CREATE A NEW PAGE SLOT
    // ============================================================

    /**
     * Creates a new page slot. Requirements: MANGAKA and owner of the chapter's series.
     * pageNumber is optional: if not provided or <= 0, the next number is auto-assigned.
     */
    public PageSlotSummary create(long chapterId, AuthenticatedUser user, Integer pageNumber) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can add page slots");
        long ownerId = chapterService.findOwnerMangakaByChapter(chapterId);
        if (ownerId != user.getId()) {
            throw new IllegalArgumentException("Only series owner can add pages");
        }
        int next = pageNumber != null && pageNumber.intValue() > 0
                ? pageNumber.intValue() : pageRepository.nextPageNumber(chapterId);
        long pageId = pageRepository.create(chapterId, next);
        pageTaskRepository.refreshChapterProgress(chapterId);
        return pageRepository.findById(pageId);
    }

    // ============================================================
    // [3] UPLOAD IMAGE FOR A PAGE SLOT
    // ============================================================

    /** Finds a page slot, throwing if not found. Used by the controller to resolve chapterId before saving a file. */
    public PageSlotSummary requireById(long pageId) {
        PageSlotSummary page = pageRepository.findById(pageId);
        if (page == null) {
            throw new IllegalArgumentException("Page not found");
        }
        return page;
    }

    /** Checks that the user is the MANGAKA who owns the page's chapter/series, throwing otherwise. */
    public void requireOwner(PageSlotSummary page, AuthenticatedUser user, String errorMessage) {
        long ownerId = chapterService.findOwnerMangakaByChapter(page.getChapterId());
        if (!user.hasRole("MANGAKA") || ownerId != user.getId()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Persists an already-saved+hashed image for a page slot. The caller must have already
     * resolved and ownership-checked the page (see requireById()/requireOwner()).
     * - Blocks reuse of an image hash already used anywhere in the chapter.
     * - If completedStage = "LETTERING": also syncs the image into ChapterImage (final stage before manuscript submission).
     * - Refreshes the chapter's completion percentage afterward.
     */
    public PageSlotSummary uploadImage(PageSlotSummary page, AuthenticatedUser user, String savedPath, String imagePhash, String completedStage) {
        chapterImageService.checkDuplicateImageInChapter(page.getChapterId(), imagePhash);

        pageRepository.markUploaded(page.getId(), savedPath, user.getId(), completedStage, imagePhash);
        PageSlotSummary updatedPage = pageRepository.findById(page.getId());
        if (updatedPage != null && "LETTERING".equalsIgnoreCase(updatedPage.getCompletedStage())) {
            chapterImageService.syncFinalPageUpload(
                    updatedPage.getChapterId(),
                    updatedPage.getPageNumber(),
                    user.getId(),
                    savedPath,
                    imagePhash);
        }
        pageTaskRepository.refreshChapterProgress(page.getChapterId());
        return updatedPage;
    }

    // ============================================================
    // [4] PAGE HISTORY
    // ============================================================

    public List<PageRevisionEntry> history(long pageId) {
        return pageRepository.listRevisions(pageId);
    }

    // ============================================================
    // [5] ROLLBACK
    // ============================================================

    /**
     * Rolls a page back to a prior revision. Requirements: MANGAKA and chapter owner.
     * Blocked once the chapter has been submitted for editorial review (BR: manuscript workspace
     * snapshots pages off ChapterImage at that point; rewriting a page afterward would desync the two).
     */
    public PageSlotSummary rollback(long pageId, AuthenticatedUser user, long revisionId) {
        PageSlotSummary page = requireById(pageId);
        requireOwner(page, user, "Only chapter owner can rollback page");

        String chapterStatus = chapterService.getChapterStatus(page.getChapterId());
        if (!"PLANNING".equalsIgnoreCase(chapterStatus) && !"IN_PROGRESS".equalsIgnoreCase(chapterStatus)) {
            throw new IllegalArgumentException(
                    "Cannot rollback a page after the chapter has been submitted for editorial review");
        }

        pageRepository.rollbackToRevision(pageId, revisionId, user.getId());
        pageTaskRepository.refreshChapterProgress(page.getChapterId());
        return pageRepository.findById(pageId);
    }

    // ============================================================
    // [6] DELETE A PAGE SLOT
    // ============================================================

    /** Hard-deletes a page slot. Requirements: MANGAKA and chapter owner. */
    public void delete(long pageId, AuthenticatedUser user) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can delete page slots");
        PageSlotSummary page = requireById(pageId);
        requireOwner(page, user, "Only chapter owner can delete pages");
        pageRepository.delete(pageId);
        pageTaskRepository.refreshChapterProgress(page.getChapterId());
    }
}
