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
 * PageTaskService - Nghiệp vụ quản lý Page Task
 * ============================================================
 *
 * MỤC LỤC:
 * ----------------------------------------------------------
 * [1] TRUY VẤN TASK
 *     - listVisible()          : Liệt kê task theo quyền người dùng
 *     - listForWebTaskPage()   : Lọc task cho trang web (Assistant chỉ thấy task của mình)
 *     - listByChapter()        : Liệt kê task theo chapter (có kiểm tra quyền)
 *     - getDetail()            : Lấy chi tiết task (có kiểm tra quyền xem)
 *     - getDetailView()        : Lấy chi tiết kèm các cờ hành động UI
 * [2] TẠO & CẬP NHẬT TASK (Mangaka)
 *     - create()               : Tạo task mới
 *     - update()               : Cập nhật toàn bộ thông tin task
 *     - patch()                : Cập nhật dueDate / priority / notes
 * [3] VÒNG ĐỜI TASK
 *     - updateStatusByAssistant(): Assistant nộp task
 *     - approve()              : Mangaka duyệt task
 *     - reject()               : Mangaka từ chối task
 *     - delete()               : Mangaka xoá task
 *     - reassign()             : Mangaka phân công lại
 *     - extend()               : Mangaka gia hạn task OVERDUE
 * [4] JOB SCHEDULER
 *     - refreshTaskAging()     : Cập nhật trạng thái OVERDUE + DELAYED (gọi trước mỗi query)
 *     - markOverdueTasks()     : Đánh dấu task quá hạn
 *     - markDelayedTasks()     : Đánh dấu task trễ tiến độ
 *     - remindDueSoonTasks()   : Nhắc task sắp đến hạn 24h
 *     - escalatePendingOverdueDecisions(): Tự huỷ task OVERDUE không có quyết định sau 3 ngày
 * [5] HELPER / PRIVATE
 *     - requireTask()          : Lấy task hoặc ném exception
 *     - requireCanView()       : Kiểm tra quyền xem task (BR-42)
 *     - validateRejectReason() : Validate lý do từ chối
 * [6] INNER CLASS: DetailView  : DTO kèm cờ hành động cho UI
 * ============================================================
 */
@Service
public class PageTaskService {

    @Autowired
    private PageTaskRepository pageTaskRepository;

    // ============================================================
    // [1] TRUY VẤN TASK
    // ============================================================

    /**
     * Liệt kê task hiển thị với người dùng, có thể lọc theo status và chapterId.
     * Gọi refreshTaskAging() trước để đảm bảo trạng thái OVERDUE/DELAYED luôn cập nhật.
     */
    public List<TaskSummary> listVisible(AuthenticatedUser user, String status, Long chapterId) {
        refreshTaskAging();
        return pageTaskRepository.listVisible(user, status, chapterId);
    }

    /**
     * Lọc danh sách task cho trang web:
     * - Không phải ASSISTANT: trả về toàn bộ danh sách
     * - ASSISTANT: chỉ trả về task được giao cho chính họ
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
     * Liệt kê task theo chapter, kiểm tra quyền theo role (BR-42):
     * - ADMIN: thấy tất cả
     * - MANGAKA: chỉ thấy nếu là chủ chapter
     * - TANTOU_EDITOR: chỉ thấy nếu được phân công cho series
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

    /** Lấy chi tiết task, kiểm tra quyền xem (BR-42) */
    public TaskSummary getDetail(long taskId, AuthenticatedUser user) {
        TaskSummary task = requireTask(taskId);
        requireCanView(task, user);
        return task;
    }

    /** Lấy toàn bộ lịch sử submit/review của task, tái dùng check quyền xem của getDetail(). */
    public List<TaskReviewHistoryEntry> getSubmissionHistory(long taskId, AuthenticatedUser user) {
        getDetail(taskId, user);
        return pageTaskRepository.listReviewHistory(taskId);
    }

    /**
     * Lấy chi tiết task kèm các cờ hành động cho UI:
     * - canAssistantUpdate : assistant có thể upload ảnh / chỉnh sửa không?
     * - canAssistantSubmit : assistant có thể nộp task không?
     * - isOwnerMangaka     : người dùng có phải Mangaka chủ task không?
     * - canMangakaReview   : Mangaka có thể duyệt/từ chối không? (task đang SUBMITTED)
     */
    public DetailView getDetailView(long taskId, AuthenticatedUser user) {
        refreshTaskAging();
        TaskSummary task = getDetail(taskId, user);

        boolean isAssignedAssistant = user.hasRole("ASSISTANT") && user.getId() == task.getAssistantId();
        // Assistant có thể update khi task đang active hoặc chờ review
        boolean canAssistantUpdate = isAssignedAssistant
                && ("IN_PROGRESS".equalsIgnoreCase(task.getStatus())
                || "SUBMITTED".equalsIgnoreCase(task.getStatus())
                || "REJECTED".equalsIgnoreCase(task.getStatus())
                || "OVERDUE".equalsIgnoreCase(task.getStatus()));
        // Assistant chỉ được submit khi chưa ở trạng thái SUBMITTED
        boolean canAssistantSubmit = isAssignedAssistant
                && ("IN_PROGRESS".equalsIgnoreCase(task.getStatus())
                || "REJECTED".equalsIgnoreCase(task.getStatus())
                || "OVERDUE".equalsIgnoreCase(task.getStatus()));

        boolean isOwnerMangaka = user.hasRole("MANGAKA") && pageTaskRepository.getTaskOwnerMangaka(taskId) == user.getId();
        boolean canMangakaReview = isOwnerMangaka && "SUBMITTED".equalsIgnoreCase(task.getStatus());

        return new DetailView(task, canAssistantUpdate, canAssistantSubmit, isOwnerMangaka, canMangakaReview);
    }

    // ============================================================
    // [2] TẠO & CẬP NHẬT TASK (Mangaka)
    // ============================================================

    /**
     * Tạo task mới cho chapter.
     * Yêu cầu: MANGAKA và là chủ chapter (BR-31).
     * taskType mặc định là MIXED nếu không truyền.
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
     * Cập nhật toàn bộ thông tin task (kể cả đổi assistant, page range, taskType).
     * Dùng khi Mangaka muốn thay đổi cấu trúc task.
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
     * Cập nhật một phần task: chỉ dueDate / priority / notes.
     * Giữ nguyên giá trị cũ nếu tham số không được truyền.
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
    // [3] VÒNG ĐỜI TASK
    // ============================================================

    /** Assistant nộp task để Mangaka review (chỉ được nộp SUBMITTED) */
    public void updateStatusByAssistant(long taskId, AuthenticatedUser user, String status) {
        SessionUserUtil.requireRole(user, "ASSISTANT", "Only ASSISTANT can submit task for review");
        pageTaskRepository.updateStatusByAssistant(taskId, user.getId(), status.toUpperCase());
    }

    /** Mangaka duyệt task SUBMITTED; kiểm tra quyền sở hữu trước khi gọi repository */
    public void approve(long taskId, AuthenticatedUser user, String comment) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can approve task");
        if (pageTaskRepository.getTaskOwnerMangaka(taskId) != user.getId()) {
            throw new IllegalArgumentException("Only owner can approve");
        }
        pageTaskRepository.approveByMangaka(taskId, user.getId(), comment);
    }

    /**
     * Mangaka từ chối task SUBMITTED; lý do từ chối bắt buộc 5-300 ký tự.
     * Kiểm tra quyền sở hữu trước khi gọi repository.
     */
    public void reject(long taskId, AuthenticatedUser user, String reason) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can reject task");
        validateRejectReason(reason);
        if (pageTaskRepository.getTaskOwnerMangaka(taskId) != user.getId()) {
            throw new IllegalArgumentException("Only owner can reject");
        }
        pageTaskRepository.rejectByMangaka(taskId, user.getId(), reason.trim());
    }

    /** Mangaka xoá task (chỉ xoá được IN_PROGRESS hoặc OVERDUE) */
    public void delete(long taskId, AuthenticatedUser user, String reason) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can delete task");
        pageTaskRepository.deleteByMangaka(taskId, user.getId(), reason);
    }

    /**
     * Mangaka phân công lại task cho assistant khác.
     * newDueDate có thể null nếu task không phải OVERDUE.
     * Trả về TaskSummary của task mới được tạo.
     */
    public TaskSummary reassign(long taskId, AuthenticatedUser user, long assistantId, String reason, String newDueDate) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can reassign task");
        Date parsedDueDate = (newDueDate == null || newDueDate.trim().isEmpty()) ? null : Date.valueOf(newDueDate);
        long newTaskId = pageTaskRepository.reassignByMangaka(taskId, user.getId(), assistantId, reason, parsedDueDate);
        return pageTaskRepository.findById(newTaskId);
    }

    /** Mangaka gia hạn task OVERDUE: đặt dueDate mới, reset về IN_PROGRESS */
    public void extend(long taskId, AuthenticatedUser user, String newDueDate, String reason) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can extend task");
        pageTaskRepository.extendOverdueTask(taskId, user.getId(), Date.valueOf(newDueDate), reason);
    }

    // ============================================================
    // [4] JOB SCHEDULER
    // ============================================================

    /**
     * Cập nhật đồng thời trạng thái DELAYED và OVERDUE.
     * Được gọi tự động trước mỗi query liệt kê/xem task để đảm bảo dữ liệu luôn mới.
     */
    public void refreshTaskAging() {
        pageTaskRepository.markDelayedTasks();
        pageTaskRepository.markOverdueTasks();
    }

    /** Đánh dấu task đã quá dueDate sang OVERDUE (dùng cho scheduler riêng lẻ) */
    public void markOverdueTasks() {
        pageTaskRepository.markOverdueTasks();
    }

    /** Đánh dấu task bị trễ tiến độ (cờ DELAYED, không đổi status) */
    public void markDelayedTasks() {
        pageTaskRepository.markDelayedTasks();
    }

    /** Nhắc assistant về task sắp đến hạn trong 24 giờ */
    public void remindDueSoonTasks() {
        pageTaskRepository.notifyDueSoonTasks();
    }

    /** Tự động huỷ (CANCELLED) task OVERDUE mà Mangaka không có quyết định sau 3 ngày */
    public void escalatePendingOverdueDecisions() {
        pageTaskRepository.escalatePendingOverdueDecisions();
    }

    // ============================================================
    // [5] HELPER / PRIVATE
    // ============================================================

    /** Lấy task theo ID; ném exception nếu không tồn tại */
    private TaskSummary requireTask(long taskId) {
        TaskSummary task = pageTaskRepository.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found");
        }
        return task;
    }

    /**
     * Kiểm tra quyền xem task (BR-42):
     * - ADMIN: luôn được xem
     * - MANGAKA: phải là chủ chapter
     * - TANTOU_EDITOR: phải được phân công cho series
     * - ASSISTANT: phải là người được giao task
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

    /** Validate lý do từ chối: bắt buộc 5-300 ký tự */
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
     * DTO trả về chi tiết task kèm các cờ hành động cho UI.
     * Các cờ được tính tại thời điểm load để controller/view không cần tính lại logic.
     */
    public static class DetailView {

        private final TaskSummary task;
        /** Assistant có thể upload ảnh / chỉnh sửa task không? */
        private final boolean canAssistantUpdate;
        /** Assistant có thể nộp task không? */
        private final boolean canAssistantSubmit;
        /** Người dùng có phải Mangaka chủ task không? */
        private final boolean canMangakaTaskOwner;
        /** Mangaka có thể duyệt/từ chối không? (task đang SUBMITTED) */
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
