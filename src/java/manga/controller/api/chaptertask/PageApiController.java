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
 * REST API controller quan ly page slot cua chapter — nhom /api/v1.
 * Moi thay doi page slot (them/xoa/upload) deu goi refreshChapterProgress()
 * de cap nhat % hoan thanh chapter.
 *
 * MUC LUC:
 *  1. listByChapter() - GET    /api/v1/chapters/{chapterId}/pages   - Lay danh sach page slot theo chapter
 *  2. create()        - POST   /api/v1/pages                        - Tao page slot moi
 *  3. uploadImage()   - POST   /api/v1/pages/{pageId}/upload        - Upload anh cho page slot
 *  4. delete()        - DELETE /api/v1/pages/{pageId}               - Xoa page slot
 *
 * HELPER:
 *  - saveMultipart()  - Luu file anh tu multipart request xuong /img/chapter/
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
    // 1. LIST PAGE SLOTS THEO CHAPTER
    // GET /api/v1/chapters/{chapterId}/pages
    // - Chi can check login, khong filter theo role
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/pages", method = RequestMethod.GET)
    public ApiResponse<List<PageSlotSummary>> listByChapter(@PathVariable("chapterId") long chapterId, HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageRepository.listByChapter(chapterId), "Chapter pages");
    }

    // ============================================================
    // 2. TAO PAGE SLOT MOI
    // POST /api/v1/pages
    // - Chi MANGAKA chu cua chapter moi duoc them (double check: role + ownership)
    // - pageNumber optional: neu khong truyen hoac <= 0 thi tu dong lay so tiep theo
    // - Sau khi tao: goi refreshChapterProgress() cap nhat % chapter
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
    // 3. UPLOAD ANH CHO PAGE SLOT
    // POST /api/v1/pages/{pageId}/upload
    // - Chi MANGAKA chu cua chapter moi duoc upload (double check: role + ownership)
    // - completedStage: optional, danh dau giai doan hoan thanh (vd: LETTERING)
    // - Neu completedStage = "LETTERING": tu dong sync anh vao ChapterImageRepository
    //   (day la giai doan cuoi cung truoc khi nop manuscript)
    // - Sau khi upload: goi refreshChapterProgress() cap nhat % chapter
    // - File luu tai: /img/chapter/{page_timestamp.ext}
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
        // Tinh pHash + chan trung anh tren pham vi ca chapter (giong luong assistant).
        // Neu trung -> xoa file vua luu roi bao loi.
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
        pageRepository.markUploaded(pageId, savedPath, user.getId(), completedStage);
        PageSlotSummary updatedPage = pageRepository.findById(pageId);
        // Neu stage la LETTERING (stage cuoi): dong bo anh vao bang chapter_image (kem hash de dedup)
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

    // HELPER: Chi cho phep JPG/PNG/WEBP truoc khi tinh hash, tranh loi 500 khi upload nham file khong phai anh
    private void requireImageExtension(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ENGLISH);
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg")
                && !name.endsWith(".png") && !name.endsWith(".webp")) {
            throw new IllegalArgumentException("Chỉ chấp nhận file ảnh JPG, PNG hoặc WEBP");
        }
    }

    // ============================================================
    // 3b. LICH SU THAY DOI ANH/STAGE CUA PAGE
    // GET /api/v1/pages/{pageId}/history
    // - Chi can login (giong listByChapter) - moi nguoi co quyen vao chapter deu xem duoc
    // ============================================================
    @RequestMapping(value = "/pages/{pageId}/history", method = RequestMethod.GET)
    public ApiResponse<List<PageRevisionEntry>> history(@PathVariable("pageId") long pageId, HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageRepository.listRevisions(pageId), "Page history");
    }

    // ============================================================
    // 3c. ROLLBACK PAGE VE 1 MOC LICH SU
    // POST /api/v1/pages/{pageId}/rollback  (param revisionId)
    // - Chi MANGAKA chu chapter moi duoc rollback (double check: role + ownership)
    // - Khoi phuc ca anh + stage; sau do refreshChapterProgress()
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
        pageRepository.rollbackToRevision(pageId, revisionId, user.getId());
        pageTaskRepository.refreshChapterProgress(page.getChapterId());
        return ApiResponse.ok(pageRepository.findById(pageId), "Page rolled back");
    }

    // ============================================================
    // 4. XOA PAGE SLOT
    // DELETE /api/v1/pages/{pageId}
    // - Chi MANGAKA chu cua chapter moi duoc xoa (double check: role + ownership)
    // - Sau khi xoa: goi refreshChapterProgress() cap nhat % chapter
    // NOTE: Xoa cung (hard delete), khong co soft delete o day
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
    // HELPER: LUU FILE ANH TU MULTIPART REQUEST
    // - Chi lay Part ten "file", bat buoc phai co
    // - Ten file luu: page_{timestamp}{ext}, luu vao /img/chapter/
    // - Extension lay tu ten file goc, fallback ".png"
    // - Nem RuntimeException neu khong luu duoc
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
