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
 * REST API controller that manages chapter/task page images — /api/v1 group.
 *
 * TABLE OF CONTENTS:
 *  1. upload()          - POST   /api/v1/chapters/{chapterId}/images  - Upload a page image for a chapter
 *  2. listByTask()      - GET    /api/v1/tasks/{taskId}/images        - List images by task
 *  3. deactivate()      - DELETE /api/v1/images/{imageId}             - Soft delete (deactivate) an image
 *
 * HELPER METHODS (private):
 *  - requireCanReadTask()    - Check permission to view a task's images
 *  - saveMultipartFileIfPresent() - Save the uploaded file to the server if present
 *  - findFilePart()          - Find the file Part in the request (tries "file", "image", "upload")
 *  - copy()                  - Write a stream to a file
 *  - extractFileName()       - Get the original file name from the Content-Disposition header
 *  - sanitizeFileName()      - Sanitize a file name (remove special characters)
 *  - originalNameFromUrl()   - Get the file name from a URL when not uploaded directly
 */
@RestController
@RequestMapping("/api/v1")
public class ChapterImageApiController {

    @Autowired
    private ChapterImageRepository chapterImageRepository;

    // ============================================================
    // 1. UPLOAD PAGE IMAGE
    // POST /api/v1/chapters/{chapterId}/images
    // - TANTOU_EDITOR is not allowed to upload (read-only)
    // - Supports 2 ways to provide the file:
    //     a) Multipart upload (attached file) -> handled by saveMultipartFileIfPresent()
    //     b) An existing URL (fileUrl or url param) -> save the URL directly to the DB
    // - If both are present: multipart takes priority and overwrites fileUrl
    // - pageTaskId and imageType decide whether to save into the "task" or "chapter" folder
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
            // Also support the "url" param name for compatibility with older clients
            fileUrl = trimToNull(request.getParameter("url"));
        }
        String originalFileName = trimToNull(request.getParameter("originalFileName"));
        Long fileSizeBytes = parseLong(request.getParameter("fileSizeBytes"));

        // If a multipart file is present, overwrite fileUrl/originalFileName/fileSizeBytes from it
        UploadInfo upload = saveMultipartFileIfPresent(request, pageTaskId, imageType);
        if (upload != null) {
            fileUrl = upload.path;
            originalFileName = upload.originalName;
            fileSizeBytes = Long.valueOf(upload.size);
        }

        // Default size to 0 if it cannot be determined
        if (fileSizeBytes == null) {
            fileSizeBytes = Long.valueOf(0L);
        }
        // If there's no file name, derive it from the URL
        if (originalFileName == null && fileUrl != null) {
            originalFileName = originalNameFromUrl(fileUrl);
        }

        // Compute the pHash for the saved file (only for direct uploads, not applicable when only a URL is provided)
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
            // Upload rejected (e.g. duplicate image) after the file was already saved to disk -> clean up
            if (savedFile != null) {
                savedFile.delete();
            }
            throw ex;
        }
        return ApiResponse.ok(chapterImageRepository.findById(id), "Chapter image uploaded");
    }

    // HELPER: Only allow JPG/PNG/WEBP before computing the hash, to avoid a 500 error when a non-image file is uploaded by mistake
    private void requireImageExtension(String originalFileName) {
        String name = originalFileName == null ? "" : originalFileName.toLowerCase(Locale.ENGLISH);
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg")
                && !name.endsWith(".png") && !name.endsWith(".webp")) {
            throw new IllegalArgumentException("Only JPG, PNG, or WEBP image files are accepted");
        }
    }

    // ============================================================
    // 2. LIST IMAGES BY TASK
    // GET /api/v1/tasks/{taskId}/images
    // - First get chapterId from the task, then check permission via requireCanReadTask()
    // - ASSISTANT can only view if they themselves are assigned to that task
    //   (unlike listByChapter, where the assistant only needs ANY task in the chapter)
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
    // 4. DEACTIVATE IMAGE (SOFT DELETE)
    // DELETE /api/v1/images/{imageId}
    // - TANTOU_EDITOR is not allowed to delete
    // - Does not hard delete from the DB, only marks it inactive (soft delete)
    // - Delete permission only checks role, no additional specific ownership check
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
    // ============================================================
    // HELPER: CHECK PERMISSION TO VIEW TASK IMAGES
    // Same permission tiers as chapter-level access, but ASSISTANT must be assigned to that specific task
    // (having any task in the same chapter is not enough)
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
    // HELPER: SAVE MULTIPART FILE TO THE SERVER
    // - Returns null if the request is not multipart
    // - Tries fields in order: "file", "image", "upload" (findFilePart)
    // - Saves to /img/task/ if pageTaskId is present or imageType = "PAGE", otherwise /img/chapter/
    // - File name: {timestamp}_{sanitizedName} to avoid collisions
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

    // HELPER: Tries "file" -> "image" -> "upload" in order, returns the first Part with size > 0
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

    // HELPER: Writes the Part's content to the target File using an 8KB buffer
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

    // HELPER: Gets the original file name from the Part's Content-Disposition header
    // Falls back to "chapter-image" if not found
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

    // HELPER: Sanitizes the file name — keeps only [A-Za-z0-9._-], replacing everything else with "_"
    // Specifically: normalizes the separator, strips the path portion, then sanitizes characters
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

    // HELPER: Gets the file name from a URL (strips the query string, takes the part after the last /)
    // Falls back to "external-image" if the file name cannot be determined from the URL
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
        private final String path;         // Relative path on the server (saved to DB)
        private final String originalName; // Original file name from the client (used when displaying)
        private final long size;           // File size (bytes)

        private UploadInfo(String path, String originalName, long size) {
            this.path = path;
            this.originalName = originalName;
            this.size = size;
        }
    }
}
