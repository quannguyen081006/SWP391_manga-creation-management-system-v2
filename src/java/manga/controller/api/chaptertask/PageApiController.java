package manga.controller.api.chaptertask;

// Chapter/task API group: page slot changes refresh chapter progress through PageService.
import manga.common.ApiResponse;
import manga.common.util.ImagePhashUtil;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.PageRevisionEntry;
import manga.model.chaptertask.PageSlotSummary;
import manga.service.chaptertask.PageService;
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
 * All business logic (ownership checks, duplicate-image checks, chapter-status
 * gates, progress refresh) lives in PageService; this controller only handles
 * HTTP/session/file-upload concerns and delegates to the service.
 *
 * TABLE OF CONTENTS:
 *  1. listByChapter() - GET    /api/v1/chapters/{chapterId}/pages   - List page slots by chapter
 *  2. create()        - POST   /api/v1/pages                        - Create a new page slot
 *  3. uploadImage()   - POST   /api/v1/pages/{pageId}/upload        - Upload an image for a page slot
 *  3b. history()      - GET    /api/v1/pages/{pageId}/history       - List a page's revision history
 *  3c. rollback()     - POST   /api/v1/pages/{pageId}/rollback      - Roll a page back to a history point
 *  4. delete()        - DELETE /api/v1/pages/{pageId}               - Delete a page slot
 *
 * HELPER:
 *  - saveMultipart()  - Save an image file from a multipart request to /img/chapter/
 */
@RestController
@RequestMapping("/api/v1")
public class PageApiController {

    @Autowired
    private PageService pageService;

    // ============================================================
    // 1. LIST PAGE SLOTS BY CHAPTER
    // GET /api/v1/chapters/{chapterId}/pages
    // - Only requires a login check, no role filtering
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/pages", method = RequestMethod.GET)
    public ApiResponse<List<PageSlotSummary>> listByChapter(@PathVariable("chapterId") long chapterId, HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageService.listByChapter(chapterId), "Chapter pages");
    }

    // ============================================================
    // 2. CREATE A NEW PAGE SLOT
    // POST /api/v1/pages
    // - Only the MANGAKA who owns the chapter can add one (double check: role + ownership)
    // - pageNumber is optional: if not provided or <= 0, the next number is auto-assigned
    // - After creation: PageService refreshes the chapter's completion %
    // ============================================================
    @RequestMapping(value = "/pages", method = RequestMethod.POST)
    public ApiResponse<PageSlotSummary> create(
            HttpSession session,
            @RequestParam("chapterId") long chapterId,
            @RequestParam(value = "pageNumber", required = false) Integer pageNumber) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageService.create(chapterId, user, pageNumber), "Page slot created");
    }

    // ============================================================
    // 3. UPLOAD IMAGE FOR A PAGE SLOT
    // POST /api/v1/pages/{pageId}/upload
    // - Only the MANGAKA who owns the chapter can upload (double check: role + ownership)
    // - completedStage: optional, marks the completed stage (e.g. LETTERING)
    // - If completedStage = "LETTERING": PageService automatically syncs the image into ChapterImage
    //   (this is the final stage before submitting the manuscript)
    // - After upload: PageService refreshes the chapter's completion %
    // - File is saved at: /img/chapter/{page_timestamp.ext}
    // ============================================================
    @RequestMapping(value = "/pages/{pageId}/upload", method = RequestMethod.POST)
    public ApiResponse<PageSlotSummary> uploadImage(
            @PathVariable("pageId") long pageId,
            HttpSession session,
            HttpServletRequest request,
            @RequestParam(value = "completedStage", required = false) String completedStage) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        // Resolve + ownership-check the page slot before saving the file, to avoid wasting a write on a denied request.
        PageSlotSummary page = pageService.requireById(pageId);
        pageService.requireOwner(page, user, "Only chapter owner can upload page images");

        String savedPath = saveMultipart(request);
        File savedFile = new File(request.getServletContext().getRealPath(savedPath));
        try {
            requireImageExtension(savedPath);
            String imagePhash = ImagePhashUtil.hashOf(savedFile);
            PageSlotSummary updatedPage = pageService.uploadImage(page, user, savedPath, imagePhash, completedStage);
            return ApiResponse.ok(updatedPage, "Page image uploaded");
        } catch (IllegalArgumentException ex) {
            // Upload rejected (e.g. duplicate image) after the file was already saved to disk -> clean up
            savedFile.delete();
            throw ex;
        }
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
        return ApiResponse.ok(pageService.history(pageId), "Page history");
    }

    // ============================================================
    // 3c. ROLLBACK A PAGE TO A HISTORY POINT
    // POST /api/v1/pages/{pageId}/rollback  (param revisionId)
    // - Only the MANGAKA who owns the chapter can roll back (double check: role + ownership)
    // - Blocked once the chapter has been submitted for editorial review - the manuscript
    //   workspace snapshots pages off ChapterImage at that point, so quietly rewriting a
    //   page's image/stage after submission would desync the two out from under review.
    // - Restores both the image + stage; PageService then refreshes the chapter's completion %
    // ============================================================
    @RequestMapping(value = "/pages/{pageId}/rollback", method = RequestMethod.POST)
    public ApiResponse<PageSlotSummary> rollback(
            @PathVariable("pageId") long pageId,
            HttpSession session,
            @RequestParam("revisionId") long revisionId) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageService.rollback(pageId, user, revisionId), "Page rolled back");
    }

    // ============================================================
    // 4. DELETE A PAGE SLOT
    // DELETE /api/v1/pages/{pageId}
    // - Only the MANGAKA who owns the chapter can delete it (double check: role + ownership)
    // - After deletion: PageService refreshes the chapter's completion %
    // NOTE: This is a hard delete, there is no soft delete here
    // ============================================================
    @RequestMapping(value = "/pages/{pageId}", method = RequestMethod.DELETE)
    public ApiResponse<Object> delete(
            @PathVariable("pageId") long pageId,
            HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        pageService.delete(pageId, user);
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
