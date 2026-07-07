<%--
  PURPOSE: Screen showing the details of a single task:
            - Assistant: view the assigned task, upload images per page, submit when done
  FLAGS FROM THE CONTROLLER (used to show/hide each section):
    - task            : TaskSummary object with full details
    - error           : error string if any (e.g. no access permission)
    - canAssistantUpdate : true if the assistant can upload images (task is IN_PROGRESS/REJECTED)
    - canAssistantSubmit : true if the assistant can click Submit (all pages uploaded)
    - canMangakaReview   : true if the Mangaka can approve/reject (task is SUBMITTED)
  STRUCTURE:
    [1] HEAD           — CSS import
    [2] ERROR ALERT    — Shows a server error if any (e.g. forbidden)
    [3] DETAIL GRID    — Basic info: Type, Pages, Assigned To, Due Date, Status
    [4] CLOSED NOTICE  — Shown when the task is DELETED or REASSIGNED, locks all actions
    [5] MANGAKA NOTE   — Note from the Mangaka when assigning the task (task.notes)
    [6] MANGAKA FEEDBACK — Shows the approval comment or rejection reason from the last review
    [7] MANGAKA REVIEW — Approve/reject form, only shown when canMangakaReview = true
    [8] APPROVED BANNER — Green banner when the task is APPROVED, notifying that images were merged into the chapter
    [9] PAGE SUBMISSION — Grid for uploading each page's image + progress bar (rendered here by JS)
    [10] STICKY SUBMIT BAR — JS renders a floating Submit button below when canAssistantSubmit
    [11] CONFIG SCRIPT — Passes task metadata down to page-submission.js
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

<%-- [2] ERROR ALERT: the controller puts "error" into the model when the task doesn't exist or access is denied --%>
<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

<%--
    [3] DETAIL GRID: basic task info, rendered server-side
    Status chip color changes by status: APPROVED=green, OVERDUE=red, others=yellow
    Delayed chip additionally shows if task.delayed = true (submitted later than the original dueDate)
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
    [4] CLOSED NOTICE: shown when the task has been closed (DELETED or REASSIGNED)
    A task in this state can no longer be uploaded to or submitted
    task.actionReason holds the reason the Mangaka entered when deleting/reassigning
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

<%-- [5] MANGAKA NOTE: note the Mangaka entered when creating the task (task.notes), only shown if present --%>
<c:if test="${not empty task.notes}">
    <div class="section-card">
        <h3 class="section-title compact-title">Mangaka Note</h3>
        <div class="task-detail-notes"><c:out value="${task.notes}" /></div>
    </div>
</c:if>

<%--
    [6] MANGAKA FEEDBACK: the Mangaka's review result from the previous review
    - approvalComment: comment made on approval (green)
    - rejectionReason: rejection reason, the assistant needs to read this to make fixes (red)
    Both can show at once if the task was rejected and then later approved
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

<%-- [6b] SUBMISSION HISTORY: timeline of all submit/review rounds, rendered here by JS --%>
<div class="section-card">
    <h3 class="section-title compact-title">Submission History</h3>
    <div id="submissionHistoryList"></div>
</div>

<%--
    [7] MANGAKA REVIEW: approve/reject form, only shown when canMangakaReview = true
    (the controller sets this true when: role MANGAKA + is the series owner + task is SUBMITTED)
    Uses a regular POST form, not a fetch API call
    Reject has a confirm dialog to prevent accidental clicks
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
    [8] APPROVED BANNER: green banner confirming the task has been approved
    When the Mangaka approves, all images from this task are automatically merged into the chapter
    Lets the assistant know the work is complete and has been recorded
--%>
<c:if test="${task.status == 'APPROVED'}">
    <div class="alert success page-approved-banner">
        This task has been approved by the Mangaka.
        All images have been updated to Chapter ${task.chapterNumber}: ${task.chapterTitle}.
    </div>
</c:if>

<%--
    [9] PAGE SUBMISSION: area for uploading each page's image
    pageProgressBar: JS renders the progress bar (X/Y pages uploaded)
    pageGrid: JS renders the grid of page slots, each slot can have an image uploaded
    pageFileInput: hidden file input, JS triggers a click when the user selects a slot
                   Only rendered if canAssistantUpdate = true
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

<%-- [10] STICKY SUBMIT BAR + TOAST: JS renders a floating "Submit task" button when canAssistantSubmit = true --%>
<div id="stickySubmitBar"></div>
<div id="toastContainer"></div>
<c:if test="${canAssistantUpdate}">
    <input type="file" id="pageFileInput" accept="image/*" class="task-detail-hidden-file" hidden />
</c:if>

<a class="btn" href="${pageContext.request.contextPath}/main/tasks">Back to Tasks</a>

<%--
    [11] CONFIG SCRIPT: passes task metadata down to page-submission.js
    canUpdate: whether the assistant can upload images
    canSubmit: whether the assistant can click Submit
    page-submission.js uses these flags to show/hide buttons and lock slots when not permitted
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
