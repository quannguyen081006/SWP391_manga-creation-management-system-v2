/**
 * task-list.js
 * Manages the page task list ("All Tasks") for Mangaka, Assistant, and Tantou Editor.
 *
 * TABLE OF CONTENTS
 * ──────────────────────────────────────────────────────────
 * 1. INITIALIZATION & STATE VARIABLES  (line ~20)
 * 2. UTILITIES (HELPERS)
 *    2a. escapeHtml / noopTaskPushMarker
 *    2b. Date handling (formatDate, dateOnly, daysUntilDate)
 *    2c. Deadline rendering (formatDeadlineCell)
 *    2d. Role & permission checks (hasRole, isAssignedAssistant, isTaskOwner)
 * 3. FILTER LOGIC & STATUS COUNTS       (computeTaskCounts, taskMatchesFilter)
 * 4. UI RENDERING
 *    4a. Status pills / dropdown filter  (renderStatusPills)
 *    4b. Task table                      (renderTasks, renderTaskRowActions)
 *    4c. Metrics (active/submitted/...)  (renderMetrics)
 *    4d. "View task" modal               (renderViewModalContent, openTaskView)
 *    4e. Task images                     (renderImages, loadTaskImages, renderImageForm)
 * 5. APPROVE / REJECT POPOVER           (openPopover, closePopovers, applyTaskDecision)
 * 6. MODAL UTILITIES                    (openModal, closeModals)
 * 7. API CALLS                          (callApi, uploadMultipart, fillAssistantSelect)
 * 8. DATA LOADING                       (loadData)
 * 9. EVENT LISTENERS                    (click, change, submit)
 * ──────────────────────────────────────────────────────────
 */

(function () {
    // ─── 1. INITIALIZATION & STATE VARIABLES ────────────────────────────────
    // ctx: app context path (taken from TASK_LIST_CONFIG or empty)
    var configScript = document.currentScript;
    var ctx = configScript ? configScript.getAttribute('data-context-path') || '' : '';
    var resultBox = document.getElementById('taskResult');   // div showing general notifications
    var currentUser = null;      // currently logged-in user (from /api/v1/auth/me)
    var seriesList = [];         // list of series
    var chapters = [];           // list of chapters
    var tasks = [];              // list of tasks (full, unfiltered)
    var seriesById = {};         // quick lookup for series by id
    var chapterById = {};        // quick lookup for chapter by id
    var taskStatusFilter = 'ALL'; // current filter applied to the table
    // State of the currently open approve/reject popover
    var activePopoverType = null;
    var activePopoverTaskId = null;
    var activePopoverCell = null;
    var viewModalTaskId = null;  // taskId currently open in the detail view modal

    // ─── 2a. UTILITY: ESCAPE HTML ────────────────────────────────────────────
    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value).replace(/[&<>"]/g, function (ch) {
            return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[ch];
        });
    }

    // Placeholder marker — used to test task push (no real logic)
    function noopTaskPushMarker() {
        return 'task-push-test';
    }

    // ─── 2b. UTILITY: DATE HANDLING ──────────────────────────────────────────
    // Normalize a date value into a "YYYY-MM-DD" string
    // Accepts: numeric timestamp, ISO string containing 'T', or an already-formatted string
    function formatDate(value) {
        if (value === null || value === undefined || value === '') {
            return '';
        }
        var text = String(value);
        if (/^\d+$/.test(text)) {
            var date = new Date(Number(text));
            if (isNaN(date.getTime())) {
                return text;
            }
            var month = String(date.getMonth() + 1);
            var day = String(date.getDate());
            return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
        }
        if (text.indexOf('T') > -1) {
            return text.substring(0, 10);
        }
        return text;
    }

    // Returns a Date object (at 00:00:00) from a date value
    function dateOnly(value) {
        var formatted = formatDate(value);
        return formatted ? new Date(formatted + 'T00:00:00') : null;
    }

    // Calculates the number of days remaining until the deadline (negative = overdue)
    function daysUntilDate(value) {
        var due = dateOnly(value);
        if (!due) { return null; }
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        return Math.ceil((due - today) / 86400000);
    }

    // ─── 2c. UTILITY: DEADLINE RENDERING ─────────────────────────────────────
    // Generates the deadline suffix text: "Done", "X days overdue", "Due today", etc.
    function deadlineSuffixText(daysLeft, isDone, isOverdue) {
        if (isDone) { return 'Done'; }
        if (isOverdue) {
            if (daysLeft !== null && daysLeft < 0) {
                var overdueDays = Math.abs(daysLeft);
                return overdueDays === 1 ? '1 day overdue' : (overdueDays + ' days overdue');
            }
            return 'Overdue';
        }
        if (daysLeft === null) { return ''; }
        if (daysLeft === 0) { return 'Due today'; }
        if (daysLeft === 1) { return '1 day left'; }
        return daysLeft + ' days left';
    }

    // Renders the deadline cell with the color/class matching its state
    // - done      → due-date-done (gray)
    // - overdue   → due-date-overdue (red) + ⚠ icon
    // - urgent (≤3 days) → due-date-urgent (orange)
    // - normal            → due-date-active (green)
    function formatDeadlineCell(dateValue, isDone, isOverdue) {
        var formatted = formatDate(dateValue);
        if (!formatted) { return '-'; }
        var daysLeft = daysUntilDate(dateValue);
        if (!isDone && !isOverdue && daysLeft !== null && daysLeft < 0) {
            isOverdue = true;
        }
        var suffixLabel = deadlineSuffixText(daysLeft, isDone, isOverdue);
        var suffix = suffixLabel ? ' (' + suffixLabel + ')' : '';

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

    // A task is considered "done" when status = APPROVED
    function isTaskDone(task) {
        return String(task.status || '').toUpperCase() === 'APPROVED';
    }

    // Today's date in "YYYY-MM-DD" format
    function todayIso() {
        var date = new Date();
        var month = String(date.getMonth() + 1);
        var day = String(date.getDate());
        return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
    }

    // Cộng thêm `days` ngày vào một giá trị ngày, trả về "YYYY-MM-DD"
    function addDaysIso(value, days) {
        var date = dateOnly(value);
        if (!date) { return ''; }
        date.setDate(date.getDate() + days);
        var month = String(date.getMonth() + 1);
        var day = String(date.getDate());
        return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
    }

    // ─── 2d. TIỆN ÍCH: KIỂM TRA ROLE & QUYỀN ────────────────────────────────
    // Kiểm tra currentUser có role tương ứng không (so sánh không phân biệt hoa thường)
    function hasRole(role) {
        var roles = currentUser && currentUser.roles ? currentUser.roles : [];
        if (String(currentUser && (currentUser.role || currentUser.activeRole || currentUser.currentRole || '')).toUpperCase() === role) {
            return true;
        }
        for (var i = 0; i < roles.length; i++) {
            var value = roles[i];
            var name = typeof value === 'string' ? value : (value && (value.name || value.role || value.authority));
            if (String(name || '').toUpperCase() === role) {
                return true;
            }
        }
        return false;
    }

    // Assistant được giao task này không?
    function isAssignedAssistant(task) {
        return hasRole('ASSISTANT') && Number(task.assistantId) === Number(currentUser.id);
    }

    // Mangaka sở hữu series chứa task này không?
    function isTaskOwner(task) {
        var chapter = chapterById[String(task.chapterId)];
        var series = chapter ? seriesById[String(chapter.seriesId)] : null;
        return hasRole('MANGAKA') && series && Number(series.mangakaId) === Number(currentUser.id);
    }

    // ─── FORMAT STATUS ────────────────────────────────────────────────────────
    // "IN_PROGRESS" → "In Progress"
    function formatStatus(status) {
        if (!status) {
            return '';
        }
        return String(status).toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, function (ch) { return ch.toUpperCase(); });
    }

    function formatTaskTypes(taskTypes) {
        var values = Array.isArray(taskTypes) ? taskTypes : String(taskTypes || '').split(',');
        return values.filter(Boolean).map(formatStatus).join(', ');
    }

    // Map status → CSS class cho status chip
    function statusClass(status) {
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

    // Task có bị overdue không?
    // - APPROVED → không bao giờ overdue
    // - status là OVERDUE, hoặc dueDate đã qua mà chưa approve
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

    // Task bị delayed (không update trong 3+ ngày kể từ khi được giao)?
    function isTaskDelayed(task) {
        return task && (task.delayed === true || task.isDelayed === true);
    }

    // ─── 3. LOGIC FILTER & ĐẾM STATUS ────────────────────────────────────────
    // Cấu hình các option filter hiển thị trên status pill bar
    function taskFilterOptions(counts) {
        return [
            { id: 'ALL', label: 'All', count: counts.ALL, cssClass: 'pill-all' },
            { id: 'IN_PROGRESS', label: 'In Progress', count: counts.IN_PROGRESS, cssClass: 'pill-progress' },
            { id: 'SUBMITTED', label: 'Submitted', count: counts.SUBMITTED, cssClass: 'pill-submitted' },
            { id: 'APPROVED', label: 'Completed', count: counts.APPROVED, cssClass: 'pill-approved' },
            { id: 'REJECTED', label: 'Rejected', count: counts.REJECTED, cssClass: 'pill-rejected' },
            { id: 'DELETED', label: 'Deleted', count: counts.DELETED, cssClass: 'pill-rejected' },
            { id: 'REASSIGNED', label: 'Reassigned', count: counts.REASSIGNED, cssClass: 'pill-pending' },
            { id: 'DELAYED', label: 'Delayed', count: counts.DELAYED, cssClass: 'pill-delayed' },
            { id: 'OVERDUE', label: 'Overdue', count: counts.OVERDUE, cssClass: 'pill-overdue' }
        ];
    }

    function renderTaskFilterOption(option, selectedId) {
        var active = selectedId === option.id ? ' is-active' : '';
        return '<button type="button" class="status-pill ' + option.cssClass + active + '" data-status-option="' + option.id + '">'
            + '<span class="status-pill-label">' + escapeHtml(option.label) + '</span>'
            + '<span class="status-pill-count">' + Number(option.count || 0) + '</span>'
            + '</button>';
    }

    // Đếm số task theo từng status (dùng cho pill bar và metrics)
    // Lưu ý: DELAYED và OVERDUE là trạng thái tính thêm, không loại trừ nhau với status chính
    function computeTaskCounts() {
        var counts = {
            ALL: tasks.length,
            IN_PROGRESS: 0,
            SUBMITTED: 0,
            APPROVED: 0,
            REJECTED: 0,
            OVERDUE: 0,
            DELETED: 0,
            REASSIGNED: 0,
            DELAYED: 0
        };
        for (var i = 0; i < tasks.length; i++) {
            var t = tasks[i];
            var st = String(t.status || '').toUpperCase();
            if (st === 'IN_PROGRESS') { counts.IN_PROGRESS++; }
            if (st === 'SUBMITTED') { counts.SUBMITTED++; }
            if (st === 'APPROVED') { counts.APPROVED++; }
            if (st === 'REJECTED') { counts.REJECTED++; }
            if (st === 'DELETED') { counts.DELETED++; }
            if (st === 'REASSIGNED') { counts.REASSIGNED++; }
            if (isTaskOverdue(t)) { counts.OVERDUE++; }
            if (isTaskDelayed(t)) { counts.DELAYED++; }
        }
        return counts;
    }

    // Kiểm tra task có khớp filter hiện tại không
    // DELAYED và OVERDUE là filter đặc biệt (không dựa vào task.status)
    function taskMatchesFilter(task, filter) {
        if (!filter || filter === 'ALL') { return true; }
        if (filter === 'DELAYED') { return isTaskDelayed(task); }
        if (filter === 'OVERDUE') { return isTaskOverdue(task); }
        return String(task.status || '').toUpperCase() === filter;
    }

    function getFilteredTasks() {
        return tasks.filter(function (t) { return taskMatchesFilter(t, taskStatusFilter); });
    }

    // ─── 4a. RENDER: STATUS PILLS / DROPDOWN FILTER ──────────────────────────
    // Render dropdown filter dạng pill (nút tóm tắt + menu xổ xuống)
    function renderStatusPills(counts) {
        var el = document.getElementById('taskStatusPills');
        if (!el) { return; }

        var options = taskFilterOptions(counts);
        var selected = options[0];
        for (var i = 0; i < options.length; i++) {
            if (options[i].id === taskStatusFilter) {
                selected = options[i];
                break;
            }
        }
        el.innerHTML = '<div class="status-filter-dropdown align-right" data-status-filter-dropdown="task">'
            + '<button type="button" class="status-pill status-filter-toggle ' + selected.cssClass + ' is-active" data-status-filter-toggle="task" aria-haspopup="true" aria-expanded="false">'
            + '<span class="status-pill-label">' + escapeHtml(selected.label) + '</span>'
            + '<span class="status-pill-count">' + Number(selected.count || 0) + '</span>'
            + '<span class="status-filter-caret">&#9662;</span>'
            + '</button>'
            + '<div class="status-filter-menu">'
            + options.map(function (option) { return renderTaskFilterOption(option, taskStatusFilter); }).join('')
            + '</div>'
            + '</div>';
    }

    // Render cell status với badge chính + badge "Delayed" nếu có
    function renderStatusCell(task) {
        var html = '<span class="status-chip ' + statusClass(task.status) + '">' + formatStatus(task.status) + '</span>';
        if (isTaskDelayed(task)) {
            html += ' <span class="status-chip status-delayed" title="No update for 3+ days since assignment">Delayed</span>';
        }
        return html;
    }

    // CSS class cho toàn bộ row (highlight đỏ/vàng nếu overdue/delayed)
    function taskRowClass(task) {
        if (isTaskOverdue(task)) {
            return ' class="task-row-overdue"';
        }
        if (isTaskDelayed(task)) {
            return ' class="task-row-delayed"';
        }
        return '';
    }

    function formatDueDateCell(task) {
        return formatDeadlineCell(task.dueDate, isTaskDone(task), isTaskOverdue(task));
    }

    // <select> chọn loại task (Sketching, Inking, Coloring, Lettering)
    function renderTaskTypeSelect(selectedTypes) {
        var options = [
            { value: 'SKETCHING', label: 'Sketching' },
            { value: 'INKING', label: 'Inking' },
            { value: 'COLORING', label: 'Coloring' },
            { value: 'SCREENTONE', label: 'Screentone' },
            { value: 'LETTERING', label: 'Lettering' },
            { value: 'MIXED', label: 'Mixed' }
        ];
        var selected = Array.isArray(selectedTypes) ? selectedTypes : String(selectedTypes || '').split(',');
        selected = selected.map(function (value) { return String(value).toUpperCase(); });
        return '<select name="taskTypes" multiple required>' + options.map(function (option) {
            return '<option value="' + option.value + '" ' + (selected.indexOf(option.value) >= 0 ? 'selected' : '') + '>' + option.label + '</option>';
        }).join('') + '</select>';
    }

    // ─── THÔNG BÁO (MESSAGE HELPERS) ─────────────────────────────────────────
    function showMessage(msg, isError) {
        if (!resultBox) {
            return;
        }
        resultBox.style.display = 'block';
        resultBox.className = isError ? 'alert error' : 'panel';
        resultBox.textContent = msg;
    }

    function showModalError(msg) {
        var el = document.getElementById('taskCreateError');
        if (!el) {
            return;
        }
        el.style.display = 'block';
        el.textContent = msg;
    }

    function showViewError(msg) {
        var el = document.getElementById('taskViewError');
        if (!el) {
            return;
        }
        if (!msg) {
            el.style.display = 'none';
            el.textContent = '';
            return;
        }
        el.style.display = 'block';
        el.textContent = msg;
    }

    function clearModalError() {
        var el = document.getElementById('taskCreateError');
        if (!el) {
            return;
        }
        el.style.display = 'none';
        el.textContent = '';
    }

    // Chuyển FormData của form thành plain object {key: value}
    function formToObject(form) {
        var data = {};
        var fd = new FormData(form);
        fd.forEach(function (v, k) {
            if (Object.prototype.hasOwnProperty.call(data, k)) {
                data[k] = [].concat(data[k], v);
            } else {
                data[k] = v;
            }
        });
        return data;
    }

    // ─── 7. API CALLS ─────────────────────────────────────────────────────────
    // Gọi API chung: GET/POST/PATCH/PUT/DELETE
    // - GET/PUT/PATCH: data gắn vào query string
    // - POST/DELETE: data gắn vào body (application/x-www-form-urlencoded)
    // Throw Error nếu HTTP status lỗi hoặc body.success === false
    async function callApi(method, path, data) {
        var opts = { method: method, headers: { 'Accept': 'application/json' } };
        var url = ctx + path;
        if (data) {
            var params = new URLSearchParams(data).toString();
            if (method === 'GET' || method === 'PUT' || method === 'PATCH') {
                url += (url.indexOf('?') === -1 ? '?' : '&') + params;
            } else {
                opts.headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=UTF-8';
                opts.body = params;
            }
        }

        var res = await fetch(url, opts);
        var text = await res.text();
        var body = null;
        try { body = text ? JSON.parse(text) : null; } catch (e) {}

        if (!res.ok || (body && body.success === false)) {
            var msg = (body && (body.message || (body.errors && body.errors[0]))) || text || ('Request failed: HTTP ' + res.status);
            throw new Error(msg);
        }
        return body;
    }

    // ─── 6. MODAL UTILITIES ───────────────────────────────────────────────────
    function openModal(id) {
        var modal = document.getElementById(id);
        if (modal) {
            if (id === 'taskCreateModal') {
                clearModalError();
            }
            modal.classList.add('open');
            modal.setAttribute('aria-hidden', 'false');
        }
    }

    function closeModals() {
        var modals = document.querySelectorAll('.modal-backdrop');
        for (var i = 0; i < modals.length; i++) {
            modals[i].classList.remove('open');
            modals[i].setAttribute('aria-hidden', 'true');
        }
        viewModalTaskId = null;
    }

    // Light-box preview ảnh (click ảnh trong danh sách)
    function openImagePreview(url, title) {
        var overlay = document.createElement('div');
        overlay.style.cssText = 'position:fixed;inset:0;background:rgba(15,23,42,.72);z-index:1400;display:flex;align-items:center;justify-content:center;padding:24px;';
        overlay.innerHTML =
            '<button type="button" aria-label="Close" class="task-preview-close">&times;</button>'
            + '<div class="task-preview-box">'
            + '<img src="' + escapeHtml(url) + '" alt="' + escapeHtml(title || 'Preview') + '" class="task-preview-image" />'
            + '<div class="task-preview-title">' + escapeHtml(title || '') + '</div></div>';
        function close() {
            overlay.remove();
            document.removeEventListener('keydown', onKey);
        }
        function onKey(e) {
            if (e.key === 'Escape') { close(); }
        }
        overlay.querySelector('button').addEventListener('click', close);
        overlay.addEventListener('click', function (e) {
            if (e.target === overlay) { close(); }
        });
        document.addEventListener('keydown', onKey);
        document.body.appendChild(overlay);
    }

    // ─── 5. POPOVER APPROVE / REJECT ─────────────────────────────────────────
    // Đóng tất cả popover đang mở và reset biến trạng thái
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

    // Mở popover approve hoặc reject tại vị trí anchorCell
    // type: 'approve' | 'reject'
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
            var reasonEl = document.getElementById('rejectPopoverReason');
            reasonEl.value = '';
            updateRejectConfirmState();
        }
    }

    // Bật/tắt nút "Confirm Reject" tùy theo độ dài reason (cần >= 5 ký tự)
    function updateRejectConfirmState() {
        var reasonEl = document.getElementById('rejectPopoverReason');
        var counterEl = document.getElementById('rejectPopoverCounter');
        var confirmBtn = document.getElementById('rejectPopoverConfirm');
        if (!reasonEl || !confirmBtn) { return; }
        var len = reasonEl.value.length;
        if (counterEl) { counterEl.textContent = len + ' / 300'; }
        confirmBtn.disabled = len < 5;
    }

    // Áp dụng kết quả quyết định approve/reject lên local task data (optimistic update)
    // Sau đó reload lại từ server qua loadData()
    function applyTaskDecision(taskId, decision) {
        var task = findTask(taskId);
        if (!task) { return; }
        task._decisionLabel = decision;
        if (decision === 'approved') {
            task.status = 'APPROVED';
        } else if (decision === 'rejected') {
            task.status = 'IN_PROGRESS';  // reject → assistant revise lại
        }
        renderMetrics();
        renderTasks();
    }

    // Upload ảnh dạng multipart/form-data
    // Nếu input file rỗng thì bỏ trường file ra khỏi FormData
    async function uploadMultipart(path, form) {
        var fd = new FormData(form);
        var file = form.querySelector('input[type="file"]');
        if (file && (!file.files || file.files.length === 0)) {
            fd.delete('file');
        }
        var res = await fetch(ctx + path, { method: 'POST', headers: { 'Accept': 'application/json' }, body: fd });
        var text = await res.text();
        var body = null;
        try { body = text ? JSON.parse(text) : null; } catch (e) {}
        if (!res.ok || (body && body.success === false)) {
            var msg = (body && (body.message || (body.errors && body.errors[0]))) || text || ('Request failed: HTTP ' + res.status);
            throw new Error(msg);
        }
        return body;
    }

    // Load danh sách assistant của series vào <select>
    // Gọi API GET /api/v1/series/{seriesId}/assistants
    async function fillAssistantSelect(select, seriesId, selectedId) {
        if (!select || !seriesId) {
            return;
        }
        select.innerHTML = '<option value="">Loading assistants...</option>';
        try {
            var res = await callApi('GET', '/api/v1/series/' + seriesId + '/assistants');
            var assistants = res.data || [];
            select.innerHTML = '<option value="">Select Assistant</option>' + assistants.map(function (a) {
                var selected = Number(a.id) === Number(selectedId) ? 'selected' : '';
                return '<option value="' + a.id + '" ' + selected + '>#' + a.id + ' - ' + escapeHtml(a.fullName || a.username) + '</option>';
            }).join('');
        } catch (err) {
            select.innerHTML = '<option value="">Cannot load assistants</option>';
            showMessage(err.message, true);
        }
    }

    // Render khu vực action "Tạo task" — chỉ hiện cho Mangaka
    // Populate <select> chapter với những chapter thuộc series của Mangaka đang đăng nhập
    function renderTaskActions() {
        var actions = document.getElementById('taskActions');
        if (!hasRole('MANGAKA')) {
            actions.style.display = 'none';
            return;
        }
        actions.style.display = 'block';

        var select = document.getElementById('createTaskChapterId');
        var ownChapters = chapters.filter(function (ch) {
            var series = seriesById[String(ch.seriesId)];
            return series && Number(series.mangakaId) === Number(currentUser.id);
        });
        select.innerHTML = '<option value="">Select Chapter</option>' + ownChapters.map(function (ch) {
            return '<option value="' + ch.id + '">#' + ch.id + ' - S' + ch.seriesId + ' - Ch.' + ch.chapterNumber + ' - ' + escapeHtml(ch.title) + '</option>';
        }).join('');
    }

    // Cập nhật hint về deadline khi Mangaka chọn chapter trong form tạo task
    // Due date của task phải nằm trong [today, chapter.submissionDeadline - 3 ngày]
    function updateCreateTaskDeadlineHint(chapter) {
        var hint = document.getElementById('createTaskDeadlineHint');
        var dueDateInput = document.getElementById('taskCreateDueDate');
        if (dueDateInput) {
            dueDateInput.min = todayIso();
            dueDateInput.removeAttribute('max');
        }
        var latestDueDate = chapter && chapter.submissionDeadline ? addDaysIso(chapter.submissionDeadline, -3) : '';
        if (dueDateInput && latestDueDate) {
            dueDateInput.max = latestDueDate;
        }
        if (!hint) {
            return;
        }
        hint.textContent = chapter && chapter.submissionDeadline
            ? ('Chapter deadline: ' + formatDate(chapter.submissionDeadline) + '. Task due date must be between today and ' + latestDueDate + '.')
            : '';
    }

    // ─── 4c. RENDER: METRICS ─────────────────────────────────────────────────
    // Cập nhật 5 số liệu tổng: Active, Submitted, Completed, Overdue, Delayed
    function renderMetrics() {
        var active = 0;
        var submitted = 0;
        var completed = 0;
        var counts = computeTaskCounts();

        for (var i = 0; i < tasks.length; i++) {
            var st = String(tasks[i].status || '').toUpperCase();
            if (st === 'PENDING' || st === 'IN_PROGRESS') { active++; }
            if (st === 'SUBMITTED') { submitted++; }
            if (st === 'APPROVED') { completed++; }
        }

        document.getElementById('activeTasks').textContent = active;
        document.getElementById('submittedTasks').textContent = submitted;
        document.getElementById('completedTasks').textContent = completed;
        document.getElementById('overdueTasks').textContent = counts.OVERDUE;
        document.getElementById('delayedTasks').textContent = counts.DELAYED;
        renderStatusPills(counts);
    }

    // ─── 4d. RENDER: MODAL XEM TASK ──────────────────────────────────────────
    // Render nội dung bên trong modal xem chi tiết task
    // - Mangaka (owner): hiện form update dueDate/priority/notes
    // - Các role khác: chỉ xem, không sửa
    // - Luôn hiện phần feedback (approval comment, rejection reason, v.v.)
    function renderViewModalContent(task) {
        var chapter = chapterById[String(task.chapterId)];
        var latestDueDate = chapter && chapter.submissionDeadline ? addDaysIso(chapter.submissionDeadline, -3) : '';
        var dueDateAttrs = ' min="' + todayIso() + '"' + (latestDueDate ? ' max="' + escapeHtml(latestDueDate) + '"' : '');
        var taskStatus = String(task.status || '').toUpperCase();
        var canEdit = isTaskOwner(task) && taskStatus !== 'APPROVED' && taskStatus !== 'DELETED' && taskStatus !== 'REASSIGNED';
        var saveBtn = document.getElementById('taskViewSaveBtn');
        if (saveBtn) {
            saveBtn.style.display = canEdit ? '' : 'none';
        }
        // Cảnh báo task đã APPROVED không thể sửa (BR-TSK-06)
        var approvedNote = String(task.status || '').toUpperCase() === 'APPROVED'
            ? '<div class="alert error task-feedback-block">Approved task cannot be edited. Create a new task instead (BR-TSK-06)</div>'
            : '';
        var feedback = '';
        if (task.approvalComment) {
            feedback += '<div class="alert success task-feedback-block"><strong>Approval comment:</strong><div class="task-feedback-body">' + escapeHtml(task.approvalComment) + '</div></div>';
        }
        if (task.rejectionReason) {
            feedback += '<div class="alert error task-feedback-block"><strong>Revision note:</strong><div class="task-feedback-body">' + escapeHtml(task.rejectionReason) + '</div></div>';
        }
        if (task.actionReason && (taskStatus === 'DELETED' || taskStatus === 'REASSIGNED')) {
            feedback += '<div class="alert warning task-feedback-block"><strong>' + (taskStatus === 'DELETED' ? 'Deleted reason:' : 'Reassigned reason:') + '</strong><div class="task-feedback-body-pre">' + escapeHtml(task.actionReason) + '</div></div>';
        }
        return approvedNote
            + '<div class="task-view-chips">'
            + '<span class="status-chip">' + escapeHtml(formatTaskTypes(task.taskTypes)) + '</span>'
            + '<span class="status-chip">Assigned: ' + escapeHtml(task.assistantName) + '</span>'
            + '<span class="status-chip">Pages ' + task.pageRangeStart + '-' + task.pageRangeEnd + '</span>'
            + renderStatusCell(task)
            + '</div>'
            + '<p class="task-view-note">Approve / Reject is done directly from the table — this modal is only for viewing and updating progress.</p>'
            + feedback
            + (canEdit
                ? ('<form id="taskViewUpdateForm" class="form-grid task-view-update-form task-view-update-form-layout">'
                    + '<input name="taskId" type="hidden" value="' + task.id + '" />'
                    + '<input name="assistantId" type="hidden" value="' + task.assistantId + '" />'
                    + '<input name="pageRangeStart" type="hidden" value="' + task.pageRangeStart + '" />'
                    + '<input name="pageRangeEnd" type="hidden" value="' + task.pageRangeEnd + '" />'
                    + '<input name="taskTypes" type="hidden" value="' + escapeHtml((task.taskTypes || []).join(',')) + '" />'
                    + '<label class="field-label" for="taskViewDueDate">Due date</label>'
                    + '<input id="taskViewDueDate" name="dueDate" type="date" value="' + escapeHtml(formatDate(task.dueDate)) + '"' + dueDateAttrs + ' required />'
                    + '<label class="field-label" for="taskViewPriority">Priority</label>'
                    + '<select id="taskViewPriority" name="priority">'
                    + '<option value="NORMAL"' + (task.priority === 'NORMAL' ? ' selected' : '') + '>Normal</option>'
                    + '<option value="HIGH"' + (task.priority === 'HIGH' ? ' selected' : '') + '>High</option>'
                    + '<option value="URGENT"' + (task.priority === 'URGENT' ? ' selected' : '') + '>Urgent</option>'
                    + '</select>'
                    + '<label class="field-label" for="taskViewNotes">Notes / progress update</label>'
                    + '<textarea id="taskViewNotes" name="notes" rows="4" placeholder="Progress note for assistant...">' + escapeHtml(task.notes || '') + '</textarea>'
                    + '</form>')
                : '<p class="section-desc">You can view this task but only the series owner Mangaka can update it.</p>')
            + renderImageForm(task);
    }

    // ─── 4b. RENDER: BẢNG TASK ───────────────────────────────────────────────
    // Render các nút action trong cột cuối của mỗi row:
    // - Assistant được giao: link "View" đến trang detail riêng (/main/tasks/{id})
    // - Mangaka (owner) + task SUBMITTED: nút Approve/Reject (mở popover)
    // - Các role khác: nút "View" mở modal
    function renderTaskRowActions(task) {
        if (task._decisionLabel === 'approved') {
            return '<span class="task-decision-label approved">Approved</span>';
        }
        if (task._decisionLabel === 'rejected') {
            return '<span class="task-decision-label rejected">Rejected</span>';
        }
        var st = String(task.status || '').toUpperCase();
        var html = isAssignedAssistant(task)
            ? '<a class="btn small" href="' + ctx + '/main/tasks/' + task.id + '">View</a>'
            : '';
        if (!html && !hasRole('MANGAKA')) {
            html = '<button class="btn small" type="button" data-task-view="' + task.id + '">View</button>';
        }
        if (isTaskOwner(task) && st === 'SUBMITTED') {
            html += ' <button class="btn small success-soft" type="button" data-task-approve-pop="' + task.id + '">Approve</button>';
            html += ' <button class="btn small danger-soft" type="button" data-task-reject-pop="' + task.id + '">Reject</button>';
        }
        // Lưu ý: Assistant submit task từ trang detail riêng, không từ bảng này
        return html;
    }

    // ─── 4e. RENDER: ẢNH TASK ────────────────────────────────────────────────
    // Form upload ảnh: chỉ assistant được giao task mới upload được
    // Mangaka và Tantou Editor chỉ xem (read-only)
    function renderImageForm(task) {
        var uploadForm = '';
        if (isAssignedAssistant(task)) {
            uploadForm = '<form class="form-grid task-image-upload-form task-image-upload-form-layout" data-task-id="' + task.id + '" data-chapter-id="' + task.chapterId + '">'
                + '<strong>Upload Page Image</strong>'
                + '<input name="imageType" type="hidden" value="PAGE" />'
                + '<input name="pageTaskId" type="hidden" value="' + task.id + '" />'
                + '<input name="pageNumber" type="number" min="1" placeholder="Page Number" required />'
                + '<input name="file" type="file" accept="image/*" required />'
                + '<button class="btn primary" type="submit">Upload Image</button>'
                + '</form>';
        } else if (hasRole('MANGAKA')) {
            uploadForm = '<p class="section-desc">PAGE images can be uploaded only by the assistant assigned to this task. Use Chapters > Images to upload cover/reference images.</p>';
        } else if (hasRole('TANTOU_EDITOR')) {
            uploadForm = '<p class="section-desc">Tantou editor has read-only access to task images.</p>';
        }
        return '<div class="panel">'
            + uploadForm
            + '<div class="task-image-list" data-task-image-list="' + task.id + '">Loading images...</div>'
            + '</div>';
    }

    // Render lưới ảnh đã upload (mỗi ảnh có nút Download và Delete nếu có quyền)
    function renderImages(images) {
        if (!images.length) {
            return '<p class="section-desc">No images uploaded yet.</p>';
        }
        return '<div class="task-image-grid">' + images.map(function (img) {
            var url = imageUrl(img.fileUrl);
            // Chỉ uploader hoặc Mangaka mới xóa được ảnh
            var deleteButton = canDeleteImage(img)
                ? '<button class="btn small danger-soft" type="button" data-task-image-delete="' + img.id + '" data-task-id="' + img.pageTaskId + '">Delete</button>'
                : '';
            var downloadButton = '<a class="btn small" href="' + escapeHtml(url) + '" download>Download</a>';
            return '<div class="panel task-image-card">'
                + '<img src="' + escapeHtml(url) + '" alt="' + escapeHtml(img.originalFileName || ('Page ' + img.pageNumber)) + '" data-preview-src="' + escapeHtml(url) + '" data-preview-title="Page ' + escapeHtml(img.pageNumber || '') + '" class="task-image-thumb" />'
                + '<div class="task-image-title">Page ' + escapeHtml(img.pageNumber || '') + '</div>'
                + '<div class="section-desc">' + escapeHtml(img.originalFileName || '') + '</div>'
                + downloadButton
                + deleteButton
                + '</div>';
        }).join('') + '</div>';
    }

    // Uploader hoặc Mangaka mới được xóa ảnh
    function canDeleteImage(img) {
        return img.id && currentUser && (Number(img.uploadedBy) === Number(currentUser.id) || hasRole('MANGAKA'));
    }

    // Chuẩn hoá URL ảnh: thêm contextPath nếu chưa có
    function imageUrl(fileUrl) {
        var url = String(fileUrl || '');
        if (url.indexOf('http://') === 0 || url.indexOf('https://') === 0) {
            return url;
        }
        if (url.indexOf(ctx + '/') === 0) {
            return url;
        }
        return ctx + url;
    }

    // Gọi API lấy danh sách ảnh của task, render vào [data-task-image-list]
    async function loadTaskImages(taskId) {
        var target = document.querySelector('[data-task-image-list="' + taskId + '"]');
        if (!target) {
            return;
        }
        target.innerHTML = 'Loading images...';
        try {
            var res = await callApi('GET', '/api/v1/tasks/' + taskId + '/images');
            target.innerHTML = renderImages(res.data || []);
        } catch (err) {
            target.innerHTML = '<div class="alert error">' + escapeHtml(err.message) + '</div>';
        }
    }

    // Render phần header thông tin task (dùng trong một số context hiển thị tóm tắt)
    function renderTaskDetail(task) {
        return '<strong>Task #' + task.id + ' Detail</strong>'
            + '<div class="inline-meta task-inline-meta-spaced">'
            + '<span>Pages: ' + task.pageRangeStart + '-' + task.pageRangeEnd + '</span>'
            + '<span>Types: ' + formatTaskTypes(task.taskTypes) + '</span>'
            + '<span>Assigned: ' + escapeHtml(task.assistantName) + '</span>'
            + '<span>Status: ' + renderStatusCell(task) + '</span>'
            + '<span>Due Date: ' + formatDueDateCell(task) + '</span>'
            + '</div>'
            + (task.notes ? '<div class="alert info task-note-block"><strong>Mangaka note:</strong><div class="task-feedback-body-pre">' + escapeHtml(task.notes) + '</div></div>' : '');
    }

    // Tìm task trong mảng tasks theo id (so sánh số)
    function findTask(taskId) {
        for (var i = 0; i < tasks.length; i++) {
            if (Number(tasks[i].id) === Number(taskId)) {
                return tasks[i];
            }
        }
        return null;
    }

    // Render toàn bộ bảng task theo filter hiện tại
    function renderTasks() {
        var tbody = document.getElementById('taskRows');
        var visible = getFilteredTasks();
        if (!tasks.length) {
            tbody.innerHTML = '<tr><td colspan="8">No tasks found.</td></tr>';
            return;
        }
        if (!visible.length) {
            tbody.innerHTML = '<tr><td colspan="8">No tasks match this filter.</td></tr>';
            return;
        }

        tbody.innerHTML = visible.map(function (task) {
            return '<tr' + taskRowClass(task) + '>'
                + '<td>' + task.id + '</td>'
                + '<td><strong>' + escapeHtml(task.seriesTitle) + '</strong><br/>Ch. ' + escapeHtml(task.chapterNumber) + ' - ' + escapeHtml(task.chapterTitle) + '</td>'
                + '<td>' + task.pageRangeStart + '-' + task.pageRangeEnd + '</td>'
                + '<td>' + formatTaskTypes(task.taskTypes) + '</td>'
                + '<td>' + escapeHtml(task.assistantName) + '</td>'
                + '<td>' + renderStatusCell(task) + '</td>'
                + '<td>' + formatDueDateCell(task) + '</td>'
                + '<td class="task-actions-cell"><div class="task-row-actions">' + renderTaskRowActions(task) + '</div></td>'
                + '</tr>';
        }).join('');
    }

    // Mở modal xem chi tiết task, tải ảnh song song
    async function openTaskView(taskId) {
        closePopovers();
        showViewError('');
        var task = findTask(taskId);
        if (!task) {
            return;
        }
        viewModalTaskId = taskId;
        document.getElementById('taskViewTitle').textContent = 'Task #' + task.id;
        document.getElementById('taskViewSubtitle').textContent = (task.seriesTitle || '') + ' - Ch. ' + task.chapterNumber + ' - ' + (task.chapterTitle || '');
        document.getElementById('taskViewContent').innerHTML = renderViewModalContent(task);
        openModal('taskViewModal');
        await loadTaskImages(taskId);
    }

    // ─── 8. LOAD DỮ LIỆU ─────────────────────────────────────────────────────
    // Gọi song song 3 API để lấy series, chapters, tasks
    // Sau đó build lookup map và render toàn bộ UI
    async function loadData() {
        try {
            var userRes = await callApi('GET', '/api/v1/auth/me');
            currentUser = userRes.data;
            var results = await Promise.all([
                callApi('GET', '/api/v1/series'),
                callApi('GET', '/api/v1/chapters'),
                callApi('GET', '/api/v1/tasks')
            ]);
            seriesList = results[0].data || [];
            chapters = results[1].data || [];
            tasks = results[2].data || [];
            seriesById = {};
            chapterById = {};
            for (var i = 0; i < seriesList.length; i++) {
                seriesById[String(seriesList[i].id)] = seriesList[i];
            }
            for (var j = 0; j < chapters.length; j++) {
                chapterById[String(chapters[j].id)] = chapters[j];
            }
            renderTaskActions();
            renderMetrics();
            renderTasks();
        } catch (err) {
            document.getElementById('taskRows').innerHTML = '<tr><td colspan="8">' + escapeHtml(err.message) + '</td></tr>';
        }
    }

    // ─── 9. EVENT LISTENERS ───────────────────────────────────────────────────
    // Một event listener click duy nhất cho toàn trang (event delegation)
    // Xử lý: toggle filter dropdown, chọn filter, approve/reject popover,
    //         đóng popover, mở/đóng modal, xem task, preview ảnh, xóa ảnh
    document.addEventListener('click', async function (e) {
        // Toggle dropdown filter pill
        var taskToggle = e.target.closest ? e.target.closest('#taskStatusPills [data-status-filter-toggle]') : null;
        if (taskToggle) {
            var taskDropdown = taskToggle.closest('[data-status-filter-dropdown]');
            if (taskDropdown) {
                taskDropdown.classList.toggle('open');
                taskToggle.setAttribute('aria-expanded', taskDropdown.classList.contains('open') ? 'true' : 'false');
            }
            return;
        }

        // Chọn filter option từ dropdown
        var taskPill = e.target.closest ? e.target.closest('#taskStatusPills [data-status-option]') : null;
        if (taskPill) {
            taskStatusFilter = taskPill.getAttribute('data-status-option') || 'ALL';
            renderStatusPills(computeTaskCounts());
            renderTasks();
            return;
        }

        // Click ra ngoài dropdown → đóng dropdown
        var openTaskDropdown = document.querySelector('#taskStatusPills [data-status-filter-dropdown].open');
        if (openTaskDropdown && !(e.target.closest && e.target.closest('#taskStatusPills [data-status-filter-dropdown]'))) {
            openTaskDropdown.classList.remove('open');
        }

        // Nút Approve → mở approve popover
        var approvePopBtn = e.target.closest ? e.target.closest('[data-task-approve-pop]') : null;
        if (approvePopBtn) {
            var approveCell = approvePopBtn.closest('.task-actions-cell');
            openPopover('approve', approvePopBtn.getAttribute('data-task-approve-pop'), approveCell);
            return;
        }

        // Nút Reject → mở reject popover
        var rejectPopBtn = e.target.closest ? e.target.closest('[data-task-reject-pop]') : null;
        if (rejectPopBtn) {
            var rejectCell = rejectPopBtn.closest('.task-actions-cell');
            openPopover('reject', rejectPopBtn.getAttribute('data-task-reject-pop'), rejectCell);
            return;
        }

        // Nút Cancel trong popover → đóng popover
        var popoverCancel = e.target.closest ? e.target.closest('[data-popover-cancel]') : null;
        if (popoverCancel) {
            closePopovers();
            return;
        }
        // Click vào scrim (backdrop) của popover → đóng popover
        if (e.target.id === 'taskPopoverScrim') {
            closePopovers();
            return;
        }

        // Click ra ngoài popover và actions cell → đóng popover
        var insidePopover = e.target.closest ? e.target.closest('.task-action-popover') : null;
        var insideActions = e.target.closest ? e.target.closest('.task-row-actions') : null;
        if (!insidePopover && !insideActions && activePopoverType) {
            closePopovers();
        }

        // Nút [data-modal-open] → mở modal tương ứng
        var openButton = e.target.closest ? e.target.closest('[data-modal-open]') : null;
        if (openButton) {
            closePopovers();
            var modalId = openButton.getAttribute('data-modal-open');
            openModal(modalId);
            return;
        }
        // Nút [data-modal-close] hoặc click backdrop modal → đóng modal
        if (e.target.closest && e.target.closest('[data-modal-close]')) {
            closeModals();
            return;
        }
        if (e.target.classList && e.target.classList.contains('modal-backdrop')) {
            closeModals();
            return;
        }

        // Nút [data-task-view] → mở modal xem chi tiết task
        var viewButton = e.target.closest ? e.target.closest('[data-task-view]') : null;
        if (viewButton) {
            closePopovers();
            await openTaskView(viewButton.getAttribute('data-task-view'));
            return;
        }

        // Click ảnh thumbnail → mở light-box preview
        var previewImg = e.target.closest ? e.target.closest('[data-preview-src]') : null;
        if (previewImg) {
            openImagePreview(previewImg.getAttribute('data-preview-src'), previewImg.getAttribute('data-preview-title'));
            return;
        }

        // Nút [data-task-image-delete] → xóa ảnh sau khi confirm
        var deleteImageBtn = e.target.closest ? e.target.closest('[data-task-image-delete]') : null;
        if (deleteImageBtn) {
            if (!confirm('Delete this image?')) return;
            try {
                var imageId = deleteImageBtn.getAttribute('data-task-image-delete');
                var reloadTaskId = deleteImageBtn.getAttribute('data-task-id');
                await callApi('DELETE', '/api/v1/images/' + imageId);
                showMessage('Image deleted.', false);
                await loadTaskImages(reloadTaskId);
            } catch (err) {
                showMessage(err.message, true);
            }
            return;
        }

    });

    // Confirm Approve: gọi POST /api/v1/tasks/{id}/approve với comment tùy chọn
    document.getElementById('approvePopoverConfirm').addEventListener('click', async function () {
        if (!activePopoverTaskId) { return; }
        try {
            var taskId = activePopoverTaskId;
            var comment = document.getElementById('approvePopoverComment').value.trim();
            var payload = comment ? { comment: comment } : {};
            await callApi('POST', '/api/v1/tasks/' + taskId + '/approve', payload);
            closePopovers();
            applyTaskDecision(taskId, 'approved');
            showMessage('Task approved.', false);
            await loadData();  // reload để đồng bộ dữ liệu mới nhất
        } catch (err) {
            showMessage(err.message, true);
        }
    });

    // Confirm Reject: gọi POST /api/v1/tasks/{id}/reject với reason bắt buộc (>= 5 ký tự)
    document.getElementById('rejectPopoverConfirm').addEventListener('click', async function () {
        if (!activePopoverTaskId) { return; }
        var reason = document.getElementById('rejectPopoverReason').value.trim();
        if (reason.length < 5) { return; }
        try {
            var taskId = activePopoverTaskId;
            await callApi('POST', '/api/v1/tasks/' + taskId + '/reject', { reason: reason });
            closePopovers();
            applyTaskDecision(taskId, 'rejected');
            showMessage('Task rejected and sent back for rework.', false);
            await loadData();
        } catch (err) {
            showMessage(err.message, true);
        }
    });

    // Cập nhật bộ đếm ký tự và trạng thái nút Confirm Reject khi gõ reason
    document.getElementById('rejectPopoverReason').addEventListener('input', updateRejectConfirmState);

    // Nút Save trong modal xem task: gọi PATCH /api/v1/tasks/{id} với dueDate/priority/notes
    // Không cho sửa task đã APPROVED (BR-TSK-06)
    document.getElementById('taskViewSaveBtn').addEventListener('click', async function () {
        var form = document.getElementById('taskViewUpdateForm');
        showViewError('');
        if (!form) {
            closeModals();
            return;
        }
        var task = findTask(viewModalTaskId);
        if (task && String(task.status || '').toUpperCase() === 'APPROVED') {
            showViewError('Approved task cannot be edited. Create a new task instead (BR-TSK-06)');
            return;
        }
        try {
            var updateData = formToObject(form);
            await callApi('PATCH', '/api/v1/tasks/' + updateData.taskId, {
                dueDate: updateData.dueDate,
                priority: updateData.priority,
                notes: updateData.notes
            });
            showMessage('Task updated successfully.', false);
            closeModals();
            await loadData();
        } catch (err) {
            showViewError(err.message);
        }
    });

    // Khi Mangaka chọn chapter → load danh sách assistant và cập nhật hint deadline
    document.addEventListener('change', async function (e) {
        if (e.target.id === 'createTaskChapterId') {
            var chapter = chapterById[String(e.target.value)];
            var assistantSelect = document.getElementById('createTaskAssistantId');
            if (!chapter) {
                assistantSelect.innerHTML = '<option value="">Select Chapter first</option>';
                updateCreateTaskDeadlineHint(null);
                return;
            }
            updateCreateTaskDeadlineHint(chapter);
            await fillAssistantSelect(assistantSelect, chapter.seriesId, '');
        }
    });

    // Submit form tạo task: POST /api/v1/chapters/{id}/tasks
    // Submit form upload ảnh (class task-image-upload-form): POST /api/v1/chapters/{id}/images
    document.addEventListener('submit', async function (e) {
        if (e.target.id === 'taskCreateForm') {
            e.preventDefault();
            try {
                var createData = formToObject(e.target);
                await callApi('POST', '/api/v1/chapters/' + createData.chapterId + '/tasks', {
                    assistantId: createData.assistantId,
                    pageRangeStart: createData.pageRangeStart,
                    pageRangeEnd: createData.pageRangeEnd,
                    taskTypes: createData.taskTypes,
                    dueDate: createData.dueDate
                });
                showMessage('Task created successfully.', false);
                e.target.reset();
                document.getElementById('createTaskAssistantId').innerHTML = '<option value="">Select Chapter first</option>';
                updateCreateTaskDeadlineHint(null);
                clearModalError();
                closeModals();
                await loadData();
            } catch (err) {
                showModalError(err.message);
            }
        }
        if (e.target.classList.contains('task-image-upload-form')) {
            e.preventDefault();
            try {
                var imageTaskId = e.target.getAttribute('data-task-id');
                var imageChapterId = e.target.getAttribute('data-chapter-id');
                await uploadMultipart('/api/v1/chapters/' + imageChapterId + '/images', e.target);
                showMessage('Task image uploaded.', false);
                e.target.reset();
                await loadTaskImages(imageTaskId);
            } catch (err) {
                showMessage(err.message, true);
            }
        }
    });

    // Khởi động: load dữ liệu ngay khi script chạy
    loadData();
})();
