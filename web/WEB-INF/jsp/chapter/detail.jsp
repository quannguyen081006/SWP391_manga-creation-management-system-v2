<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Chapter Detail</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=20260525" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/chapter-detail.css?v=20260605buttons" />
</head>
<body>
<jsp:include page="../common/header.jsp" />
<%-- Chapter/task note: page-specific CSS is in /assets/chapter-detail.css; this JSP keeps markup and data hooks only. --%>

<div id="detailResult" class="alert error chapter-detail-inline-1"></div>

<div id="breadcrumb" class="chapter-detail-inline-2">
    <a href="${pageContext.request.contextPath}/main/series" class="chapter-detail-inline-3">My Series</a>
    <span>›</span>
    <a id="breadcrumbSeries" href="#" class="chapter-detail-inline-4"></a>
    <span>›</span>
    <span id="breadcrumbChapter" class="chapter-detail-inline-5"></span>
    <span id="breadcrumbStatusPill" class="chapter-detail-inline-6"></span>
</div>

<div class="chapter-detail-inline-7">
    <button id="btnDelete" class="btn small chapter-detail-inline-8" type="button" style="display:none;">Delete chapter</button>
    <button id="btnMarkDone" class="btn primary chapter-detail-inline-9" type="button" style="display:none;">Submit for review</button>
    <a id="btnManuscriptWorkspace" href="#" class="btn small chapter-detail-inline-10" style="display:none;">📝 Manuscript Workspace</a>
</div>

<div class="chapter-workspace">
    <div class="section-card chapter-main-card">
        <div id="tabBar" class="chapter-tab-bar">
            <button class="chapter-tab-btn active" type="button" data-tab="pages">
                Pages <span id="tabPageCount" class="status-chip chapter-detail-inline-11">0</span>
            </button>
            <button class="chapter-tab-btn" type="button" data-tab="tasks">
                Tasks <span id="tabTaskCount" class="status-chip chapter-detail-inline-12">0</span>
            </button>
            <button class="chapter-tab-btn" type="button" data-tab="edit">Edit details</button>
        </div>

        <div id="tabPages" class="chapter-tab-panel">
            <div class="pages-toolbar">
                <span id="pageCountLabel" class="chapter-detail-inline-13">Đang tải...</span>
                <div id="pagesOwnerActions" class="chapter-detail-inline-14">
                    <button class="btn small primary" type="button" id="btnAddPage">+ Thêm trang</button>
                    <input id="singleFileInput" type="file" accept="image/*" class="chapter-detail-inline-15" />
                </div>
            </div>

            <div id="pagesHint" class="pages-hint chapter-detail-inline-16">
                Chọn các trang trống hoặc đã upload để gán task cho assistant.
            </div>

            <div id="selectionBar" class="pages-selection-bar">
                <span id="selectionLabel" class="chapter-detail-inline-17">0 trang đã chọn</span>
                <div class="chapter-detail-inline-18">
                    <button class="btn small primary" type="button" id="btnAssignFromSelection">Gán task</button>
                    <button class="btn small" type="button" id="btnClearSelection">Bỏ chọn</button>
                </div>
            </div>
            <div id="pageSlotGrid" class="page-slot-grid">
                <p class="chapter-detail-inline-19">Đang tải trang...</p>
            </div>
            <div id="progressSection">
                <div class="chapter-detail-inline-20">
                    <span class="chapter-detail-inline-21">Tiến độ page</span>
                    <span id="progressLabel" class="chapter-detail-inline-22"></span>
                </div>
                <div class="progress"><span id="progressFill" class="chapter-detail-inline-23"></span></div>
            </div>
        </div>

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

        <div id="tabEdit" class="chapter-tab-panel chapter-detail-inline-28">
            <form id="chapterUpdateForm" class="form-grid chapter-inline-update-form" onsubmit="return false;">
                <input name="chapterId" type="hidden" id="updateChapterId" />
                <label class="field-label" for="updateTitle">Title</label>
                <input id="updateTitle" name="title" type="text" required />
                <label class="field-label" for="updateDeadline">Submission deadline</label>
                <input id="updateDeadline" name="submissionDeadline" type="date" required />
                <div id="updateError" class="alert error chapter-detail-inline-29"></div>
            </form>
        </div>
    </div>

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
            <strong class="chapter-detail-inline-47">Chú thích trạng thái trang</strong>
            <div class="page-status-legend">
                <div class="legend-row">
                    <span class="legend-swatch legend-empty"></span>
                    <span><strong>Trống</strong>Chưa có ảnh page.</span>
                </div>
                <div class="legend-row">
                    <span class="legend-swatch legend-progress"></span>
                    <span><strong>Đang làm</strong>Task của trang đang in progress.</span>
                </div>
                <div class="legend-row">
                    <span class="legend-swatch legend-submitted"></span>
                    <span><strong>Chờ duyệt</strong>Assistant đã submit task.</span>
                </div>
                <div class="legend-row">
                    <span class="legend-swatch legend-complete-solid"></span>
                    <span><strong>Hoàn tất</strong>Page đã xong đủ 5 stage.</span>
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

<div id="pageCompareModal" class="chapter-detail-inline-51">
  <div class="chapter-detail-inline-52">
    <button id="pageCompareClose" class="chapter-detail-inline-53">&times;</button>
    <div id="pageCompareTitle" class="chapter-detail-inline-54"></div>
    <div id="pageCompareBody"></div>
  </div>
</div>

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

<div id="assignTaskModal" class="modal-backdrop" aria-hidden="true">
    <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="assignTaskTitle">
        <button class="modal-close" type="button" data-modal-close aria-label="Close">&times;</button>
        <h3 id="assignTaskTitle" class="section-title compact-title">Gán task cho trang</h3>
        <form id="assignTaskForm" class="form-grid">
            <label class="field-label">Trang đã chọn</label>
            <div id="assignPageChips" class="assign-chips"><span class="section-desc chapter-detail-inline-62">Chưa chọn trang — chọn trên lưới Pages hoặc mở từ sidebar sau khi chọn.</span></div>
            <label class="field-label">Work to do</label>
            <div id="assignTaskTypeSummary" class="assign-stage-summary section-desc chapter-detail-inline-63">Tự tính theo stage tiếp theo của từng trang.</div>
            <label class="field-label" for="assignAssistantId">Assistant</label>
            <select id="assignAssistantId" name="assistantId" required>
                <option value="">Loading assistants...</option>
            </select>
            <label class="field-label" for="assignDueDate">Due date</label>
            <input id="assignDueDate" name="dueDate" type="date" required />
            <p id="assignDueHint" class="section-desc"></p>
            <label class="field-label" for="assignPriority">Priority</label>
            <select id="assignPriority" name="priority">
                <option value="NORMAL">Normal</option>
                <option value="HIGH">High</option>
                <option value="URGENT">Urgent</option>
            </select>
            <label class="field-label" for="assignNotes">Notes</label>
            <textarea id="assignNotes" name="notes" rows="3" placeholder="Hướng dẫn cho assistant..."></textarea>
            <div id="assignTaskError" class="alert error chapter-detail-inline-64"></div>
            <button class="btn primary" type="submit" id="assignTaskSubmit">Tạo task</button>
        </form>
    </div>
</div>

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
            <textarea id="taskReassignReason" rows="3" maxlength="300" required placeholder="Lý do reassign..."></textarea>
            <div id="taskReassignError" class="alert error chapter-detail-inline-65"></div>
            <button class="btn primary" type="submit">Confirm reassign</button>
        </form>
    </div>
</div>

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

<script>
(function () {
    var ctx = '${pageContext.request.contextPath}';
    var params = new URLSearchParams(window.location.search);
    var chapterId = params.get('id');
    var urlError = params.get('error');
    var currentUser = null;
    var chapter = null;
    var seriesData = null;
    var pageSlots = [];
    var chapterTasks = [];
    var selectedPageIds = {};
    var lastSlotIndex = -1;
    var pendingUploadPageId = null;
    var pendingUploadSlot = null;
    var activePopoverType = null;
    var activePopoverTaskId = null;
    var activePopoverCell = null;
    var activeOverdueTaskId = null;
    var taskImagesCache = {};
    var taskInlineLoaded = {};
    var metadataSaveTimer = null;

    function escapeHtml(v) {
        if (v === null || v === undefined) { return ''; }
        return String(v).replace(/[&<>"]/g, function (c) {
            return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c];
        });
    }

    function formatDate(v) {
        if (!v) { return ''; }
        var s = String(v);
        if (/^\d+$/.test(s)) {
            var date = new Date(Number(s));
            if (!isNaN(date.getTime())) {
                var month = String(date.getMonth() + 1);
                var day = String(date.getDate());
                return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
            }
        }
        if (s.indexOf('T') > -1) { return s.substring(0, 10); }
        return s;
    }

    function initials(name) {
        if (!name) { return '?'; }
        var parts = String(name).trim().split(/\s+/).filter(Boolean);
        if (parts.length >= 2) {
            return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        return parts[0].substring(0, 2).toUpperCase();
    }

    function dateOnly(v) {
        var d = formatDate(v);
        return d ? new Date(d + 'T00:00:00') : null;
    }

    function daysUntilDate(value) {
        var due = dateOnly(value);
        if (!due) { return null; }
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        return Math.ceil((due - today) / 86400000);
    }

    function todayIso() {
        var date = new Date();
        var month = String(date.getMonth() + 1);
        var day = String(date.getDate());
        return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
    }

    function addDaysIso(value, days) {
        var date = dateOnly(value);
        if (!date) { return ''; }
        date.setDate(date.getDate() + days);
        var month = String(date.getMonth() + 1);
        var day = String(date.getDate());
        return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
    }

    function hasRole(role) {
        if (!currentUser) { return false; }
        if (String(currentUser.role || currentUser.activeRole || currentUser.currentRole || '').toUpperCase() === role) {
            return true;
        }
        var roles = currentUser.roles || [];
        for (var i = 0; i < roles.length; i++) {
            var value = roles[i];
            var name = typeof value === 'string' ? value : (value && (value.name || value.role || value.authority));
            if (String(name || '').toUpperCase() === role) {
                return true;
            }
        }
        return false;
    }

    function isOwner() {
        return hasRole('MANGAKA') && seriesData && Number(seriesData.mangakaId) === Number(currentUser.id);
    }

    async function callApi(method, path, data) {
        var opts = { method: method, headers: { 'Accept': 'application/json' } };
        var url = ctx + path;
        if (data) {
            var p = new URLSearchParams(data).toString();
            if (method === 'GET' || method === 'PUT' || method === 'PATCH') {
                url += (url.indexOf('?') === -1 ? '?' : '&') + p;
            } else {
                opts.headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=UTF-8';
                opts.body = p;
            }
        }
        var res = await fetch(url, opts);
        var text = await res.text();
        var body = null;
        try { body = text ? JSON.parse(text) : null; } catch (e) {}
        if (!res.ok || (body && body.success === false)) {
            var msg = (body && (body.message || (body.errors && body.errors[0]))) || text || ('HTTP ' + res.status);
            throw new Error(msg);
        }
        return body;
    }

    async function uploadMultipart(path, formOrFile) {
        var fd;
        if (formOrFile instanceof FormData) {
            fd = formOrFile;
        } else if (formOrFile && formOrFile.tagName === 'FORM') {
            fd = new FormData(formOrFile);
        } else {
            fd = new FormData();
            fd.append('file', formOrFile);
        }
        var res = await fetch(ctx + path, { method: 'POST', headers: { 'Accept': 'application/json' }, body: fd });
        var text = await res.text();
        var body = null;
        try { body = text ? JSON.parse(text) : null; } catch (e) {}
        if (!res.ok || (body && body.success === false)) {
            var msg = (body && (body.message || (body.errors && body.errors[0]))) || text || ('HTTP ' + res.status);
            throw new Error(msg);
        }
        return body;
    }

    function showError(msg) {
        var el = document.getElementById('detailResult');
        el.style.display = msg ? 'block' : 'none';
        el.textContent = msg || '';
    }

    function formatStatus(s) {
        return String(s || '').toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, function (c) { return c.toUpperCase(); });
    }

    var pageStageOrder = ['SKETCHING', 'INKING', 'COLORING', 'SCREENTONE', 'LETTERING'];

    function normalizeStage(stage) {
        var s = String(stage || '').trim().toUpperCase();
        return pageStageOrder.indexOf(s) >= 0 ? s : '';
    }

    function nextAllowedStage(slot) {
        var current = normalizeStage(slot && slot.completedStage);
        if (!current) { return pageStageOrder[0]; }
        var idx = pageStageOrder.indexOf(current);
        return pageStageOrder[Math.min(idx + 1, pageStageOrder.length - 1)];
    }

    function prepareStageSelect(slot) {
        var picker = document.getElementById('pageUploadStagePicker');
        if (!picker) { return; }
        var current = normalizeStage(slot && slot.completedStage);
        var currentIndex = current ? pageStageOrder.indexOf(current) : -1;
        var boxes = picker.querySelectorAll('input[type="checkbox"]');
        picker.setAttribute('data-base-index', String(currentIndex));
        for (var i = 0; i < boxes.length; i++) {
            var optStage = normalizeStage(boxes[i].value);
            var optIndex = pageStageOrder.indexOf(optStage);
            boxes[i].checked = optIndex <= currentIndex;
        }
        refreshStagePickerEnabled();
    }

    function refreshStagePickerEnabled() {
        var picker = document.getElementById('pageUploadStagePicker');
        if (!picker) { return; }
        var boxes = picker.querySelectorAll('input[type="checkbox"]');
        var baseIndex = Number(picker.getAttribute('data-base-index') || '-1');
        var highest = baseIndex;
        for (var i = 0; i < boxes.length; i++) {
            if (boxes[i].checked) {
                highest = Math.max(highest, i);
            }
        }
        var maxEnabled = Math.min(highest + 1, boxes.length - 1);
        for (var j = 0; j < boxes.length; j++) {
            boxes[j].disabled = j <= baseIndex || j > maxEnabled;
        }
    }

    function selectedUploadStage(slot) {
        var picker = document.getElementById('pageUploadStagePicker');
        if (!picker) { return ''; }
        var boxes = picker.querySelectorAll('input[type="checkbox"]');
        var highest = -1;
        for (var i = 0; i < boxes.length; i++) {
            if (boxes[i].checked) {
                highest = Math.max(highest, pageStageOrder.indexOf(normalizeStage(boxes[i].value)));
            }
        }
        for (var j = 0; j <= highest; j++) {
            if (!boxes[j].checked) {
                throw new Error('Stage phải tick theo thứ tự từ Sketching trước.');
            }
        }
        return highest >= 0 ? pageStageOrder[highest] : '';
    }

    function syncStagePickerFromClick(changedBox) {
        var picker = document.getElementById('pageUploadStagePicker');
        if (!picker || !changedBox) { return; }
        var boxes = picker.querySelectorAll('input[type="checkbox"]');
        var changedIndex = pageStageOrder.indexOf(normalizeStage(changedBox.value));
        if (changedBox.checked) {
            for (var i = 0; i < boxes.length; i++) {
                if (!boxes[i].disabled && i < changedIndex) {
                    boxes[i].checked = true;
                }
            }
        } else {
            for (var j = 0; j < boxes.length; j++) {
                if (!boxes[j].disabled && j > changedIndex) {
                    boxes[j].checked = false;
                }
            }
        }
        refreshStagePickerEnabled();
    }

    function renderStageTrack(stage) {
        var current = normalizeStage(stage);
        var doneIndex = current ? pageStageOrder.indexOf(current) : -1;
        var activeIndex = Math.min(doneIndex + 1, pageStageOrder.length - 1);
        return '<div class="page-stage-track">'
            + pageStageOrder.map(function (s, i) {
                var cls = i <= doneIndex ? ' done' : (i === activeIndex ? ' current' : '');
                return '<span class="page-stage-dot' + cls + '" title="' + escapeHtml(formatStatus(s)) + '">' + s.charAt(0) + '</span>';
            }).join('')
            + '</div>';
    }

    function showPageUploadError(message) {
        var el = document.getElementById('pageUploadError');
        if (!el) { return; }
        el.style.display = message ? 'block' : 'none';
        el.textContent = message || '';
    }

    function openPageUploadModal(slot) {
        if (!slot) { return; }
        pendingUploadPageId = slot.id;
        pendingUploadSlot = slot;
        showPageUploadError('');
        document.getElementById('pageUploadTitle').textContent = slot.imageUrl ? 'Replace page ' + slot.pageNumber : 'Upload page ' + slot.pageNumber;
        document.getElementById('pageUploadSubtitle').textContent = 'Choose the image and tick the stages already completed for this page.';
        document.getElementById('pageModalFileInput').value = '';
        prepareStageSelect(slot);
        var preview = document.getElementById('pageUploadPreview');
        var download = document.getElementById('pageUploadDownload');
        if (slot.imageUrl) {
            var url = imageUrl(slot.imageUrl);
            preview.innerHTML = '<img src="' + escapeHtml(url) + '" alt="Page ' + slot.pageNumber + '" />';
            download.href = url;
            download.style.display = '';
        } else {
            preview.innerHTML = '<span class="section-desc">No image uploaded yet.</span>';
            download.style.display = 'none';
        }
        document.getElementById('pageUploadDelete').style.display = isOwner() ? '' : 'none';
        openModal('pageUploadModal');
    }

    function chapterStatusClass(status) {
        status = String(status || '').toUpperCase();
        if (status === 'PLANNING') { return 'status-draft'; }
        if (status === 'IN_PROGRESS') { return 'status-progress'; }
        if (status === 'COMPLETE') { return 'status-approved'; }
        if (status === 'EDITORIAL_REVIEW') { return 'status-review'; }
        if (status === 'APPROVED') { return 'status-approved'; }
        if (status === 'REJECTED') { return 'status-rejected'; }
        return 'status-draft';
    }

    function taskStatusClass(status) {
        status = String(status || '').toUpperCase();
        if (status === 'OVERDUE') { return 'status-overdue'; }
        if (status === 'IN_PROGRESS') { return 'status-progress'; }
        if (status === 'PENDING') { return 'status-pending'; }
        if (status === 'SUBMITTED') { return 'status-review'; }
        if (status === 'APPROVED') { return 'status-approved'; }
        if (status === 'REJECTED') { return 'status-rejected'; }
        if (status === 'DELETED') { return 'status-rejected'; }
        if (status === 'REASSIGNED') { return 'status-pending'; }
        return 'status-draft';
    }

    function isChapterDone(ch) {
        var st = String(ch.status || '').toUpperCase();
        return st === 'COMPLETE' || st === 'APPROVED' || Number(ch.completionPct || 0) >= 100;
    }

    function isChapterOverdue(ch) {
        if (isChapterDone(ch)) { return false; }
        var daysLeft = daysUntilDate(ch.submissionDeadline);
        return daysLeft !== null && daysLeft < 0;
    }

    function deadlineSuffixText(daysLeft, isDone, isOverdue) {
        if (isDone) { return 'Done'; }
        if (isOverdue) {
            if (daysLeft !== null && daysLeft < 0) {
                var n = Math.abs(daysLeft);
                return n === 1 ? '1 day overdue' : (n + ' days overdue');
            }
            return 'Overdue';
        }
        if (daysLeft === null) { return ''; }
        if (daysLeft === 0) { return 'Due today'; }
        if (daysLeft === 1) { return '1 day left'; }
        return daysLeft + ' days left';
    }

    function formatDeadlineCell(dateValue, isDone, isOverdue) {
        var formatted = formatDate(dateValue);
        if (!formatted) { return '<span class="chapter-detail-inline-66">—</span>'; }
        var daysLeft = daysUntilDate(dateValue);
        if (!isDone && !isOverdue && daysLeft !== null && daysLeft < 0) { isOverdue = true; }
        var suffix = deadlineSuffixText(daysLeft, isDone, isOverdue);
        suffix = suffix ? ' (' + suffix + ')' : '';
        if (isDone) {
            return '<span class="due-date-done">' + escapeHtml(formatted) + suffix + '</span>';
        }
        if (isOverdue) {
            return '<span class="due-date-overdue">&#9888; ' + escapeHtml(formatted) + suffix + '</span>';
        }
        if (daysLeft !== null && daysLeft <= 3) {
            return '<span class="due-date-urgent">' + escapeHtml(formatted) + suffix + '</span>';
        }
        return '<span class="due-date-active">' + escapeHtml(formatted) + suffix + '</span>';
    }

    function imageUrl(fileUrl) {
        var url = String(fileUrl || '');
        if (url.indexOf('http://') === 0 || url.indexOf('https://') === 0) { return url; }
        if (url.indexOf(ctx + '/') === 0) { return url; }
        return ctx + url;
    }

    function findPageById(id) {
        for (var i = 0; i < pageSlots.length; i++) {
            if (Number(pageSlots[i].id) === Number(id)) { return pageSlots[i]; }
        }
        return null;
    }

    function getSelectedPages() {
        var ids = Object.keys(selectedPageIds);
        var out = [];
        for (var i = 0; i < ids.length; i++) {
            var p = findPageById(ids[i]);
            if (p && isAssignablePage(p)) { out.push(p); }
        }
        out.sort(function (a, b) { return a.pageNumber - b.pageNumber; });
        return out;
    }

    function isPageFullyComplete(slot) {
        return normalizeStage(slot && slot.completedStage) === 'LETTERING';
    }

    function isAssignablePage(slot) {
        return !!slot && !slot.taskId && !isPageFullyComplete(slot);
    }

    function toggleSelectedPage(pageId, slot) {
        if (!isAssignablePage(slot)) { return; }
        if (selectedPageIds[String(pageId)]) {
            delete selectedPageIds[String(pageId)];
        } else {
            selectedPageIds[String(pageId)] = true;
        }
    }

    function countUploaded() {
        var n = 0;
        for (var i = 0; i < pageSlots.length; i++) {
            if (pageSlots[i].imageUrl) { n++; }
        }
        return n;
    }

    function pageStageScore(slot) {
        var stage = normalizeStage(slot && slot.completedStage);
        return stage ? pageStageOrder.indexOf(stage) + 1 : 0;
    }

    function pageCompletionPercent() {
        if (!pageSlots.length) { return 0; }
        var completedUnits = 0;
        for (var i = 0; i < pageSlots.length; i++) {
            completedUnits += pageStageScore(pageSlots[i]);
        }
        return Math.round((completedUnits * 100) / (pageSlots.length * pageStageOrder.length));
    }

    function countFullyCompletePages() {
        var n = 0;
        for (var i = 0; i < pageSlots.length; i++) {
            if (isPageFullyComplete(pageSlots[i])) { n++; }
        }
        return n;
    }

    function slotStateClass(slot) {
        if (String(slot.status || '').toUpperCase() === 'UPLOADED' || slot.imageUrl) { return 'state-uploaded'; }
        return 'state-empty';
    }

    function renderSelectionBar() {
        var selected = getSelectedPages();
        var bar = document.getElementById('selectionBar');
        if (!selected.length) {
            bar.classList.remove('visible');
            return;
        }
        bar.classList.add('visible');
        document.getElementById('selectionLabel').textContent = selected.length + ' trang đã chọn ('
            + selected[0].pageNumber + (selected.length > 1 ? '–' + selected[selected.length - 1].pageNumber : '') + ')';
    }

    function renderAssignChips() {
        var el = document.getElementById('assignPageChips');
        var selected = getSelectedPages();
        if (!selected.length) {
            el.innerHTML = '<span class="section-desc chapter-detail-inline-67">Chưa chọn trang — chọn trên lưới Pages trước khi gán.</span>';
            document.getElementById('assignTaskSubmit').disabled = true;
            return;
        }
        document.getElementById('assignTaskSubmit').disabled = false;
        el.innerHTML = selected.map(function (p) {
            return '<span class="assign-chip">Trang ' + p.pageNumber + '</span>';
        }).join('');
    }

    function nextTaskTypeForPages(selected) {
        if (!selected || !selected.length) {
            return pageStageOrder[0];
        }
        var first = null;
        var mixed = false;
        selected.forEach(function (p) {
            var stage = normalizeStage(p.completedStage);
            var nextIndex = stage ? Math.min(pageStageOrder.indexOf(stage) + 1, pageStageOrder.length - 1) : 0;
            var next = pageStageOrder[nextIndex];
            if (first === null) {
                first = next;
            } else if (first !== next) {
                mixed = true;
            }
        });
        return mixed ? 'MIXED' : first;
    }

    function groupConsecutivePages(selected) {
        var groups = [];
        var current = [];
        selected.forEach(function (page) {
            if (!current.length || page.pageNumber === current[current.length - 1].pageNumber + 1) {
                current.push(page);
            } else {
                groups.push(current);
                current = [page];
            }
        });
        if (current.length) {
            groups.push(current);
        }
        return groups;
    }

    function setDefaultAssignTaskType() {
        var summary = document.getElementById('assignTaskTypeSummary');
        if (!summary) { return; }
        var selected = getSelectedPages();
        if (!selected.length) {
            summary.textContent = 'Tự tính theo stage tiếp theo của từng trang.';
            return;
        }
        summary.innerHTML = selected.map(function (p) {
            var stage = normalizeStage(p.completedStage);
            var nextIndex = stage ? Math.min(pageStageOrder.indexOf(stage) + 1, pageStageOrder.length - 1) : 0;
            return '<span class="assign-chip">Page ' + p.pageNumber + ': ' + escapeHtml(formatStatus(pageStageOrder[nextIndex])) + '</span>';
        }).join('');
    }

    function renderPageGrid() {
        var grid = document.getElementById('pageSlotGrid');
        var owner = isOwner();
        if (!pageSlots.length) {
            grid.innerHTML = '<p class="chapter-detail-inline-68">Chưa có ô trang. '
                + (owner ? 'Nhấn + Thêm trang để bắt đầu.' : 'No page slots yet.') + '</p>'
                + (owner ? '<div class="page-slot page-slot-add chapter-detail-inline-69" data-add-page="1" title="Thêm trang">+</div>' : '');
            return;
        }

        var html = pageSlots.map(function (slot, index) {
            var selectable = isAssignablePage(slot);
            if (!selectable && selectedPageIds[String(slot.id)]) {
                delete selectedPageIds[String(slot.id)];
            }
            var selected = selectable && !!selectedPageIds[String(slot.id)];
            var state = slotStateClass(slot);
            var inProgressTaskCls = String(slot.taskStatus || '').toUpperCase() === 'IN_PROGRESS' ? ' task-in-progress' : '';
            var inProgressTaskIcon = inProgressTaskCls
                ? '<span class="page-slot-status-icon icon-in-progress">●<span class="icon-tooltip">Đang làm</span></span>'
                : '';
            var submittedTaskCls = String(slot.taskStatus || '').toUpperCase() === 'SUBMITTED' ? ' task-submitted' : '';
            var submittedTaskIcon = submittedTaskCls
                ? '<span class="page-slot-status-icon icon-submitted">●<span class="icon-tooltip">Đã nộp</span></span>'
                : '';
            var completeStageCls = normalizeStage(slot.completedStage) === 'LETTERING' ? ' stage-complete' : '';
            var cls = 'page-slot ' + state + inProgressTaskCls + submittedTaskCls + completeStageCls + (selected ? ' state-selected' : '');
            var num = '<span class="page-slot-num">' + slot.pageNumber + '</span>';
            var lockIconHtml = slot.taskId ? '<span class="page-slot-lock" title="Trang này đã được gán task">🔒</span>' : '';
            var inner = '';
            if (state === 'state-empty') {
                inner = '<span class="page-slot-upload-label">+ Upload</span>';
            } else if (slot.imageUrl) {
                inner = '<img src="' + escapeHtml(imageUrl(slot.imageUrl)) + '" alt="Page ' + slot.pageNumber + '" />'
                    + '<a class="page-download-btn" href="' + escapeHtml(imageUrl(slot.imageUrl)) + '" download title="Download page image" data-page-download>↓</a>';
            }
            if (slot.taskId && slot.assistantName && String(slot.taskStatus || '').toUpperCase() !== 'APPROVED') {
                inner += '<span class="page-slot-initials" title="' + escapeHtml(slot.assistantName) + '">' + escapeHtml(initials(slot.assistantName)) + '</span>';
            }
            inner += renderStageTrack(slot.completedStage);
            return '<div class="' + cls + '" data-page-id="' + slot.id + '" data-slot-index="' + index + '" data-page-number="' + slot.pageNumber + '">' + num + lockIconHtml + inProgressTaskIcon + submittedTaskIcon + inner + '</div>';
        }).join('');

        if (owner) {
            html += '<div class="page-slot page-slot-add" data-add-page="1" title="Thêm trang">+</div>';
        }
        grid.innerHTML = html;
        renderSelectionBar();
    }

    function renderPageProgress() {
        var total = pageSlots.length;
        var uploaded = countUploaded();
        var completePages = countFullyCompletePages();
        var pct = pageCompletionPercent();
        document.getElementById('progressLabel').textContent = pct + '% (' + completePages + ' / ' + total + ' pages complete)';
        document.getElementById('progressFill').style.width = pct + '%';
        document.getElementById('pageCountLabel').textContent = uploaded + ' / ' + total + ' đã upload';
        document.getElementById('tabPageCount').textContent = total;
        document.getElementById('metaPages').textContent = uploaded + ' / ' + total;
        document.getElementById('metaProgress').textContent = pct + '% page';
    }

    function renderSidebarTasks() {
        var el = document.getElementById('sidebarTaskList');
        if (!chapterTasks.length) {
            el.innerHTML = '<p class="section-desc chapter-detail-inline-70">Chưa có task.</p>';
            return;
        }
        var preview = chapterTasks.slice(0, 5);
        el.innerHTML = preview.map(function (t) {
            return '<div class="sidebar-task-mini">'
                + '<strong>#' + t.id + '</strong> p.' + t.pageRangeStart + '-' + t.pageRangeEnd
                + ' · ' + escapeHtml(formatStatus(t.taskType))
                + '<br/><span class="status-chip ' + taskStatusClass(t.status) + ' chapter-detail-inline-71">' + formatStatus(t.status) + '</span>'
                + '</div>';
        }).join('')
            + (chapterTasks.length > 5 ? '<p class="section-desc chapter-detail-inline-72">+' + (chapterTasks.length - 5) + ' task khác — xem tab Tasks</p>' : '');
    }

    function renderMeta() {
        if (!chapter) { return; }
        var progress = Math.max(0, Math.min(100, Number(chapter.completionPct || 0)));
        var done = isChapterDone(chapter);
        var overdue = isChapterOverdue(chapter);
        var seriesTitle = seriesData ? seriesData.title : ('#' + chapter.seriesId);

        document.getElementById('breadcrumbSeries').textContent = seriesTitle;
        document.getElementById('breadcrumbSeries').href = ctx + '/main/chapters?seriesId=' + chapter.seriesId;
        document.getElementById('breadcrumbChapter').textContent = 'Ch.' + chapter.chapterNumber + ' — ' + chapter.title;
        document.getElementById('breadcrumbStatusPill').innerHTML =
            '<span class="status-chip chapter-status-chip ' + chapterStatusClass(chapter.status) + '">' + formatStatus(chapter.status) + '</span>';

        document.getElementById('panelChapterTitle').textContent = 'Ch.' + chapter.chapterNumber + ' — ' + chapter.title;
        document.getElementById('panelSeriesName').textContent = seriesTitle + ' · Chapter ' + chapter.chapterNumber;

        document.getElementById('metaDeadline').innerHTML = formatDeadlineCell(chapter.submissionDeadline, done, overdue);
        document.getElementById('metaDeadlineSub').textContent = '';
        document.getElementById('metaStatus').innerHTML =
            '<span class="status-chip chapter-status-chip ' + chapterStatusClass(chapter.status) + '">' + formatStatus(chapter.status) + '</span>';

        document.getElementById('updateChapterId').value = chapter.id;
        document.getElementById('updateTitle').value = chapter.title || '';
        document.getElementById('updateDeadline').value = formatDate(chapter.submissionDeadline) || '';

        var owner = isOwner();
        var chapterStatus = String(chapter.status || '').toUpperCase();
        var canSubmit = owner && progress >= 100 && (chapterStatus === 'IN_PROGRESS' || chapterStatus === 'COMPLETE')
            && seriesData && String(seriesData.status || '').toUpperCase() !== 'CANCELLED';

        document.getElementById('btnDelete').style.display = (owner && chapterStatus === 'PLANNING') ? '' : 'none';
        document.getElementById('btnMarkDone').style.display = canSubmit ? '' : 'none';
        
        // Show manuscript workspace button for EDITORIAL_REVIEW status
        var isEditorialReview = chapterStatus === 'EDITORIAL_REVIEW';
        var btnManuscriptWorkspace = document.getElementById('btnManuscriptWorkspace');
        if (isEditorialReview) {
            btnManuscriptWorkspace.style.display = '';
            btnManuscriptWorkspace.href = '${pageContext.request.contextPath}/main/chapters/' + chapter.id + '/manuscript-workspace/create';
        } else {
            btnManuscriptWorkspace.style.display = 'none';
        }
        
        document.getElementById('pagesOwnerActions').style.display = owner ? 'flex' : 'none';
        document.getElementById('pagesOwnerActions').style.gap = '8px';
        document.getElementById('pagesHint').style.display = owner ? '' : 'none';

        updateAssignDueConstraints();
        renderPageProgress();
    }

    function findTask(taskId) {
        for (var i = 0; i < chapterTasks.length; i++) {
            if (Number(chapterTasks[i].id) === Number(taskId)) { return chapterTasks[i]; }
        }
        return null;
    }

    function findTaskByPageNumber(pageNumber) {
        for (var i = 0; i < chapterTasks.length; i++) {
            var t = chapterTasks[i];
            if (Number(pageNumber) >= Number(t.pageRangeStart) && Number(pageNumber) <= Number(t.pageRangeEnd)) {
                return t;
            }
        }
        return null;
    }

    function isTaskOverdue(task) {
        var st = String(task.status || '').toUpperCase();
        if (st === 'APPROVED') { return false; }
        if (st === 'OVERDUE') { return true; }
        if (!task.dueDate) { return false; }
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        var due = dateOnly(task.dueDate);
        return due && due < today;
    }

    function formatDueDateCell(task) {
        var formatted = formatDate(task.dueDate);
        if (!formatted) { return '—'; }
        var done = String(task.status || '').toUpperCase() === 'APPROVED';
        var overdue = isTaskOverdue(task);
        return formatDeadlineCell(task.dueDate, done, overdue);
    }

    function renderTaskRowActions(task) {
        if (task._decisionLabel === 'approved') {
            return '<span class="task-decision-label approved">Approved</span>';
        }
        if (task._decisionLabel === 'rejected') {
            return '<span class="task-decision-label rejected">Rejected</span>';
        }
        var st = String(task.status || '').toUpperCase();
        var html = '';
        html += ' <button class="task-expand-btn" type="button" data-task-expand="' + task.id + '">▼ Trang</button>';
        if (isOwner() && st === 'IN_PROGRESS') {
            html += ' <button class="btn small" type="button" data-task-reassign="' + task.id + '">Reassign</button>';
            html += ' <button class="btn small danger-soft" type="button" data-task-delete="' + task.id + '">Delete</button>';
        }
        if (isOwner() && st === 'OVERDUE') {
            html += ' <button class="btn small warning-soft" type="button" data-task-overdue-decision="' + task.id + '">Decide</button>';
        }
        if (isOwner() && st === 'SUBMITTED') {
            html += ' <button class="btn small success-soft" type="button" data-task-approve-pop="' + task.id + '">Approve</button>';
            html += ' <button class="btn small danger-soft" type="button" data-task-reject-pop="' + task.id + '">Reject</button>';
        }
        return html;
    }

    function renderChapterTasks() {
        var tbody = document.getElementById('chapterTaskRows');
        document.getElementById('tabTaskCount').textContent = chapterTasks.length;
        if (!chapterTasks.length) {
            tbody.innerHTML = '<tr><td colspan="7">No tasks for this chapter.</td></tr>';
            renderSidebarTasks();
            return;
        }
        tbody.innerHTML = chapterTasks.map(function (task) {
            return '<tr>'
                + '<td>' + task.id + '</td>'
                + '<td>' + task.pageRangeStart + '-' + task.pageRangeEnd + '</td>'
                + '<td>' + formatStatus(task.taskType) + '</td>'
                + '<td>' + escapeHtml(task.assistantName || '') + '</td>'
                + '<td><span class="status-chip ' + taskStatusClass(task.status) + '">' + formatStatus(task.status) + '</span></td>'
                + '<td>' + formatDueDateCell(task) + '</td>'
                + '<td class="task-actions-cell"><div class="task-row-actions">' + renderTaskRowActions(task) + '</div></td>'
                + '</tr>'
                + '<tr class="task-inline-row chapter-detail-inline-73" id="task-inline-' + task.id + '">'
                + '<td colspan="7"><div class="task-inline-body" id="task-inline-body-' + task.id + '">Đang tải...</div></td>'
                + '</tr>';
        }).join('');
        renderSidebarTasks();
    }

    async function loadTaskInlinePages(taskId) {
        var task = findTask(taskId);
        if (!task) { return; }
        var bodyEl = document.getElementById('task-inline-body-' + taskId);
        if (!bodyEl) { return; }
        if (!taskInlineLoaded[taskId]) {
            bodyEl.innerHTML = '<span class="chapter-detail-inline-74">Đang tải...</span>';
            try {
                var res = await callApi('GET', '/api/v1/tasks/' + taskId + '/images');
                var imgs = res.data || res || [];
                var imgMap = {};
                imgs.forEach(function (img) { imgMap[img.pageNumber] = img; });
                taskImagesCache[taskId] = imgs;
                var html = '';
                for (var p = task.pageRangeStart; p <= task.pageRangeEnd; p++) {
                    var img = imgMap[p];
                    html += '<div class="task-page-mini">';
                    if (img) {
                        html += '<img src="' + escapeHtml(imageUrl(img.fileUrl)) + '" alt="p' + p + '" />';
                    } else {
                        html += '<div class="no-thumb">+</div>';
                    }
                    html += '<div>Trang ' + p + '</div></div>';
                }
                bodyEl.innerHTML = html || '<span class="chapter-detail-inline-75">Chưa có ảnh nào.</span>';
                taskInlineLoaded[taskId] = true;
            } catch (e) {
                bodyEl.innerHTML = '<span class="chapter-detail-inline-76">' + escapeHtml(e.message) + '</span>';
            }
        }
    }

    async function openPageCompare(slot) {
        var modal = document.getElementById('pageCompareModal');
        var title = document.getElementById('pageCompareTitle');
        var body = document.getElementById('pageCompareBody');
        modal.style.display = 'flex';
        title.textContent = 'Trang ' + slot.pageNumber;
        var ts = String(slot.taskStatus || '').toUpperCase();
        var origUrl = slot.imageUrl ? imageUrl(slot.imageUrl) : null;
        if (!slot.taskId || (ts !== 'SUBMITTED' && ts !== 'APPROVED')) {
            body.innerHTML = origUrl
                ? (isOwner() && !slot.taskId ? '<div class="chapter-detail-inline-77"><button class="btn small primary" type="button" id="pageCompareEdit">Upload / replace</button></div>' : '')
                    + '<img src="' + escapeHtml(origUrl) + '" class="chapter-detail-inline-78" />'
                : '<div class="chapter-detail-inline-79">Chưa có ảnh</div>';
            var editBtn = document.getElementById('pageCompareEdit');
            if (editBtn) {
                editBtn.addEventListener('click', function () {
                    modal.style.display = 'none';
                    openPageUploadModal(slot);
                });
            }
            return;
        }
        var taskImgs = taskImagesCache[slot.taskId];
        if (!taskImgs) {
            body.innerHTML = '<div class="chapter-detail-inline-80">Đang tải ảnh...</div>';
            try {
                var res = await callApi('GET', '/api/v1/tasks/' + slot.taskId + '/images');
                taskImgs = res.data || res || [];
                taskImagesCache[slot.taskId] = taskImgs;
            } catch (e) {
                body.innerHTML = '<div class="alert error">' + escapeHtml(e.message) + '</div>';
                return;
            }
        }
        var assistantImg = null;
        for (var i = 0; i < taskImgs.length; i++) {
            if (Number(taskImgs[i].pageNumber) === Number(slot.pageNumber)) {
                assistantImg = taskImgs[i];
                break;
            }
        }
        var assistantUrl = assistantImg ? imageUrl(assistantImg.fileUrl) : null;
        if (ts === 'SUBMITTED') {
            body.innerHTML =
                '<div class="chapter-detail-inline-81">'
                + '<div><div class="chapter-detail-inline-82">Bản gốc (Mangaka)</div>'
                + (origUrl ? '<img src="' + escapeHtml(origUrl) + '" class="chapter-detail-inline-83" />' : '<div class="chapter-detail-inline-84">Không có ảnh gốc</div>')
                + '</div>'
                + '<div><div class="chapter-detail-inline-85">Bản assistant nộp</div>'
                + (assistantUrl ? '<img src="' + escapeHtml(assistantUrl) + '" class="chapter-detail-inline-86" />' : '<div class="chapter-detail-inline-87">Chưa có ảnh</div>')
                + '</div></div>';
            return;
        }
        var finalUrl = assistantUrl || origUrl;
        body.innerHTML = finalUrl
            ? '<div class="chapter-detail-inline-88"><span class="chapter-detail-inline-89">✓ Đã được duyệt</span></div>'
                + '<img src="' + escapeHtml(finalUrl) + '" class="chapter-detail-inline-90" />'
            : '<div class="chapter-detail-inline-91">Không có ảnh</div>';
    }

    function switchTab(tab) {
        document.querySelectorAll('.chapter-tab-btn').forEach(function (b) {
            b.classList.toggle('active', b.getAttribute('data-tab') === tab);
        });
        document.getElementById('tabPages').style.display = tab === 'pages' ? '' : 'none';
        document.getElementById('tabTasks').style.display = tab === 'tasks' ? '' : 'none';
        document.getElementById('tabEdit').style.display = tab === 'edit' ? '' : 'none';
    }

    function openModal(id) {
        var modal = document.getElementById(id);
        if (modal) {
            modal.classList.add('open');
            modal.setAttribute('aria-hidden', 'false');
        }
    }

    function closeModals() {
        document.querySelectorAll('.modal-backdrop').forEach(function (m) {
            m.classList.remove('open');
            m.setAttribute('aria-hidden', 'true');
        });
        pendingUploadPageId = null;
        pendingUploadSlot = null;
        activeOverdueTaskId = null;
        showPageUploadError('');
    }

    function closePopovers() {
        var host = document.getElementById('taskPopoverHost');
        var scrim = document.getElementById('taskPopoverScrim');
        var approvePop = document.getElementById('taskApprovePopover');
        var rejectPop = document.getElementById('taskRejectPopover');
        if (scrim) {
            scrim.classList.remove('open');
            scrim.setAttribute('aria-hidden', 'true');
            if (host) { host.appendChild(scrim); }
        }
        if (approvePop) {
            approvePop.classList.remove('open');
            approvePop.setAttribute('aria-hidden', 'true');
            if (host) { host.appendChild(approvePop); }
        }
        if (rejectPop) {
            rejectPop.classList.remove('open');
            rejectPop.setAttribute('aria-hidden', 'true');
            if (host) { host.appendChild(rejectPop); }
        }
        activePopoverType = null;
        activePopoverTaskId = null;
        activePopoverCell = null;
    }

    function openPopover(type, taskId, anchorCell) {
        closePopovers();
        var task = findTask(taskId);
        if (!task) { return; }
        var scrim = document.getElementById('taskPopoverScrim');
        var popId = type === 'approve' ? 'taskApprovePopover' : 'taskRejectPopover';
        var pop = document.getElementById(popId);
        if (!pop) { return; }
        if (scrim) {
            document.body.appendChild(scrim);
            scrim.classList.add('open');
            scrim.setAttribute('aria-hidden', 'false');
        }
        document.body.appendChild(pop);
        pop.classList.add('open');
        pop.setAttribute('aria-hidden', 'false');
        activePopoverType = type;
        activePopoverTaskId = taskId;
        activePopoverCell = anchorCell;
        if (type === 'approve') {
            document.getElementById('approvePopoverTitle').textContent = 'Approve task #' + task.id;
            document.getElementById('approvePopoverComment').value = '';
        } else {
            document.getElementById('rejectPopoverTitle').textContent = 'Reject task #' + task.id;
            document.getElementById('rejectPopoverReason').value = '';
            updateRejectConfirmState();
        }
    }

    function updateRejectConfirmState() {
        var reasonEl = document.getElementById('rejectPopoverReason');
        var counterEl = document.getElementById('rejectPopoverCounter');
        var confirmBtn = document.getElementById('rejectPopoverConfirm');
        if (!reasonEl || !confirmBtn) { return; }
        var len = reasonEl.value.length;
        if (counterEl) { counterEl.textContent = len + ' / 300'; }
        confirmBtn.disabled = len < 5;
    }

    function openAssignModal() {
        renderAssignChips();
        setDefaultAssignTaskType();
        var err = document.getElementById('assignTaskError');
        err.style.display = 'none';
        err.textContent = '';
        openModal('assignTaskModal');
    }

    function openOverdueDecisionModal(taskId) {
        var task = findTask(taskId);
        if (!task) { return; }
        activeOverdueTaskId = taskId;
        document.getElementById('taskExtendId').value = taskId;
        document.getElementById('taskOverdueDecisionTitle').textContent = 'Overdue task #' + taskId;
        document.getElementById('taskOverdueDecisionSummary').textContent =
            'Pages ' + task.pageRangeStart + '-' + task.pageRangeEnd + ' · '
            + formatStatus(task.taskType) + ' · assigned to ' + (task.assistantName || ('#' + task.assistantId));

        var latest = latestTaskDueDate();
        var dueInput = document.getElementById('taskExtendDueDate');
        dueInput.value = '';
        dueInput.min = todayIso();
        dueInput.removeAttribute('max');
        if (latest) { dueInput.max = latest; }
        document.getElementById('taskExtendHint').textContent = chapter && chapter.submissionDeadline
            ? ('Chapter deadline: ' + formatDate(chapter.submissionDeadline) + '. Extension must be today through ' + (latest || formatDate(chapter.submissionDeadline)) + '.')
            : 'Extension date cannot be in the past.';

        document.getElementById('taskExtendReason').value = '';
        document.getElementById('taskOverdueReason').value = '';
        document.getElementById('taskOverdueDeleteReason').value = '';
        document.getElementById('taskOverdueReassignAssistantId').value = '';
        var reassignDueInput = document.getElementById('taskOverdueReassignDueDate');
        reassignDueInput.value = '';
        reassignDueInput.min = todayIso();
        reassignDueInput.removeAttribute('max');
        if (latest) { reassignDueInput.max = latest; }
        document.getElementById('taskExtendError').style.display = 'none';
        document.getElementById('taskExtendError').textContent = '';
        document.getElementById('taskOverdueDecisionError').style.display = 'none';
        document.getElementById('taskOverdueDecisionError').textContent = '';
        document.getElementById('taskOverdueDeleteError').style.display = 'none';
        document.getElementById('taskOverdueDeleteError').textContent = '';
        setOverdueDecisionChoice('');
        openModal('taskOverdueDecisionModal');
    }

    function setOverdueDecisionChoice(choice) {
        var panels = document.querySelectorAll('[data-overdue-action-panel]');
        for (var i = 0; i < panels.length; i++) {
            panels[i].style.display = panels[i].getAttribute('data-overdue-action-panel') === choice ? '' : 'none';
        }
        var buttons = document.querySelectorAll('[data-overdue-action-choice]');
        for (var j = 0; j < buttons.length; j++) {
            var active = buttons[j].getAttribute('data-overdue-action-choice') === choice;
            buttons[j].classList.toggle('is-active', active);
            buttons[j].setAttribute('aria-pressed', active ? 'true' : 'false');
        }
    }

    async function fillAssistantSelect() {
        var select = document.getElementById('assignAssistantId');
        var reassignSelect = document.getElementById('taskReassignAssistantId');
        var overdueReassignSelect = document.getElementById('taskOverdueReassignAssistantId');
        if (!chapter || !select) { return; }
        select.innerHTML = '<option value="">Loading assistants...</option>';
        if (reassignSelect) { reassignSelect.innerHTML = '<option value="">Loading assistants...</option>'; }
        if (overdueReassignSelect) { overdueReassignSelect.innerHTML = '<option value="">Loading assistants...</option>'; }
        try {
            var res = await callApi('GET', '/api/v1/series/' + chapter.seriesId + '/assistants');
            var assistants = res.data || [];
            var options = '<option value="">Select Assistant</option>' + assistants.map(function (a) {
                return '<option value="' + a.id + '">#' + a.id + ' - ' + escapeHtml(a.fullName || a.username) + '</option>';
            }).join('');
            select.innerHTML = options;
            if (reassignSelect) { reassignSelect.innerHTML = options; }
            if (overdueReassignSelect) { overdueReassignSelect.innerHTML = options; }
        } catch (err) {
            select.innerHTML = '<option value="">Cannot load assistants</option>';
            if (reassignSelect) { reassignSelect.innerHTML = '<option value="">Cannot load assistants</option>'; }
            if (overdueReassignSelect) { overdueReassignSelect.innerHTML = '<option value="">Cannot load assistants</option>'; }
            showError(err.message);
        }
    }

    function latestTaskDueDate() {
        return chapter && chapter.submissionDeadline ? addDaysIso(chapter.submissionDeadline, -3) : '';
    }

    function updateAssignDueConstraints() {
        var dueInput = document.getElementById('assignDueDate');
        var hint = document.getElementById('assignDueHint');
        if (!dueInput) { return; }
        dueInput.min = todayIso();
        dueInput.removeAttribute('max');
        var latest = latestTaskDueDate();
        if (latest) {
            dueInput.max = latest;
        }
        if (hint) {
            hint.textContent = chapter && chapter.submissionDeadline
                ? ('Chapter deadline: ' + formatDate(chapter.submissionDeadline) + '. Task due date: today → ' + (latest || formatDate(chapter.submissionDeadline)) + '.')
                : '';
        }
    }

    async function loadPages() {
        var res = await callApi('GET', '/api/v1/chapters/' + chapterId + '/pages');
        pageSlots = res.data || [];
        renderPageGrid();
        renderPageProgress();
        renderMeta();
    }

    async function loadTasks() {
        var res = await callApi('GET', '/api/v1/chapters/' + chapterId + '/tasks');
        chapterTasks = res.data || [];
        renderChapterTasks();
    }

    async function loadData() {
        if (!chapterId) {
            showError('No chapter ID specified.');
            return;
        }
        try {
            var userRes = await callApi('GET', '/api/v1/auth/me');
            currentUser = userRes.data;
            var chRes = await callApi('GET', '/api/v1/chapters/' + chapterId);
            chapter = chRes.data;
            var sListRes = await callApi('GET', '/api/v1/series');
            var sList = sListRes.data || [];
            for (var i = 0; i < sList.length; i++) {
                if (Number(sList[i].id) === Number(chapter.seriesId)) {
                    seriesData = sList[i];
                    break;
                }
            }
            await Promise.all([loadPages(), loadTasks(), fillAssistantSelect()]);
            renderMeta();
        } catch (err) {
            showError(err.message);
        }
    }

    document.getElementById('tabBar').addEventListener('click', function (e) {
        var btn = e.target.closest('.chapter-tab-btn');
        if (!btn) { return; }
        switchTab(btn.getAttribute('data-tab'));
    });

    async function saveChapterMetadata() {
        var updateError = document.getElementById('updateError');
        updateError.style.display = 'none';
        try {
            var title = document.getElementById('updateTitle').value;
            var deadline = document.getElementById('updateDeadline').value;
            if (!chapter || (title === (chapter.title || '') && deadline === formatDate(chapter.submissionDeadline))) {
                return;
            }
            var qs = new URLSearchParams({
                title: title,
                submissionDeadline: deadline,
                publicationDate: deadline,
                deadline: deadline,
                chapterDeadline: deadline
            }).toString();
            await callApi('PUT', '/api/v1/chapters/' + chapterId + '?' + qs);
            chapter.title = title;
            chapter.submissionDeadline = deadline;
            renderMeta();
            showError('');
        } catch (err) {
            updateError.style.display = 'block';
            updateError.textContent = err.message;
        }
    }

    function scheduleMetadataSave() {
        if (!isOwner()) { return; }
        clearTimeout(metadataSaveTimer);
        metadataSaveTimer = setTimeout(saveChapterMetadata, 700);
    }

    document.getElementById('updateTitle').addEventListener('input', scheduleMetadataSave);
    document.getElementById('updateDeadline').addEventListener('change', saveChapterMetadata);

    document.getElementById('btnDelete').addEventListener('click', async function () {
        if (!confirm('Delete this chapter? This cannot be undone.')) { return; }
        try {
            await callApi('DELETE', '/api/v1/chapters/' + chapterId);
            window.location.href = ctx + '/main/chapters?seriesId=' + chapter.seriesId;
        } catch (err) { showError(err.message); }
    });

    document.getElementById('btnMarkDone').addEventListener('click', async function () {
        try {
            await callApi('POST', '/api/v1/chapters/' + chapterId + '/submit-review');
            await loadData();
            showError('');
        } catch (err) { showError(err.message); }
    });

    document.getElementById('btnAddPage').addEventListener('click', async function () {
        try {
            await callApi('POST', '/api/v1/pages', { chapterId: chapterId });
            await loadData();
        } catch (err) { showError(err.message); }
    });

    document.getElementById('pageUploadStagePicker').addEventListener('change', function (e) {
        if (e.target && e.target.type === 'checkbox') {
            syncStagePickerFromClick(e.target);
        }
    });

    document.getElementById('pageModalFileInput').addEventListener('change', function (e) {
        var file = e.target.files && e.target.files[0];
        var preview = document.getElementById('pageUploadPreview');
        if (!file || !preview) { return; }
        var reader = new FileReader();
        reader.onload = function (ev) {
            preview.innerHTML = '<img src="' + escapeHtml(ev.target.result) + '" alt="Selected page image" />';
        };
        reader.readAsDataURL(file);
    });

    document.getElementById('singleFileInput').addEventListener('change', async function (e) {
        var file = e.target.files && e.target.files[0];
        e.target.value = '';
        if (!file || !pendingUploadPageId) { return; }
        try {
            var fd = new FormData();
            fd.append('file', file);
            fd.append('completedStage', selectedUploadStage(findPageById(pendingUploadPageId)));
            await uploadMultipart('/api/v1/pages/' + pendingUploadPageId + '/upload', fd);
            pendingUploadPageId = null;
            showError('');
            await loadData();
        } catch (err) {
            showError(err.message);
        }
    });

    document.getElementById('pageUploadSave').addEventListener('click', async function () {
        if (!pendingUploadPageId || !pendingUploadSlot) { return; }
        var fileInput = document.getElementById('pageModalFileInput');
        var file = fileInput.files && fileInput.files[0];
        var hasExisting = !!pendingUploadSlot.imageUrl;
        if (!file && !hasExisting) {
            showPageUploadError('Choose an image file first.');
            return;
        }
        try {
            if (!file && hasExisting) {
                showPageUploadError('Choose a replacement image to update this page.');
                return;
            }
            var fd = new FormData();
            fd.append('file', file);
            fd.append('completedStage', selectedUploadStage(pendingUploadSlot));
            await uploadMultipart('/api/v1/pages/' + pendingUploadPageId + '/upload', fd);
            pendingUploadPageId = null;
            pendingUploadSlot = null;
            closeModals();
            showError('');
            await loadData();
        } catch (err) {
            showPageUploadError(err.message);
        }
    });

    document.getElementById('pageUploadDelete').addEventListener('click', async function () {
        if (!pendingUploadPageId || !pendingUploadSlot) { return; }
        if (!confirm('Delete page ' + pendingUploadSlot.pageNumber + '? This cannot be undone.')) { return; }
        try {
            await callApi('DELETE', '/api/v1/pages/' + pendingUploadPageId);
            pendingUploadPageId = null;
            pendingUploadSlot = null;
            closeModals();
            selectedPageIds = {};
            showError('');
            await loadData();
        } catch (err) {
            showPageUploadError(err.message);
        }
    });

    document.getElementById('pageSlotGrid').addEventListener('click', function (e) {
        var addBtn = e.target.closest('[data-add-page]');
        if (addBtn && isOwner()) {
            document.getElementById('btnAddPage').click();
            return;
        }
        var slotEl = e.target.closest('[data-page-id]');
        if (!slotEl) { return; }
        if (e.target.closest('[data-page-download]')) {
            return;
        }
        var pageId = slotEl.getAttribute('data-page-id');
        var index = Number(slotEl.getAttribute('data-slot-index'));
        var slot = findPageById(pageId);
        if (!slot) { return; }

        if (e.shiftKey) {
            toggleSelectedPage(pageId, slot);
            lastSlotIndex = index;
            renderPageGrid();
            return;
        }

        lastSlotIndex = index;

        if (slot.imageUrl && e.target.closest('img')) {
            openPageCompare(slot);
            return;
        }

        if (!isOwner()) {
            if (slot.imageUrl || slot.taskId) {
                openPageCompare(slot);
            }
            return;
        }

        if (selectedPageIds[String(pageId)]) {
            delete selectedPageIds[String(pageId)];
            renderPageGrid();
            return;
        }

        if (slot.imageUrl || slot.taskId) {
            openPageCompare(slot);
            return;
        }

        if (isOwner() && !slot.taskId) {
            openPageUploadModal(slot);
            return;
        }

        renderPageGrid();
    });

    document.getElementById('btnClearSelection').addEventListener('click', function () {
        selectedPageIds = {};
        lastSlotIndex = -1;
        renderPageGrid();
    });

    document.getElementById('btnAssignFromSelection').addEventListener('click', function () {
        if (!getSelectedPages().length) { return; }
        openAssignModal();
    });

    document.getElementById('assignTaskForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        var errEl = document.getElementById('assignTaskError');
        errEl.style.display = 'none';
        var selected = getSelectedPages();
        if (!selected.length) {
            errEl.style.display = 'block';
            errEl.textContent = 'Chọn ít nhất một trang trên lưới Pages.';
            return;
        }
        var groups = groupConsecutivePages(selected);
        try {
            for (var g = 0; g < groups.length; g++) {
                var group = groups[g];
                await callApi('POST', '/api/v1/chapters/' + chapterId + '/tasks', {
                    assistantId: document.getElementById('assignAssistantId').value,
                    pageRangeStart: group[0].pageNumber,
                    pageRangeEnd: group[group.length - 1].pageNumber,
                    taskType: nextTaskTypeForPages(group),
                    dueDate: document.getElementById('assignDueDate').value,
                    priority: document.getElementById('assignPriority').value,
                    notes: document.getElementById('assignNotes').value
                });
            }
            selectedPageIds = {};
            e.target.reset();
            closeModals();
            showError('');
            await Promise.all([loadPages(), loadTasks()]);
        } catch (err) {
            errEl.style.display = 'block';
            errEl.textContent = err.message;
        }
    });

    document.getElementById('taskReassignForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        var errEl = document.getElementById('taskReassignError');
        errEl.style.display = 'none';
        var taskId = document.getElementById('taskReassignId').value;
        var assistantId = document.getElementById('taskReassignAssistantId').value;
        var reason = document.getElementById('taskReassignReason').value.trim();
        if (reason.length < 5) {
            errEl.style.display = 'block';
            errEl.textContent = 'Lý do reassign phải có ít nhất 5 ký tự.';
            return;
        }
        try {
            await callApi('POST', '/api/v1/tasks/' + taskId + '/reassign', {
                assistantId: assistantId,
                reason: reason
            });
            closeModals();
            showError('');
            await loadData();
        } catch (err) {
            errEl.style.display = 'block';
            errEl.textContent = err.message;
        }
    });

    document.getElementById('taskExtendForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        var errEl = document.getElementById('taskExtendError');
        errEl.style.display = 'none';
        errEl.textContent = '';
        var taskId = document.getElementById('taskExtendId').value;
        var newDueDate = document.getElementById('taskExtendDueDate').value;
        var reason = document.getElementById('taskExtendReason').value.trim();
        try {
            await callApi('POST', '/api/v1/tasks/' + taskId + '/extend', {
                newDueDate: newDueDate,
                reason: reason
            });
            closeModals();
            showError('');
            await loadData();
        } catch (err) {
            errEl.style.display = 'block';
            errEl.textContent = err.message;
        }
    });

    document.getElementById('taskOverdueReassignBtn').addEventListener('click', async function () {
        var errEl = document.getElementById('taskOverdueDecisionError');
        errEl.style.display = 'none';
        errEl.textContent = '';
        var assistantId = document.getElementById('taskOverdueReassignAssistantId').value;
        var newDueDate = document.getElementById('taskOverdueReassignDueDate').value;
        var reason = document.getElementById('taskOverdueReason').value.trim();
        if (!activeOverdueTaskId) { return; }
        if (!assistantId) {
            errEl.style.display = 'block';
            errEl.textContent = 'Choose a new assistant.';
            return;
        }
        if (!newDueDate) {
            errEl.style.display = 'block';
            errEl.textContent = 'Choose a new due date.';
            return;
        }
        if (reason.length < 5) {
            errEl.style.display = 'block';
            errEl.textContent = 'Reason must be at least 5 characters.';
            return;
        }
        try {
            await callApi('POST', '/api/v1/tasks/' + activeOverdueTaskId + '/reassign', {
                assistantId: assistantId,
                newDueDate: newDueDate,
                reason: reason
            });
            closeModals();
            showError('');
            await loadData();
        } catch (err) {
            errEl.style.display = 'block';
            errEl.textContent = err.message;
        }
    });

    document.getElementById('taskOverdueDeleteBtn').addEventListener('click', async function () {
        var errEl = document.getElementById('taskOverdueDeleteError');
        errEl.style.display = 'none';
        errEl.textContent = '';
        var reason = document.getElementById('taskOverdueDeleteReason').value.trim();
        if (!activeOverdueTaskId) { return; }
        if (reason.length < 5) {
            errEl.style.display = 'block';
            errEl.textContent = 'Reason must be at least 5 characters.';
            return;
        }
        try {
            await callApi('POST', '/api/v1/tasks/' + activeOverdueTaskId + '/delete', { reason: reason });
            closeModals();
            showError('');
            await loadData();
        } catch (err) {
            errEl.style.display = 'block';
            errEl.textContent = err.message;
        }
    });

    document.addEventListener('click', async function (e) {
        var overdueChoiceBtn = e.target.closest('[data-overdue-action-choice]');
        if (overdueChoiceBtn) {
            setOverdueDecisionChoice(overdueChoiceBtn.getAttribute('data-overdue-action-choice'));
            return;
        }
        var expandBtn = e.target.closest('[data-task-expand]');
        if (expandBtn) {
            var tid = expandBtn.getAttribute('data-task-expand');
            var row = document.getElementById('task-inline-' + tid);
            if (row) {
                var isOpen = row.style.display !== 'none';
                row.style.display = isOpen ? 'none' : '';
                expandBtn.textContent = isOpen ? '▼ Trang' : '▲ Trang';
                if (!isOpen) {
                    loadTaskInlinePages(Number(tid));
                }
            }
            return;
        }
        var overdueDecisionBtn = e.target.closest('[data-task-overdue-decision]');
        if (overdueDecisionBtn) {
            openOverdueDecisionModal(overdueDecisionBtn.getAttribute('data-task-overdue-decision'));
            return;
        }
        var approvePopBtn = e.target.closest('[data-task-approve-pop]');
        if (approvePopBtn) {
            openPopover('approve', approvePopBtn.getAttribute('data-task-approve-pop'), approvePopBtn.closest('.task-actions-cell'));
            return;
        }
        var rejectPopBtn = e.target.closest('[data-task-reject-pop]');
        if (rejectPopBtn) {
            openPopover('reject', rejectPopBtn.getAttribute('data-task-reject-pop'), rejectPopBtn.closest('.task-actions-cell'));
            return;
        }
        var taskDeleteBtn = e.target.closest('[data-task-delete]');
        if (taskDeleteBtn) {
            var deleteTaskId = taskDeleteBtn.getAttribute('data-task-delete');
            var reason = prompt('Lý do xóa task #' + deleteTaskId + ':');
            if (!reason) { return; }
            try {
                await callApi('POST', '/api/v1/tasks/' + deleteTaskId + '/delete', { reason: reason });
                await loadData();
                showError('');
            } catch (err) {
                showError(err.message);
            }
            return;
        }
        var taskReassignBtn = e.target.closest('[data-task-reassign]');
        if (taskReassignBtn) {
            document.getElementById('taskReassignId').value = taskReassignBtn.getAttribute('data-task-reassign');
            document.getElementById('taskReassignReason').value = '';
            document.getElementById('taskReassignError').style.display = 'none';
            document.getElementById('taskReassignError').textContent = '';
            openModal('taskReassignModal');
            return;
        }
        if (e.target.closest('[data-popover-cancel]')) {
            closePopovers();
            return;
        }
        if (e.target.id === 'taskPopoverScrim') {
            closePopovers();
            return;
        }
        var insidePopover = e.target.closest('.task-action-popover');
        var insideActions = e.target.closest('.task-row-actions');
        if (!insidePopover && !insideActions && activePopoverType) {
            closePopovers();
        }
        if (e.target.closest('[data-modal-close]')) {
            closeModals();
            return;
        }
        if (e.target.classList.contains('modal-backdrop')) {
            closeModals();
        }
    });

    document.getElementById('approvePopoverConfirm').addEventListener('click', async function () {
        if (!activePopoverTaskId) { return; }
        try {
            var taskId = activePopoverTaskId;
            var comment = document.getElementById('approvePopoverComment').value.trim();
            var payload = comment ? { comment: comment } : {};
            await callApi('POST', '/api/v1/tasks/' + taskId + '/approve', payload);
            closePopovers();
            var t = findTask(taskId);
            if (t) { t._decisionLabel = 'approved'; t.status = 'APPROVED'; }
            renderChapterTasks();
            await loadData();
            showError('');
        } catch (err) { showError(err.message); }
    });

    document.getElementById('rejectPopoverConfirm').addEventListener('click', async function () {
        if (!activePopoverTaskId) { return; }
        var reason = document.getElementById('rejectPopoverReason').value.trim();
        if (reason.length < 5) { return; }
        try {
            var taskId = activePopoverTaskId;
            await callApi('POST', '/api/v1/tasks/' + taskId + '/reject', { reason: reason });
            closePopovers();
            var t = findTask(taskId);
            if (t) { t._decisionLabel = 'rejected'; t.status = 'IN_PROGRESS'; }
            renderChapterTasks();
            await loadData();
            showError('');
        } catch (err) { showError(err.message); }
    });

    document.getElementById('rejectPopoverReason').addEventListener('input', updateRejectConfirmState);
    document.getElementById('pageCompareClose').addEventListener('click', function () {
        document.getElementById('pageCompareModal').style.display = 'none';
    });
    document.getElementById('pageCompareModal').addEventListener('click', function (e) {
        if (e.target === this) {
            this.style.display = 'none';
        }
    });

    if (urlError) {
        showError(decodeURIComponent(urlError));
    }

    switchTab('pages');
    loadData();
})();
</script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
