package manga.service.chaptertask;

import manga.model.AuthenticatedUser;
import manga.model.chaptertask.ChapterImageItem;
import manga.repository.chaptertask.ChapterImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ============================================================
 * ChapterImageService - Chapter image business logic
 * ============================================================
 *
 * TABLE OF CONTENTS:
 * ----------------------------------------------------------
 * [1] upload()                - Upload a new PAGE image (permission + persist)
 * [2] findById()               - Find an image by ID
 * [3] listByTask()              - List a task's images (permission-checked)
 * [4] deactivate()             - Soft-delete an image (permission-checked)
 * [5] checkDuplicateImageInChapter() - Block reuse of an already-used image hash
 * [6] syncFinalPageUpload()    - Sync a Mangaka's LETTERING-stage page upload into ChapterImage
 * ============================================================
 */
@Service
public class ChapterImageService {

    @Autowired
    private ChapterImageRepository chapterImageRepository;

    // ============================================================
    // [1] UPLOAD
    // ============================================================

    /** Uploads a PAGE image submitted by the assigned ASSISTANT for a task. TANTOU_EDITOR cannot upload. */
    public long upload(AuthenticatedUser user, long chapterId, Long pageTaskId, Integer pageNumber,
            String fileUrl, String originalFileName, long fileSizeBytes, String imagePhash) {
        if (user.hasRole("TANTOU_EDITOR")) {
            throw new IllegalArgumentException("TANTOU_EDITOR can only read chapter images");
        }
        return chapterImageRepository.upload(
                chapterId, pageTaskId, user.getId(), pageNumber, fileUrl, originalFileName, fileSizeBytes, imagePhash);
    }

    // ============================================================
    // [2] FIND BY ID
    // ============================================================

    public ChapterImageItem findById(long id) {
        return chapterImageRepository.findById(id);
    }

    // ============================================================
    // [3] LIST BY TASK
    // ============================================================

    /**
     * Lists a task's images. Permission tiers: ADMIN sees all; MANGAKA must own the chapter's series;
     * TANTOU_EDITOR must be assigned to the chapter's series; ASSISTANT must be assigned to that specific task.
     */
    public List<ChapterImageItem> listByTask(long taskId, AuthenticatedUser user) {
        long chapterId = chapterImageRepository.findTaskChapterId(taskId);
        requireCanReadTask(chapterId, taskId, user);
        return chapterImageRepository.listByTask(taskId);
    }

    private void requireCanReadTask(long chapterId, long taskId, AuthenticatedUser user) {
        if (user.hasRole("ADMIN")) {
            return;
        }
        if (user.hasRole("MANGAKA") && chapterImageRepository.findChapterOwnerMangaka(chapterId) == user.getId()) {
            return;
        }
        if (user.hasRole("TANTOU_EDITOR") && chapterImageRepository.findChapterTantouEditor(chapterId) == user.getId()) {
            return;
        }
        if (user.hasRole("ASSISTANT") && chapterImageRepository.findTaskAssistantId(taskId) == user.getId()) {
            return;
        }
        throw new IllegalArgumentException("Only assigned users can view task images");
    }

    // ============================================================
    // [4] DEACTIVATE (SOFT DELETE)
    // ============================================================

    /** Soft-deletes an image. TANTOU_EDITOR cannot delete. */
    public void deactivate(long imageId, AuthenticatedUser user) {
        if (user.hasRole("TANTOU_EDITOR")) {
            throw new IllegalArgumentException("TANTOU_EDITOR cannot delete chapter images");
        }
        chapterImageRepository.deactivate(imageId, user.getId());
    }

    // ============================================================
    // [5] DUPLICATE IMAGE CHECK
    // ============================================================

    /** Blocks reuse of an image hash already used anywhere in the chapter (including rejected/replaced versions). */
    public void checkDuplicateImageInChapter(long chapterId, String imagePhash) {
        chapterImageRepository.checkDuplicateImageInChapter(chapterId, imagePhash);
    }

    // ============================================================
    // [6] SYNC FINAL (LETTERING) PAGE UPLOAD
    // ============================================================

    /** Mangaka syncs a finished (LETTERING-stage) page image from PageRepository into ChapterImage. */
    public void syncFinalPageUpload(long chapterId, int pageNumber, long uploadedBy, String fileUrl, String imagePhash) {
        chapterImageRepository.syncFinalPageUpload(chapterId, pageNumber, uploadedBy, fileUrl, imagePhash);
    }
}
