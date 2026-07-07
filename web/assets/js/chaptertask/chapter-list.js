/**
 * chapterList.js — Chapter Tracker Page Script
 * Manga production management system (SWP391)
 *
 * ============================================================
 * TABLE OF CONTENTS
 * ============================================================
 * 1.  VARIABLE INITIALIZATION — State, config, DOM refs
 * 2.  HELPER / UTILITY        — escapeHtml, formatDate, date math
 * 3.  DEADLINE DISPLAY        — Compute & display deadline status
 * 4.  STATUS HELPERS          — isChapterDone, isOverdue, row CSS
 * 5.  FILTER & SORT           — Count, filter, sort the chapter list
 * 6.  RENDER — PILLS          — Status filter dropdown
 * 7.  RENDER — TABLE GROUPS   — Overdue / In-Progress / Completed
 * 8.  RENDER — SIDEBAR        — Chapter creation form + overview stats
 * 9.  DATA LOADING            — Call API, build state, trigger render
 * 10. EVENT LISTENERS         — Click (sort, filter, toggle), Submit form
 * ============================================================
 */

(function () {
    /* ==========================================================
     * 1. VARIABLE INITIALIZATION
     * ==========================================================
     * Reads config from window.CHAPTER_LIST_CONFIG injected by the server into the JSP/HTML.
     * filterSeriesId: if present → only show chapters of that series (URL ?seriesId=X).
     * serverCanCreateChapter: flag from the server to check chapter creation permission.
     * ========================================================== */
    var configScript = document.currentScript;
    var ctx = configScript ? configScript.getAttribute('data-context-path') || '' : '';
    var box = document.getElementById('chapterResult');     // area showing error/success messages
    var currentUser = null;                                 // currently logged-in user (loaded from /api/v1/auth/me)
    var seriesList = [];                                    // all series the user has permission to view
    var chapters = [];                                      // chapter list (all or filtered by series)
    var seriesById = {};                                    // map seriesId → seriesObject for quick lookup
    var filterSeriesId = new URLSearchParams(window.location.search).get('seriesId');
    var sortField = null;                                   // field currently being sorted: 'no'|'title'|'status'|'deadline'
    var sortDir = 'asc';                                    // sort direction
    var completedVisible = false;                           // whether the "Completed" group is expanded
    var chapterStatusFilter = 'ALL';                        // currently selected status filter
    var serverCanCreateChapter = configScript && configScript.getAttribute('data-can-create-chapter') === 'true';

    /* ==========================================================
     * 2. HELPER / UTILITY
     * ========================================================== */

    /** Escape special HTML characters to prevent XSS when rendering to the DOM. */
    function escapeHtml(value) {
        if (value === null || value === undefined) { return ''; }
        return String(value).replace(/[&<>\"]/g, function (ch) {
            return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[ch];
        });
    }

    /**
     * Normalizes various date value formats into a YYYY-MM-DD string.
     * Supports: Unix timestamp (number), ISO 8601 (contains 'T'), or a raw date string.
     */
    function formatDate(value) {
        if (value === null || value === undefined || value === '') { return ''; }
        var text = String(value);
        if (/^\d+$/.test(text)) {
            var date = new Date(Number(text));
            if (isNaN(date.getTime())) { return text; }
            var month = String(date.getMonth() + 1);
            var day = String(date.getDate());
            return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
        }
        if (text.indexOf('T') > -1) { return text.substring(0, 10); }
        return text;
    }

    /** Returns a Date object (time = 00:00) or null if it can't be parsed. */
    function dateOnly(value) {
        var formatted = formatDate(value);
        return formatted ? new Date(formatted + 'T00:00:00') : null;
    }

    /** Number of days remaining until the deadline (negative = overdue). */
    function daysUntilDate(value) {
        var due = dateOnly(value);
        if (!due) { return null; }
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        return Math.ceil((due - today) / 86400000);
    }

    /* ==========================================================
     * 3. DEADLINE DISPLAY
     * ========================================================== */

    /**
     * Builds the suffix text for the deadline cell.
     * E.g.: "3 days left", "2 days overdue", "Due today", "Done".
     */
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

    /**
     * Renders the HTML for the deadline cell in the table.
     * Color classification:
     *   - Green      → done
     *   - Red + icon → overdue
     *   - Orange     → ≤ 3 days left (urgent)
     *   - Blue       → normal
     */
    function formatDeadlineCell(dateValue, isDone, isOverdue) {
        var formatted = formatDate(dateValue);
        if (!formatted) { return '<span class="chapter-empty-muted">—</span>'; }
        var daysLeft = daysUntilDate(dateValue);
        // Auto mark as overdue if the deadline has passed and it's not yet done
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

    /* ==========================================================
     * 4. STATUS HELPERS
     * ========================================================== */

    /** A chapter is considered "done" if its status is COMPLETE/APPROVED or completionPct = 100. */
    function isChapterDone(ch) {
        var st = String(ch.status || '').toUpperCase();
        return st === 'COMPLETE' || st === 'APPROVED' || Number(ch.completionPct || 0) >= 100;
    }

    /** A chapter is overdue if it's not done and its deadline has passed. */
    function isChapterOverdue(ch) {
        if (isChapterDone(ch)) { return false; }
        var daysLeft = daysUntilDate(ch.submissionDeadline);
        return daysLeft !== null && daysLeft < 0;
    }

    /** CSS class for the row: overdue (red) → delayed (yellow) → none. */
    function chapterRowClass(ch, forceOverdue) {
        if (forceOverdue || isChapterOverdue(ch)) { return ' class="task-row-overdue"'; }
        if (ch.atRisk && !isChapterDone(ch)) { return ' class="task-row-delayed"'; }
        return '';
    }

    /**
     * Applies the width and color to the progress bar fill.
     * Uses a data attribute instead of inline style to avoid CSP issues when rendering an HTML string.
     * Called after every table render.
     */
    function applyChapterProgressStyles(root) {
        var scope = root || document;
        var fills = scope.querySelectorAll('[data-chapter-progress]');
        for (var i = 0; i < fills.length; i++) {
            var fill = fills[i];
            fill.style.width = fill.getAttribute('data-chapter-progress') + '%';
            fill.style.background = fill.getAttribute('data-chapter-progress-color') || '#8b5cf6';
        }
    }

    /** Today's date in YYYY-MM-DD format (used for the min attribute of a date input). */
    function todayIso() {
        var date = new Date();
        var month = String(date.getMonth() + 1);
        var day = String(date.getDate());
        return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
    }

    /** Adds/subtracts N days from a date value, returning YYYY-MM-DD. */
    function addDaysIso(value, days) {
        var date = dateOnly(value);
        if (!date) { return ''; }
        date.setDate(date.getDate() + days);
        var month = String(date.getMonth() + 1);
        var day = String(date.getDate());
        return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
    }

    /**
     * The maximum deadline for a chapter = the series' publicationDate - 7 days.
     * Ensures the chapter is submitted before the series is published.
     */
    function latestChapterDeadline(series) {
        return series && series.publicationDate ? addDaysIso(series.publicationDate, -7) : '';
    }

    /** Checks whether currentUser has a specific role (supports multiple role object shapes). */
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

    /* ==========================================================
     * 5. FILTER & SORT
     * ========================================================== */

    /** Viết hoa chữ cái đầu, thay '_' bằng dấu cách. VD: "IN_PROGRESS" → "In Progress". */
    function formatStatus(status) {
        if (!status) { return ''; }
        return String(status).toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, function (ch) { return ch.toUpperCase(); });
    }

    /** Map status string → CSS class để render chip màu. */
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

    function renderChapterStatusCell(ch) {
        return '<span class="status-chip chapter-status-chip ' + chapterStatusClass(ch.status) + '">'
            + escapeHtml(formatStatus(ch.status))
            + '</span>';
    }

    /** Chip "AT RISK" / "NORMAL" màu đỏ/xanh. */
    function renderAtRiskCell(ch) {
        return '<span class="status-chip ' + (ch.atRisk ? 'status-rejected' : 'status-approved') + '">'
            + (ch.atRisk ? 'AT RISK' : 'NORMAL')
            + '</span>';
    }

    /** Danh sách option cho filter dropdown, kèm count. */
    function chapterFilterOptions(counts) {
        return [
            { id: 'ALL', label: 'All', count: counts.ALL, cssClass: 'pill-all' },
            { id: 'OVERDUE', label: 'Overdue', count: counts.OVERDUE, cssClass: 'pill-overdue' },
            { id: 'PLANNING', label: 'Planning', count: counts.PLANNING, cssClass: 'pill-planning' },
            { id: 'IN_PROGRESS', label: 'In Progress', count: counts.IN_PROGRESS, cssClass: 'pill-progress' },
            { id: 'COMPLETE', label: 'Complete', count: counts.COMPLETE, cssClass: 'pill-complete' },
            { id: 'EDITORIAL_REVIEW', label: 'Editorial Review', count: counts.EDITORIAL_REVIEW, cssClass: 'pill-review' },
            { id: 'APPROVED', label: 'Approved', count: counts.APPROVED, cssClass: 'pill-approved' },
            { id: 'REJECTED', label: 'Rejected', count: counts.REJECTED, cssClass: 'pill-rejected' },
            { id: 'AT_RISK', label: 'At Risk', count: counts.AT_RISK, cssClass: 'pill-at-risk' }
        ];
    }

    function renderFilterOption(option, selectedId) {
        var active = selectedId === option.id ? ' is-active' : '';
        return '<button type="button" class="status-pill ' + option.cssClass + active + '" data-chapter-status-option="' + option.id + '">'
            + '<span class="status-pill-label">' + escapeHtml(option.label) + '</span>'
            + '<span class="status-pill-count">' + Number(option.count || 0) + '</span>'
            + '</button>';
    }

    /** Đếm số chapter theo từng nhóm status để hiển thị badge trên filter pill. */
    function computeChapterCounts() {
        var counts = {
            ALL: chapters.length,
            OVERDUE: 0, PLANNING: 0, IN_PROGRESS: 0, COMPLETE: 0,
            EDITORIAL_REVIEW: 0, APPROVED: 0, REJECTED: 0, AT_RISK: 0
        };
        for (var i = 0; i < chapters.length; i++) {
            var ch = chapters[i];
            var st = String(ch.status || '').toUpperCase();
            if (isChapterOverdue(ch)) { counts.OVERDUE++; }
            if (st === 'PLANNING') { counts.PLANNING++; }
            if (st === 'IN_PROGRESS') { counts.IN_PROGRESS++; }
            if (st === 'COMPLETE') { counts.COMPLETE++; }
            if (st === 'EDITORIAL_REVIEW') { counts.EDITORIAL_REVIEW++; }
            if (st === 'APPROVED') { counts.APPROVED++; }
            if (st === 'REJECTED') { counts.REJECTED++; }
            if (ch.atRisk) { counts.AT_RISK++; }
        }
        return counts;
    }

    /** Trả về true nếu chapter thoả mãn filter đang chọn. */
    function chapterMatchesFilter(ch, filter) {
        if (!filter || filter === 'ALL') { return true; }
        if (filter === 'OVERDUE') { return isChapterOverdue(ch); }
        if (filter === 'AT_RISK') { return !!ch.atRisk; }
        return String(ch.status || '').toUpperCase() === filter;
    }

    /* ==========================================================
     * 6. RENDER — PILLS (filter dropdown)
     * ========================================================== */

    /** Render lại toàn bộ dropdown filter status với số đếm mới nhất. */
    function renderChapterStatusPills(counts) {
        var el = document.getElementById('chapterStatusPills');
        if (!el) { return; }
        var options = chapterFilterOptions(counts);
        var selected = options[0];
        for (var i = 0; i < options.length; i++) {
            if (options[i].id === chapterStatusFilter) { selected = options[i]; break; }
        }
        el.innerHTML = '<div class="status-filter-dropdown" data-status-filter-dropdown="chapter">'
            + '<button type="button" class="status-pill status-filter-toggle ' + selected.cssClass + ' is-active" data-status-filter-toggle="chapter" aria-haspopup="true" aria-expanded="false">'
            + '<span class="status-pill-label">' + escapeHtml(selected.label) + '</span>'
            + '<span class="status-pill-count">' + Number(selected.count || 0) + '</span>'
            + '<span class="status-filter-caret">&#9662;</span>'
            + '</button>'
            + '<div class="status-filter-menu">'
            + options.map(function (option) { return renderFilterOption(option, chapterStatusFilter); }).join('')
            + '</div>'
            + '</div>';
    }

    /**
     * Số cột của bảng tracker.
     * Khi lọc theo series (filterSeriesId có giá trị) thì ẩn cột "Series" → colspan nhỏ hơn 1.
     */
    function trackerColspan() {
        return filterSeriesId ? 7 : 8;
    }

    /** Ẩn/hiện cột "Series" dựa vào filterSeriesId. */
    function toggleSeriesColumns() {
        var show = !filterSeriesId;
        var cols = document.querySelectorAll('.col-series');
        for (var i = 0; i < cols.length; i++) {
            cols[i].style.display = show ? '' : 'none';
        }
    }

    /** Sort mảng chapter theo sortField + sortDir hiện tại, không mutate mảng gốc. */
    function sortChapterList(list) {
        if (!sortField) { return list.slice(); }
        var dir = sortDir === 'asc' ? 1 : -1;
        return list.slice().sort(function (a, b) {
            var av, bv;
            if (sortField === 'no') {
                av = Number(a.chapterNumber || 0);
                bv = Number(b.chapterNumber || 0);
            } else if (sortField === 'title') {
                av = String(a.title || '').toLowerCase();
                bv = String(b.title || '').toLowerCase();
            } else if (sortField === 'status') {
                av = String(a.status || '');
                bv = String(b.status || '');
            } else if (sortField === 'deadline') {
                // Chapter không có deadline xếp cuối ('zzz')
                av = a.submissionDeadline ? String(a.submissionDeadline) : 'zzz';
                bv = b.submissionDeadline ? String(b.submissionDeadline) : 'zzz';
            } else {
                return 0;
            }
            if (av < bv) { return -1 * dir; }
            if (av > bv) { return 1 * dir; }
            return 0;
        });
    }

    /* Hiện thông báo success/error ở #chapterResult. Truyền msg rỗng để ẩn. */
    function showMessage(msg, isError) {
        if (!box) { return; }
        if (!msg) { box.style.display = 'none'; box.textContent = ''; return; }
        box.style.display = 'block';
        box.className = isError ? 'alert error' : 'panel';
        box.textContent = msg;
    }

    /** Serialize form thành plain object {key: value}. */
    function formToObject(form) {
        var data = {};
        var fd = new FormData(form);
        fd.forEach(function (v, k) { data[k] = v; });
        return data;
    }

    /**
     * Wrapper fetch gọi API nội bộ.
     * - GET: params đưa lên query string.
     * - POST/PUT/DELETE: params đưa vào body (application/x-www-form-urlencoded).
     * Throw Error nếu response không ok hoặc body.success === false.
     */
    async function callApi(method, path, data) {
        var opts = { method: method, headers: { 'Accept': 'application/json' } };
        var url = ctx + path;
        if (data) {
            var params = new URLSearchParams(data).toString();
            if (method === 'GET') {
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
            var msg = (body && (body.message || (body.errors && body.errors[0]))) || text || ('HTTP ' + res.status);
            throw new Error(msg);
        }
        return body;
    }

    /**
     * Tự động đề xuất số chapter tiếp theo cho series.
     * Duyệt chapters hiện có, lấy max(chapterNumber) + 1.
     */
    function nextChapterNumber(seriesId) {
        var next = 1;
        for (var i = 0; i < chapters.length; i++) {
            if (Number(chapters[i].seriesId) === Number(seriesId)) {
                next = Math.max(next, Number(chapters[i].chapterNumber || 0) + 1);
            }
        }
        return next;
    }

    /**
     * Cập nhật ràng buộc ngày cho input deadline trong form tạo chapter:
     *   - min = hôm nay
     *   - max = publicationDate của series - 7 ngày (nếu có)
     */
    function updateCreateDeadlineConstraints() {
        var deadlineInput = document.getElementById('chapterCreateDeadline');
        var deadlineHint = document.getElementById('createSeriesDeadlineHint');
        if (!deadlineInput) { return; }
        deadlineInput.min = todayIso();
        deadlineInput.removeAttribute('max');
        if (!deadlineHint) { return; }
        var series = seriesById[String(filterSeriesId)];
        var maxDeadline = latestChapterDeadline(series);
        if (maxDeadline) { deadlineInput.max = maxDeadline; }
        deadlineHint.textContent = series && series.publicationDate
            ? ('Series deadline: ' + formatDate(series.publicationDate) + '. Chapter deadline must be on or before ' + maxDeadline + '.')
            : (filterSeriesId ? 'Chapter deadline cannot be in the past.' : '');
    }

    /** Hiện skeleton "Loading..." trong tất cả tbody khi đang gọi API. */
    function setTrackerLoading(msg) {
        var colspan = trackerColspan();
        var html = '<tr><td colspan="' + colspan + '" class="chapter-empty-cell">' + escapeHtml(msg) + '</td></tr>';
        document.getElementById('rowsOverdue').innerHTML = html;
        document.getElementById('rowsInProgress').innerHTML = html;
        document.getElementById('rowsCompleted').innerHTML = html;
    }

    /* ==========================================================
     * 8. RENDER — SIDEBAR (form tạo chapter + overview stats)
     * ========================================================== */

    /**
     * Hiện/ẩn sidebar tạo chapter dựa vào quyền và filterSeriesId.
     * Nếu có quyền và đang filter theo series → hiện sidebar + 2-cột layout.
     * Tính tóm tắt tiến độ tổng thể (overall %, overdue, in-progress, done) cho series.
     */
    function renderChapterActions() {
        var header = document.getElementById('chapterActionHeader');
        var layout = document.getElementById('chapterLayoutGrid');
        var sidebar = document.getElementById('createSidebar');
        var showActions = serverCanCreateChapter || hasRole('MANGAKA');

        if (header) { header.style.display = showActions ? '' : 'none'; }

        if (!showActions || !filterSeriesId) {
            if (sidebar) { sidebar.style.display = 'none'; }
            if (layout) { layout.style.gridTemplateColumns = '1fr'; }
            return;
        }

        sidebar.style.display = 'block';
        if (layout) { layout.style.gridTemplateColumns = '1fr 280px'; }

        var nextNum = nextChapterNumber(filterSeriesId);
        var seriesName = (seriesById[String(filterSeriesId)] || {}).title || ('#' + filterSeriesId);
        document.getElementById('createSidebarTitle').textContent = 'New chapter';
        document.getElementById('createSidebarSub').textContent = seriesName + ' · #' + nextNum;
        updateCreateDeadlineConstraints();

        // Tính tóm tắt tiến độ
        var total = chapters.length;
        var today = new Date(); today.setHours(0, 0, 0, 0);
        var overdueCount = 0, inProgressCount = 0, doneCount = 0;
        for (var i = 0; i < chapters.length; i++) {
            var ch = chapters[i];
            var status = String(ch.status || '').toUpperCase();
            var deadlineDate = dateOnly(ch.submissionDeadline);
            var isComplete = isChapterDone(ch);
            if (isComplete || status === 'EDITORIAL_REVIEW') {
                doneCount++;
            } else if (!isComplete && deadlineDate && deadlineDate < today) {
                overdueCount++;
            } else {
                inProgressCount++;
            }
        }
        var overallPct = total > 0 ? Math.round((doneCount / total) * 100) : 0;
        document.getElementById('seriesOverviewStats').innerHTML = ''
            + '<div class="chapter-overview-row">'
            + '<span class="chapter-overview-label">Overall progress</span>'
            + '<span class="chapter-overview-value">' + overallPct + '%</span></div>'
            + '<div class="progress chapter-overview-progress"><span class="chapter-progress-fill" data-chapter-progress="' + overallPct + '" data-chapter-progress-color="#8b5cf6"></span></div>'
            + '<div class="chapter-overview-row chapter-overview-row-compact">'
            + '<span class="chapter-overview-label">Completed</span><span class="chapter-overview-value-complete">' + doneCount + '</span></div>'
            + '<div class="chapter-overview-row chapter-overview-row-compact">'
            + '<span class="chapter-overview-label">In progress</span><span class="chapter-overview-value-progress">' + inProgressCount + '</span></div>'
            + '<div class="chapter-overview-row chapter-overview-row-last">'
            + '<span class="chapter-overview-label">Overdue</span><span class="chapter-overview-value-overdue">' + overdueCount + '</span></div>';
        applyChapterProgressStyles(document.getElementById('seriesOverviewStats'));
    }

    /* ==========================================================
     * 7. RENDER — TABLE GROUPS
     * ========================================================== */

    /**
     * Render một nhóm row vào tbody được chỉ định (Overdue / InProgress / Completed).
     * isOverdueGroup = true → force class "task-row-overdue" cho mọi row trong nhóm.
     */
    function renderGroup(tbodyId, list, isOverdueGroup) {
        var tbody = document.getElementById(tbodyId);
        var colspan = trackerColspan();
        if (!list.length) {
            var emptyMsg = chapterStatusFilter === 'ALL' ? 'None' : 'No chapters match this filter.';
            tbody.innerHTML = '<tr><td colspan="' + colspan + '" class="chapter-empty-cell">' + emptyMsg + '</td></tr>';
            return;
        }
        var showActions = serverCanCreateChapter || hasRole('MANGAKA');
        var showSeries = !filterSeriesId;
        tbody.innerHTML = list.map(function (ch) {
            var progress = Math.max(0, Math.min(100, Number(ch.completionPct || 0)));
            var done = isChapterDone(ch);
            var overdue = isChapterOverdue(ch);
            var deadlineText = formatDeadlineCell(ch.submissionDeadline, done, overdue);
            var seriesName = (seriesById[String(ch.seriesId)] || {}).title || ('#' + ch.seriesId);
            // Màu progress bar: xanh ≥100%, vàng ≥50%, đỏ <50%
            var progressColor = progress >= 100 ? '#10b981' : (progress >= 50 ? '#f59e0b' : '#ef4444');
            var seriesCell = showSeries
                ? '<td class="chapter-series-cell" title="' + escapeHtml(seriesName) + '">' + escapeHtml(seriesName) + '</td>'
                : '';
            var actionsCell = showActions
                ? '<td><a class="btn small" href="' + ctx + '/main/chapters/detail?id=' + ch.id + '">View</a></td>'
                : '<td></td>';
            return '<tr' + chapterRowClass(ch, isOverdueGroup) + '>'
                + '<td class="chapter-number-cell">' + ch.chapterNumber + '</td>'
                + seriesCell
                + '<td class="chapter-title-cell">' + escapeHtml(ch.title) + '</td>'
                + '<td>' + renderChapterStatusCell(ch) + '</td>'
                + '<td>' + deadlineText + '</td>'
                + '<td class="chapter-progress-cell">'
                + '<div class="chapter-progress-meta">'
                + '<span class="chapter-progress-percent">' + Math.round(progress) + '%</span>'
                + '</div>'
                + '<div class="progress chapter-progress-bar' + (progress < 40 ? ' red' : '') + '">'
                + '<span class="chapter-progress-fill" data-chapter-progress="' + progress + '" data-chapter-progress-color="' + progressColor + '"></span></div>'
                + '</td>'
                + '<td>' + renderAtRiskCell(ch) + '</td>'
                + actionsCell
                + '</tr>';
        }).join('');
        applyChapterProgressStyles(tbody);
    }

    /** Đồng bộ display của tbody và text nút toggle theo state completedVisible. */
    function updateCompletedVisibility() {
        var body = document.getElementById('completedBody');
        var toggle = document.getElementById('toggleCompleted');
        if (body) { body.style.display = completedVisible ? 'block' : 'none'; }
        if (toggle) { toggle.textContent = completedVisible ? 'Hide' : 'Show'; }
    }

    /**
     * Hàm render chính — điều phối toàn bộ quá trình vẽ lại trang:
     * 1. Cập nhật pills + count
     * 2. Phân loại chapters vào 3 nhóm (overdue / inProgress / completed)
     * 3. Render từng nhóm + cập nhật header count
     * 4. Ẩn nhóm rỗng, cập nhật nút sort
     */
    function renderChapters() {
        renderChapterStatusPills(computeChapterCounts());
        toggleSeriesColumns();

        var today = new Date(); today.setHours(0, 0, 0, 0);
        var overdue = [], inProgress = [], completed = [];

        var filtered = chapters.filter(function (ch) {
            return chapterMatchesFilter(ch, chapterStatusFilter);
        });
        var sorted = sortChapterList(filtered);

        for (var i = 0; i < sorted.length; i++) {
            var ch = sorted[i];
            var status = String(ch.status || '').toUpperCase();
            var deadlineDate = dateOnly(ch.submissionDeadline);
            var isComplete = isChapterDone(ch);
            var isOverdue = !isComplete && deadlineDate && deadlineDate < today;

            // EDITORIAL_REVIEW được coi là "gần hoàn thành" → vào nhóm completed
            if (isComplete || status === 'EDITORIAL_REVIEW') {
                completed.push(ch);
            } else if (isOverdue) {
                overdue.push(ch);
            } else {
                inProgress.push(ch);
            }
        }

        document.getElementById('countOverdue').textContent = overdue.length;
        document.getElementById('countInProgress').textContent = inProgress.length;
        document.getElementById('countCompleted').textContent = completed.length;

        renderGroup('rowsOverdue', overdue, true);
        renderGroup('rowsInProgress', inProgress, false);
        renderGroup('rowsCompleted', completed, false);

        document.getElementById('groupOverdue').style.display = overdue.length ? '' : 'none';
        document.getElementById('groupInProgress').style.display = inProgress.length ? '' : 'none';
        document.getElementById('groupCompleted').style.display = completed.length ? '' : 'none';
        updateCompletedVisibility();
        updateSortIndicators();
    }

    /** Cập nhật icon ↑ ↓ ↕ trên các nút sort header theo sortField/sortDir hiện tại. */
    function updateSortIndicators() {
        var buttons = document.querySelectorAll('.chapter-sort-btn[data-sort]');
        for (var i = 0; i < buttons.length; i++) {
            var btn = buttons[i];
            var field = btn.getAttribute('data-sort');
            if (field === sortField) {
                btn.textContent = sortDir === 'asc' ? '↑' : '↓';
                btn.setAttribute('aria-pressed', 'true');
            } else {
                btn.textContent = '↕';
                btn.setAttribute('aria-pressed', 'false');
            }
        }
    }

    /* ==========================================================
     * 9. DATA LOADING
     * ========================================================== */

    /**
     * Load toàn bộ dữ liệu khi trang khởi động (hoặc sau khi tạo chapter thành công).
     * Thứ tự:
     *   1. GET /api/v1/auth/me          → lấy currentUser
     *   2. GET /api/v1/series           → danh sách series
     *   3. GET /api/v1/[series/X/]chapters → danh sách chapter
     * Dùng Promise.all để chạy 2 và 3 song song.
     */
    async function loadData() {
        setTrackerLoading('Loading...');
        try {
            var userRes = await callApi('GET', '/api/v1/auth/me');
            currentUser = userRes.data;
            var results = await Promise.all([
                callApi('GET', '/api/v1/series'),
                callApi('GET', filterSeriesId
                    ? ('/api/v1/series/' + encodeURIComponent(filterSeriesId) + '/chapters')
                    : '/api/v1/chapters')
            ]);
            seriesList = results[0].data || [];
            chapters = results[1].data || [];
            // Build lookup map để tránh duyệt mảng nhiều lần
            seriesById = {};
            for (var i = 0; i < seriesList.length; i++) {
                seriesById[String(seriesList[i].id)] = seriesList[i];
            }
            // Hiện subtitle "Viewing chapters for series #X — TênSeries"
            var filterSubtitle = document.getElementById('chapterFilterSubtitle');
            if (filterSubtitle && filterSeriesId) {
                var filteredSeries = seriesById[String(filterSeriesId)];
                filterSubtitle.style.display = 'block';
                filterSubtitle.textContent = filteredSeries
                    ? ('Viewing chapters for series #' + filterSeriesId + ' — ' + filteredSeries.title)
                    : ('Viewing chapters for series #' + filterSeriesId);
            }
            renderChapterActions();
            renderChapters();
            showMessage('');
        } catch (err) {
            setTrackerLoading(err.message);
            showMessage(err.message, true);
        }
    }

    /* ==========================================================
     * 10. EVENT LISTENERS
     * ========================================================== */

    /** Toggle hiện/ẩn nhóm "Completed". */
    document.getElementById('toggleCompleted').addEventListener('click', function () {
        completedVisible = !completedVisible;
        updateCompletedVisibility();
    });

    /**
     * Delegated click handler cho toàn document.
     * Xử lý 3 loại click:
     *   a) Nút sort header (.chapter-sort-btn[data-sort]) → đổi field/hướng sort rồi re-render
     *   b) Toggle dropdown filter ([data-status-filter-toggle]) → mở/đóng menu
     *   c) Option trong dropdown ([data-chapter-status-option]) → đổi filter rồi re-render
     *   d) Click ngoài dropdown → đóng dropdown
     */
    document.addEventListener('click', function (e) {
        // a) Sort
        var sortBtn = e.target.closest ? e.target.closest('.chapter-sort-btn[data-sort]') : null;
        if (sortBtn) {
            var field = sortBtn.getAttribute('data-sort');
            if (sortField === field) {
                sortDir = sortDir === 'asc' ? 'desc' : 'asc'; // toggle chiều
            } else {
                sortField = field;
                sortDir = 'asc';
            }
            renderChapters();
            return;
        }

        // b) Toggle dropdown
        var chapterToggle = e.target.closest ? e.target.closest('#chapterStatusPills [data-status-filter-toggle]') : null;
        if (chapterToggle) {
            var chapterDropdown = chapterToggle.closest('[data-status-filter-dropdown]');
            if (chapterDropdown) {
                chapterDropdown.classList.toggle('open');
                chapterToggle.setAttribute('aria-expanded', chapterDropdown.classList.contains('open') ? 'true' : 'false');
            }
            return;
        }

        // c) Chọn option trong dropdown
        var chapterPill = e.target.closest ? e.target.closest('#chapterStatusPills [data-chapter-status-option]') : null;
        if (chapterPill) {
            chapterStatusFilter = chapterPill.getAttribute('data-chapter-status-option') || 'ALL';
            renderChapters();
            return;
        }

        // d) Click ngoài → đóng dropdown nếu đang mở
        var openChapterDropdown = document.querySelector('#chapterStatusPills [data-status-filter-dropdown].open');
        if (openChapterDropdown && !(e.target.closest && e.target.closest('#chapterStatusPills [data-status-filter-dropdown]'))) {
            openChapterDropdown.classList.remove('open');
        }
    });

    /**
     * Nút +/- điều chỉnh số trang cho input "Total Pages" trong form tạo chapter.
     * data-total-pages-delta="+1" hoặc "-1".
     */
    document.addEventListener('click', function (e) {
        var deltaBtn = e.target.closest ? e.target.closest('[data-total-pages-delta]') : null;
        if (!deltaBtn) { return; }
        var input = document.getElementById('chapterCreateTotalPages');
        if (!input) { return; }
        var next = Number(input.value || 1) + Number(deltaBtn.getAttribute('data-total-pages-delta'));
        input.value = Math.max(1, next); // không cho nhập < 1
    });

    /**
     * Submit form tạo chapter (#chapterCreateForm).
     * POST lên /api/v1/series/{seriesId}/chapters.
     * Nếu thành công → redirect sang trang detail của chapter mới.
     * Nếu lỗi → hiện thông báo trong #createErrorBox.
     */
    document.addEventListener('submit', async function (e) {
        if (e.target.id === 'chapterCreateForm') {
            e.preventDefault();
            var errorBox = document.getElementById('createErrorBox');
            errorBox.style.display = 'none';
            try {
                var createData = formToObject(e.target);
                var targetSeriesId = filterSeriesId;
                if (!targetSeriesId) { throw new Error('Series not found.'); }
                var totalPages = Math.max(1, Number(createData.totalPages) || 24);
                var createRes = await callApi('POST', '/api/v1/series/' + targetSeriesId + '/chapters', {
                    title: createData.title,
                    submissionDeadline: createData.submissionDeadline,
                    totalPages: totalPages
                });
                e.target.reset();
                var pagesInput = document.getElementById('chapterCreateTotalPages');
                if (pagesInput) { pagesInput.value = '24'; } // reset về default 24 trang
                showMessage('Chapter created successfully.', false);
                if (createRes && createRes.data && createRes.data.id) {
                    // Redirect sang detail page ngay sau khi tạo thành công
                    window.location.href = ctx + '/main/chapters/detail?id=' + createRes.data.id;
                    return;
                }
                await loadData(); // fallback nếu không có id trong response
            } catch (err) {
                errorBox.style.display = 'block';
                errorBox.textContent = err.message;
            }
        }
    });

    // Khởi chạy — load dữ liệu ngay khi script được thực thi
    loadData();
})();
