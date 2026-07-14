<%--
  1. HEAD         — CSS imports
  2. HEADER       — Shared navigation header
  3. BREADCRUMB   — Navigation: My Series › Series › Chapter
  4. ACTION BAR   — Delete / Submit for review / Manuscript Workspace buttons
  5. TAB BAR      — 3 tabs: Pages | Tasks | Edit details
     5a. TAB PAGES     — Page slot grid + progress bar
     5b. TAB TASKS     — Task table + Approve/Reject popover
     5c. TAB EDIT      — Form to edit title & deadline
  6. SIDEBAR      — Meta panel + color legend + condensed task list
  7. MODAL: pageCompareModal        — Compare page versions
  8. MODAL: pageUploadModal         — Upload page slot image
  9. MODAL: assignTaskModal         — Assign task to assistant
  10. MODAL: taskReassignModal      — Reassign task
  11. MODAL: taskOverdueDecisionModal — Handle overdue task
  12. CONFIG SCRIPT — Pass contextPath down to JS
--%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<%-- [1] HEAD: import global CSS (styles.css) and this page's own CSS (chapter-detail.css) --%>
<head>
    <meta charset="UTF-8">
    <title>Chapter Detail</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=20260525" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/chaptertask/chapter-detail.css?v=20260703history" />
</head>
<body>
<%-- [2] HEADER: shared navigation bar used across the whole app, see common/header.jsp --%>
<jsp:include page="../common/header.jsp" />

<div id="detailResult" class="alert error chapter-detail-inline-1"></div>
<%--
    [3] BREADCRUMB: shows the current location
    Displays: My Series › {series name} › {chapter name} [STATUS]
    breadcrumbSeries and breadcrumbChapter start empty — JS fills them in after fetching the API
--%>
<div id="breadcrumb" class="chapter-detail-inline-2">
    <a href="${pageContext.request.contextPath}/main/series" class="chapter-detail-inline-3">My Series</a>
    <span>›</span>
    <a id="breadcrumbSeries" href="#" class="chapter-detail-inline-4"></a>
    <span>›</span>
    <span id="breadcrumbChapter" class="chapter-detail-inline-5"></span>
    <span id="breadcrumbStatusPill" class="chapter-detail-inline-6"></span>
</div>
<%--
    [4] ACTION BAR: 3 main action buttons, hidden by default, JS shows them based on conditions
    - btnDelete: only shown when the chapter is in PLANNING and has no tasks yet (chapter deletion business rule)
    - btnMarkDone: only shown when the chapter is eligible to submit the manuscript
    - btnManuscriptWorkspace: link to the manuscript review page
--%>
<div class="chapter-detail-inline-7">
    <button id="btnDelete" class="btn small chapter-detail-inline-8 is-hidden-initial" type="button">Delete chapter</button>
    <button id="btnMarkDone" class="btn primary chapter-detail-inline-9 is-hidden-initial" type="button">Submit for review</button>
    <a id="btnManuscriptWorkspace" href="#" class="btn small chapter-detail-inline-10 is-hidden-initial">📝 Manuscript Workspace</a>
</div>

<div class="chapter-workspace">
    <div class="section-card chapter-main-card">
<%--
    [5] TAB BAR: 3 tabs for navigating the main content
    - Pages (5a): manage page slots, assign tasks
    - Tasks (5b): view & review tasks
    - Edit details (5c): edit chapter information
    The badge counts (tabPageCount, tabTaskCount) are updated by JS
--%>
        <div id="tabBar" class="chapter-tab-bar">
            <button class="chapter-tab-btn active" type="button" data-tab="pages">
                Pages <span id="tabPageCount" class="status-chip chapter-detail-inline-11">0</span>
            </button>
            <button class="chapter-tab-btn" type="button" data-tab="tasks">
                Tasks <span id="tabTaskCount" class="status-chip chapter-detail-inline-12">0</span>
            </button>
            <button class="chapter-tab-btn" type="button" data-tab="edit">Edit details</button>
        </div>
<%--
    [5a] TAB PAGES: thumbnail grid of all page slots in the chapter
    - Mangaka can select multiple slots → click "Assign task" → opens assignTaskModal
    - Click a slot → opens pageUploadModal to upload/view the image
    - pagesOwnerActions (the + Add page button) is only shown to the Mangaka who owns the series
--%>
        <div id="tabPages" class="chapter-tab-panel">
            <div class="pages-toolbar">
                <span id="pageCountLabel" class="chapter-detail-inline-13">Loading...</span>
                <div id="pagesOwnerActions" class="chapter-detail-inline-14">
                    <button class="btn small primary" type="button" id="btnAddPage">+ Add page</button>
                    <input id="singleFileInput" type="file" accept="image/*" class="chapter-detail-inline-15" />
                </div>
            </div>

            <div id="pagesHint" class="pages-hint chapter-detail-inline-16">
                Select empty or uploaded pages to assign a task to an assistant.
            </div>

            <div id="selectionBar" class="pages-selection-bar">
                <span id="selectionLabel" class="chapter-detail-inline-17">0 pages selected</span>
                <div class="chapter-detail-inline-18">
                    <button class="btn small primary" type="button" id="btnAssignFromSelection">Assign task</button>
                    <button class="btn small" type="button" id="btnClearSelection">Clear selection</button>
                </div>
            </div>
            <div id="pageSlotGrid" class="page-slot-grid">
                <p class="chapter-detail-inline-19">Loading pages...</p>
            </div>
<%--
    [5a.1] PROGRESS BAR: chapter progress bar
    Formula BR-TSK-11: (Approved tasks / Total tasks) × 100%
    JS automatically updates it whenever a task's status changes
--%>
            <div id="progressSection">
                <div class="chapter-detail-inline-20">
                    <span class="chapter-detail-inline-21">Page progress</span>
                    <span id="progressLabel" class="chapter-detail-inline-22"></span>
                </div>
                <div class="progress"><span id="progressFill" class="chapter-detail-inline-23"></span></div>
            </div>
        </div>
<%--
    [5b] TAB TASKS: table listing all tasks belonging to this chapter
    Columns: ID | Pages | Type | Assigned To | Status | Due Date | Action
    taskPopoverHost contains 2 inline popovers (not modals):
      - taskApprovePopover: Mangaka approves the task, comment is optional
      - taskRejectPopover: Mangaka rejects the task, reason is required (BR-TSK-05)
--%>
        <div id="tabTasks" class="chapter-tab-panel chapter-detail-inline-24">
            <div id="chapterTaskTableWrap" class="section-card chapter-detail-inline-25">
                <table class="data-table chapter-detail-inline-26">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Pages</th>
                            <th>Type</th>
                            <th>Assigned To</th>
                            <th>Status</th>
                            <th>Due Date</th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody id="chapterTaskRows">
                        <tr><td colspan="7">Loading tasks...</td></tr>
                    </tbody>
                </table>
            </div>
            <div id="taskPopoverHost" class="chapter-detail-inline-27" aria-hidden="true">
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
    [5c] TAB EDIT: form to edit chapter information
    Only the Mangaka who owns the series can perform this
    Fields: Title, Submission deadline
    BR-CHP-02: deadline must be at least 14 days before the publication date
--%>
        <div id="tabEdit" class="chapter-tab-panel chapter-detail-inline-28">
            <form id="chapterUpdateForm" class="form-grid chapter-inline-update-form" data-prevent-submit>
                <input name="chapterId" type="hidden" id="updateChapterId" />
                <label class="field-label" for="updateTitle">Title</label>
                <input id="updateTitle" name="title" type="text" required />
                <label class="field-label" for="updateDeadline">Submission deadline</label>
                <input id="updateDeadline" name="submissionDeadline" type="date" required />
                <div id="updateError" class="alert error chapter-detail-inline-29"></div>
                <div class="chapter-edit-actions">
                    <button type="button" class="btn primary" id="btnSaveChapterMetadata" disabled>Save</button>
                </div>
            </form>
        </div>
    </div>
<%--
    [6] RIGHT SIDEBAR: 3 supporting info panels
    - Meta panel: deadline, total page count, status, % progress (filled in by JS)
    - Color legend: explains the 4 page slot states (Empty/In progress/Pending review/Done)
    - Condensed task list: a quick list of tasks (JS fills sidebarTaskList)
--%>
    <aside>
        <div class="panel chapter-detail-inline-30">
            <strong id="panelChapterTitle" class="chapter-detail-inline-31"></strong>
            <p id="panelSeriesName" class="section-desc chapter-detail-inline-32"></p>
            <div class="chapter-detail-inline-33">
                <div class="chapter-detail-inline-34">
                    <div class="chapter-detail-inline-35">Deadline</div>
                    <div id="metaDeadline"></div>
                    <div id="metaDeadlineSub" class="chapter-detail-inline-36"></div>
                </div>
                <div class="chapter-detail-inline-37">
                    <div class="chapter-detail-inline-38">Pages</div>
                    <div id="metaPages" class="chapter-detail-inline-39"></div>
                </div>
                <div class="chapter-detail-inline-40">
                    <div class="chapter-detail-inline-41">Status</div>
                    <div id="metaStatus" class="chapter-detail-inline-42"></div>
                </div>
                <div class="chapter-detail-inline-43">
                    <div class="chapter-detail-inline-44">Page progress</div>
                    <div id="metaProgress" class="chapter-detail-inline-45"></div>
                </div>
            </div>
        </div>
        <div class="panel chapter-detail-inline-46">
            <strong class="chapter-detail-inline-47">Page status legend</strong>
            <div class="page-status-legend">
                <div class="legend-row">
                    <span class="legend-swatch legend-empty"></span>
                    <span><strong>Empty</strong>No page image yet.</span>
                </div>
                <div class="legend-row">
                    <span class="legend-swatch legend-progress"></span>
                    <span><strong>In progress</strong>The page's task is in progress.</span>
                </div>
                <div class="legend-row">
                    <span class="legend-swatch legend-submitted"></span>
                    <span><strong>Pending review</strong>Assistant has submitted the task.</span>
                </div>
                <div class="legend-row">
                    <span class="legend-swatch legend-complete-solid"></span>
                    <span><strong>Done</strong>Page has completed all 5 stages.</span>
                </div>
            </div>
        </div>
        <div class="panel">
            <div class="chapter-detail-inline-48">
                <strong class="chapter-detail-inline-49">Tasks</strong>
            </div>
            <div id="sidebarTaskList"><p class="section-desc chapter-detail-inline-50">Loading...</p></div>
        </div>
    </aside>
</div>
<%-- [7] MODAL pageCompareModal: compares image versions of a page
Opens when: clicking "Compare" on a page slot that has upload history --%>
<div id="pageCompareModal" class="chapter-detail-inline-51">
  <div class="chapter-detail-inline-52">
    <button id="pageCompareClose" class="chapter-detail-inline-53">&times;</button>
    <div id="pageCompareTitle" class="chapter-detail-inline-54"></div>
    <div id="pageCompareBody"></div>
  </div>
</div>
<%-- [7b] MODAL pageHistoryModal: upload + stage history of a page, allows the owning Mangaka to roll back --%>
<div id="pageHistoryModal" class="chapter-detail-inline-51">
  <div class="chapter-detail-inline-52">
    <button id="pageHistoryClose" class="chapter-detail-inline-53">&times;</button>
    <div id="pageHistoryTitle" class="chapter-detail-inline-54"></div>
    <div id="pageHistoryBody"></div>
  </div>
</div>
<%--
    [8] MODAL pageUploadModal: upload/view/delete the image of a page slot
    Stage picker: tick off the 5 stages (Sketching→Inking→Coloring→Screentone→Lettering)
    pageUploadDelete is only shown to the Mangaka (JS checks isOwner())
--%>
<div id="pageUploadModal" class="modal-backdrop" aria-hidden="true">
    <div class="modal-card modal-card-wide" role="dialog" aria-modal="true" aria-labelledby="pageUploadTitle">
        <button class="modal-close" type="button" data-modal-close aria-label="Close">&times;</button>
        <h3 id="pageUploadTitle" class="section-title compact-title">Upload page</h3>
        <p id="pageUploadSubtitle" class="section-desc"></p>
        <div id="pageUploadPreview" class="page-upload-preview"></div>
        <label class="field-label chapter-detail-inline-55" for="pageModalFileInput">Image file</label>
        <input id="pageModalFileInput" type="file" accept="image/*" />
        <label class="field-label chapter-detail-inline-56">Stages completed</label>
        <div id="pageUploadStagePicker" class="page-stage-picker" title="Tick stages completed by this page image">
            <label><input type="checkbox" value="SKETCHING" />Sketching</label>
            <label><input type="checkbox" value="INKING" />Inking</label>
            <label><input type="checkbox" value="COLORING" />Coloring</label>
            <label><input type="checkbox" value="SCREENTONE" />Screentone</label>
            <label><input type="checkbox" value="LETTERING" />Lettering</label>
        </div>
        <div id="pageUploadError" class="alert error chapter-detail-inline-57"></div>
        <div class="page-upload-modal-actions chapter-detail-inline-58">
            <a id="pageUploadDownload" class="btn small chapter-detail-inline-59" href="#" download>Download current</a>
            <button class="btn small danger-soft chapter-detail-inline-60" type="button" id="pageUploadDelete">Delete page</button>
            <div class="chapter-detail-inline-61">
                <button class="btn small" type="button" data-modal-close>Cancel</button>
                <button class="btn small primary" type="button" id="pageUploadSave">Save page</button>
            </div>
        </div>
    </div>
</div>
<%--
    [9] MODAL assignTaskModal: assign a new task to an assistant
    Pre-fills assignPageChips with the pages selected from the Pages grid
    BR-CHP-03: only Mangaka can assign | BR-CHP-05: cannot assign to self
--%>
<div id="assignTaskModal" class="modal-backdrop" aria-hidden="true">
    <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="assignTaskTitle">
        <button class="modal-close" type="button" data-modal-close aria-label="Close">&times;</button>
        <h3 id="assignTaskTitle" class="section-title compact-title">Assign task to page</h3>
        <form id="assignTaskForm" class="form-grid">
            <label class="field-label">Selected pages</label>
            <div id="assignPageChips" class="assign-chips"><span class="section-desc chapter-detail-inline-62">No pages selected — select from the Pages grid or open from the sidebar after selecting.</span></div>
            <label class="field-label">Work to do</label>
            <div id="assignTaskTypeSummary" class="assign-stage-summary section-desc chapter-detail-inline-63">Automatically calculated based on each page's next stage.</div>
            <label class="field-label" for="assignAssistantId">Assistant</label>
            <select id="assignAssistantId" name="assistantId" required>
                <option value="">Loading assistants...</option>
            </select>
            <label class="field-label" for="assignDueDate">Due date</label>
            <input id="assignDueDate" name="dueDate" type="date" required />
            <p id="assignDueHint" class="section-desc"></p>
            <label class="field-label" for="assignNotes">Notes</label>
            <textarea id="assignNotes" name="notes" rows="3" placeholder="Instructions for assistant..."></textarea>
            <div id="assignTaskError" class="alert error chapter-detail-inline-64"></div>
            <button class="btn primary" type="submit" id="assignTaskSubmit">Create task</button>
        </form>
    </div>
</div>
<%--
    [10] MODAL taskReassignModal: change the assistant working on a task
    BR-TSK-03: on reassign → task resets to In Progress, previous submission is cleared
--%>
<div id="taskReassignModal" class="modal-backdrop" aria-hidden="true">
    <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="taskReassignTitle">
        <button class="modal-close" type="button" data-modal-close aria-label="Close">&times;</button>
        <h3 id="taskReassignTitle" class="section-title compact-title">Reassign task</h3>
        <form id="taskReassignForm" class="form-grid">
            <input type="hidden" id="taskReassignId" />
            <label class="field-label" for="taskReassignAssistantId">New assistant</label>
            <select id="taskReassignAssistantId" required>
                <option value="">Loading assistants...</option>
            </select>
            <label class="field-label" for="taskReassignReason">Reason</label>
            <textarea id="taskReassignReason" rows="3" maxlength="300" required placeholder="Reason for reassignment..."></textarea>
            <div id="taskReassignError" class="alert error chapter-detail-inline-65"></div>
            <button class="btn primary" type="submit">Confirm reassign</button>
        </form>
    </div>
</div>
<%--
    [11] MODAL taskOverdueDecisionModal: handle an overdue task (BR-TSK-10)
    3 choices:
      - Extend: extend the due date
      - Reassign: change assistant + set a new due date
      - Delete: remove the task from production tracking (reason required)
--%>
<div id="taskOverdueDecisionModal" class="modal-backdrop" aria-hidden="true">
    <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="taskOverdueDecisionTitle">
        <button class="modal-close" type="button" data-modal-close aria-label="Close">&times;</button>
        <h3 id="taskOverdueDecisionTitle" class="section-title compact-title">Overdue task decision</h3>
        <p id="taskOverdueDecisionSummary" class="section-desc"></p>
        <div class="overdue-choice-row" role="tablist" aria-label="Overdue task action">
            <button class="btn small overdue-choice-btn" type="button" data-overdue-action-choice="extend">Extend</button>
            <button class="btn small overdue-choice-btn" type="button" data-overdue-action-choice="reassign">Reassign</button>
            <button class="btn small overdue-choice-btn danger-soft" type="button" data-overdue-action-choice="delete">Delete</button>
        </div>
        <div class="overdue-decision-stack">
            <form id="taskExtendForm" class="form-grid overdue-decision-panel" data-overdue-action-panel="extend">
                <input type="hidden" id="taskExtendId" />
                <strong>Extend deadline</strong>
                <label class="field-label" for="taskExtendDueDate">New due date</label>
                <input id="taskExtendDueDate" type="date" required />
                <p id="taskExtendHint" class="section-desc"></p>
                <label class="field-label" for="taskExtendReason">Reason</label>
                <textarea id="taskExtendReason" rows="3" maxlength="300" placeholder="Reason for extension..."></textarea>
                <div id="taskExtendError" class="alert error chapter-detail-inline-65"></div>
                <button class="btn primary" type="submit">Extend task</button>
            </form>
            <div class="form-grid overdue-decision-panel" data-overdue-action-panel="reassign">
                <strong>Reassign task</strong>
                <label class="field-label" for="taskOverdueReassignAssistantId">New assistant</label>
                <select id="taskOverdueReassignAssistantId">
                    <option value="">Loading assistants...</option>
                </select>
                <label class="field-label" for="taskOverdueReassignDueDate">New due date</label>
                <input id="taskOverdueReassignDueDate" type="date" />
                <label class="field-label" for="taskOverdueReason">Reason</label>
                <textarea id="taskOverdueReason" rows="3" maxlength="300" placeholder="Reason for reassign..."></textarea>
                <div id="taskOverdueDecisionError" class="alert error chapter-detail-inline-65"></div>
                <div class="overdue-decision-actions">
                    <button class="btn small" type="button" id="taskOverdueReassignBtn">Reassign</button>
                </div>
            </div>
            <div class="form-grid overdue-decision-panel" data-overdue-action-panel="delete">
                <strong>Delete task</strong>
                <p class="section-desc">This closes the overdue task and removes it from active production tracking.</p>
                <label class="field-label" for="taskOverdueDeleteReason">Reason</label>
                <textarea id="taskOverdueDeleteReason" rows="3" maxlength="300" placeholder="Reason for delete..."></textarea>
                <div id="taskOverdueDeleteError" class="alert error chapter-detail-inline-65"></div>
                <div class="overdue-decision-actions">
                    <button class="btn small danger-soft" type="button" id="taskOverdueDeleteBtn">Delete task</button>
                </div>
            </div>
        </div>
    </div>
</div>
<%--
    [12] CONFIG SCRIPT: passes contextPath from the server down to JS
    Needed so fetch() calls the correct API URL when the app is deployed under a subdirectory
    Placed before chapter-detail.js so JS can read it immediately on load
--%>
<script src="${pageContext.request.contextPath}/assets/js/chaptertask/chapter-detail.js?v=20260703history"
        data-context-path="${pageContext.request.contextPath}"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
