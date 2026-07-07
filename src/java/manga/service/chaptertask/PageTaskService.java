package manga.service.chaptertask;

import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.TaskReviewHistoryEntry;
import manga.model.chaptertask.TaskSummary;
import manga.repository.chaptertask.PageTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * PageTaskService - Page Task management business logic
 * ============================================================
 *
 * TABLE OF CONTENTS:
 * ----------------------------------------------------------
 * [1] TASK QUERIES
 *     - listVisible()          : List tasks according to user permissions
 *     - listForWebTaskPage()   : Filter tasks for the web page (Assistant only sees their own tasks)
 *     - listByChapter()        : List tasks by chapter (with permission check)
 *     - getDetail()            : Get task details (with view permission check)
 *     - getDetailView()        : Get details with UI action flags
 * [2] CREATE & UPDATE TASK (Mangaka)
 *     - create()               : Create a new task
 *     - update()               : Update the full task information
 *     - patch()                : Update dueDate / priority / notes
 * [3] TASK LIFECYCLE
 *     - updateStatusByAssistant(): Assistant submits task
 *     - approve()              : Mangaka approves task
 *     - reject()               : Mangaka rejects task
 *     - delete()               : Mangaka deletes task
 *     - reassign()             : Mangaka reassigns task
 *     - extend()               : Mangaka extends an OVERDUE task
 * [4] JOB SCHEDULER
 *     - refreshTaskAging()     : Update OVERDUE + DELAYED status (called before every query)
 *     - markOverdueTasks()     : Mark overdue tasks
 *     - markDelayedTasks()     : Mark tasks that are behind schedule
 *     - remindDueSoonTasks()   : Remind about tasks due within 24h
 *     - escalatePendingOverdueDecisions(): Auto-cancel OVERDUE tasks with no decision after 3 days
 * [5] HELPER / PRIVATE
 *     - requireTask()          : Get task or throw exception
 *     - requireCanView()       : Check task view permission (BR-42)
 *     - validateRejectReason() : Validate rejection reason
 * [6] INNER CLASS: DetailView  : DTO with action flags for the UI
 * ============================================================
 */
@Service
public class PageTaskService {

    @Autowired
    private PageTaskRepository pageTaskRepository;

    // ============================================================
    // [1] TASK QUERIES
    // ============================================================

    /**
     * List tasks visible to the user, optionally filtered by status and chapterId.
     * Calls refreshTaskAging() first to ensure OVERDUE/DELAYED status is always up to date.
     */
    public List<TaskSummary> listVisible(AuthenticatedUser user, String status, Long chapterId) {
        refreshTaskAging();
        return pageTaskRepository.listVisible(user, status, chapterId);
    }

    /**
     * Filter the task list for the web page:
     * - Not ASSISTANT: return the full list
     * - ASSISTANT: return only tasks assigned to themselves
     */
    public List<TaskSummary> listForWebTaskPage(AuthenticatedUser user, List<TaskSummary> allTasks) {
        refreshTaskAging();
        if (user == null || !user.hasRole("ASSISTANT")) {
            return allTasks;
        }
        List<TaskSummary> assigned = new ArrayList<TaskSummary>();
        for (TaskSummary task : allTasks) {
            if (task.getAssistantId() == user.getId()) {
                assigned.add(task);
            }
        }
        return assigned;
    }

    /**
     * List tasks by chapter, with role-based permission check (BR-42):
     * - ADMIN: sees everything
     * - MANGAKA: only sees it if they own the chapter
     * - TANTOU_EDITOR: only sees it if assigned to the series
     */
    public List<TaskSummary> listByChapter(long chapterId, AuthenticatedUser user) {
        if (user.hasRole("ADMIN")) {
            return pageTaskRepository.listByChapter(chapterId);
        }

        if (user.hasRole("MANGAKA")) {
            long ownerId = pageTaskRepository.findChapterOwnerMangaka(chapterId);
            if (ownerId != user.getId()) {
                throw new IllegalArgumentException("Only chapter owner Mangaka can view this task list (BR-42)");
            }
            return pageTaskRepository.listByChapter(chapterId);
        }

        if (user.hasRole("TANTOU_EDITOR")) {
            long tantouId = pageTaskRepository.findChapterTantouEditor(chapterId);
            if (tantouId != user.getId()) {
                throw new IllegalArgumentException("Only assigned Tantou can view this chapter task list (BR-42)");
            }
            return pageTaskRepository.listByChapter(chapterId);
        }

        throw new IllegalArgumentException("Only MANGAKA/TANTOU_EDITOR/ADMIN can view chapter task list (BR-42)");
    }

    /** Get task details, checking view permission (BR-42) */
    public TaskSummary getDetail(long taskId, AuthenticatedUser user) {
        TaskSummary task = requireTask(taskId);
        requireCanView(task, user);
        return task;
    }

    /** Get the full submit/review history of a task, reusing getDetail()'s view permission check. */
    public List<TaskReviewHistoryEntry> getSubmissionHistory(long taskId, AuthenticatedUser user) {
        getDetail(taskId, user);
        return pageTaskRepository.listReviewHistory(taskId);
    }

    /**
     * Get task details along with UI action flags:
     * - canAssistantUpdate : can the assistant upload images / make edits?
     * - canAssistantSubmit : can the assistant submit the task?
     * - isOwnerMangaka     : is the user the Mangaka who owns the task?
     * - canMangakaReview   : can the Mangaka approve/reject? (task is SUBMITTED)
     */
    public DetailView getDetailView(long taskId, AuthenticatedUser user) {
        refreshTaskAging();
        TaskSummary task = getDetail(taskId, user);

        boolean isAssignedAssistant = user.hasRole("ASSISTANT") && user.getId() == task.getAssistantId();
        // Assistant can update while the task is active or pending review
        boolean canAssistantUpdate = isAssignedAssistant
                && ("IN_PROGRESS".equalsIgnoreCase(task.getStatus())
                || "SUBMITTED".equalsIgnoreCase(task.getStatus())
                || "REJECTED".equalsIgnoreCase(task.getStatus())
                || "OVERDUE".equalsIgnoreCase(task.getStatus()));
        // Assistant can only submit when not already in SUBMITTED status
        boolean canAssistantSubmit = isAssignedAssistant
                && ("IN_PROGRESS".equalsIgnoreCase(task.getStatus())
                || "REJECTED".equalsIgnoreCase(task.getStatus())
                || "OVERDUE".equalsIgnoreCase(task.getStatus()));

        boolean isOwnerMangaka = user.hasRole("MANGAKA") && pageTaskRepository.getTaskOwnerMangaka(taskId) == user.getId();
        boolean canMangakaReview = isOwnerMangaka && "SUBMITTED".equalsIgnoreCase(task.getStatus());

        return new DetailView(task, canAssistantUpdate, canAssistantSubmit, isOwnerMangaka, canMangakaReview);
    }

    // ============================================================
    // [2] CREATE & UPDATE TASK (Mangaka)
    // ============================================================

    /**
     * Create a new task for a chapter.
     * Requirements: MANGAKA and owner of the chapter (BR-31).
     * taskType defaults to MIXED if not provided.
     */
    public TaskSummary create(
            long chapterId,
            AuthenticatedUser user,
            long assistantId,
            int pageRangeStart,
            int pageRangeEnd,
            List<String> taskTypes,
            String dueDate,
            String priority,
            String notes) {
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
                taskTypes,
                Date.valueOf(dueDate),
                priority,
                notes);
        return pageTaskRepository.findById(taskId);
    }

    /**
     * Update the full task information (including assistant, page range, taskType).
     * Used when the Mangaka wants to change the task's structure.
     */
    public TaskSummary update(
            long taskId,
            AuthenticatedUser user,
            long assistantId,
            int pageRangeStart,
            int pageRangeEnd,
            List<String> taskTypes,
            String dueDate) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can update task");
        pageTaskRepository.updateTaskByMangaka(
                taskId,
                user.getId(),
                assistantId,
                pageRangeStart,
                pageRangeEnd,
                taskTypes,
                Date.valueOf(dueDate));
        return pageTaskRepository.findById(taskId);
    }

    /**
     * Partially update a task: only dueDate / priority / notes.
     * Keeps the existing value if a parameter is not provided.
     */
    public TaskSummary patch(long taskId, AuthenticatedUser user, String dueDate, String priority, String notes) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can update task");
        TaskSummary existing = requireTask(taskId);
        String nextDue = dueDate != null && !dueDate.trim().isEmpty()
                ? dueDate
                : (existing.getDueDate() != null ? existing.getDueDate().toString() : null);
        if (nextDue == null) {
            throw new IllegalArgumentException("dueDate is required");
        }
        pageTaskRepository.updateTaskProgress(
                taskId,
                user.getId(),
                Date.valueOf(nextDue),
                priority != null ? priority : existing.getPriority(),
                notes != null ? notes : existing.getNotes());
        return pageTaskRepository.findById(taskId);
    }

    // ============================================================
    // [3] TASK LIFECYCLE
    // ============================================================

    /** Assistant submits task for Mangaka review (can only submit as SUBMITTED) */
    public void updateStatusByAssistant(long taskId, AuthenticatedUser user, String status) {
        SessionUserUtil.requireRole(user, "ASSISTANT", "Only ASSISTANT can submit task for review");
        pageTaskRepository.updateStatusByAssistant(taskId, user.getId(), status.toUpperCase());
    }

    /** Mangaka approves a SUBMITTED task; checks ownership before calling the repository */
    public void approve(long taskId, AuthenticatedUser user, String comment) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can approve task");
        if (pageTaskRepository.getTaskOwnerMangaka(taskId) != user.getId()) {
            throw new IllegalArgumentException("Only owner can approve");
        }
        pageTaskRepository.approveByMangaka(taskId, user.getId(), comment);
    }

    /**
     * Mangaka rejects a SUBMITTED task; the rejection reason must be 5-300 characters.
     * Checks ownership before calling the repository.
     */
    public void reject(long taskId, AuthenticatedUser user, String reason) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can reject task");
        validateRejectReason(reason);
        if (pageTaskRepository.getTaskOwnerMangaka(taskId) != user.getId()) {
            throw new IllegalArgumentException("Only owner can reject");
        }
        pageTaskRepository.rejectByMangaka(taskId, user.getId(), reason.trim());
    }

    /** Mangaka deletes a task (can only delete IN_PROGRESS or OVERDUE tasks) */
    public void delete(long taskId, AuthenticatedUser user, String reason) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can delete task");
        pageTaskRepository.deleteByMangaka(taskId, user.getId(), reason);
    }

    /**
     * Mangaka reassigns a task to a different assistant.
     * newDueDate may be null if the task is not OVERDUE.
     * Returns the TaskSummary of the newly created task.
     */
    public TaskSummary reassign(long taskId, AuthenticatedUser user, long assistantId, String reason, String newDueDate) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can reassign task");
        Date parsedDueDate = (newDueDate == null || newDueDate.trim().isEmpty()) ? null : Date.valueOf(newDueDate);
        long newTaskId = pageTaskRepository.reassignByMangaka(taskId, user.getId(), assistantId, reason, parsedDueDate);
        return pageTaskRepository.findById(newTaskId);
    }

    /** Mangaka extends an OVERDUE task: sets a new dueDate, resets status to IN_PROGRESS */
    public void extend(long taskId, AuthenticatedUser user, String newDueDate, String reason) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can extend task");
        pageTaskRepository.extendOverdueTask(taskId, user.getId(), Date.valueOf(newDueDate), reason);
    }

    // ============================================================
    // [4] JOB SCHEDULER
    // ============================================================

    /**
     * Updates both DELAYED and OVERDUE statuses.
     * Called automatically before every task list/view query to keep data fresh.
     */
    public void refreshTaskAging() {
        pageTaskRepository.markDelayedTasks();
        pageTaskRepository.markOverdueTasks();
    }

    /** Marks tasks past their dueDate as OVERDUE (used by the standalone scheduler) */
    public void markOverdueTasks() {
        pageTaskRepository.markOverdueTasks();
    }

    /** Marks tasks that are behind schedule (DELAYED flag, does not change status) */
    public void markDelayedTasks() {
        pageTaskRepository.markDelayedTasks();
    }

    /** Reminds assistants about tasks due within 24 hours */
    public void remindDueSoonTasks() {
        pageTaskRepository.notifyDueSoonTasks();
    }

    /** Automatically cancels (CANCELLED) OVERDUE tasks that the Mangaka has not decided on after 3 days */
    public void escalatePendingOverdueDecisions() {
        pageTaskRepository.escalatePendingOverdueDecisions();
    }

    // ============================================================
    // [5] HELPER / PRIVATE
    // ============================================================

    /** Get task by ID; throws exception if not found */
    private TaskSummary requireTask(long taskId) {
        TaskSummary task = pageTaskRepository.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found");
        }
        return task;
    }

    /**
     * Checks task view permission (BR-42):
     * - ADMIN: always allowed
     * - MANGAKA: must be the chapter owner
     * - TANTOU_EDITOR: must be assigned to the series
     * - ASSISTANT: must be the assignee of the task
     */
    private void requireCanView(TaskSummary task, AuthenticatedUser user) {
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
    }

    /** Validates the rejection reason: must be 5-300 characters */
    private void validateRejectReason(String reason) {
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("Rejection reason must be at least 5 characters");
        }
        if (reason.trim().length() > 300) {
            throw new IllegalArgumentException("Rejection reason must be at most 300 characters");
        }
    }

    // ============================================================
    // [6] INNER CLASS: DetailView
    // ============================================================

    /**
     * DTO returning task details along with UI action flags.
     * Flags are computed at load time so the controller/view doesn't need to recompute the logic.
     */
    public static class DetailView {

        private final TaskSummary task;
        /** Can the assistant upload images / edit the task? */
        private final boolean canAssistantUpdate;
        /** Can the assistant submit the task? */
        private final boolean canAssistantSubmit;
        /** Is the user the Mangaka who owns the task? */
        private final boolean canMangakaTaskOwner;
        /** Can the Mangaka approve/reject? (task is SUBMITTED) */
        private final boolean canMangakaReview;

        public DetailView(
                TaskSummary task,
                boolean canAssistantUpdate,
                boolean canAssistantSubmit,
                boolean canMangakaTaskOwner,
                boolean canMangakaReview) {
            this.task = task;
            this.canAssistantUpdate = canAssistantUpdate;
            this.canAssistantSubmit = canAssistantSubmit;
            this.canMangakaTaskOwner = canMangakaTaskOwner;
            this.canMangakaReview = canMangakaReview;
        }

        public TaskSummary getTask() { return task; }
        public boolean isCanAssistantUpdate() { return canAssistantUpdate; }
        public boolean isCanAssistantSubmit() { return canAssistantSubmit; }
        public boolean isCanMangakaTaskOwner() { return canMangakaTaskOwner; }
        public boolean isCanMangakaReview() { return canMangakaReview; }
    }
}
