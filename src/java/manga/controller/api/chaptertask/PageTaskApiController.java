package manga.controller.api.chaptertask;

// Chapter/task API group: task assignment, review, overdue, and extension endpoints live here.
import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.TaskReviewHistoryEntry;
import manga.model.chaptertask.TaskSummary;
import manga.service.chaptertask.PageTaskService;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for managing PageTask (page drawing tasks) — /api/v1 group.
 *
 * TABLE OF CONTENTS:
 *  1.  listVisible()   - GET   /api/v1/tasks                           - Get tasks by user permission (can filter by status/chapterId)
 *  2.  list()          - GET   /api/v1/chapters/{chapterId}/tasks       - Get tasks by chapter
 *  3.  create()        - POST  /api/v1/chapters/{chapterId}/tasks       - Create a new task assigned to an assistant
 *  4.  patch()         - PATCH /api/v1/tasks/{id}                       - Partially update task (dueDate/priority/notes)
 *  5.  detail()        - GET   /api/v1/tasks/{id}                       - View task detail
 *  6.  approve()       - POST  /api/v1/tasks/{id}/approve               - Mangaka approves task
 *  7.  reject()        - POST  /api/v1/tasks/{id}/reject                - Mangaka rejects task
 *  8.  deleteTask()    - POST  /api/v1/tasks/{id}/delete                - Delete task (uses POST instead of DELETE)
 *  9.  reassignTask()  - POST  /api/v1/tasks/{id}/reassign              - Reassign task to another assistant
 * 10.  extendTask()    - POST  /api/v1/tasks/{id}/extend                - Extend task deadline
 */
@RestController
@RequestMapping("/api/v1")
public class PageTaskApiController {

    @Autowired
    private PageTaskService pageTaskService;

    // ============================================================
    // 1. LIST TASKS BY USER PERMISSION
    // GET /api/v1/tasks?status=&chapterId=
    // - ASSISTANT: only sees tasks assigned to them
    // - MANGAKA/TANTOU/ADMIN: sees tasks belonging to series/chapters they have permission for
    // - status and chapterId are both optional, used for additional filtering
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
    // 2. LIST TASKS BY CHAPTER
    // GET /api/v1/chapters/{chapterId}/tasks
    // - Service filters by role (ASSISTANT only sees their own tasks)
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/tasks", method = RequestMethod.GET)
    public ApiResponse<List<TaskSummary>> list(@PathVariable("chapterId") long chapterId, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageTaskService.listByChapter(chapterId, user), "Task list");
    }

    // ============================================================
    // 3. CREATE NEW TASK
    // POST /api/v1/chapters/{chapterId}/tasks
    // - Only the MANGAKA who owns the chapter can create (service enforces BR-CHP-03, BR-CHP-05)
    // - pageRangeStart/End: page range, must not overlap (BR-CHP-07)
    // - dueDate: must not exceed the chapter deadline (BR-CHP-08)
    // - priority defaults to NORMAL if not provided
    // - taskTypes and notes are optional
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/tasks", method = RequestMethod.POST)
    public ApiResponse<TaskSummary> create(
            @PathVariable("chapterId") long chapterId,
            HttpSession session,
            @RequestParam("assistantId") long assistantId,
            @RequestParam("pageRangeStart") int pageRangeStart,
            @RequestParam("pageRangeEnd") int pageRangeEnd,
            @RequestParam(value = "taskTypes", required = false) String[] taskTypes,
            @RequestParam("dueDate") String dueDate,
            @RequestParam(value = "priority", defaultValue = "NORMAL") String priority,
            @RequestParam(value = "notes", required = false) String notes) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(
                pageTaskService.create(chapterId, user, assistantId, pageRangeStart, pageRangeEnd,
                        parseTaskTypes(taskTypes), dueDate, priority, notes),
                "Task created");
    }

    private List<String> parseTaskTypes(String[] taskTypes) {
        List<String> values = new ArrayList<String>();
        if (taskTypes != null) {
            for (String value : taskTypes) {
                if (value != null) {
                    values.addAll(Arrays.asList(value.split(",")));
                }
            }
        }
        return values;
    }

    // ============================================================
    // 4. PARTIAL TASK UPDATE (PATCH)
    // PATCH /api/v1/tasks/{id}
    // - Only these fields can be updated: dueDate, priority, notes — all optional
    // - assistantId and pageRange cannot be changed via this endpoint
    //   (use reassign() if those fields need to change)
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
    // 5. VIEW TASK DETAIL
    // GET /api/v1/tasks/{id}
    // - Service checks view permission by role
    // ============================================================
    @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
    public ApiResponse<TaskSummary> detail(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageTaskService.getDetail(id, user), "Task detail");
    }

    // ============================================================
    // 5b. TASK SUBMIT/REVIEW HISTORY
    // GET /api/v1/tasks/{id}/submission-history
    // - Returns all submission rounds (not just the most recent one)
    // - View permission is the same as detail() (reuses getDetail() for the check)
    // ============================================================
    @RequestMapping(value = "/tasks/{id}/submission-history", method = RequestMethod.GET)
    public ApiResponse<List<TaskReviewHistoryEntry>> submissionHistory(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(pageTaskService.getSubmissionHistory(id, user), "Submission history");
    }

    // ============================================================
    // 8. APPROVE TASK
    // POST /api/v1/tasks/{id}/approve
    // - Only the MANGAKA who owns the chapter can approve (service enforced)
    // - comment is optional
    // - An APPROVED task cannot be rolled back (BR-TSK-06)
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
    // 9. REJECT TASK
    // POST /api/v1/tasks/{id}/reject
    // - reason is required (unlike the web form in ModuleWebController which hardcodes "Rejected via web form")
    // - After 3 rejections: service automatically escalates to Tantou Editor (BR-TSK-05)
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
    // 10. DELETE TASK
    // POST /api/v1/tasks/{id}/delete
    // - Uses POST instead of DELETE (possibly to avoid restrictions on some clients)
    // - reason is required
    // - Delete permission is checked by the service
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
    // 11. REASSIGN TASK TO ANOTHER ASSISTANT
    // POST /api/v1/tasks/{id}/reassign
    // - reason is required
    // - newDueDate is optional: if provided, updates the new deadline
    // - BR-TSK-03: reassigning resets task status to PENDING, clears the old submission
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
    // 12. EXTEND TASK DEADLINE
    // POST /api/v1/tasks/{id}/extend
    // - newDueDate is required
    // - reason is optional
    // - newDueDate must still fall within the chapter deadline (service enforces BR-CHP-08)
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
