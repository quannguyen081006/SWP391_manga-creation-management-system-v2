<%--
  MỤC ĐÍCH: Màn hình xem chi tiết 1 task cụ thể:
            - Assistant: xem task được giao, upload ảnh từng page, submit khi xong
  CÁC FLAG TỪ CONTROLLER (dùng để ẩn/hiện từng section):
    - task            : object TaskSummary đầy đủ thông tin
    - error           : chuỗi lỗi nếu có (vd: không có quyền truy cập)
    - canAssistantUpdate : true nếu assistant có thể upload ảnh (task đang IN_PROGRESS/REJECTED)
    - canAssistantSubmit : true nếu assistant có thể bấm Submit (đã upload đủ trang)
    - canMangakaReview   : true nếu Mangaka có thể approve/reject (task đang SUBMITTED)
  CẤU TRÚC:
    [1] HEAD           — CSS import
    [2] ERROR ALERT    — Hiện lỗi từ server nếu có (vd: forbidden)
    [3] DETAIL GRID    — Thông tin cơ bản: Type, Pages, Assigned To, Due Date, Status
    [4] CLOSED NOTICE  — Hiện khi task bị DELETED hoặc REASSIGNED, khoá mọi thao tác
    [5] MANGAKA NOTE   — Ghi chú từ Mangaka khi gán task (task.notes)
    [6] MANGAKA FEEDBACK — Hiện comment approve hoặc lý do reject từ lần review trước
    [7] MANGAKA REVIEW — Form approve/reject, chỉ hiện khi canMangakaReview = true
    [8] APPROVED BANNER — Banner xanh khi task đã APPROVED, thông báo ảnh đã vào chapter
    [9] PAGE SUBMISSION — Grid upload ảnh từng page + progress bar (JS render vào đây)
    [10] STICKY SUBMIT BAR — JS render nút Submit nổi bên dưới khi canAssistantSubmit
    [11] CONFIG SCRIPT — Truyền task metadata xuống page-submission.js
--%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<%-- [1] HEAD --%>
<head>
    <meta charset="UTF-8">
    <title>Task Detail</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/chaptertask/task-list.css?v=20260605fix3" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<%-- [2] ERROR ALERT: controller đặt "error" vào model khi task không tồn tại hoặc không có quyền --%>
<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

<%--
    [3] DETAIL GRID: thông tin cơ bản của task, render server-side
    Status chip đổi màu theo trạng thái: APPROVED=xanh, OVERDUE=đỏ, còn lại=vàng
    Delayed chip hiện thêm nếu task.delayed = true (nộp trễ so với dueDate gốc)
--%>
<div class="section-card detail-grid">
    <div><span class="detail-label">Types</span><strong><c:out value="${task.taskTypesDisplay}" /></strong></div>
    <div><span class="detail-label">Pages</span><strong>${task.pageRangeStart}-${task.pageRangeEnd}</strong></div>
    <div><span class="detail-label">Assigned To</span><strong>${task.assistantName}</strong></div>
    <div><span class="detail-label">Due Date</span><strong>${task.dueDate}</strong></div>
    <div><span class="detail-label">Status</span>
        <span class="status-chip ${task.status=='APPROVED' ? 'status-approved' : (task.status=='OVERDUE' ? 'status-overdue' : 'status-progress')}">${task.status}</span>
        <c:if test="${task.delayed}"><span class="status-chip status-delayed task-detail-delayed-chip">Delayed</span></c:if>
    </div>
</div>

<%--
    [4] CLOSED NOTICE: hiện khi task đã bị đóng (DELETED hoặc REASSIGNED)
    Task ở trạng thái này không thể upload hay submit thêm gì nữa
    task.actionReason chứa lý do Mangaka nhập khi delete/reassign
--%>
<c:if test="${task.status == 'DELETED' || task.status == 'REASSIGNED'}">
    <div class="section-card">
        <h3 class="section-title compact-title">${task.status == 'DELETED' ? 'Task Deleted' : 'Task Reassigned'}</h3>
        <div class="alert warning task-detail-alert-flat">
            <div>This task is no longer editable.</div>
            <c:if test="${not empty task.actionReason}">
                <strong>Reason:</strong>
                <div class="task-detail-preline-note"><c:out value="${task.actionReason}" /></div>
            </c:if>
        </div>
    </div>
</c:if>

<%-- [5] MANGAKA NOTE: ghi chú Mangaka điền khi tạo task (task.notes), chỉ hiện nếu có --%>
<c:if test="${not empty task.notes}">
    <div class="section-card">
        <h3 class="section-title compact-title">Mangaka Note</h3>
        <div class="task-detail-notes"><c:out value="${task.notes}" /></div>
    </div>
</c:if>

<%--
    [6] MANGAKA FEEDBACK: kết quả review của Mangaka từ lần duyệt trước
    - approvalComment: comment khi approve (màu xanh)
    - rejectionReason: lý do reject, assistant cần đọc để sửa (màu đỏ)
    Hai cái có thể cùng hiện nếu task bị reject rồi approve sau đó
--%>
<c:if test="${not empty task.approvalComment || not empty task.rejectionReason}">
    <div class="section-card">
        <h3 class="section-title compact-title">Mangaka Feedback</h3>
        <c:if test="${not empty task.approvalComment}">
            <div class="alert success task-detail-alert-flat">
                <strong>Approval comment:</strong>
                <div class="task-detail-feedback-body"><c:out value="${task.approvalComment}" /></div>
            </div>
        </c:if>
        <c:if test="${not empty task.rejectionReason}">
            <div class="alert error task-detail-alert-flat">
                <strong>Revision note:</strong>
                <div class="task-detail-feedback-body"><c:out value="${task.rejectionReason}" /></div>
            </div>
        </c:if>
    </div>
</c:if>

<%-- [6b] SUBMISSION HISTORY: timeline toàn bộ các round submit/review, JS render vào đây --%>
<div class="section-card">
    <h3 class="section-title compact-title">Submission History</h3>
    <div id="submissionHistoryList"></div>
</div>

<%--
    [7] MANGAKA REVIEW: form approve/reject, chỉ hiện khi canMangakaReview = true
    (controller set true khi: role MANGAKA + là chủ series + task đang SUBMITTED)
    Dùng form POST thông thường, không phải fetch API
    Reject có confirm dialog để tránh bấm nhầm
--%>
<c:if test="${canMangakaReview}">
    <div class="section-card">
        <h3 class="section-title compact-title">Mangaka Review</h3>
        <div class="detail-actions">
            <form method="post" action="${pageContext.request.contextPath}/main/tasks/${task.id}/approve">
                <button class="btn success-soft" type="submit">Approve</button>
            </form>
            <form method="post" action="${pageContext.request.contextPath}/main/tasks/${task.id}/reject">
                <button class="btn danger-soft" type="submit" data-confirm="Reject this task?">Reject</button>
            </form>
        </div>
    </div>
</c:if>

<%--
    [8] APPROVED BANNER: banner xanh xác nhận task đã được duyệt
    Khi Mangaka approve, tất cả ảnh của task này tự động được cập nhật vào chapter
    Thông báo để assistant biết công việc đã hoàn thành và được ghi nhận
--%>
<c:if test="${task.status == 'APPROVED'}">
    <div class="alert success page-approved-banner">
        This task has been approved by the Mangaka.
        All images have been updated to Chapter ${task.chapterNumber}: ${task.chapterTitle}.
    </div>
</c:if>

<%--
    [9] PAGE SUBMISSION: khu vực upload ảnh từng page
    pageProgressBar: JS render thanh tiến độ (X/Y trang đã upload)
    pageGrid: JS render grid các page slot, mỗi slot có thể upload ảnh
    pageFileInput: input file ẩn, JS trigger click khi người dùng chọn slot
                   Chỉ render nếu canAssistantUpdate = true
--%>
<div class="section-card">
    <div class="section-head">
        <div>
            <h3 class="section-title compact-title">Page Submission</h3>
            <p class="section-desc">
                Pages ${task.pageRangeStart}–${task.pageRangeEnd} · <c:out value="${task.taskTypesDisplay}" />
            </p>
        </div>
    </div>
    <div id="pageProgressBar"></div>
    <div id="pageGrid" class="page-submission-grid"></div>
</div>

<%-- [10] STICKY SUBMIT BAR + TOAST: JS render nút "Submit task" nổi khi canAssistantSubmit = true --%>
<div id="stickySubmitBar"></div>
<div id="toastContainer"></div>
<c:if test="${canAssistantUpdate}">
    <input type="file" id="pageFileInput" accept="image/*" class="task-detail-hidden-file" hidden />
</c:if>

<a class="btn" href="${pageContext.request.contextPath}/main/tasks">Back to Tasks</a>

<%--
    [11] CONFIG SCRIPT: truyền task metadata xuống page-submission.js
    canUpdate: assistant có thể upload ảnh không
    canSubmit: assistant có thể bấm Submit không
    page-submission.js dùng các flag này để ẩn/hiện nút, khóa slot khi không có quyền
--%>
<script src="${pageContext.request.contextPath}/assets/js/chaptertask/page-submission.js?v=20260605fix1"
        data-task-id="${task.id}"
        data-chapter-id="${task.chapterId}"
        data-page-start="${task.pageRangeStart}"
        data-page-end="${task.pageRangeEnd}"
        data-task-types="${task.taskTypesDisplay}"
        data-status="${task.status}"
        data-can-update="${canAssistantUpdate}"
        data-can-submit="${canAssistantSubmit}"
        data-context-path="${pageContext.request.contextPath}"></script>

<script src="${pageContext.request.contextPath}/assets/js/chaptertask/submission-history.js?v=20260703"
        data-task-id="${task.id}"
        data-context-path="${pageContext.request.contextPath}"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
