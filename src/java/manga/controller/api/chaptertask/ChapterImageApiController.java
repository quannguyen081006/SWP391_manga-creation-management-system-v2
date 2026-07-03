package manga.controller.api.chaptertask;

// Chapter/task API group: handles uploaded chapter page images and keeps image workflow endpoints together.
import manga.common.ApiResponse;
import manga.common.util.ImagePhashUtil;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.ChapterImageItem;
import manga.repository.chaptertask.ChapterImageRepository;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller quan ly anh trang cua chapter/task — nhom /api/v1.
 *
 * MUC LUC:
 *  1. upload()          - POST   /api/v1/chapters/{chapterId}/images  - Upload anh trang cho chapter
 *  2. listByChapter()   - GET    /api/v1/chapters/{chapterId}/images  - Lay danh sach anh theo chapter
 *  3. listByTask()      - GET    /api/v1/tasks/{taskId}/images        - Lay danh sach anh theo task
 *  4. deactivate()      - DELETE /api/v1/images/{imageId}             - Xoa mem (deactivate) anh
 *
 * HELPER METHODS (private):
 *  - requireCanReadChapter() - Kiem tra quyen xem anh cua chapter
 *  - requireCanReadTask()    - Kiem tra quyen xem anh cua task
 *  - saveMultipartFileIfPresent() - Luu file upload xuong server neu co
 *  - findFilePart()          - Tim Part file trong request (thu "file", "image", "upload")
 *  - copy()                  - Ghi stream vao file
 *  - extractFileName()       - Lay ten file goc tu Content-Disposition header
 *  - sanitizeFileName()      - Lam sach ten file (bo ky tu dac biet)
 *  - originalNameFromUrl()   - Lay ten file tu URL neu khong upload truc tiep
 */
@RestController
@RequestMapping("/api/v1")
public class ChapterImageApiController {

    @Autowired
    private ChapterImageRepository chapterImageRepository;

    // ============================================================
    // 1. UPLOAD ANH TRANG
    // POST /api/v1/chapters/{chapterId}/images
    // - TANTOU_EDITOR khong duoc upload (chi doc)
    // - Ho tro 2 cach truyen file:
    //     a) Multipart upload (file dinh kem) -> saveMultipartFileIfPresent() xu ly
    //     b) URL co san (fileUrl hoac url param) -> luu thang URL vao DB
    // - Neu ca hai cung co: multipart duoc uu tien, ghi de fileUrl
    // - pageTaskId va imageType quyet dinh luu vao thu muc "task" hay "chapter"
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/images", method = RequestMethod.POST)
    public ApiResponse<ChapterImageItem> upload(
            @PathVariable("chapterId") long chapterId,
            HttpSession session,
            HttpServletRequest request) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        if (user.hasRole("TANTOU_EDITOR")) {
            throw new IllegalArgumentException("TANTOU_EDITOR can only read chapter images");
        }

        String imageType = request.getParameter("imageType");
        Long pageTaskId = parseLong(request.getParameter("pageTaskId"));
        Integer pageNumber = parseInteger(request.getParameter("pageNumber"));
        String fileUrl = trimToNull(request.getParameter("fileUrl"));
        if (fileUrl == null) {
            // Ho tro them param ten "url" de tuong thich voi client cu
            fileUrl = trimToNull(request.getParameter("url"));
        }
        String originalFileName = trimToNull(request.getParameter("originalFileName"));
        Long fileSizeBytes = parseLong(request.getParameter("fileSizeBytes"));

        // Neu co file multipart thi ghi de fileUrl/originalFileName/fileSizeBytes tu params
        UploadInfo upload = saveMultipartFileIfPresent(request, pageTaskId, imageType);
        if (upload != null) {
            fileUrl = upload.path;
            originalFileName = upload.originalName;
            fileSizeBytes = Long.valueOf(upload.size);
        }

        // Mac dinh size = 0 neu khong xac dinh duoc
        if (fileSizeBytes == null) {
            fileSizeBytes = Long.valueOf(0L);
        }
        // Neu khong co ten file thi lay tu URL
        if (originalFileName == null && fileUrl != null) {
            originalFileName = originalNameFromUrl(fileUrl);
        }

        // Tinh pHash cho file vua luu (chi khi upload truc tiep, khong ap dung khi chi truyen URL co san)
        String imagePhash = null;
        File savedFile = null;
        if (upload != null) {
            requireImageExtension(originalFileName);
            savedFile = new File(request.getServletContext().getRealPath(upload.path));
            imagePhash = ImagePhashUtil.hashOf(savedFile);
        }

        long id;
        try {
            id = chapterImageRepository.upload(
                    chapterId,
                    pageTaskId,
                    user.getId(),
                    imageType,
                    pageNumber,
                    fileUrl,
                    originalFileName,
                    fileSizeBytes.longValue(),
                    imagePhash);
        } catch (IllegalArgumentException ex) {
            // Upload bi tu choi (vd: trung anh) sau khi file da luu xuong dia -> don rac
            if (savedFile != null) {
                savedFile.delete();
            }
            throw ex;
        }
        return ApiResponse.ok(chapterImageRepository.findById(id), "Chapter image uploaded");
    }

    // HELPER: Chi cho phep JPG/PNG/WEBP truoc khi tinh hash, tranh loi 500 khi upload nham file khong phai anh
    private void requireImageExtension(String originalFileName) {
        String name = originalFileName == null ? "" : originalFileName.toLowerCase(Locale.ENGLISH);
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg")
                && !name.endsWith(".png") && !name.endsWith(".webp")) {
            throw new IllegalArgumentException("Chỉ chấp nhận file ảnh JPG, PNG hoặc WEBP");
        }
    }

    // ============================================================
    // 2. LIST ANH THEO CHAPTER
    // GET /api/v1/chapters/{chapterId}/images
    // - Kiem tra quyen qua requireCanReadChapter():
    //     ADMIN: luon duoc
    //     MANGAKA: phai la chu cua chapter do
    //     TANTOU_EDITOR: phai la editor duoc assign vao series chua chapter do
    //     ASSISTANT: phai co task duoc giao trong chapter do
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/images", method = RequestMethod.GET)
    public ApiResponse<List<ChapterImageItem>> listByChapter(
            @PathVariable("chapterId") long chapterId,
            HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        requireCanReadChapter(chapterId, user);
        return ApiResponse.ok(chapterImageRepository.listByChapter(chapterId), "Chapter images");
    }

    // ============================================================
    // 3. LIST ANH THEO TASK
    // GET /api/v1/tasks/{taskId}/images
    // - Lay chapterId tu task truoc, sau do kiem tra quyen qua requireCanReadTask()
    // - ASSISTANT chi xem duoc neu chinh ho duoc assign task do
    //   (khac voi listByChapter: assistant chi can co BAT KY task nao trong chapter)
    // ============================================================
    @RequestMapping(value = "/tasks/{taskId}/images", method = RequestMethod.GET)
    public ApiResponse<List<ChapterImageItem>> listByTask(
            @PathVariable("taskId") long taskId,
            HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        long chapterId = chapterImageRepository.findTaskChapterId(taskId);
        requireCanReadTask(chapterId, taskId, user);
        return ApiResponse.ok(chapterImageRepository.listByTask(taskId), "Task images");
    }

    // ============================================================
    // 4. DEACTIVATE ANH (XOA MEM)
    // DELETE /api/v1/images/{imageId}
    // - TANTOU_EDITOR khong duoc xoa
    // - Khong xoa cung khoi DB, chi danh dau inactive (soft delete)
    // - Quyen xoa chi kiem tra role, khong kiem tra them ownership cu the
    // ============================================================
    @RequestMapping(value = "/images/{imageId}", method = RequestMethod.DELETE)
    public ApiResponse<Object> deactivate(
            @PathVariable("imageId") long imageId,
            HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        if (user.hasRole("TANTOU_EDITOR")) {
            throw new IllegalArgumentException("TANTOU_EDITOR cannot delete chapter images");
        }
        chapterImageRepository.deactivate(imageId, user.getId());
        return ApiResponse.ok(null, "Chapter image deactivated");
    }

    // ============================================================
    // HELPER: KIEM TRA QUYEN XEM ANH CHAPTER
    // Thu tu uu tien: ADMIN -> MANGAKA (chu chapter) -> TANTOU (duoc assign) -> ASSISTANT (co task trong chapter)
    // Nem IllegalArgumentException neu khong thoa man bat ky dieu kien nao
    // ============================================================
    private void requireCanReadChapter(long chapterId, AuthenticatedUser user) {
        if (user.hasRole("ADMIN")) {
            return;
        }
        if (user.hasRole("MANGAKA") && chapterImageRepository.findChapterOwnerMangaka(chapterId) == user.getId()) {
            return;
        }
        if (user.hasRole("TANTOU_EDITOR") && chapterImageRepository.findChapterTantouEditor(chapterId) == user.getId()) {
            return;
        }
        if (user.hasRole("ASSISTANT") && chapterImageRepository.hasAssignedTaskInChapter(chapterId, user.getId())) {
            return;
        }
        throw new IllegalArgumentException("Only assigned users can view chapter images");
    }

    // ============================================================
    // HELPER: KIEM TRA QUYEN XEM ANH TASK
    // Giong requireCanReadChapter nhung ASSISTANT phai duoc assign chinh task do
    // (khong du chi co task trong cung chapter)
    // ============================================================
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
    // HELPER: LUU FILE MULTIPART XUONG SERVER
    // - Tra ve null neu request khong phai multipart
    // - Thu lan luot cac field: "file", "image", "upload" (findFilePart)
    // - Luu vao /img/task/ neu co pageTaskId hoac imageType = "PAGE", nguoc lai /img/chapter/
    // - Ten file: {timestamp}_{sanitizedName} de tranh trung lap
    // ============================================================
    private UploadInfo saveMultipartFileIfPresent(HttpServletRequest request, Long pageTaskId, String imageType) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/")) {
            return null;
        }

        try {
            Part part = findFilePart(request);
            if (part == null || part.getSize() <= 0) {
                return null;
            }

            String originalName = extractFileName(part);
            String storedName = System.currentTimeMillis() + "_" + sanitizeFileName(originalName);
            boolean taskImage = pageTaskId != null
                    || "PAGE".equalsIgnoreCase(trimToNull(imageType));
            String folder = taskImage ? "task" : "chapter";
            String publicBase = "/img/" + folder;
            String uploadPath = request.getServletContext().getRealPath(publicBase);
            if (uploadPath == null) {
                throw new IllegalArgumentException("Cannot resolve upload directory");
            }
            File dir = new File(uploadPath);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalArgumentException("Cannot create upload directory");
            }

            File target = new File(dir, storedName);
            copy(part, target);
            return new UploadInfo(publicBase + "/" + storedName, originalName, part.getSize());
        } catch (IOException ex) {
            throw new RuntimeException("Cannot save uploaded image", ex);
        } catch (ServletException ex) {
            throw new RuntimeException("Invalid multipart image upload", ex);
        }
    }

    // HELPER: Thu lan luot "file" -> "image" -> "upload", tra ve Part dau tien co size > 0
    private Part findFilePart(HttpServletRequest request) throws IOException, ServletException {
        Part part = request.getPart("file");
        if (part != null && part.getSize() > 0) {
            return part;
        }
        part = request.getPart("image");
        if (part != null && part.getSize() > 0) {
            return part;
        }
        part = request.getPart("upload");
        if (part != null && part.getSize() > 0) {
            return part;
        }
        return null;
    }

    // HELPER: Ghi noi dung Part vao File dich voi buffer 8KB
    private void copy(Part part, File target) throws IOException {
        byte[] buffer = new byte[8192];
        try (InputStream in = part.getInputStream();
             FileOutputStream out = new FileOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    // HELPER: Lay ten file goc tu Content-Disposition header cua Part
    // Fallback ve "chapter-image" neu khong tim thay
    private String extractFileName(Part part) {
        String header = part.getHeader("content-disposition");
        if (header != null) {
            String[] items = header.split(";");
            for (String item : items) {
                String trimmed = item.trim();
                if (trimmed.startsWith("filename=")) {
                    String value = trimmed.substring("filename=".length()).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return sanitizeFileName(value);
                }
            }
        }
        return "chapter-image";
    }

    // HELPER: Lam sach ten file — chi giu [A-Za-z0-9._-], thay the con lai bang "_"
    // Cu the: chuan hoa separator, cat bo phan path, sanitize ky tu
    private String sanitizeFileName(String fileName) {
        String name = fileName == null ? "chapter-image" : fileName;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.trim().isEmpty()) {
            return "chapter-image";
        }
        return name;
    }

    // HELPER: Lay ten file tu URL (bo query string, lay phan sau dau / cuoi cung)
    // Fallback ve "external-image" neu URL khong xac dinh duoc ten file
    private String originalNameFromUrl(String fileUrl) {
        String value = fileUrl;
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0 && slash < value.length() - 1) {
            value = value.substring(slash + 1);
        }
        value = sanitizeFileName(value);
        if (value.trim().isEmpty()) {
            return "external-image";
        }
        return value;
    }

    private Long parseLong(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return Long.valueOf(trimmed);
    }

    private Integer parseInteger(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return Integer.valueOf(trimmed);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static class UploadInfo {
        private final String path;         // Duong dan relative tren server (luu vao DB)
        private final String originalName; // Ten file goc tu client (dung khi hien thi)
        private final long size;           // Kich thuoc file (bytes)

        private UploadInfo(String path, String originalName, long size) {
            this.path = path;
            this.originalName = originalName;
            this.size = size;
        }
    }
}
