<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Task Detail</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<h2 class="page-title">Task #${task.id}</h2>
<p class="page-sub">${task.seriesTitle} - Ch. ${task.chapterNumber} ${task.chapterTitle}</p>

<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

<div class="section-card detail-grid">
    <div><span class="detail-label">Type</span><strong>${task.taskType}</strong></div>
    <div><span class="detail-label">Pages</span><strong>${task.pageRangeStart}-${task.pageRangeEnd}</strong></div>
    <div><span class="detail-label">Assigned To</span><strong>${task.assistantName}</strong></div>
    <div><span class="detail-label">Due Date</span><strong>${task.dueDate}</strong></div>
    <div><span class="detail-label">Status</span>
        <span class="status-chip ${task.status=='APPROVED' ? 'status-approved' : (task.status=='OVERDUE' ? 'status-overdue' : 'status-progress')}">${task.status}</span>
        <c:if test="${task.delayed}"><span class="status-chip status-delayed" style="margin-left:6px;">Delayed</span></c:if>
    </div>
</div>

<c:if test="${task.status == 'DELETED' || task.status == 'REASSIGNED'}">
    <div class="section-card">
        <h3 class="section-title compact-title">${task.status == 'DELETED' ? 'Task Deleted' : 'Task Reassigned'}</h3>
        <div class="alert warning" style="margin-bottom:0;">
            <div>This task is no longer editable.</div>
            <c:if test="${not empty task.actionReason}">
                <strong>Reason:</strong>
                <div style="margin-top:6px;white-space:pre-wrap;"><c:out value="${task.actionReason}" /></div>
            </c:if>
        </div>
    </div>
</c:if>

<c:if test="${not empty task.notes}">
    <div class="section-card">
        <h3 class="section-title compact-title">Mangaka Note</h3>
        <div style="white-space:pre-wrap;"><c:out value="${task.notes}" /></div>
    </div>
</c:if>

<c:if test="${not empty task.approvalComment || not empty task.rejectionReason}">
    <div class="section-card">
        <h3 class="section-title compact-title">Mangaka Feedback</h3>
        <c:if test="${not empty task.approvalComment}">
            <div class="alert success" style="margin-bottom:0;">
                <strong>Approval comment:</strong>
                <div style="margin-top:6px;"><c:out value="${task.approvalComment}" /></div>
            </div>
        </c:if>
        <c:if test="${not empty task.rejectionReason}">
            <div class="alert error" style="margin-bottom:0;">
                <strong>Revision note:</strong>
                <div style="margin-top:6px;"><c:out value="${task.rejectionReason}" /></div>
            </div>
        </c:if>
    </div>
</c:if>

<c:if test="${canMangakaReview}">
    <div class="section-card">
        <h3 class="section-title compact-title">Mangaka Review</h3>
        <div class="detail-actions">
            <form method="post" action="${pageContext.request.contextPath}/main/tasks/${task.id}/approve">
                <button class="btn success-soft" type="submit">Approve</button>
            </form>
            <form method="post" action="${pageContext.request.contextPath}/main/tasks/${task.id}/reject">
                <button class="btn danger-soft" type="submit" onclick="return confirm('Reject this task?');">Reject</button>
            </form>
        </div>
    </div>
</c:if>

<c:if test="${task.status == 'APPROVED'}">
    <div class="alert success page-approved-banner">
        Task này đã được Mangaka duyệt.
        Tất cả ảnh đã được cập nhật vào Chapter ${task.chapterNumber}: ${task.chapterTitle}.
    </div>
</c:if>

<div class="section-card">
    <div class="section-head">
        <div>
            <h3 class="section-title compact-title">Page Submission</h3>
            <p class="section-desc">
                Pages ${task.pageRangeStart}–${task.pageRangeEnd} · ${task.taskType}
            </p>
        </div>
    </div>
    <div id="pageProgressBar"></div>
    <div id="pageGrid" class="page-submission-grid"></div>
</div>

<div id="stickySubmitBar"></div>
<div id="toastContainer"></div>
<input type="file" id="pageFileInput" accept="image/*" style="display:none" />

<a class="btn" href="${pageContext.request.contextPath}/main/tasks">Back to Tasks</a>

<script>
const PAGE_TASK = {
    taskId: ${task.id},
    chapterId: ${task.chapterId},
    pageStart: ${task.pageRangeStart},
    pageEnd: ${task.pageRangeEnd},
    taskType: '${task.taskType}',
    status: '${task.status}',
    canUpdate: ${canAssistantUpdate},
    canSubmit: ${canAssistantSubmit},
    ctx: '${pageContext.request.contextPath}'
};
</script>
<script src="${pageContext.request.contextPath}/assets/page-submission.js"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
