package manga.controller.api;

import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.TaskSummary;
import manga.repository.PageTaskRepository;
import java.sql.Date;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PageTaskApiController {

    @Autowired
    private PageTaskRepository pageTaskRepository;

    @RequestMapping(value = "/tasks", method = RequestMethod.GET)
    public ApiResponse<List<TaskSummary>> listVisible(
            HttpSession session,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "chapterId", required = false) Long chapterId) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        pageTaskRepository.markDelayedTasks();
        pageTaskRepository.markOverdueTasks();
        return ApiResponse.ok(pageTaskRepository.listVisible(user, status, chapterId), "Task list");
    }
    @RequestMapping(value = "/chapters/{chapterId}/tasks", method = RequestMethod.GET)
    public ApiResponse<List<TaskSummary>> list(@PathVariable("chapterId") long chapterId, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);

        if (user.hasRole("ADMIN")) {
            return ApiResponse.ok(pageTaskRepository.listByChapter(chapterId), "Task list");
        }

        if (user.hasRole("MANGAKA")) {
            long ownerId = pageTaskRepository.findChapterOwnerMangaka(chapterId);
            if (ownerId != user.getId()) {
                throw new IllegalArgumentException("Only chapter owner Mangaka can view this task list (BR-42)");
            }
            return ApiResponse.ok(pageTaskRepository.listByChapter(chapterId), "Task list");
        }

        if (user.hasRole("TANTOU_EDITOR")) {
            long tantouId = pageTaskRepository.findChapterTantouEditor(chapterId);
            if (tantouId != user.getId()) {
                throw new IllegalArgumentException("Only assigned Tantou can view this chapter task list (BR-42)");
            }
            return ApiResponse.ok(pageTaskRepository.listByChapter(chapterId), "Task list");
        }

        throw new IllegalArgumentException("Only MANGAKA/TANTOU_EDITOR/ADMIN can view chapter task list (BR-42)");
    }

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
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can create task");

        long ownerId = pageTaskRepository.findChapterOwnerMangaka(chapterId);
        if (ownerId != user.getId()) {
            throw new IllegalArgumentException("Only chapter owner can assign task (BR-31)");
        }

        long taskId = pageTaskRepository.create(
                chapterId,
                assistantId,
                pageRangeStart,
                pageRangeEnd,
                taskType == null || taskType.trim().isEmpty() ? "MIXED" : taskType,
                Date.valueOf(dueDate),
                priority,
                notes);
        return ApiResponse.ok(pageTaskRepository.findById(taskId), "Task created");
    }

    @RequestMapping(value = "/tasks/{id}", method = RequestMethod.PATCH)
    public ApiResponse<TaskSummary> patch(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam(value = "dueDate", required = false) String dueDate,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "notes", required = false) String notes) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can update task");
        TaskSummary existing = pageTaskRepository.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Task not found");
        }
        String nextDue = dueDate != null && !dueDate.trim().isEmpty()
                ? dueDate
                : (existing.getDueDate() != null ? existing.getDueDate().toString() : null);
        if (nextDue == null) {
            throw new IllegalArgumentException("dueDate is required");
        }
        pageTaskRepository.updateTaskProgress(
                id,
                user.getId(),
                Date.valueOf(nextDue),
                priority != null ? priority : existing.getPriority(),
                notes != null ? notes : existing.getNotes());
        return ApiResponse.ok(pageTaskRepository.findById(id), "Task updated");
    }

    @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
    public ApiResponse<TaskSummary> detail(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        TaskSummary task = pageTaskRepository.findById(id);
        if (task == null) {
            throw new IllegalArgumentException("Task not found");
        }

        long chapterId = task.getChapterId();
        long ownerMangakaId = pageTaskRepository.findChapterOwnerMangaka(chapterId);
        long tantouId = pageTaskRepository.findChapterTantouEditor(chapterId);

        boolean allowed = user.hasRole("ADMIN")
                || (user.hasRole("MANGAKA") && ownerMangakaId == user.getId())
                || (user.hasRole("TANTOU_EDITOR") && tantouId == user.getId())
                || (user.hasRole("ASSISTANT") && task.getAssistantId() == user.getId());

        if (!allowed) {
            throw new IllegalArgumentException("Only assigned roles can view this task (BR-42)");
        }

        return ApiResponse.ok(task, "Task detail");
    }

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
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can update task");

        pageTaskRepository.updateTaskByMangaka(
                id,
                user.getId(),
                assistantId,
                pageRangeStart,
                pageRangeEnd,
                taskType,
                Date.valueOf(dueDate));

        return ApiResponse.ok(pageTaskRepository.findById(id), "Task updated");
    }

    @RequestMapping(value = "/tasks/{id}/status", method = RequestMethod.PATCH)
    public ApiResponse<Object> updateStatus(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("status") String status) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "ASSISTANT", "Only ASSISTANT can submit task for review");
        pageTaskRepository.updateStatusByAssistant(id, user.getId(), status.toUpperCase());
        return ApiResponse.ok(null, "Task submitted for review");
    }

    @RequestMapping(value = "/tasks/{id}/approve", method = RequestMethod.POST)
    public ApiResponse<Object> approve(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam(value = "comment", required = false) String comment) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can approve task");
        pageTaskRepository.approveByMangaka(id, user.getId(), comment);
        return ApiResponse.ok(null, "Task approved");
    }

    @RequestMapping(value = "/tasks/{id}/reject", method = RequestMethod.POST)
    public ApiResponse<Object> reject(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("reason") String reason) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can reject task");
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("Rejection reason must be at least 5 characters");
        }
        if (reason.trim().length() > 300) {
            throw new IllegalArgumentException("Rejection reason must be at most 300 characters");
        }
        pageTaskRepository.rejectByMangaka(id, user.getId(), reason.trim());
        return ApiResponse.ok(null, "Task rejected");
    }

    @RequestMapping(value = "/tasks/{id}/delete", method = RequestMethod.POST)
    public ApiResponse<Object> deleteTask(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("reason") String reason) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can delete task");
        pageTaskRepository.deleteByMangaka(id, user.getId(), reason);
        return ApiResponse.ok(null, "Task deleted");
    }

    @RequestMapping(value = "/tasks/{id}/reassign", method = RequestMethod.POST)
    public ApiResponse<TaskSummary> reassignTask(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("assistantId") long assistantId,
            @RequestParam("reason") String reason) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can reassign task");
        long newTaskId = pageTaskRepository.reassignByMangaka(id, user.getId(), assistantId, reason);
        return ApiResponse.ok(pageTaskRepository.findById(newTaskId), "Task reassigned");
    }
}
