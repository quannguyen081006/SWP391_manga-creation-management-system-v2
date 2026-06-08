package manga.controller.api.chaptertask;

// Chapter/task API group: task assignment, review, overdue, and extension endpoints live here.
import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.TaskSummary;
import manga.service.chaptertask.PageTaskService;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller quan ly PageTask (task ve trang) — nhom /api/v1.
 *
 * MUC LUC:
 *  1.  listVisible()   - GET   /api/v1/tasks                           - Lay task theo quyen cua user (co the filter status/chapterId)
 *  2.  list()          - GET   /api/v1/chapters/{chapterId}/tasks       - Lay task theo chapter
 *  3.  create()        - POST  /api/v1/chapters/{chapterId}/tasks       - Tao task moi giao cho assistant
 *  4.  patch()         - PATCH /api/v1/tasks/{id}                       - Cap nhat mot phan task (dueDate/priority/notes)
 *  5.  detail()        - GET   /api/v1/tasks/{id}                       - Xem chi tiet task
 *  6.  update()        - PUT   /api/v1/tasks/{id}                       - Cap nhat toan bo task
 *  7.  updateStatus()  - PATCH /api/v1/tasks/{id}/status                - Assistant cap nhat trang thai task
 *  8.  approve()       - POST  /api/v1/tasks/{id}/approve               - Mangaka duyet task
 *  9.  reject()        - POST  /api/v1/tasks/{id}/reject                - Mangaka tu choi task
 * 10.  deleteTask()    - POST  /api/v1/tasks/{id}/delete                - Xoa task (dung POST thay vi DELETE)
 * 11.  reassignTask()  - POST  /api/v1/tasks/{id}/reassign              - Chuyen task sang assistant khac
 * 12.  extendTask()    - POST  /api/v1/tasks/{id}/extend                - Gia han deadline task
 */
@RestController
@RequestMapping("/api/v1")
public class PageTaskApiController {

    @Autowired
    private PageTaskService pageTaskService;

    // ============================================================
    // 1. LIST TASK THEO QUYEN USER
    // GET /api/v1/tasks?status=&chapterId=
    // - ASSISTANT: chi thay task duoc giao cho minh
    // - MANGAKA/TANTOU/ADMIN: thay task thuoc series/chapter co quyen
    // - status va chapterId deu optional, dung de filter them
    // ============================================================
    @RequestMapping(value = "/tasks", method = RequestMethod.GET)
    public ApiResponse<List<TaskSummary>> listVisible(
            HttpSession session,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "chapterId", required = false) Long chapterId) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageTaskService.listVisible(user, status, chapterId), "Task list");
    }

    // ============================================================
    // 2. LIST TASK THEO CHAPTER
    // GET /api/v1/chapters/{chapterId}/tasks
    // - Service tu filter theo role (ASSISTANT chi thay task cua minh)
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/tasks", method = RequestMethod.GET)
    public ApiResponse<List<TaskSummary>> list(@PathVariable("chapterId") long chapterId, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageTaskService.listByChapter(chapterId, user), "Task list");
    }

    // ============================================================
    // 3. TAO TASK MOI
    // POST /api/v1/chapters/{chapterId}/tasks
    // - Chi MANGAKA chu chapter moi duoc tao (service enforce BR-CHP-03, BR-CHP-05)
    // - pageRangeStart/End: pham vi trang, khong duoc chong nhau (BR-CHP-07)
    // - dueDate: khong vuot qua deadline chapter (BR-CHP-08)
    // - priority mac dinh NORMAL neu khong truyen
    // - taskType va notes optional
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/tasks", method = RequestMethod.POST)
    public ApiResponse<TaskSummary> create(
            @PathVariable("chapterId") long chapterId,
            HttpSession session,
            @RequestParam("assistantId") long assistantId,
            @RequestParam("pageRangeStart") int pageRangeStart,
            @RequestParam("pageRangeEnd") int pageRangeEnd,
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam("dueDate") String dueDate,
            @RequestParam(value = "priority", defaultValue = "NORMAL") String priority,
            @RequestParam(value = "notes", required = false) String notes) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(
                pageTaskService.create(chapterId, user, assistantId, pageRangeStart, pageRangeEnd, taskType, dueDate, priority, notes),
                "Task created");
    }

    // ============================================================
    // 4. CAP NHAT MOT PHAN TASK (PATCH)
    // PATCH /api/v1/tasks/{id}
    // - Chi cap nhat duoc: dueDate, priority, notes — tat ca optional
    // - Khong doi duoc assistantId hay pageRange qua endpoint nay
    //   (dung reassign() hoac update() neu can doi nhung truong do)
    // ============================================================
    @RequestMapping(value = "/tasks/{id}", method = RequestMethod.PATCH)
    public ApiResponse<TaskSummary> patch(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam(value = "dueDate", required = false) String dueDate,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "notes", required = false) String notes) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageTaskService.patch(id, user, dueDate, priority, notes), "Task updated");
    }

    // ============================================================
    // 5. XEM CHI TIET TASK
    // GET /api/v1/tasks/{id}
    // - Service kiem tra quyen xem theo role
    // ============================================================
    @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
    public ApiResponse<TaskSummary> detail(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageTaskService.getDetail(id, user), "Task detail");
    }

    // ============================================================
    // 6. CAP NHAT TOAN BO TASK (PUT)
    // PUT /api/v1/tasks/{id}
    // - Tat ca params bat buoc (khac voi PATCH)
    // - Dung khi can thay doi ca assistantId hoac pageRange
    // - BR-TSK-03: doi assistantId reset trang thai task ve PENDING
    // ============================================================
    @RequestMapping(value = "/tasks/{id}", method = RequestMethod.PUT)
    public ApiResponse<TaskSummary> update(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("assistantId") long assistantId,
            @RequestParam("pageRangeStart") int pageRangeStart,
            @RequestParam("pageRangeEnd") int pageRangeEnd,
            @RequestParam("taskType") String taskType,
            @RequestParam("dueDate") String dueDate) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(
                pageTaskService.update(id, user, assistantId, pageRangeStart, pageRangeEnd, taskType, dueDate),
                "Task updated");
    }

    // ============================================================
    // 7. ASSISTANT CAP NHAT TRANG THAI TASK
    // PATCH /api/v1/tasks/{id}/status
    // - Flow hop le: Pending -> In-Progress -> Submitted (BR-TSK-01)
    // - Chi assistant duoc giao task moi duoc goi endpoint nay (service enforce)
    // ============================================================
    @RequestMapping(value = "/tasks/{id}/status", method = RequestMethod.PATCH)
    public ApiResponse<Object> updateStatus(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("status") String status) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        pageTaskService.updateStatusByAssistant(id, user, status);
        return ApiResponse.ok(null, "Task submitted for review");
    }

    // ============================================================
    // 8. DUYET TASK
    // POST /api/v1/tasks/{id}/approve
    // - Chi MANGAKA chu chapter moi duoc duyet (service enforce)
    // - comment optional
    // - Task da APPROVED khong the rollback (BR-TSK-06)
    // ============================================================
    @RequestMapping(value = "/tasks/{id}/approve", method = RequestMethod.POST)
    public ApiResponse<Object> approve(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam(value = "comment", required = false) String comment) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        pageTaskService.approve(id, user, comment);
        return ApiResponse.ok(null, "Task approved");
    }

    // ============================================================
    // 9. TU CHOI TASK
    // POST /api/v1/tasks/{id}/reject
    // - reason bat buoc (khac voi web form o ModuleWebController hardcode "Rejected via web form")
    // - Sau 3 lan reject: service tu dong escalate len Tantou Editor (BR-TSK-05)
    // ============================================================
    @RequestMapping(value = "/tasks/{id}/reject", method = RequestMethod.POST)
    public ApiResponse<Object> reject(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("reason") String reason) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        pageTaskService.reject(id, user, reason);
        return ApiResponse.ok(null, "Task rejected");
    }

    // ============================================================
    // 10. XOA TASK
    // POST /api/v1/tasks/{id}/delete
    // - Dung POST thay vi DELETE (co the de tranh gioi han cua mot so client)
    // - reason bat buoc
    // - Quyen xoa do service kiem tra
    // ============================================================
    @RequestMapping(value = "/tasks/{id}/delete", method = RequestMethod.POST)
    public ApiResponse<Object> deleteTask(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("reason") String reason) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        pageTaskService.delete(id, user, reason);
        return ApiResponse.ok(null, "Task deleted");
    }

    // ============================================================
    // 11. CHUYEN TASK SANG ASSISTANT KHAC
    // POST /api/v1/tasks/{id}/reassign
    // - reason bat buoc
    // - newDueDate optional: neu co thi cap nhat deadline moi
    // - BR-TSK-03: reassign reset trang thai task ve PENDING, xoa submission cu
    // ============================================================
    @RequestMapping(value = "/tasks/{id}/reassign", method = RequestMethod.POST)
    public ApiResponse<TaskSummary> reassignTask(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("assistantId") long assistantId,
            @RequestParam("reason") String reason,
            @RequestParam(value = "newDueDate", required = false) String newDueDate) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageTaskService.reassign(id, user, assistantId, reason, newDueDate), "Task reassigned");
    }

    // ============================================================
    // 12. GIA HAN DEADLINE TASK
    // POST /api/v1/tasks/{id}/extend
    // - newDueDate bat buoc
    // - reason optional
    // - newDueDate van phai nam trong deadline cua chapter (service enforce BR-CHP-08)
    // ============================================================
    @RequestMapping(value = "/tasks/{id}/extend", method = RequestMethod.POST)
    public ApiResponse<Object> extendTask(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("newDueDate") String newDueDate,
            @RequestParam(value = "reason", required = false) String reason) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        pageTaskService.extend(id, user, newDueDate, reason);
        return ApiResponse.ok(null, "Task extended");
    }
}
