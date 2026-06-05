package manga.controller.api.chaptertask;

// Chapter/task API group: handles uploaded chapter page images and keeps image workflow endpoints together.
import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.ChapterImageItem;
import manga.repository.chaptertask.ChapterImageRepository;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChapterImageApiController {

    @Autowired
    private ChapterImageRepository chapterImageRepository;

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
            fileUrl = trimToNull(request.getParameter("url"));
        }
        String originalFileName = trimToNull(request.getParameter("originalFileName"));
        Long fileSizeBytes = parseLong(request.getParameter("fileSizeBytes"));

        UploadInfo upload = saveMultipartFileIfPresent(request, pageTaskId, imageType);
        if (upload != null) {
            fileUrl = upload.path;
            originalFileName = upload.originalName;
            fileSizeBytes = Long.valueOf(upload.size);
        }

        if (fileSizeBytes == null) {
            fileSizeBytes = Long.valueOf(0L);
        }
        if (originalFileName == null && fileUrl != null) {
            originalFileName = originalNameFromUrl(fileUrl);
        }

        long id = chapterImageRepository.upload(
                chapterId,
                pageTaskId,
                user.getId(),
                imageType,
                pageNumber,
                fileUrl,
                originalFileName,
                fileSizeBytes.longValue());
        return ApiResponse.ok(chapterImageRepository.findById(id), "Chapter image uploaded");
    }

    @RequestMapping(value = "/chapters/{chapterId}/images", method = RequestMethod.GET)
    public ApiResponse<List<ChapterImageItem>> listByChapter(
            @PathVariable("chapterId") long chapterId,
            HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        requireCanReadChapter(chapterId, user);
        return ApiResponse.ok(chapterImageRepository.listByChapter(chapterId), "Chapter images");
    }

    @RequestMapping(value = "/tasks/{taskId}/images", method = RequestMethod.GET)
    public ApiResponse<List<ChapterImageItem>> listByTask(
            @PathVariable("taskId") long taskId,
            HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        long chapterId = chapterImageRepository.findTaskChapterId(taskId);
        requireCanReadTask(chapterId, taskId, user);
        return ApiResponse.ok(chapterImageRepository.listByTask(taskId), "Task images");
    }

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
        private final String path;
        private final String originalName;
        private final long size;

        private UploadInfo(String path, String originalName, long size) {
            this.path = path;
            this.originalName = originalName;
            this.size = size;
        }
    }
}

