<%--
  PURPOSE: Screen for managing all tasks:
            - Assistant: view assigned tasks, click to go to the submission page
    - Task table (tbody#taskRows): rendered server-side with JSTL <c:forEach> on every load
    - Metric cards (Active/Submitted/Delayed/Overdue/Completed): JS recounts and fills these in afterward
    - Filter pills (taskStatusPills): JS renders these after load
    - Action column (the Action column in the table): JS fills in buttons based on role and status
  STRUCTURE:
    [1] HEAD           — CSS import
    [2] METRIC CARDS   — 5 cards counting tasks by group, JS fills in the numbers afterward
    [3] ALERT BOX      — Shows API errors inline
    [4] TASK ACTIONS   — Panel containing the "Create Task" button, only shown to Mangaka (JS checks this)
    [5] MODAL: taskCreateModal  — Form to create a new task (select chapter → load assistants → fill in range/type/date)
    [6] ALL TASKS TABLE — List of all tasks, rendered server-side, Action column left empty for JS to fill in
    [7] POPOVER: approve/reject — Inline popover (not a modal) for the Mangaka to review a task
                                   Approve: comment optional | Reject: reason required
    [8] MODAL: taskViewModal    — Modal to view/edit task details (JS fills in the content dynamically)
    [9] CONFIG SCRIPT  — Passes contextPath down to task-list.js
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
    [2] METRIC CARDS: 5 cards summarizing task counts by status
    Default value is 0, JS reads the taskRows table then recounts and fills in these ids
    Active = IN_PROGRESS | Submitted = SUBMITTED | Delayed = late but not yet OVERDUE
    Overdue = past deadline | Completed = APPROVED
--%>
<section class="metric-grid">
    <article class="metric-card"><div id="activeTasks" class="metric-value">0</div><div class="metric-label">Active</div></article>
    <article class="metric-card"><div id="submittedTasks" class="metric-value metric-violet">0</div><div class="metric-label">Submitted</div></article>
    <article class="metric-card"><div id="delayedTasks" class="metric-value metric-amber">0</div><div class="metric-label">Delayed</div></article>
    <article class="metric-card"><div id="overdueTasks" class="metric-value metric-danger">0</div><div class="metric-label">Overdue</div></article>
    <article class="metric-card"><div id="completedTasks" class="metric-value metric-ok">0</div><div class="metric-label">Completed</div></article>
</section>

<%-- [3] ALERT BOX: JS shows this when an API error occurs (approve/reject/create failed) --%>
<div id="taskResult" class="alert error task-alert-hidden"></div>

<%--
    [4] TASK ACTIONS PANEL: contains the "Create Task" button
    JS checks the role — only Mangaka sees this panel
    Button data-modal-open="taskCreateModal" → JS handles the event to open modal [5]
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
    [5] MODAL taskCreateModal: form to create a new task
    Selection flow: Chapter (dropdown) → automatically loads the Assistant list for that chapter
              → fill in Page Start/End + Task Type + Due Date → submit
    createTaskDeadlineHint: JS fills in the suggested max deadline (chapter deadline - 3 days)
    taskCreateError: JS shows validation errors or API errors
    Note: this form is MISSING the SCREENTONE option in taskType — only 4/5 stages are present
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
        <select name="taskTypes" multiple required>
            <option value="">Select Task Type</option>
            <option value="SKETCHING">Sketching</option>
            <option value="INKING">Inking</option>
            <option value="COLORING">Coloring</option>
            <option value="SCREENTONE">Screentone</option>
            <option value="LETTERING">Lettering</option>
            <option value="MIXED">Mixed</option>
        </select>
        <label class="field-label" for="taskCreateDueDate">Due Date</label>
        <input id="taskCreateDueDate" name="dueDate" type="date" aria-label="Due Date" required />
        <div id="taskCreateError" class="alert error task-create-error"></div>
        <button class="btn primary" type="submit">Create</button>
        </form>
    </div>
</div>

<%--
    [6] ALL TASKS TABLE: task list rendered server-side with JSTL
    Status chip color changes by status: OVERDUE=red, IN_PROGRESS=yellow, PENDING=gray, APPROVED=green, others=draft
    Action column (last td) is left EMPTY — JS fills in buttons based on role and task.status:
      - Mangaka + SUBMITTED → Approve / Reject buttons
      - Assistant + IN_PROGRESS → View button (goes to task detail to upload)
    taskStatusPills: JS renders filter pills to filter by status
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
                    <td><c:out value="${t.taskTypesDisplay}" /></td>
                    <td>${t.assistantName}</td>
                    <td>
                        <span class="status-chip ${t.status=='OVERDUE' ? 'status-overdue' : (t.status=='IN_PROGRESS' ? 'status-progress' : (t.status=='PENDING' ? 'status-pending' : (t.status=='APPROVED' ? 'status-approved' : 'status-draft')))}">${t.status}</span>
                    </td>
                    <td>${t.dueDate}</td>
                    <td></td><%-- JS fills in the Action button here --%>
                </tr>
            </c:forEach>
            <c:if test="${empty tasks}"><tr><td colspan="8">No tasks found.</td></tr></c:if>
        </tbody>
    </table>

    <%--
        [7] POPOVER approve/reject: shown inline over the table (not a separate modal)
        taskPopoverScrim: dimmed overlay behind it, click to close the popover
        taskApprovePopover: Mangaka approves, comment optional, can approve without filling it in
        taskRejectPopover:  Mangaka rejects, reason is REQUIRED (Confirm button disabled until text is entered)
                            rejectPopoverCounter: real-time character counter (0/300)
    --%>
    <div id="taskPopoverHost" class="task-popover-host" aria-hidden="true">
    <div id="taskPopoverScrim" class="task-popover-scrim" aria-hidden="true"></div>
    <div id="taskApprovePopover" class="task-action-popover" aria-hidden="true">
        <strong id="approvePopoverTitle">Approve task</strong>
        <label class="field-label" for="approvePopoverComment">Comment (optional)</label>
        <textarea id="approvePopoverComment" maxlength="300" placeholder="Note for assistant (optional)"></textarea>
        <p class="popover-helper">You can approve without filling this in.</p>
        <div class="popover-actions">
            <button class="btn small" type="button" data-popover-cancel="approve">Cancel</button>
            <button class="btn small success-soft" type="button" id="approvePopoverConfirm">Confirm approve</button>
        </div>
    </div>
    <div id="taskRejectPopover" class="task-action-popover" aria-hidden="true">
        <strong id="rejectPopoverTitle">Reject task</strong>
        <label class="field-label" for="rejectPopoverReason">Rejection reason *</label>
        <textarea id="rejectPopoverReason" maxlength="300" placeholder="Describe what needs to be fixed..."></textarea>
        <div class="popover-counter" id="rejectPopoverCounter">0 / 300</div>
        <p class="popover-helper">Required — the assignee needs to know what to fix.</p>
        <div class="popover-actions">
            <button class="btn small" type="button" data-popover-cancel="reject">Cancel</button>
            <button class="btn small danger-soft" type="button" id="rejectPopoverConfirm" disabled>Confirm reject</button>
        </div>
    </div>
    </div>
</div>

<%--
    [8] MODAL taskViewModal: view and edit task details
    Opens when JS catches a click on the View/Edit button on a task row
    taskViewContent: JS fetches the API for details then renders the HTML here
    taskViewSaveBtn: JS calls a PUT API to save changes (due date, notes...)
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

<%-- [9] CONFIG SCRIPT: passes contextPath down to task-list.js so it fetches the correct API URL --%>
<script src="${pageContext.request.contextPath}/assets/js/chaptertask/task-list.js?v=20260608split"
        data-context-path="${pageContext.request.contextPath}"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
