package manga.controller.api.chaptertask;

// Chapter/task API group: chapter CRUD endpoints are kept separate from general APIs for easier tracing.
import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.ChapterSummary;
import manga.service.chaptertask.ChapterService;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for Chapter — /api/v1 group.
 *
 * TABLE OF CONTENTS:
 *  1. listAll()      - GET  /api/v1/chapters                        - Get all chapters the user has permission to view
 *  2. list()         - GET  /api/v1/series/{seriesId}/chapters      - Get chapters by series
 *  3. create()       - POST /api/v1/series/{seriesId}/chapters      - Create a new chapter
 *  4. detail()       - GET  /api/v1/chapters/{id}                   - View chapter detail
 *  5. update()       - PUT  /api/v1/chapters/{id}                   - Update chapter
 *  6. submitReview() - POST /api/v1/chapters/{id}/submit-review     - Submit chapter for editorial review
 *  7. delete()       - DELETE /api/v1/chapters/{id}                 - Delete chapter
 */
@RestController
@RequestMapping("/api/v1")
public class ChapterApiController {

    @Autowired
    private ChapterService chapterService;

    // ============================================================
    // 1. LIST ALL CHAPTERS
    // GET /api/v1/chapters
    // - Get all chapters the user has permission to view (service filters by role)
    // ============================================================
    @RequestMapping(value = "/chapters", method = RequestMethod.GET)
    public ApiResponse<List<ChapterSummary>> listAll(HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(chapterService.listAll(user), "Chapters");
    }

    // ============================================================
    // 2. LIST CHAPTERS BY SERIES
    // GET /api/v1/series/{seriesId}/chapters
    // - Get chapters by seriesId
    // - Only requires login check, no additional role filtering
    // ============================================================
    @RequestMapping(value = "/series/{seriesId}/chapters", method = RequestMethod.GET)
    public ApiResponse<List<ChapterSummary>> list(@PathVariable("seriesId") long seriesId, HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(chapterService.listBySeries(seriesId), "Chapters");
    }

    // ============================================================
    // 3. CREATE CHAPTER
    // POST /api/v1/series/{seriesId}/chapters
    // - Only the MANGAKA of that series can create (service enforces BR-CHP-01)
    // - Required params: title, submissionDeadline
    // - totalPages defaults to 0 if not provided
    // - BR-CHP-02: submissionDeadline must be at least 14 days before publicationDate (service enforced)
    // ============================================================
    @RequestMapping(value = "/series/{seriesId}/chapters", method = RequestMethod.POST)
    public ApiResponse<ChapterSummary> create(
            @PathVariable("seriesId") long seriesId,
            HttpSession session,
            @RequestParam("title") String title,    
            @RequestParam("submissionDeadline") String submissionDeadline,
            @RequestParam(value = "totalPages", defaultValue = "0") int totalPages) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(chapterService.create(seriesId, user, title, submissionDeadline, totalPages), "Chapter created");
    }

    // ============================================================
    // 4. CHAPTER DETAIL
    // GET /api/v1/chapters/{id}
    // - Get chapter detail by id
    // - Only requires login check
    // ============================================================
    @RequestMapping(value = "/chapters/{id}", method = RequestMethod.GET)
    public ApiResponse<ChapterSummary> detail(@PathVariable("id") long id, HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(chapterService.getDetail(id), "Chapter detail");
    }

    // ============================================================
    // 5. UPDATE CHAPTER
    // PUT /api/v1/chapters/{id}
    // - All params are optional (only pass the ones you want to update)
    // - Multiple param names for deadline (submissionDeadline / deadline / chapterDeadline)
    //   -> service handles it, controller just passes everything through
    // - Update permission is checked by the service (usually only the MANGAKA who owns the chapter)
    // ============================================================
    @RequestMapping(value = "/chapters/{id}", method = RequestMethod.PUT)
    public ApiResponse<ChapterSummary> update(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "submissionDeadline", required = false) String submissionDeadline,
            @RequestParam(value = "publicationDate", required = false) String publicationDate,
            @RequestParam(value = "deadline", required = false) String deadline,
            @RequestParam(value = "chapterDeadline", required = false) String chapterDeadline) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(
                chapterService.update(id, user, title, submissionDeadline, publicationDate, deadline, chapterDeadline),
                "Chapter updated");
    }

    // ============================================================
    // 6. SUBMIT CHAPTER FOR EDITORIAL REVIEW
    // POST /api/v1/chapters/{id}/submit-review
    // - Moves the chapter to EDITORIAL_REVIEW status
    // - Service enforces: chapter must have 100% of tasks APPROVED (BR-MAN-01, BR-MAN-02)
    // - Returns null data, only confirms via message
    // ============================================================
    @RequestMapping(value = "/chapters/{id}/submit-review", method = RequestMethod.POST)
    public ApiResponse<Object> submitReview(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        chapterService.submitForReview(id, user);
        return ApiResponse.ok(null, "Chapter submitted for editorial review");
    }

    // ============================================================
    // 7. DELETE CHAPTER
    // DELETE /api/v1/chapters/{id}
    // - Deletes chapter (service enforces permission and validity conditions)
    // - Returns null data, only confirms via message
    // ============================================================
    @RequestMapping(value = "/chapters/{id}", method = RequestMethod.DELETE)
    public ApiResponse<Object> delete(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        chapterService.delete(id, user);
        return ApiResponse.ok(null, "Chapter deleted");
    }
}
