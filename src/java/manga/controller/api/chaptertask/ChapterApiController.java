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
 * REST API controller cho Chapter — nhóm /api/v1.
 *
 * MUC LUC:
 *  1. listAll()      - GET  /api/v1/chapters                        - Lay toan bo chapter ma user co quyen xem
 *  2. list()         - GET  /api/v1/series/{seriesId}/chapters      - Lay chapter theo series
 *  3. create()       - POST /api/v1/series/{seriesId}/chapters      - Tao chapter moi
 *  4. detail()       - GET  /api/v1/chapters/{id}                   - Xem chi tiet chapter
 *  5. update()       - PUT  /api/v1/chapters/{id}                   - Cap nhat chapter
 *  6. submitReview() - POST /api/v1/chapters/{id}/submit-review     - Nop chapter len editorial review
 *  7. delete()       - DELETE /api/v1/chapters/{id}                 - Xoa chapter
 */
@RestController
@RequestMapping("/api/v1")
public class ChapterApiController {

    @Autowired
    private ChapterService chapterService;

    // ============================================================
    // 1. LIST ALL CHAPTERS
    // GET /api/v1/chapters
    // - Lay toan bo chapter ma user co quyen xem (service tu filter theo role)
    // ============================================================
    @RequestMapping(value = "/chapters", method = RequestMethod.GET)
    public ApiResponse<List<ChapterSummary>> listAll(HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(chapterService.listAll(user), "Chapters");
    }

    // ============================================================
    // 2. LIST CHAPTERS BY SERIES
    // GET /api/v1/series/{seriesId}/chapters
    // - Lay danh sach chapter theo seriesId
    // - Chi can check login, khong filter them theo role
    // ============================================================
    @RequestMapping(value = "/series/{seriesId}/chapters", method = RequestMethod.GET)
    public ApiResponse<List<ChapterSummary>> list(@PathVariable("seriesId") long seriesId, HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(chapterService.listBySeries(seriesId), "Chapters");
    }

    // ============================================================
    // 3. CREATE CHAPTER
    // POST /api/v1/series/{seriesId}/chapters
    // - Chi MANGAKA cua series do moi duoc tao (service enforce BR-CHP-01)
    // - Params bat buoc: title, submissionDeadline
    // - totalPages mac dinh = 0 neu khong truyen
    // - BR-CHP-02: submissionDeadline phai truoc publicationDate it nhat 14 ngay (service enforce)
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
    // - Lay chi tiet mot chapter theo id
    // - Chi can check login
    // ============================================================
    @RequestMapping(value = "/chapters/{id}", method = RequestMethod.GET)
    public ApiResponse<ChapterSummary> detail(@PathVariable("id") long id, HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(chapterService.getDetail(id), "Chapter detail");
    }

    // ============================================================
    // 5. UPDATE CHAPTER
    // PUT /api/v1/chapters/{id}
    // - Tat ca params deu optional (chi truyen cai nao muon cap nhat)
    // - Co nhieu ten param cho deadline (submissionDeadline / deadline / chapterDeadline)
    //   -> service tu xu ly, controller chi truyen het vao
    // - Quyen cap nhat do service kiem tra (thuong chi MANGAKA so huu chapter)
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
    // - Chuyen chapter sang trang thai EDITORIAL_REVIEW
    // - Service enforce: chapter phai dat 100% task APPROVED (BR-MAN-01, BR-MAN-02)
    // - Tra ve null data, chi confirm bang message
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
    // - Xoa chapter (service enforce quyen va dieu kien hop le)
    // - Tra ve null data, chi confirm bang message
    // ============================================================
    @RequestMapping(value = "/chapters/{id}", method = RequestMethod.DELETE)
    public ApiResponse<Object> delete(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        chapterService.delete(id, user);
        return ApiResponse.ok(null, "Chapter deleted");
    }
}
