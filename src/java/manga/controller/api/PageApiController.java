package manga.controller.api;

import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.PageSlotSummary;
import manga.repository.ChapterRepository;
import manga.repository.PageRepository;
import manga.repository.PageTaskRepository;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PageApiController {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private PageTaskRepository pageTaskRepository;

    @RequestMapping(value = "/chapters/{chapterId}/pages", method = RequestMethod.GET)
    public ApiResponse<List<PageSlotSummary>> listByChapter(@PathVariable("chapterId") long chapterId, HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageRepository.listByChapter(chapterId), "Chapter pages");
    }

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
        pageRepository.markUploaded(pageId, savedPath, user.getId(), completedStage);
        pageTaskRepository.refreshChapterProgress(page.getChapterId());
        return ApiResponse.ok(pageRepository.findById(pageId), "Page image uploaded");
    }

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
