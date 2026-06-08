<%--
  MỤC ĐÍCH: Màn hình quản lý tất cả task:
            - Assistant: xem task được giao, bấm vào để vào trang submission
    - Bảng task (tbody#taskRows): render server-side bằng JSTL <c:forEach> luôn khi load
    - Metric cards (Active/Submitted/Delayed/Overdue/Completed): JS đếm lại và điền sau
    - Filter pills (taskStatusPills): JS render sau khi load
    - Action column (cột Action trong bảng): JS điền nút tuỳ theo role và status
  CẤU TRÚC:
    [1] HEAD           — CSS import
    [2] METRIC CARDS   — 5 card đếm số task theo nhóm, JS điền số vào sau
    [3] ALERT BOX      — Hiển thị lỗi API inline
    [4] TASK ACTIONS   — Panel chứa nút "Create Task", chỉ hiện với Mangaka (JS kiểm tra)
    [5] MODAL: taskCreateModal  — Form tạo task mới (chọn chapter → load assistant → điền range/type/date)
    [6] BẢNG ALL TASKS — Danh sách toàn bộ task, render server-side, cột Action để trống cho JS điền
    [7] POPOVER: approve/reject — Inline popover (không phải modal) để Mangaka duyệt task
                                   Approve: comment tuỳ chọn | Reject: bắt buộc nhập lý do
    [8] MODAL: taskViewModal    — Modal xem/sửa chi tiết task (JS điền nội dung động)
    [9] CONFIG SCRIPT  — Truyền contextPath xuống task-list.js
--%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<%-- [1] HEAD --%>
<head>
    <meta charset="UTF-8">
    <title>Tasks</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=20260525" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/chaptertask/task-list.css?v=20260605fix3" />
</head>
<body>
<jsp:include page="../common/header.jsp" />
<%-- Chapter/task note: task table and review popover CSS is in /assets/css/chaptertask/task-list.css, not embedded in JSP. --%>

<%--
    [2] METRIC CARDS: 5 card tóm tắt số lượng task theo trạng thái
    Giá trị mặc định là 0, JS đọc bảng taskRows rồi đếm lại và điền vào các id này
    Active = IN_PROGRESS | Submitted = SUBMITTED | Delayed = đã trễ nhưng chưa OVERDUE
    Overdue = quá deadline | Completed = APPROVED
--%>
<section class="metric-grid">
    <article class="metric-card"><div id="activeTasks" class="metric-value">0</div><div class="metric-label">Active</div></article>
    <article class="metric-card"><div id="submittedTasks" class="metric-value metric-violet">0</div><div class="metric-label">Submitted</div></article>
    <article class="metric-card"><div id="delayedTasks" class="metric-value metric-amber">0</div><div class="metric-label">Delayed</div></article>
    <article class="metric-card"><div id="overdueTasks" class="metric-value metric-danger">0</div><div class="metric-label">Overdue</div></article>
    <article class="metric-card"><div id="completedTasks" class="metric-value metric-ok">0</div><div class="metric-label">Completed</div></article>
</section>

<%-- [3] ALERT BOX: JS show khi có lỗi API (approve/reject/create thất bại) --%>
<div id="taskResult" class="alert error task-alert-hidden"></div>

<%--
    [4] TASK ACTIONS PANEL: chứa nút "Create Task"
    JS kiểm tra role — chỉ Mangaka mới thấy panel này
    Nút data-modal-open="taskCreateModal" → JS bắt sự kiện mở modal [5]
--%>
<div id="taskActions" class="section-card task-actions-panel">
    <div class="section-head">
        <div>
            <h3 class="section-title">Task Actions</h3>
            <p class="section-desc">Create a task in a small popup. New assignments start In Progress.</p>
        </div>
        <button class="btn primary" type="button" data-modal-open="taskCreateModal">Create Task</button>
    </div>
</div>

<%--
    [5] MODAL taskCreateModal: form tạo task mới
    Flow chọn: Chapter (dropdown) → tự load danh sách Assistant theo chapter đó
              → điền Page Start/End + Task Type + Due Date → submit
    createTaskDeadlineHint: JS điền gợi ý deadline tối đa (chapter deadline - 3 ngày)
    taskCreateError: JS hiện lỗi validate hoặc lỗi API
    Lưu ý: form này THIẾU option SCREENTONE trong taskType — chỉ có 4/5 stage
--%>
<div id="taskCreateModal" class="modal-backdrop" aria-hidden="true">
    <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="taskCreateTitle">
        <button class="modal-close" type="button" data-modal-close aria-label="Close">&times;</button>
        <h3 id="taskCreateTitle" class="section-title compact-title">Create Task</h3>
        <form id="taskCreateForm" class="form-grid">
        <strong>Create Task</strong>
        <select id="createTaskChapterId" name="chapterId" required>
            <option value="">Loading chapters...</option>
        </select>
        <p id="createTaskDeadlineHint" class="section-desc"></p>
        <select id="createTaskAssistantId" name="assistantId" required>
            <option value="">Select Chapter first</option>
        </select>
        <input name="pageRangeStart" type="number" min="1" placeholder="Page Start" required />
        <input name="pageRangeEnd" type="number" min="1" placeholder="Page End" required />
        <select name="taskType" required>
            <option value="">Select Task Type</option>
            <option value="SKETCHING">Sketching</option>
            <option value="INKING">Inking</option>
            <option value="COLORING">Coloring</option>
            <option value="LETTERING">Lettering</option>
        </select>
        <label class="field-label" for="taskCreateDueDate">Due Date</label>
        <input id="taskCreateDueDate" name="dueDate" type="date" aria-label="Due Date" required />
        <div id="taskCreateError" class="alert error task-create-error"></div>
        <button class="btn primary" type="submit">Create</button>
        </form>
    </div>
</div>

<%--
    [6] BẢNG ALL TASKS: danh sách task render server-side bằng JSTL
    Status chip đổi màu theo trạng thái: OVERDUE=đỏ, IN_PROGRESS=vàng, PENDING=xám, APPROVED=xanh, còn lại=draft
    Cột Action (td cuối) để TRỐNG — JS điền nút tuỳ theo role và task.status:
      - Mangaka + SUBMITTED → nút Approve / Reject
      - Assistant + IN_PROGRESS → nút View (vào task detail để upload)
    taskStatusPills: JS render filter pills để lọc theo status
--%>
<div class="section-card task-table-card">
    <div class="section-head task-table-head">
        <div class="task-table-title-wrap">
            <h3 class="section-title task-table-title">All Tasks</h3>
        </div>
        <div id="taskStatusPills" class="task-status-pills"></div>
    </div>
    <table class="data-table task-data-table">
        <thead>
            <tr>
                <th>ID</th>
                <th>Series</th>
                <th>Pages</th>
                <th>Type</th>
                <th>Assigned To</th>
                <th>Status</th>
                <th>Due Date</th>
                <th>Action</th>
            </tr>
        </thead>
        <tbody id="taskRows">
            <c:forEach items="${tasks}" var="t">
                <tr>
                    <td>${t.id}</td>
                    <td><strong>${t.seriesTitle}</strong><br/>Ch. ${t.chapterNumber} - ${t.chapterTitle}</td>
                    <td>${t.pageRangeStart}-${t.pageRangeEnd}</td>
                    <td>${t.taskType}</td>
                    <td>${t.assistantName}</td>
                    <td>
                        <span class="status-chip ${t.status=='OVERDUE' ? 'status-overdue' : (t.status=='IN_PROGRESS' ? 'status-progress' : (t.status=='PENDING' ? 'status-pending' : (t.status=='APPROVED' ? 'status-approved' : 'status-draft')))}">${t.status}</span>
                    </td>
                    <td>${t.dueDate}</td>
                    <td></td><%-- JS điền nút Action vào đây --%>
                </tr>
            </c:forEach>
            <c:if test="${empty tasks}"><tr><td colspan="8">No tasks found.</td></tr></c:if>
        </tbody>
    </table>

    <%--
        [7] POPOVER approve/reject: hiện inline trên bảng (không phải modal riêng)
        taskPopoverScrim: lớp mờ phía sau, click vào để đóng popover
        taskApprovePopover: Mangaka approve, comment tuỳ chọn, không cần điền vẫn approve được
        taskRejectPopover:  Mangaka reject, BẮT BUỘC nhập lý do (nút Confirm disabled cho đến khi có text)
                            rejectPopoverCounter: đếm ký tự realtime (0/300)
    --%>
    <div id="taskPopoverHost" class="task-popover-host" aria-hidden="true">
    <div id="taskPopoverScrim" class="task-popover-scrim" aria-hidden="true"></div>
    <div id="taskApprovePopover" class="task-action-popover" aria-hidden="true">
        <strong id="approvePopoverTitle">Approve task</strong>
        <label class="field-label" for="approvePopoverComment">Comment (optional)</label>
        <textarea id="approvePopoverComment" maxlength="300" placeholder="Ghi chú cho assistant (tuỳ chọn)"></textarea>
        <p class="popover-helper">Không điền vẫn có thể approve bình thường.</p>
        <div class="popover-actions">
            <button class="btn small" type="button" data-popover-cancel="approve">Cancel</button>
            <button class="btn small success-soft" type="button" id="approvePopoverConfirm">Confirm approve</button>
        </div>
    </div>
    <div id="taskRejectPopover" class="task-action-popover" aria-hidden="true">
        <strong id="rejectPopoverTitle">Reject task</strong>
        <label class="field-label" for="rejectPopoverReason">Lý do từ chối *</label>
        <textarea id="rejectPopoverReason" maxlength="300" placeholder="Mô tả cần sửa gì..."></textarea>
        <div class="popover-counter" id="rejectPopoverCounter">0 / 300</div>
        <p class="popover-helper">Bắt buộc — người nhận task cần biết phải sửa gì.</p>
        <div class="popover-actions">
            <button class="btn small" type="button" data-popover-cancel="reject">Cancel</button>
            <button class="btn small danger-soft" type="button" id="rejectPopoverConfirm" disabled>Confirm reject</button>
        </div>
    </div>
    </div>
</div>

<%--
    [8] MODAL taskViewModal: xem và sửa chi tiết task
    Mở khi JS bắt click nút View/Edit trên hàng task
    taskViewContent: JS fetch API lấy detail rồi render HTML vào đây
    taskViewSaveBtn: JS gọi API PUT để lưu thay đổi (due date, notes...)
--%>
<div id="taskViewModal" class="modal-backdrop" aria-hidden="true">
    <div class="modal-card modal-card-wide" role="dialog" aria-modal="true" aria-labelledby="taskViewTitle">
        <button class="modal-close" type="button" data-modal-close aria-label="Close">&times;</button>
        <h3 id="taskViewTitle" class="section-title compact-title">Task Detail</h3>
        <p id="taskViewSubtitle" class="section-desc"></p>
        <div id="taskViewContent"></div>
        <div id="taskViewError" class="alert error task-view-error"></div>
        <div class="detail-actions modal-actions modal-actions-bottom task-view-actions">
            <button class="btn small" type="button" data-modal-close>Cancel</button>
            <button class="btn small primary" type="button" id="taskViewSaveBtn">Save changes</button>
        </div>
    </div>
</div>

<%-- [9] CONFIG SCRIPT: truyền contextPath xuống task-list.js để fetch đúng API URL --%>
<script>
window.TASK_LIST_CONFIG = {
    contextPath: '${pageContext.request.contextPath}'
};
</script>
<script src="${pageContext.request.contextPath}/assets/js/chaptertask/task-list.js?v=20260608split"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
