package manga.controller.api.chaptertask;

// Chapter/task API group: page slot changes refresh chapter progress through PageTaskRepository.
import manga.common.ApiResponse;
import manga.common.util.ImagePhashUtil;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.PageRevisionEntry;
import manga.model.chaptertask.PageSlotSummary;
import manga.repository.chaptertask.ChapterRepository;
import manga.repository.chaptertask.ChapterImageRepository;
import manga.repository.chaptertask.PageRepository;
import manga.repository.chaptertask.PageTaskRepository;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller that manages chapter page slots — /api/v1 group.
 * Every page slot change (add/delete/upload) calls refreshChapterProgress()
 * to update the chapter's completion percentage.
 *
 * TABLE OF CONTENTS:
 *  1. listByChapter() - GET    /api/v1/chapters/{chapterId}/pages   - List page slots by chapter
 *  2. create()        - POST   /api/v1/pages                        - Create a new page slot
 *  3. uploadImage()   - POST   /api/v1/pages/{pageId}/upload        - Upload an image for a page slot
 *  4. delete()        - DELETE /api/v1/pages/{pageId}               - Delete a page slot
 *
 * HELPER:
 *  - saveMultipart()  - Save an image file from a multipart request to /img/chapter/
 */
@RestController
@RequestMapping("/api/v1")
public class PageApiController {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private ChapterImageRepository chapterImageRepository;

    @Autowired
    private PageTaskRepository pageTaskRepository;

    // ============================================================
    // 1. LIST PAGE SLOTS BY CHAPTER
    // GET /api/v1/chapters/{chapterId}/pages
    // - Only requires a login check, no role filtering
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/pages", method = RequestMethod.GET)
    public ApiResponse<List<PageSlotSummary>> listByChapter(@PathVariable("chapterId") long chapterId, HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageRepository.listByChapter(chapterId), "Chapter pages");
    }

    // ============================================================
    // 2. CREATE A NEW PAGE SLOT
    // POST /api/v1/pages
    // - Only the MANGAKA who owns the chapter can add one (double check: role + ownership)
    // - pageNumber is optional: if not provided or <= 0, the next number is auto-assigned
    // - After creation: calls refreshChapterProgress() to update the chapter's %
    // ============================================================
    @RequestMapping(value = "/pages", method = RequestMethod.POST)
    public ApiResponse<PageSlotSummary> create(
            HttpSession session,
            @RequestParam("chapterId") long chapterId,
            @RequestParam(value = "pageNumber", required = false) Integer pageNumber) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can add page slots");
        long ownerId = chapterRepository.findOwnerMangakaByChapter(chapterId);
        if (ownerId != user.getId()) {
            throw new IllegalArgumentException("Only series owner can add pages");
        }
            int next = pageNumber != null && pageNumber > 0 ? pageNumber.intValue() : pageRepository.nextPageNumber(chapterId);
            long pageId = pageRepository.create(chapterId, next);
        pageTaskRepository.refreshChapterProgress(chapterId);
        return ApiResponse.ok(pageRepository.findById(pageId), "Page slot created");
    }

    // ============================================================
    // 3. UPLOAD IMAGE FOR A PAGE SLOT
    // POST /api/v1/pages/{pageId}/upload
    // - Only the MANGAKA who owns the chapter can upload (double check: role + ownership)
    // - completedStage: optional, marks the completed stage (e.g. LETTERING)
    // - If completedStage = "LETTERING": automatically syncs the image into ChapterImageRepository
    //   (this is the final stage before submitting the manuscript)
    // - After upload: calls refreshChapterProgress() to update the chapter's %
    // - File is saved at: /img/chapter/{page_timestamp.ext}
    // ============================================================
    @RequestMapping(value = "/pages/{pageId}/upload", method = RequestMethod.POST)
    public ApiResponse<PageSlotSummary> uploadImage(
            @PathVariable("pageId") long pageId,
            HttpSession session,
            HttpServletRequest request,
            @RequestParam(value = "completedStage", required = false) String completedStage) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        PageSlotSummary page = pageRepository.findById(pageId);
        if (page == null) {
            throw new IllegalArgumentException("Page not found");
        }
        long ownerId = chapterRepository.findOwnerMangakaByChapter(page.getChapterId());
        if (!user.hasRole("MANGAKA") || ownerId != user.getId()) {
            throw new IllegalArgumentException("Only chapter owner can upload page images");
        }
        String savedPath = saveMultipart(request);
        // Compute pHash + block duplicate images across the entire chapter (same as the assistant flow).
        // If a duplicate is found -> delete the just-saved file and report an error.
        File savedFile = new File(request.getServletContext().getRealPath(savedPath));
        String imagePhash;
        try {
            requireImageExtension(savedPath);
            imagePhash = ImagePhashUtil.hashOf(savedFile);
            chapterImageRepository.checkDuplicateImageInChapter(page.getChapterId(), imagePhash);
        } catch (IllegalArgumentException ex) {
            savedFile.delete();
            throw ex;
        }
        pageRepository.markUploaded(pageId, savedPath, user.getId(), completedStage, imagePhash);
        PageSlotSummary updatedPage = pageRepository.findById(pageId);
        // If the stage is LETTERING (final stage): sync the image into the chapter_image table (with hash for dedup)
        if (updatedPage != null && "LETTERING".equalsIgnoreCase(updatedPage.getCompletedStage())) {
            chapterImageRepository.syncFinalPageUpload(
                    updatedPage.getChapterId(),
                    updatedPage.getPageNumber(),
                    user.getId(),
                    savedPath,
                    imagePhash);
        }
        pageTaskRepository.refreshChapterProgress(page.getChapterId());
        return ApiResponse.ok(updatedPage, "Page image uploaded");
    }

    // HELPER: Only allow JPG/PNG/WEBP before computing the hash, to avoid a 500 error when a non-image file is uploaded by mistake
    private void requireImageExtension(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ENGLISH);
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg")
                && !name.endsWith(".png") && !name.endsWith(".webp")) {
            throw new IllegalArgumentException("Only JPG, PNG, or WEBP image files are accepted");
        }
    }

    // ============================================================
    // 3b. PAGE IMAGE/STAGE CHANGE HISTORY
    // GET /api/v1/pages/{pageId}/history
    // - Only requires login (same as listByChapter) - anyone with access to the chapter can view it
    // ============================================================
    @RequestMapping(value = "/pages/{pageId}/history", method = RequestMethod.GET)
    public ApiResponse<List<PageRevisionEntry>> history(@PathVariable("pageId") long pageId, HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageRepository.listRevisions(pageId), "Page history");
    }

    // ============================================================
    // 3c. ROLLBACK A PAGE TO A HISTORY POINT
    // POST /api/v1/pages/{pageId}/rollback  (param revisionId)
    // - Only the MANGAKA who owns the chapter can roll back (double check: role + ownership)
    // - Blocked once the chapter has been submitted for editorial review - the manuscript
    //   workspace snapshots pages off ChapterImage at that point, so quietly rewriting a
    //   page's image/stage after submission would desync the two out from under review.
    // - Restores both the image + stage; then calls refreshChapterProgress()
    // ============================================================
    @RequestMapping(value = "/pages/{pageId}/rollback", method = RequestMethod.POST)
    public ApiResponse<PageSlotSummary> rollback(
            @PathVariable("pageId") long pageId,
            HttpSession session,
            @RequestParam("revisionId") long revisionId) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        PageSlotSummary page = pageRepository.findById(pageId);
        if (page == null) {
            throw new IllegalArgumentException("Page not found");
        }
        long ownerId = chapterRepository.findOwnerMangakaByChapter(page.getChapterId());
        if (!user.hasRole("MANGAKA") || ownerId != user.getId()) {
            throw new IllegalArgumentException("Only chapter owner can rollback page");
        }
        String chapterStatus = chapterRepository.getChapterStatus(page.getChapterId());
        if (!"PLANNING".equalsIgnoreCase(chapterStatus) && !"IN_PROGRESS".equalsIgnoreCase(chapterStatus)) {
            throw new IllegalArgumentException(
                    "Cannot rollback a page after the chapter has been submitted for editorial review");
        }
        pageRepository.rollbackToRevision(pageId, revisionId, user.getId());
        pageTaskRepository.refreshChapterProgress(page.getChapterId());
        return ApiResponse.ok(pageRepository.findById(pageId), "Page rolled back");
    }

    // ============================================================
    // 4. DELETE A PAGE SLOT
    // DELETE /api/v1/pages/{pageId}
    // - Only the MANGAKA who owns the chapter can delete it (double check: role + ownership)
    // - After deletion: calls refreshChapterProgress() to update the chapter's %
    // NOTE: This is a hard delete, there is no soft delete here
    // ============================================================
    @RequestMapping(value = "/pages/{pageId}", method = RequestMethod.DELETE)
    public ApiResponse<Object> delete(
            @PathVariable("pageId") long pageId,
            HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can delete page slots");
        PageSlotSummary page = pageRepository.findById(pageId);
        if (page == null) {
            throw new IllegalArgumentException("Page not found");
        }
        long ownerId = chapterRepository.findOwnerMangakaByChapter(page.getChapterId());
        if (ownerId != user.getId()) {
            throw new IllegalArgumentException("Only chapter owner can delete pages");
        }
        pageRepository.delete(pageId);
        pageTaskRepository.refreshChapterProgress(page.getChapterId());
        return ApiResponse.ok(null, "Page deleted");
    }

    // ============================================================
    // HELPER: SAVE AN IMAGE FILE FROM A MULTIPART REQUEST
    // - Only reads the Part named "file", which is required
    // - Saved file name: page_{timestamp}{ext}, saved into /img/chapter/
    // - Extension taken from the original file name, falls back to ".png"
    // - Throws RuntimeException if it cannot be saved
    // ============================================================
    private String saveMultipart(HttpServletRequest request) {
        try {
            Part part = request.getPart("file");
            if (part == null || part.getSize() <= 0) {
                throw new IllegalArgumentException("Image file is required");
            }
            String original = part.getSubmittedFileName();
            String ext = ".png";
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf('.'));
            }
            String fileName = "page_" + System.currentTimeMillis() + ext;
            File dir = new File(request.getServletContext().getRealPath("/img/chapter"));
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Cannot create upload directory");
            }
            File target = new File(dir, fileName);
            try (InputStream in = part.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
            }
            return "/img/chapter/" + fileName;
        } catch (ServletException | IOException ex) {
            throw new RuntimeException("Cannot save uploaded file", ex);
        }
    }
}
