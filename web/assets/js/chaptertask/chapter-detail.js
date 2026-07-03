/**
 * chapter-detail.js
 * Script cho trang Chapter Detail — quản lý page slots, tasks, upload ảnh.
 *
 * ============================================================
 * MỤC LỤC
 * ============================================================
 * 1.  BIẾN TOÀN CỤC (state)
 * 2.  UTILITY — escapeHtml, formatDate, initials, dateOnly, daysUntilDate, todayIso, addDaysIso
 * 3.  ROLE / PERMISSION — hasRole, isOwner
 * 4.  API HELPERS — callApi, uploadMultipart
 * 5.  UI HELPERS — showError, formatStatus, showPageUploadError
 * 6.  STAGE LOGIC — pageStageOrder, normalizeStage, nextAllowedStage, prepareStageSelect,
 *                   refreshStagePickerEnabled, selectedUploadStage, syncStagePickerFromClick, renderStageTrack
 * 7.  PAGE MODAL — openPageUploadModal
 * 8.  STATUS CSS — chapterStatusClass, taskStatusClass
 * 9.  DEADLINE HELPERS — isChapterDone, isChapterOverdue, deadlineSuffixText, formatDeadlineCell
 * 10. IMAGE URL — imageUrl
 * 11. PAGE SELECTION — findPageById, getSelectedPages, isPageFullyComplete, isAssignablePage,
 *                      toggleSelectedPage, countUploaded, pageStageScore, pageCompletionPercent,
 *                      countFullyCompletePages, slotStateClass
 * 12. RENDER — renderSelectionBar, renderAssignChips, nextTaskTypeForPages,
 *              setDefaultAssignTaskType, renderPageGrid, renderPageProgress, renderSidebarTasks,
 *              updateManuscriptWorkspaceButton, renderMeta
 * 13. TASK HELPERS — findTask, findTaskByPageNumber, isTaskOverdue, formatDueDateCell, renderTaskRowActions, renderChapterTasks
 * 14. TASK INLINE / COMPARE — loadTaskInlinePages, openPageCompare
 * 15. MODAL / POPOVER — switchTab, openModal, closeModals, closePopovers, openPopover,
 *                        updateRejectConfirmState, openAssignModal, openOverdueDecisionModal, setOverdueDecisionChoice
 * 16. ASSISTANT SELECT — fillAssistantSelect, latestTaskDueDate, updateAssignDueConstraints
 * 17. LOAD DATA — loadPages, loadTasks, loadData
 * 18. EVENT LISTENERS — tab bar, metadata save, btn delete/markDone/addPage, upload picker,
 *                        page grid click, selection bar, assign form, reassign form, extend form,
 *                        overdue decision buttons, approve/reject popovers, page compare modal
 * ============================================================
 */

(function () {
    // ============================================================
    // 1. BIẾN TOÀN CỤC (state)
    // ============================================================
    var configScript = document.currentScript;
    var ctx = configScript ? configScript.getAttribute('data-context-path') || '' : '';
    var params = new URLSearchParams(window.location.search);
    var chapterId = params.get('id');       // ID chapter từ query string
    var urlError = params.get('error');     // Lỗi được truyền qua URL (nếu có)
    var currentUser = null;     // User đang đăng nhập (từ /api/v1/auth/me)
    var chapter = null;         // Chi tiết chapter hiện tại
    var seriesData = null;      // Series chứa chapter này
    var pageSlots = [];         // Danh sách page slots của chapter
    var chapterTasks = [];      // Danh sách tasks của chapter
    var selectedPageIds = {};   // Map pageId → true cho các page đang được chọn để gán task
    var lastSlotIndex = -1;     // Index slot cuối được click (dùng cho shift-click)
    var pendingUploadPageId = null;  // PageId đang chờ upload trong modal
    var pendingUploadSlot = null;    // Slot đang chờ upload trong modal
    var activePopoverType = null;    // 'approve' | 'reject' — popover đang mở
    var activePopoverTaskId = null;  // Task ID của popover đang mở
    var activePopoverCell = null;    // DOM cell anchor của popover
    var activeOverdueTaskId = null;  // Task ID trong modal overdue decision
    var taskImagesCache = {};        // Cache ảnh theo taskId: { taskId: [imgObjects] }
    var taskInlineLoaded = {};       // Đánh dấu task nào đã load inline xong
    var metadataSaveTimer = null;    // Timer debounce cho auto-save metadata

    // ============================================================
    // 2. UTILITY
    // ============================================================

    /** Escape HTML đặc biệt để tránh XSS khi render vào innerHTML */
    function escapeHtml(v) {
        if (v === null || v === undefined) {
            return '';
        }
        return String(v).replace(/[&<>"]/g, function (c) {
            return ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'})[c];
        });
    }

    /**
     * Format giá trị ngày thành chuỗi YYYY-MM-DD.
     * Hỗ trợ: timestamp số, chuỗi ISO (có 'T'), hoặc chuỗi ngày sẵn.
     */
    function formatDate(v) {
        if (!v) {
            return '';
        }
        var s = String(v);
        if (/^\d+$/.test(s)) {
            var date = new Date(Number(s));
            if (!isNaN(date.getTime())) {
                var month = String(date.getMonth() + 1);
                var day = String(date.getDate());
                return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
            }
        }
        if (s.indexOf('T') > -1) {
            return s.substring(0, 10);
        }
        return s;
    }

    /**
     * Lấy chữ viết tắt từ tên đầy đủ (VD: "Nguyen Van A" → "NA").
     * Dùng để hiển thị avatar assistant trên page slot.
     */
    function initials(name) {
        if (!name) {
            return '?';
        }
        var parts = String(name).trim().split(/\s+/).filter(Boolean);
        if (parts.length >= 2) {
            return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        return parts[0].substring(0, 2).toUpperCase();
    }

    /** Chuyển giá trị ngày thành Date object ở 00:00:00 local time */
    function dateOnly(v) {
        var d = formatDate(v);
        return d ? new Date(d + 'T00:00:00') : null;
    }

    /** Tính số ngày còn lại đến deadline. Âm = đã quá hạn. */
    function daysUntilDate(value) {
        var due = dateOnly(value);
        if (!due) {
            return null;
        }
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        return Math.ceil((due - today) / 86400000);
    }

    /** Trả về ngày hôm nay dạng YYYY-MM-DD */
    function todayIso() {
        var date = new Date();
        var month = String(date.getMonth() + 1);
        var day = String(date.getDate());
        return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
    }

    /** Cộng thêm `days` ngày vào một giá trị ngày, trả về YYYY-MM-DD */
    function addDaysIso(value, days) {
        var date = dateOnly(value);
        if (!date) {
            return '';
        }
        date.setDate(date.getDate() + days);
        var month = String(date.getMonth() + 1);
        var day = String(date.getDate());
        return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
    }

    // ============================================================
    // 3. ROLE / PERMISSION
    // ============================================================

    /**
     * Kiểm tra currentUser có role chỉ định không.
     * Hỗ trợ cả field role đơn lẫn mảng roles (string hoặc object).
     */
    function hasRole(role) {
        if (!currentUser) {
            return false;
        }
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

    /**
     * Kiểm tra user hiện tại có phải owner (MANGAKA) của series này không.
     * Dùng để ẩn/hiện các nút action chỉ dành cho Mangaka.
     */
    function isOwner() {
        return hasRole('MANGAKA') && seriesData && Number(seriesData.mangakaId) === Number(currentUser.id);
    }

    // ============================================================
    // 4. API HELPERS
    // ============================================================

    /**
     * Gọi API JSON chung cho GET/POST/PUT/PATCH/DELETE.
     * - GET/PUT/PATCH: data được serialize thành query string.
     * - POST/DELETE: data được gửi trong body (form-urlencoded).
     * Throws Error nếu response không OK hoặc body.success === false.
     */
    async function callApi(method, path, data) {
        var opts = {method: method, headers: {'Accept': 'application/json'}};
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
        try {
            body = text ? JSON.parse(text) : null;
        } catch (e) {
        }
        if (!res.ok || (body && body.success === false)) {
            var msg = (body && (body.message || (body.errors && body.errors[0]))) || text || ('HTTP ' + res.status);
            throw new Error(msg);
        }
        return body;
    }

    /**
     * Upload file multipart/form-data.
     * Nhận FormData, form element, hoặc File object.
     * Throws Error nếu upload thất bại.
     */
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
        var res = await fetch(ctx + path, {method: 'POST', headers: {'Accept': 'application/json'}, body: fd});
        var text = await res.text();
        var body = null;
        try {
            body = text ? JSON.parse(text) : null;
        } catch (e) {
        }
        if (!res.ok || (body && body.success === false)) {
            var msg = (body && (body.message || (body.errors && body.errors[0]))) || text || ('HTTP ' + res.status);
            throw new Error(msg);
        }
        return body;
    }

    // ============================================================
    // 5. UI HELPERS
    // ============================================================

    /** Hiển thị / ẩn thông báo lỗi chung (#detailResult) */
    function showError(msg) {
        var el = document.getElementById('detailResult');
        el.style.display = msg ? 'block' : 'none';
        el.textContent = msg || '';
    }

    /** Format enum status thành dạng Title Case (VD: IN_PROGRESS → "In Progress") */
    function formatStatus(s) {
        return String(s || '').toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, function (c) {
            return c.toUpperCase();
        });
    }

    function formatTaskTypes(taskTypes) {
        var values = Array.isArray(taskTypes) ? taskTypes : String(taskTypes || '').split(',');
        return values.filter(Boolean).map(formatStatus).join(', ');
    }

    /** Hiển thị / ẩn lỗi trong modal upload page (#pageUploadError) */
    function showPageUploadError(message) {
        var el = document.getElementById('pageUploadError');
        if (!el) {
            return;
        }
        el.style.display = message ? 'block' : 'none';
        el.textContent = message || '';
    }

    // ============================================================
    // 6. STAGE LOGIC
    // ============================================================

    /** Thứ tự các stage sản xuất của một page */
    var pageStageOrder = ['SKETCHING', 'INKING', 'COLORING', 'SCREENTONE', 'LETTERING'];

    /** Normalize stage string về giá trị hợp lệ trong pageStageOrder, hoặc '' nếu không hợp lệ */
    function normalizeStage(stage) {
        var s = String(stage || '').trim().toUpperCase();
        return pageStageOrder.indexOf(s) >= 0 ? s : '';
    }

    /** Lấy stage tiếp theo có thể thực hiện sau completedStage hiện tại của slot */
    function nextAllowedStage(slot) {
        var current = normalizeStage(slot && slot.completedStage);
        if (!current) {
            return pageStageOrder[0];
        }
        var idx = pageStageOrder.indexOf(current);
        return pageStageOrder[Math.min(idx + 1, pageStageOrder.length - 1)];
    }

    /**
     * Khởi tạo stage picker checkbox trong modal upload.
     * Các stage đã hoàn thành sẽ được checked và disabled.
     */
    function prepareStageSelect(slot) {
        var picker = document.getElementById('pageUploadStagePicker');
        if (!picker) {
            return;
        }
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

    /**
     * Cập nhật trạng thái enabled/disabled của từng checkbox stage.
     * Quy tắc: chỉ cho phép tick stage kế tiếp sau stage cao nhất đã tick.
     */
    function refreshStagePickerEnabled() {
        var picker = document.getElementById('pageUploadStagePicker');
        if (!picker) {
            return;
        }
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

    /**
     * Đọc stage cao nhất đang được chọn trong picker.
     * Throws nếu user tick không theo thứ tự (bỏ sót stage giữa chừng).
     * Trả về tên stage cao nhất, hoặc '' nếu chưa chọn gì.
     */
    function selectedUploadStage(slot) {
        var picker = document.getElementById('pageUploadStagePicker');
        if (!picker) {
            return '';
        }
        var boxes = picker.querySelectorAll('input[type="checkbox"]');
        var highest = -1;
        for (var i = 0; i < boxes.length; i++) {
            if (boxes[i].checked) {
                highest = Math.max(highest, pageStageOrder.indexOf(normalizeStage(boxes[i].value)));
            }
        }
        for (var j = 0; j <= highest; j++) {
            if (!boxes[j].checked) {
                throw new Error('Stages must be checked in order, starting from Sketching.');
            }
        }
        return highest >= 0 ? pageStageOrder[highest] : '';
    }

    /**
     * Xử lý khi user click vào một checkbox stage.
     * Auto-check tất cả stage phía trước (nếu check lên),
     * hoặc auto-uncheck tất cả stage phía sau (nếu uncheck).
     */
    function syncStagePickerFromClick(changedBox) {
        var picker = document.getElementById('pageUploadStagePicker');
        if (!picker || !changedBox) {
            return;
        }
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

    /**
     * Render thanh tiến trình stage (dot track) cho một page slot.
     * Dot có class 'done' nếu đã hoàn thành, 'current' nếu đang làm.
     * Hiển thị chữ cái đầu của tên stage (S/I/C/S/L).
     */
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

    // ============================================================
    // 7. PAGE MODAL
    // ============================================================

    /**
     * Mở modal upload/replace ảnh cho một page slot.
     * - Hiển thị preview ảnh hiện tại nếu có.
     * - Chuẩn bị stage picker dựa trên completedStage của slot.
     * - Nút Delete chỉ hiện cho owner.
     */
    function openPageUploadModal(slot) {
        if (!slot) {
            return;
        }
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

    // ============================================================
    // 8. STATUS CSS
    // ============================================================

    /** Trả về CSS class tương ứng với trạng thái chapter */
    function chapterStatusClass(status) {
        status = String(status || '').toUpperCase();
        if (status === 'PLANNING') {
            return 'status-draft';
        }
        if (status === 'IN_PROGRESS') {
            return 'status-progress';
        }
        if (status === 'COMPLETE') {
            return 'status-approved';
        }
        if (status === 'EDITORIAL_REVIEW') {
            return 'status-review';
        }
        if (status === 'APPROVED') {
            return 'status-approved';
        }
        if (status === 'REJECTED') {
            return 'status-rejected';
        }
        return 'status-draft';
    }

    /** Trả về CSS class tương ứng với trạng thái task */
    function taskStatusClass(status) {
        status = String(status || '').toUpperCase();
        if (status === 'OVERDUE') {
            return 'status-overdue';
        }
        if (status === 'IN_PROGRESS') {
            return 'status-progress';
        }
        if (status === 'PENDING') {
            return 'status-pending';
        }
        if (status === 'SUBMITTED') {
            return 'status-review';
        }
        if (status === 'APPROVED') {
            return 'status-approved';
        }
        if (status === 'REJECTED') {
            return 'status-rejected';
        }
        if (status === 'DELETED') {
            return 'status-rejected';
        }
        if (status === 'REASSIGNED') {
            return 'status-pending';
        }
        return 'status-draft';
    }

    // ============================================================
    // 9. DEADLINE HELPERS
    // ============================================================

    /** Chapter được coi là xong nếu status COMPLETE/APPROVED hoặc completionPct >= 100 */
    function isChapterDone(ch) {
        var st = String(ch.status || '').toUpperCase();
        return st === 'COMPLETE' || st === 'APPROVED' || Number(ch.completionPct || 0) >= 100;
    }

    /** Chapter bị overdue nếu chưa xong và đã qua submissionDeadline */
    function isChapterOverdue(ch) {
        if (isChapterDone(ch)) {
            return false;
        }
        var daysLeft = daysUntilDate(ch.submissionDeadline);
        return daysLeft !== null && daysLeft < 0;
    }

    /**
     * Tạo text suffix cho deadline (VD: "3 days left", "1 day overdue", "Done").
     * Dùng trong formatDeadlineCell.
     */
    function deadlineSuffixText(daysLeft, isDone, isOverdue) {
        if (isDone) {
            return 'Done';
        }
        if (isOverdue) {
            if (daysLeft !== null && daysLeft < 0) {
                var n = Math.abs(daysLeft);
                return n === 1 ? '1 day overdue' : (n + ' days overdue');
            }
            return 'Overdue';
        }
        if (daysLeft === null) {
            return '';
        }
        if (daysLeft === 0) {
            return 'Due today';
        }
        if (daysLeft === 1) {
            return '1 day left';
        }
        return daysLeft + ' days left';
    }

    /**
     * Render HTML cho cell deadline với màu sắc theo trạng thái:
     * - done: xanh lá (due-date-done)
     * - overdue: đỏ với icon cảnh báo (due-date-overdue)
     * - urgent (≤3 ngày): cam (due-date-urgent)
     * - bình thường: xanh dương (due-date-active)
     */
    function formatDeadlineCell(dateValue, isDone, isOverdue) {
        var formatted = formatDate(dateValue);
        if (!formatted) {
            return '<span class="chapter-detail-inline-66">—</span>';
        }
        var daysLeft = daysUntilDate(dateValue);
        if (!isDone && !isOverdue && daysLeft !== null && daysLeft < 0) {
            isOverdue = true;
        }
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

    // ============================================================
    // 10. IMAGE URL
    // ============================================================

    /**
     * Chuẩn hóa URL ảnh: nếu là absolute URL giữ nguyên,
     * nếu là relative path thì thêm ctx prefix.
     */
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

    // ============================================================
    // 11. PAGE SELECTION
    // ============================================================

    /** Tìm page slot theo ID trong mảng pageSlots */
    function findPageById(id) {
        for (var i = 0; i < pageSlots.length; i++) {
            if (Number(pageSlots[i].id) === Number(id)) {
                return pageSlots[i];
            }
        }
        return null;
    }

    /**
     * Lấy danh sách page đang được chọn (selectedPageIds),
     * lọc chỉ lấy các page có thể gán task, sắp xếp theo pageNumber.
     */
    function getSelectedPages() {
        var ids = Object.keys(selectedPageIds);
        var out = [];
        for (var i = 0; i < ids.length; i++) {
            var p = findPageById(ids[i]);
            if (p && isAssignablePage(p)) {
                out.push(p);
            }
        }
        out.sort(function (a, b) {
            return a.pageNumber - b.pageNumber;
        });
        return out;
    }

    /** Page hoàn chỉnh khi đã đạt stage LETTERING */
    function isPageFullyComplete(slot) {
        return normalizeStage(slot && slot.completedStage) === 'LETTERING';
    }

    /**
     * Page có thể gán task khi:
     * - Chưa được gán task nào (slot.taskId falsy)
     * - Chưa hoàn chỉnh (chưa LETTERING)
     */
    function isAssignablePage(slot) {
        return !!slot && !slot.taskId && !isPageFullyComplete(slot);
    }

    /** Toggle chọn/bỏ chọn page. Chỉ hoạt động với page có thể gán task. */
    function toggleSelectedPage(pageId, slot) {
        if (!isAssignablePage(slot)) {
            return;
        }
        if (selectedPageIds[String(pageId)]) {
            delete selectedPageIds[String(pageId)];
        } else {
            selectedPageIds[String(pageId)] = true;
        }
    }

    /** Đếm số page đã có ảnh upload */
    function countUploaded() {
        var n = 0;
        for (var i = 0; i < pageSlots.length; i++) {
            if (pageSlots[i].imageUrl) {
                n++;
            }
        }
        return n;
    }

    /**
     * Tính điểm tiến độ của một page dựa trên completedStage.
     * SKETCHING=1, INKING=2, COLORING=3, SCREENTONE=4, LETTERING=5, chưa có=0.
     */
    function pageStageScore(slot) {
        var stage = normalizeStage(slot && slot.completedStage);
        return stage ? pageStageOrder.indexOf(stage) + 1 : 0;
    }

    /**
     * Tính % hoàn thành của chapter dựa trên tổng điểm stage của tất cả page.
     * Công thức: sum(stageScore) * 100 / (totalPages * 5)
     */
    function pageCompletionPercent() {
        if (!pageSlots.length) {
            return 0;
        }
        var completedUnits = 0;
        for (var i = 0; i < pageSlots.length; i++) {
            completedUnits += pageStageScore(pageSlots[i]);
        }
        return Math.round((completedUnits * 100) / (pageSlots.length * pageStageOrder.length));
    }

    /** Đếm số page đã đạt LETTERING (hoàn chỉnh) */
    function countFullyCompletePages() {
        var n = 0;
        for (var i = 0; i < pageSlots.length; i++) {
            if (isPageFullyComplete(pageSlots[i])) {
                n++;
            }
        }
        return n;
    }

    /** Trả về CSS class cho page slot: 'state-uploaded' hoặc 'state-empty' */
    function slotStateClass(slot) {
        if (String(slot.status || '').toUpperCase() === 'UPLOADED' || slot.imageUrl) {
            return 'state-uploaded';
        }
        return 'state-empty';
    }

    // ============================================================
    // 12. RENDER
    // ============================================================

    /** Hiển thị/ẩn thanh selection bar phía trên page grid khi có page được chọn */
    function renderSelectionBar() {
        var selected = getSelectedPages();
        var bar = document.getElementById('selectionBar');
        if (!selected.length) {
            bar.classList.remove('visible');
            return;
        }
        bar.classList.add('visible');
        document.getElementById('selectionLabel').textContent = selected.length + ' pages selected ('
                + selected[0].pageNumber + (selected.length > 1 ? '–' + selected[selected.length - 1].pageNumber : '') + ')';
    }

    /**
     * Render chip list các page đã chọn trong modal gán task.
     * Disable nút submit nếu chưa chọn page nào.
     */
    function renderAssignChips() {
        var el = document.getElementById('assignPageChips');
        var selected = getSelectedPages();
        if (!selected.length) {
            el.innerHTML = '<span class="section-desc chapter-detail-inline-67">No pages selected — select from the Pages grid before assigning.</span>';
            document.getElementById('assignTaskSubmit').disabled = true;
            return;
        }
        document.getElementById('assignTaskSubmit').disabled = false;
        el.innerHTML = selected.map(function (p) {
            return '<span class="assign-chip">Page ' + p.pageNumber + '</span>';
        }).join('');
    }

    /**
     * Tính taskType mặc định cho nhóm page được chọn.
     * Nếu tất cả page cùng next stage → trả về stage đó.
     * Nếu mixed (các page ở stage khác nhau) → trả về 'MIXED'.
     */
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

    /** Cập nhật phần tóm tắt taskType trong modal gán task */
    function setDefaultAssignTaskType() {
        var summary = document.getElementById('assignTaskTypeSummary');
        if (!summary) {
            return;
        }
        var selected = getSelectedPages();
        if (!selected.length) {
            summary.textContent = 'Automatically calculated based on each page\'s next stage.';
            return;
        }
        summary.innerHTML = selected.map(function (p) {
            var stage = normalizeStage(p.completedStage);
            var nextIndex = stage ? Math.min(pageStageOrder.indexOf(stage) + 1, pageStageOrder.length - 1) : 0;
            return '<span class="assign-chip">Page ' + p.pageNumber + ': ' + escapeHtml(formatStatus(pageStageOrder[nextIndex])) + '</span>';
        }).join('');
    }

    /**
     * Render toàn bộ page grid.
     * Mỗi slot hiển thị: thumbnail, page number, lock icon (nếu có task),
     * icon trạng thái task (IN_PROGRESS/SUBMITTED), stage track dots,
     * avatar initials của assistant (nếu task chưa approved).
     */
    function renderPageGrid() {
        var grid = document.getElementById('pageSlotGrid');
        var owner = isOwner();
        if (!pageSlots.length) {
            grid.innerHTML = '<p class="chapter-detail-inline-68">No page slots yet. '
                    + (owner ? 'Click + Add page to get started.' : 'No page slots yet.') + '</p>'
                    + (owner ? '<div class="page-slot page-slot-add chapter-detail-inline-69" data-add-page="1" title="Add page">+</div>' : '');
            return;
        }

        var html = pageSlots.map(function (slot, index) {
            var selectable = isAssignablePage(slot);
            // Xóa khỏi selection nếu page không còn gán được
            if (!selectable && selectedPageIds[String(slot.id)]) {
                delete selectedPageIds[String(slot.id)];
            }
            var selected = selectable && !!selectedPageIds[String(slot.id)];
            var state = slotStateClass(slot);
            var inProgressTaskCls = String(slot.taskStatus || '').toUpperCase() === 'IN_PROGRESS' ? ' task-in-progress' : '';
            var inProgressTaskIcon = inProgressTaskCls
                    ? '<span class="page-slot-status-icon icon-in-progress">●<span class="icon-tooltip">In progress</span></span>'
                    : '';
            var submittedTaskCls = String(slot.taskStatus || '').toUpperCase() === 'SUBMITTED' ? ' task-submitted' : '';
            var submittedTaskIcon = submittedTaskCls
                    ? '<span class="page-slot-status-icon icon-submitted">●<span class="icon-tooltip">Submitted</span></span>'
                    : '';
            var completeStageCls = normalizeStage(slot.completedStage) === 'LETTERING' ? ' stage-complete' : '';
            var cls = 'page-slot ' + state + inProgressTaskCls + submittedTaskCls + completeStageCls + (selected ? ' state-selected' : '');
            var num = '<span class="page-slot-num">' + slot.pageNumber + '</span>';
            var lockIconHtml = slot.taskId ? '<span class="page-slot-lock" title="This page already has an assigned task">🔒</span>' : '';
            var inner = '';
            if (state === 'state-empty') {
                inner = '<span class="page-slot-upload-label">+ Upload</span>';
            } else if (slot.imageUrl) {
                inner = '<img src="' + escapeHtml(imageUrl(slot.imageUrl)) + '" alt="Page ' + slot.pageNumber + '" />'
                        + '<a class="page-download-btn" href="' + escapeHtml(imageUrl(slot.imageUrl)) + '" download title="Download page image" data-page-download>↓</a>';
            }
            // Hiện initials assistant nếu task chưa approved
            if (slot.taskId && slot.assistantName && String(slot.taskStatus || '').toUpperCase() !== 'APPROVED') {
                inner += '<span class="page-slot-initials" title="' + escapeHtml(slot.assistantName) + '">' + escapeHtml(initials(slot.assistantName)) + '</span>';
            }
            inner += renderStageTrack(slot.completedStage);
            return '<div class="' + cls + '" data-page-id="' + slot.id + '" data-slot-index="' + index + '" data-page-number="' + slot.pageNumber + '">' + num + lockIconHtml + inProgressTaskIcon + submittedTaskIcon + inner + '</div>';
        }).join('');

        if (owner) {
            html += '<div class="page-slot page-slot-add" data-add-page="1" title="Add page">+</div>';
        }
        grid.innerHTML = html;
        renderSelectionBar();
    }

    /** Cập nhật progress bar, label đếm page, và metadata panel */
    function renderPageProgress() {
        var total = pageSlots.length;
        var uploaded = countUploaded();
        var completePages = countFullyCompletePages();
        var pct = pageCompletionPercent();
        document.getElementById('progressLabel').textContent = pct + '% (' + completePages + ' / ' + total + ' pages complete)';
        document.getElementById('progressFill').style.width = pct + '%';
        document.getElementById('pageCountLabel').textContent = uploaded + ' / ' + total + ' uploaded';
        document.getElementById('tabPageCount').textContent = total;
        document.getElementById('metaPages').textContent = uploaded + ' / ' + total;
        document.getElementById('metaProgress').textContent = pct + '% page';
    }

    /** Render preview 5 task đầu tiên trong sidebar */
    function renderSidebarTasks() {
        var el = document.getElementById('sidebarTaskList');
        if (!chapterTasks.length) {
            el.innerHTML = '<p class="section-desc chapter-detail-inline-70">No tasks yet.</p>';
            return;
        }
        var preview = chapterTasks.slice(0, 5);
        el.innerHTML = preview.map(function (t) {
            return '<div class="sidebar-task-mini">'
                    + '<strong>#' + t.id + '</strong> p.' + t.pageRangeStart + '-' + t.pageRangeEnd
                    + ' · ' + escapeHtml(formatTaskTypes(t.taskTypes))
                    + '<br/><span class="status-chip ' + taskStatusClass(t.status) + ' chapter-detail-inline-71">' + formatStatus(t.status) + '</span>'
                    + '</div>';
        }).join('')
                + (chapterTasks.length > 5 ? '<p class="section-desc chapter-detail-inline-72">+' + (chapterTasks.length - 5) + ' more tasks — see the Tasks tab</p>' : '');
    }

    /**
     * Cập nhật nút btnManuscriptWorkspace dựa trên trạng thái workspace thực tế từ API.
     * Gọi API /api/v1/manuscript-versions/workspace?chapterId=... để lấy trạng thái,
     * sau đó hiển thị label và href phù hợp theo từng case:
     *   - workspaceExists=false              → "📝 Create Workspace"      → /create
     *   - status=DRAFT                       → "✏️ Continue Manuscript"   → /workspace/:id
     *   - status=SUBMITTED_FOR_REVIEW        → "📤 View Submitted..."     → /workspace/:id
     *   - status=UNDER_REVIEW                → "👀 View Under Review..."  → /workspace/:id
     *   - status=APPROVED                    → "✅ View Approved..."      → /workspace/:id
     *   - status=REJECTED                    → "🔄 Create New Version"    → /new-version
     *   - Lỗi API                            → ẩn nút
     */
    async function updateManuscriptWorkspaceButton() {
        if (!chapter) {
            return;
        }

        var btnManuscriptWorkspace = document.getElementById('btnManuscriptWorkspace');
        if (!btnManuscriptWorkspace) {
            return;
        }

        try {
            var response = await callApi('GET', '/api/v1/manuscript-versions/workspace?chapterId=' + chapter.id);
            var workspace = response.data;

            if (!workspace.workspaceExists) {
                // Case 1: Chưa có manuscript version nào → tạo mới
                btnManuscriptWorkspace.style.display = '';
                btnManuscriptWorkspace.textContent = '📝 Create Workspace';
                btnManuscriptWorkspace.href = ctx + '/main/chapters/' + chapter.id + '/manuscript-workspace/create';
            } else {
                // Case 2–6: Workspace đã tồn tại → xác định label theo status
                var status = String(workspace.status || '').toUpperCase();
                var workspaceId = workspace.workspaceId;

                btnManuscriptWorkspace.style.display = '';

                if (status === 'DRAFT') {
                    btnManuscriptWorkspace.textContent = '✏️ Continue Manuscript';
                    btnManuscriptWorkspace.href = ctx + '/main/manuscript-workspace/' + workspaceId;
                } else if (status === 'SUBMITTED_FOR_REVIEW') {
                    btnManuscriptWorkspace.textContent = '📤 View Submitted Manuscript';
                    btnManuscriptWorkspace.href = ctx + '/main/manuscript-workspace/' + workspaceId;
                } else if (status === 'UNDER_REVIEW') {
                    btnManuscriptWorkspace.textContent = '👀 View Under Review Manuscript';
                    btnManuscriptWorkspace.href = ctx + '/main/manuscript-workspace/' + workspaceId;
                } else if (status === 'APPROVED') {
                    btnManuscriptWorkspace.textContent = '✅ View Approved Manuscript';
                    btnManuscriptWorkspace.href = ctx + '/main/manuscript-workspace/' + workspaceId;
                } else if (status === 'REJECTED') {

                    // Authorization: Only the chapter owner (MANGAKA) can create new version
                    if (isOwner()) {
                        btnManuscriptWorkspace.textContent =
                                '🔄 Create New Version';

                        btnManuscriptWorkspace.removeAttribute('href');

                        btnManuscriptWorkspace.onclick = async function (e) {

                            e.preventDefault();

                            const response = await fetch(
                                    ctx
                                    + '/main/chapters/'
                                    + chapter.id
                                    + '/manuscript-workspace/new-version',
                                    {
                                        method: 'POST'
                                    }
                            );

                            if (response.redirected) {

                                window.location.href = response.url;
                            }
                        };
                    } else {
                        // Non-owners cannot create new version - show view instead
                        btnManuscriptWorkspace.textContent = '👀 View Rejected Manuscript';
                        btnManuscriptWorkspace.href = ctx + '/main/manuscript-workspace/' + workspaceId;
                    }
                } else {
                    // Default: fallback về view workspace
                    btnManuscriptWorkspace.textContent = '📝 Manuscript Workspace';
                    btnManuscriptWorkspace.href = ctx + '/main/manuscript-workspace/' + workspaceId;
                }
            }
        } catch (error) {
            // Nếu API lỗi → ẩn nút để tránh hiển thị trạng thái sai
            console.error('Failed to load workspace status:', error);
            btnManuscriptWorkspace.style.display = 'none';
        }
    }

    /**
     * Cập nhật toàn bộ phần metadata (breadcrumb, title, deadline, status chips,
     * nút action, assign due constraints, page progress).
     * Nút btnManuscriptWorkspace được cập nhật async qua updateManuscriptWorkspaceButton().
     */
    function renderMeta() {
        if (!chapter) {
            return;
        }
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
        // canSubmit: owner, 100%, status IN_PROGRESS/COMPLETE, series chưa CANCELLED
        var canSubmit = owner && progress >= 100 && (chapterStatus === 'IN_PROGRESS' || chapterStatus === 'COMPLETE')
                && seriesData && String(seriesData.status || '').toUpperCase() !== 'CANCELLED';

        document.getElementById('btnDelete').style.display = (owner && chapterStatus === 'PLANNING') ? '' : 'none';
        document.getElementById('btnMarkDone').style.display = canSubmit ? '' : 'none';

        // Cập nhật nút Manuscript Workspace async (gọi API để lấy trạng thái thực tế)
        updateManuscriptWorkspaceButton();

        document.getElementById('pagesOwnerActions').style.display = owner ? 'flex' : 'none';
        document.getElementById('pagesOwnerActions').style.gap = '8px';
        document.getElementById('pagesHint').style.display = owner ? '' : 'none';

        updateAssignDueConstraints();
        renderPageProgress();
    }

    // ============================================================
    // 13. TASK HELPERS
    // ============================================================

    /** Tìm task theo taskId trong mảng chapterTasks */
    function findTask(taskId) {
        for (var i = 0; i < chapterTasks.length; i++) {
            if (Number(chapterTasks[i].id) === Number(taskId)) {
                return chapterTasks[i];
            }
        }
        return null;
    }

    /** Tìm task chứa pageNumber trong pageRange (dùng để hiển thị thông tin task của page) */
    function findTaskByPageNumber(pageNumber) {
        for (var i = 0; i < chapterTasks.length; i++) {
            var t = chapterTasks[i];
            if (Number(pageNumber) >= Number(t.pageRangeStart) && Number(pageNumber) <= Number(t.pageRangeEnd)) {
                return t;
            }
        }
        return null;
    }

    /** Task overdue nếu status là OVERDUE, hoặc chưa APPROVED và đã qua dueDate */
    function isTaskOverdue(task) {
        var st = String(task.status || '').toUpperCase();
        if (st === 'APPROVED') {
            return false;
        }
        if (st === 'OVERDUE') {
            return true;
        }
        if (!task.dueDate) {
            return false;
        }
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        var due = dateOnly(task.dueDate);
        return due && due < today;
    }

    /** Render cell deadline của task với màu sắc phù hợp */
    function formatDueDateCell(task) {
        var formatted = formatDate(task.dueDate);
        if (!formatted) {
            return '—';
        }
        var done = String(task.status || '').toUpperCase() === 'APPROVED';
        var overdue = isTaskOverdue(task);
        return formatDeadlineCell(task.dueDate, done, overdue);
    }

    /**
     * Render nút action cho một hàng task trong bảng.
     * - Tất cả task: nút expand ▼ Trang để xem ảnh inline.
     * - IN_PROGRESS (owner): Reassign, Delete.
     * - OVERDUE (owner): Decide (mở modal overdue decision).
     * - SUBMITTED (owner): Approve popover, Reject popover.
     * - Sau khi approve/reject: hiển thị label tương ứng.
     */
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

    /**
     * Render toàn bộ bảng task (tab Tasks).
     * Mỗi task có 2 row: row chính + row inline ẩn để hiển thị ảnh khi expand.
     */
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
                    + '<td>' + formatTaskTypes(task.taskTypes) + '</td>'
                    + '<td>' + escapeHtml(task.assistantName || '') + '</td>'
                    + '<td><span class="status-chip ' + taskStatusClass(task.status) + '">' + formatStatus(task.status) + '</span></td>'
                    + '<td>' + formatDueDateCell(task) + '</td>'
                    + '<td class="task-actions-cell"><div class="task-row-actions">' + renderTaskRowActions(task) + '</div></td>'
                    + '</tr>'
                    + '<tr class="task-inline-row chapter-detail-inline-73" id="task-inline-' + task.id + '">'
                    + '<td colspan="7"><div class="task-inline-body" id="task-inline-body-' + task.id + '">Loading...</div></td>'
                    + '</tr>';
        }).join('');
        renderSidebarTasks();
    }

    // ============================================================
    // 14. TASK INLINE / COMPARE
    // ============================================================

    /**
     * Load và render ảnh của task vào row inline (expand ▼ Trang).
     * Dùng cache taskImagesCache để tránh gọi API nhiều lần.
     * Hiển thị thumbnail mỗi page trong task range; ô trống nếu chưa có ảnh.
     */
    async function loadTaskInlinePages(taskId) {
        var task = findTask(taskId);
        if (!task) {
            return;
        }
        var bodyEl = document.getElementById('task-inline-body-' + taskId);
        if (!bodyEl) {
            return;
        }
        if (!taskInlineLoaded[taskId]) {
            bodyEl.innerHTML = '<span class="chapter-detail-inline-74">Loading...</span>';
            try {
                var res = await callApi('GET', '/api/v1/tasks/' + taskId + '/images');
                var imgs = res.data || res || [];
                var imgMap = {};
                imgs.forEach(function (img) {
                    imgMap[img.pageNumber] = img;
                });
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
                    html += '<div>Page ' + p + '</div></div>';
                }
                bodyEl.innerHTML = html || '<span class="chapter-detail-inline-75">No images yet.</span>';
                taskInlineLoaded[taskId] = true;
            } catch (e) {
                bodyEl.innerHTML = '<span class="chapter-detail-inline-76">' + escapeHtml(e.message) + '</span>';
            }
        }
    }

    /**
     * Mở modal so sánh ảnh của một page slot.
     * Logic hiển thị tùy theo trạng thái task của page:
     * - Không có task / task chưa SUBMITTED: chỉ hiện ảnh gốc (+ nút Upload nếu owner).
     * - Task SUBMITTED: 2 cột so sánh ảnh gốc Mangaka vs ảnh assistant nộp.
     * - Task APPROVED: hiện ảnh đã duyệt với badge ✓.
     */
    async function openPageCompare(slot) {
        var modal = document.getElementById('pageCompareModal');
        var title = document.getElementById('pageCompareTitle');
        var body = document.getElementById('pageCompareBody');
        modal.style.display = 'flex';
        title.innerHTML = 'Page ' + slot.pageNumber
                + ' <button class="btn small" type="button" id="pageCompareHistory">History</button>';
        var histBtn = document.getElementById('pageCompareHistory');
        if (histBtn) {
            histBtn.addEventListener('click', function () {
                modal.style.display = 'none';
                openPageHistory(slot);
            });
        }
        var ts = String(slot.taskStatus || '').toUpperCase();
        var origUrl = slot.imageUrl ? imageUrl(slot.imageUrl) : null;
        if (!slot.taskId || (ts !== 'SUBMITTED' && ts !== 'APPROVED')) {
            body.innerHTML = origUrl
                    ? (isOwner() && !slot.taskId ? '<div class="chapter-detail-inline-77"><button class="btn small primary" type="button" id="pageCompareEdit">Upload / replace</button></div>' : '')
                    + '<img src="' + escapeHtml(origUrl) + '" class="chapter-detail-inline-78" />'
                    : '<div class="chapter-detail-inline-79">No image</div>';
            var editBtn = document.getElementById('pageCompareEdit');
            if (editBtn) {
                editBtn.addEventListener('click', function () {
                    modal.style.display = 'none';
                    openPageUploadModal(slot);
                });
            }
            return;
        }
        // Cần ảnh task: dùng cache hoặc gọi API
        var taskImgs = taskImagesCache[slot.taskId];
        if (!taskImgs) {
            body.innerHTML = '<div class="chapter-detail-inline-80">Loading image...</div>';
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
            // 2-column compare: ảnh gốc vs ảnh assistant nộp
            body.innerHTML =
                    '<div class="chapter-detail-inline-81">'
                    + '<div><div class="chapter-detail-inline-82">Original (Mangaka)</div>'
                    + (origUrl ? '<img src="' + escapeHtml(origUrl) + '" class="chapter-detail-inline-83" />' : '<div class="chapter-detail-inline-84">No original image</div>')
                    + '</div>'
                    + '<div><div class="chapter-detail-inline-85">Assistant\'s submission</div>'
                    + (assistantUrl ? '<img src="' + escapeHtml(assistantUrl) + '" class="chapter-detail-inline-86" />' : '<div class="chapter-detail-inline-87">No image</div>')
                    + '</div></div>';
            return;
        }
        // APPROVED: hiện ảnh đã duyệt
        var finalUrl = assistantUrl || origUrl;
        body.innerHTML = finalUrl
                ? '<div class="chapter-detail-inline-88"><span class="chapter-detail-inline-89">✓ Approved</span></div>'
                + '<img src="' + escapeHtml(finalUrl) + '" class="chapter-detail-inline-90" />'
                : '<div class="chapter-detail-inline-91">No image</div>';
    }

    /**
     * Mở modal lịch sử ảnh + stage của 1 page.
     * Timeline mới-nhất-trước; mỗi mốc (trừ mốc hiện tại) có nút Rollback nếu là Mangaka chủ chapter.
     */
    async function openPageHistory(slot) {
        var modal = document.getElementById('pageHistoryModal');
        var title = document.getElementById('pageHistoryTitle');
        var body = document.getElementById('pageHistoryBody');
        modal.style.display = 'flex';
        title.textContent = 'Page ' + slot.pageNumber + ' — History';
        body.innerHTML = '<div class="chapter-detail-inline-80">Loading history...</div>';

        var revisions;
        try {
            var res = await callApi('GET', '/api/v1/pages/' + slot.id + '/history');
            revisions = (res && res.data) || [];
        } catch (e) {
            body.innerHTML = '<div class="alert error">' + escapeHtml(e.message) + '</div>';
            return;
        }

        if (!revisions.length) {
            body.innerHTML = '<p class="page-history-empty">No history yet. Changes made from now on will appear here.</p>';
            return;
        }

        var owner = isOwner();
        var html = revisions.map(function (rev, index) {
            var stageLabel = rev.completedStage
                    ? '<span class="page-history-stage">' + escapeHtml(formatStatus(rev.completedStage)) + '</span>'
                    : '';
            var thumb = rev.imageUrl
                    ? '<a href="' + escapeHtml(imageUrl(rev.imageUrl)) + '" target="_blank" rel="noopener">'
                        + '<img src="' + escapeHtml(imageUrl(rev.imageUrl)) + '" class="page-history-thumb" alt="revision" /></a>'
                    : '<div class="page-history-thumb page-history-thumb-empty">No image</div>';
            // Mốc đầu list (index 0) là trạng thái hiện tại -> không cần rollback
            var rollbackBtn = (owner && index > 0)
                    ? '<button class="btn small" type="button" data-rollback-revision="' + rev.id + '" data-page-id="' + slot.id + '">Rollback to this</button>'
                    : (index === 0 ? '<span class="page-history-current">Current</span>' : '');
            return '<div class="page-history-entry">'
                    + thumb
                    + '<div class="page-history-meta">'
                    + stageLabel
                    + '<span class="page-history-source">' + escapeHtml(formatStatus(rev.source)) + '</span>'
                    + '<span class="page-history-when">'
                    + (rev.changedByName ? escapeHtml(rev.changedByName) + ' · ' : '')
                    + escapeHtml(rev.changedAt || '') + '</span>'
                    + rollbackBtn
                    + '</div>'
                    + '</div>';
        }).join('');
        body.innerHTML = '<div class="page-history-list">' + html + '</div>';
    }

    /** Xử lý click nút Rollback trong modal lịch sử. */
    async function handleRollback(pageId, revisionId) {
        if (!window.confirm('Rollback this page to the selected version? Current image and stage will be replaced.')) {
            return;
        }
        try {
            await callApi('POST', '/api/v1/pages/' + pageId + '/rollback', {revisionId: revisionId});
        } catch (e) {
            alert(e.message);
            return;
        }
        document.getElementById('pageHistoryModal').style.display = 'none';
        await loadPages();
    }

    // ============================================================
    // 15. MODAL / POPOVER
    // ============================================================

    /** Chuyển tab (pages / tasks / edit), ẩn/hiện các panel tương ứng */
    function switchTab(tab) {
        document.querySelectorAll('.chapter-tab-btn').forEach(function (b) {
            b.classList.toggle('active', b.getAttribute('data-tab') === tab);
        });
        document.getElementById('tabPages').style.display = tab === 'pages' ? '' : 'none';
        document.getElementById('tabTasks').style.display = tab === 'tasks' ? '' : 'none';
        document.getElementById('tabEdit').style.display = tab === 'edit' ? '' : 'none';
    }

    /** Mở modal theo id bằng cách thêm class 'open' */
    function openModal(id) {
        var modal = document.getElementById(id);
        if (modal) {
            modal.classList.add('open');
            modal.setAttribute('aria-hidden', 'false');
        }
    }

    /**
     * Đóng tất cả modal, reset pending upload state và overdue state.
     * Gọi khi click backdrop hoặc nút [data-modal-close].
     */
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

    /**
     * Đóng tất cả approve/reject popover, trả các element về host container.
     * Reset activePopover* state.
     */
    function closePopovers() {
        var host = document.getElementById('taskPopoverHost');
        var scrim = document.getElementById('taskPopoverScrim');
        var approvePop = document.getElementById('taskApprovePopover');
        var rejectPop = document.getElementById('taskRejectPopover');
        if (scrim) {
            scrim.classList.remove('open');
            scrim.setAttribute('aria-hidden', 'true');
            if (host) {
                host.appendChild(scrim);
            }
        }
        if (approvePop) {
            approvePop.classList.remove('open');
            approvePop.setAttribute('aria-hidden', 'true');
            if (host) {
                host.appendChild(approvePop);
            }
        }
        if (rejectPop) {
            rejectPop.classList.remove('open');
            rejectPop.setAttribute('aria-hidden', 'true');
            if (host) {
                host.appendChild(rejectPop);
            }
        }
        activePopoverType = null;
        activePopoverTaskId = null;
        activePopoverCell = null;
    }

    /**
     * Mở approve hoặc reject popover cho một task.
     * Popover được append vào body để tránh overflow clip.
     */
    function openPopover(type, taskId, anchorCell) {
        closePopovers();
        var task = findTask(taskId);
        if (!task) {
            return;
        }
        var scrim = document.getElementById('taskPopoverScrim');
        var popId = type === 'approve' ? 'taskApprovePopover' : 'taskRejectPopover';
        var pop = document.getElementById(popId);
        if (!pop) {
            return;
        }
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

    /** Validate và cập nhật trạng thái nút Confirm trong reject popover (tối thiểu 5 ký tự) */
    function updateRejectConfirmState() {
        var reasonEl = document.getElementById('rejectPopoverReason');
        var counterEl = document.getElementById('rejectPopoverCounter');
        var confirmBtn = document.getElementById('rejectPopoverConfirm');
        if (!reasonEl || !confirmBtn) {
            return;
        }
        var len = reasonEl.value.length;
        if (counterEl) {
            counterEl.textContent = len + ' / 300';
        }
        confirmBtn.disabled = len < 5;
    }

    /** Mở modal gán task, refresh chip list và task type summary */
    function openAssignModal() {
        renderAssignChips();
        setDefaultAssignTaskType();
        var err = document.getElementById('assignTaskError');
        err.style.display = 'none';
        err.textContent = '';
        openModal('assignTaskModal');
    }

    /**
     * Mở modal quyết định cho task overdue.
     * Có 3 lựa chọn: Extend deadline, Reassign, Delete task.
     * Khởi tạo các input với ràng buộc ngày hợp lệ.
     */
    function openOverdueDecisionModal(taskId) {
        var task = findTask(taskId);
        if (!task) {
            return;
        }
        activeOverdueTaskId = taskId;
        document.getElementById('taskExtendId').value = taskId;
        document.getElementById('taskOverdueDecisionTitle').textContent = 'Overdue task #' + taskId;
        document.getElementById('taskOverdueDecisionSummary').textContent =
                'Pages ' + task.pageRangeStart + '-' + task.pageRangeEnd + ' · '
                + formatTaskTypes(task.taskTypes) + ' · assigned to ' + (task.assistantName || ('#' + task.assistantId));

        var latest = latestTaskDueDate();
        var dueInput = document.getElementById('taskExtendDueDate');
        dueInput.value = '';
        dueInput.min = todayIso();
        dueInput.removeAttribute('max');
        if (latest) {
            dueInput.max = latest;
        }
        document.getElementById('taskExtendHint').textContent = chapter && chapter.submissionDeadline
                ? ('Chapter deadline: ' + formatDate(chapter.submissionDeadline) + '. Extension must be today through ' + (latest || formatDate(chapter.submissionDeadline)) + '.')
                : 'Extension date cannot be in the past.';

        // Reset tất cả input trong modal
        document.getElementById('taskExtendReason').value = '';
        document.getElementById('taskOverdueReason').value = '';
        document.getElementById('taskOverdueDeleteReason').value = '';
        document.getElementById('taskOverdueReassignAssistantId').value = '';
        var reassignDueInput = document.getElementById('taskOverdueReassignDueDate');
        reassignDueInput.value = '';
        reassignDueInput.min = todayIso();
        reassignDueInput.removeAttribute('max');
        if (latest) {
            reassignDueInput.max = latest;
        }
        document.getElementById('taskExtendError').style.display = 'none';
        document.getElementById('taskExtendError').textContent = '';
        document.getElementById('taskOverdueDecisionError').style.display = 'none';
        document.getElementById('taskOverdueDecisionError').textContent = '';
        document.getElementById('taskOverdueDeleteError').style.display = 'none';
        document.getElementById('taskOverdueDeleteError').textContent = '';
        setOverdueDecisionChoice('');
        openModal('taskOverdueDecisionModal');
    }

    /**
     * Chuyển panel hiển thị trong modal overdue decision.
     * choice: '' | 'extend' | 'reassign' | 'delete'
     */
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

    // ============================================================
    // 16. ASSISTANT SELECT
    // ============================================================

    /**
     * Load danh sách assistant của series vào các select box:
     * - assignAssistantId (modal gán task)
     * - taskReassignAssistantId (modal reassign)
     * - taskOverdueReassignAssistantId (modal overdue)
     */
    async function fillAssistantSelect() {
        var select = document.getElementById('assignAssistantId');
        var reassignSelect = document.getElementById('taskReassignAssistantId');
        var overdueReassignSelect = document.getElementById('taskOverdueReassignAssistantId');
        if (!chapter || !select) {
            return;
        }
        select.innerHTML = '<option value="">Loading assistants...</option>';
        if (reassignSelect) {
            reassignSelect.innerHTML = '<option value="">Loading assistants...</option>';
        }
        if (overdueReassignSelect) {
            overdueReassignSelect.innerHTML = '<option value="">Loading assistants...</option>';
        }
        try {
            var res = await callApi('GET', '/api/v1/series/' + chapter.seriesId + '/assistants');
            var assistants = res.data || [];
            var options = '<option value="">Select Assistant</option>' + assistants.map(function (a) {
                return '<option value="' + a.id + '">#' + a.id + ' - ' + escapeHtml(a.fullName || a.username) + '</option>';
            }).join('');
            select.innerHTML = options;
            if (reassignSelect) {
                reassignSelect.innerHTML = options;
            }
            if (overdueReassignSelect) {
                overdueReassignSelect.innerHTML = options;
            }
        } catch (err) {
            select.innerHTML = '<option value="">Cannot load assistants</option>';
            if (reassignSelect) {
                reassignSelect.innerHTML = '<option value="">Cannot load assistants</option>';
            }
            if (overdueReassignSelect) {
                overdueReassignSelect.innerHTML = '<option value="">Cannot load assistants</option>';
            }
            showError(err.message);
        }
    }

    /**
     * Tính ngày due tối đa cho task: deadline chapter - 3 ngày.
     * Đảm bảo task xong trước khi chapter đến hạn submit.
     */
    function latestTaskDueDate() {
        return chapter && chapter.submissionDeadline ? addDaysIso(chapter.submissionDeadline, -3) : '';
    }

    /** Cập nhật min/max cho input assignDueDate và hint text */
    function updateAssignDueConstraints() {
        var dueInput = document.getElementById('assignDueDate');
        var hint = document.getElementById('assignDueHint');
        if (!dueInput) {
            return;
        }
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

    // ============================================================
    // 17. LOAD DATA
    // ============================================================

    /** Load/reload page slots của chapter, render grid và progress */
    async function loadPages() {
        var res = await callApi('GET', '/api/v1/chapters/' + chapterId + '/pages');
        pageSlots = res.data || [];
        renderPageGrid();
        renderPageProgress();
        renderMeta();
    }

    /** Load/reload danh sách tasks của chapter, render bảng task */
    async function loadTasks() {
        var res = await callApi('GET', '/api/v1/chapters/' + chapterId + '/tasks');
        chapterTasks = res.data || [];
        renderChapterTasks();
    }

    /**
     * Load toàn bộ dữ liệu trang:
     * 1. currentUser từ /api/v1/auth/me
     * 2. chapter detail
     * 3. series list để tìm seriesData của chapter
     * 4. Song song: pages, tasks, assistant select
     * 5. renderMeta
     */
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

    // ============================================================
    // 18. EVENT LISTENERS
    // ============================================================

    // Tab bar: click chuyển tab
    document.getElementById('tabBar').addEventListener('click', function (e) {
        var btn = e.target.closest('.chapter-tab-btn');
        if (!btn) {
            return;
        }
        switchTab(btn.getAttribute('data-tab'));
    });

    /**
     * Auto-save metadata (title + deadline) sau 700ms debounce.
     * Chỉ gọi API nếu có thay đổi thực sự so với chapter data.
     */
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
        if (!isOwner()) {
            return;
        }
        clearTimeout(metadataSaveTimer);
        metadataSaveTimer = setTimeout(saveChapterMetadata, 700); // debounce 700ms
    }

    document.getElementById('updateTitle').addEventListener('input', scheduleMetadataSave);
    document.getElementById('updateDeadline').addEventListener('change', saveChapterMetadata);

    // Xóa chapter (chỉ khi PLANNING)
    document.getElementById('btnDelete').addEventListener('click', async function () {
        if (!confirm('Delete this chapter? This cannot be undone.')) {
            return;
        }
        try {
            await callApi('DELETE', '/api/v1/chapters/' + chapterId);
            window.location.href = ctx + '/main/chapters?seriesId=' + chapter.seriesId;
        } catch (err) {
            showError(err.message);
        }
    });

    // Submit chapter sang editorial review (cần 100%)
    document.getElementById('btnMarkDone').addEventListener('click', async function () {
        try {
            await callApi('POST', '/api/v1/chapters/' + chapterId + '/submit-review');
            await loadData();
            showError('');
        } catch (err) {
            showError(err.message);
        }
    });

    // Thêm page slot mới
    document.getElementById('btnAddPage').addEventListener('click', async function () {
        try {
            await callApi('POST', '/api/v1/pages', {chapterId: chapterId});
            await loadData();
        } catch (err) {
            showError(err.message);
        }
    });

    // Stage picker checkbox: sync khi thay đổi
    document.getElementById('pageUploadStagePicker').addEventListener('change', function (e) {
        if (e.target && e.target.type === 'checkbox') {
            syncStagePickerFromClick(e.target);
        }
    });

    // Preview ảnh được chọn trong file input của modal
    document.getElementById('pageModalFileInput').addEventListener('change', function (e) {
        var file = e.target.files && e.target.files[0];
        var preview = document.getElementById('pageUploadPreview');
        if (!file || !preview) {
            return;
        }
        var reader = new FileReader();
        reader.onload = function (ev) {
            preview.innerHTML = '<img src="' + escapeHtml(ev.target.result) + '" alt="Selected page image" />';
        };
        reader.readAsDataURL(file);
    });

    // Upload nhanh (single file input ẩn, không qua modal)
    document.getElementById('singleFileInput').addEventListener('change', async function (e) {
        var file = e.target.files && e.target.files[0];
        e.target.value = '';
        if (!file || !pendingUploadPageId) {
            return;
        }
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

    // Nút Save trong modal upload page
    document.getElementById('pageUploadSave').addEventListener('click', async function () {
        if (!pendingUploadPageId || !pendingUploadSlot) {
            return;
        }
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

    // Nút Delete page trong modal upload
    document.getElementById('pageUploadDelete').addEventListener('click', async function () {
        if (!pendingUploadPageId || !pendingUploadSlot) {
            return;
        }
        if (!confirm('Delete page ' + pendingUploadSlot.pageNumber + '? This cannot be undone.')) {
            return;
        }
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

    /**
     * Click trên page grid:
     * - [data-add-page]: thêm page mới
     * - [data-page-download]: bỏ qua (download link)
     * - Shift + click: toggle selection
     * - Click vào img: mở compare modal
     * - Owner click page đã có ảnh/task: compare modal
     * - Owner click page trống chưa gán: upload modal
     */
    document.getElementById('pageSlotGrid').addEventListener('click', function (e) {
        var addBtn = e.target.closest('[data-add-page]');
        if (addBtn && isOwner()) {
            document.getElementById('btnAddPage').click();
            return;
        }
        var slotEl = e.target.closest('[data-page-id]');
        if (!slotEl) {
            return;
        }
        if (e.target.closest('[data-page-download]')) {
            return;
        }
        var pageId = slotEl.getAttribute('data-page-id');
        var index = Number(slotEl.getAttribute('data-slot-index'));
        var slot = findPageById(pageId);
        if (!slot) {
            return;
        }

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

    // Nút Clear Selection
    document.getElementById('btnClearSelection').addEventListener('click', function () {
        selectedPageIds = {};
        lastSlotIndex = -1;
        renderPageGrid();
    });

    // Nút Assign From Selection: mở modal gán task
    document.getElementById('btnAssignFromSelection').addEventListener('click', function () {
        if (!getSelectedPages().length) {
            return;
        }
        openAssignModal();
    });

    /**
     * Form gán task: gom toàn bộ page đã chọn vào một task duy nhất.
     * taskType tự động (MIXED nếu các page ở stage khác nhau).
     */
    document.getElementById('assignTaskForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        var errEl = document.getElementById('assignTaskError');
        errEl.style.display = 'none';
        var selected = getSelectedPages();
        if (!selected.length) {
            errEl.style.display = 'block';
            errEl.textContent = 'Select at least one page from the Pages grid.';
            return;
        }
        selected.sort(function (a, b) { return a.pageNumber - b.pageNumber; });
        try {
            await callApi('POST', '/api/v1/chapters/' + chapterId + '/tasks', {
                assistantId: document.getElementById('assignAssistantId').value,
                pageRangeStart: selected[0].pageNumber,
                pageRangeEnd: selected[selected.length - 1].pageNumber,
                dueDate: document.getElementById('assignDueDate').value,
                notes: document.getElementById('assignNotes').value
            });
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

    // Form reassign task: yêu cầu lý do ít nhất 5 ký tự
    document.getElementById('taskReassignForm').addEventListener('submit', async function (e) {
        e.preventDefault();
        var errEl = document.getElementById('taskReassignError');
        errEl.style.display = 'none';
        var taskId = document.getElementById('taskReassignId').value;
        var assistantId = document.getElementById('taskReassignAssistantId').value;
        var reason = document.getElementById('taskReassignReason').value.trim();
        if (reason.length < 5) {
            errEl.style.display = 'block';
            errEl.textContent = 'Reassign reason must be at least 5 characters.';
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

    // Form extend deadline task (trong modal overdue)
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

    // Nút Reassign trong modal overdue decision
    document.getElementById('taskOverdueReassignBtn').addEventListener('click', async function () {
        var errEl = document.getElementById('taskOverdueDecisionError');
        errEl.style.display = 'none';
        errEl.textContent = '';
        var assistantId = document.getElementById('taskOverdueReassignAssistantId').value;
        var newDueDate = document.getElementById('taskOverdueReassignDueDate').value;
        var reason = document.getElementById('taskOverdueReason').value.trim();
        if (!activeOverdueTaskId) {
            return;
        }
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

    // Nút Delete task trong modal overdue decision
    document.getElementById('taskOverdueDeleteBtn').addEventListener('click', async function () {
        var errEl = document.getElementById('taskOverdueDeleteError');
        errEl.style.display = 'none';
        errEl.textContent = '';
        var reason = document.getElementById('taskOverdueDeleteReason').value.trim();
        if (!activeOverdueTaskId) {
            return;
        }
        if (reason.length < 5) {
            errEl.style.display = 'block';
            errEl.textContent = 'Reason must be at least 5 characters.';
            return;
        }
        try {
            await callApi('POST', '/api/v1/tasks/' + activeOverdueTaskId + '/delete', {reason: reason});
            closeModals();
            showError('');
            await loadData();
        } catch (err) {
            errEl.style.display = 'block';
            errEl.textContent = err.message;
        }
    });

    /**
     * Global click handler (event delegation) xử lý các action buttons trong bảng task:
     * - [data-overdue-action-choice]: chuyển panel trong modal overdue
     * - [data-task-expand]: toggle expand/collapse inline pages
     * - [data-task-overdue-decision]: mở modal overdue decision
     * - [data-task-approve-pop]: mở approve popover
     * - [data-task-reject-pop]: mở reject popover
     * - [data-task-delete]: xóa task (prompt lý do)
     * - [data-task-reassign]: mở modal reassign
     * - [data-popover-cancel]: đóng popover
     * - #taskPopoverScrim: đóng popover khi click scrim
     * - Click ngoài popover/actions: đóng popover
     * - [data-modal-close]: đóng modal
     * - .modal-backdrop: đóng modal khi click backdrop
     */
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
            var reason = prompt('Reason for deleting task #' + deleteTaskId + ':');
            if (!reason) {
                return;
            }
            try {
                await callApi('POST', '/api/v1/tasks/' + deleteTaskId + '/delete', {reason: reason});
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

    // Approve popover: gọi API approve, cập nhật task local, reload data
    document.getElementById('approvePopoverConfirm').addEventListener('click', async function () {
        if (!activePopoverTaskId) {
            return;
        }
        try {
            var taskId = activePopoverTaskId;
            var comment = document.getElementById('approvePopoverComment').value.trim();
            var payload = comment ? {comment: comment} : {};
            await callApi('POST', '/api/v1/tasks/' + taskId + '/approve', payload);
            closePopovers();
            var t = findTask(taskId);
            if (t) {
                t._decisionLabel = 'approved';
                t.status = 'APPROVED';
            }
            renderChapterTasks();
            await loadData();
            showError('');
        } catch (err) {
            showError(err.message);
        }
    });

    // Reject popover: validate lý do ≥ 5 ký tự, gọi API reject
    document.getElementById('rejectPopoverConfirm').addEventListener('click', async function () {
        if (!activePopoverTaskId) {
            return;
        }
        var reason = document.getElementById('rejectPopoverReason').value.trim();
        if (reason.length < 5) {
            return;
        }
        try {
            var taskId = activePopoverTaskId;
            await callApi('POST', '/api/v1/tasks/' + taskId + '/reject', {reason: reason});
            closePopovers();
            var t = findTask(taskId);
            if (t) {
                t._decisionLabel = 'rejected';
                t.status = 'IN_PROGRESS';
            }
            renderChapterTasks();
            await loadData();
            showError('');
        } catch (err) {
            showError(err.message);
        }
    });

    // Realtime validate textarea lý do reject
    document.getElementById('rejectPopoverReason').addEventListener('input', updateRejectConfirmState);

    // Đóng page compare modal
    document.getElementById('pageCompareClose').addEventListener('click', function () {
        document.getElementById('pageCompareModal').style.display = 'none';
    });
    document.getElementById('pageCompareModal').addEventListener('click', function (e) {
        if (e.target === this) {
            this.style.display = 'none';
        }
    });

    // Đóng page history modal
    document.getElementById('pageHistoryClose').addEventListener('click', function () {
        document.getElementById('pageHistoryModal').style.display = 'none';
    });
    document.getElementById('pageHistoryModal').addEventListener('click', function (e) {
        if (e.target === this) {
            this.style.display = 'none';
        }
        var rollbackEl = e.target.closest('[data-rollback-revision]');
        if (rollbackEl) {
            handleRollback(rollbackEl.getAttribute('data-page-id'), rollbackEl.getAttribute('data-rollback-revision'));
        }
    });

    // ============================================================
    // INIT
    // ============================================================

    // Hiển thị lỗi từ URL param (nếu có)
    if (urlError) {
        showError(decodeURIComponent(urlError));
    }

    switchTab('pages'); // Mở tab Pages mặc định
    loadData();         // Load toàn bộ dữ liệu
})();
