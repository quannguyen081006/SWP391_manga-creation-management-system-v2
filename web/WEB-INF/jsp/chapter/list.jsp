<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Chapters</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=20260525" />
    <style>
        .chapter-page-intro { margin-bottom: 28px; }
        .chapter-page-intro .page-sub { margin: 10px 0 0; font-size: 15px; line-height: 1.5; }
        .chapter-page-intro #chapterFilterSubtitle { margin: 8px 0 0; font-size: 14px; line-height: 1.5; }
        #chapterResult { margin-bottom: 16px; }
        .data-table th.th-sortable { white-space: nowrap; }
        .data-table th.th-sortable .th-sort-inner {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            white-space: nowrap;
        }
        .data-table th.th-sortable .chapter-sort-btn {
            flex-shrink: 0;
            padding: 2px 6px;
            line-height: 1;
        }
        .data-table th.th-sort-no { min-width: 72px; }
    </style>
</head>
<body>
<jsp:include page="../common/header.jsp" />

<header class="chapter-page-intro">
    <h2 class="page-title">Chapters</h2>
    <p class="page-sub">Track each chapter and current chapter progress</p>
    <p id="chapterFilterSubtitle" class="section-desc" style="display:none;"></p>
</header>

<div id="chapterResult" class="alert error" style="display:none;"></div>

<div id="chapterLayoutGrid" style="display:grid;grid-template-columns:1fr;gap:20px;align-items:start;margin-top:4px;">

    <div class="section-card">
        <div class="section-head" style="margin-bottom:8px;">
            <div>
                <h3 class="section-title" style="margin:0;">Chapter Tracker</h3>
                <p class="section-desc" style="margin:6px 0 0;">Current chapter progress across your series</p>
            </div>
        </div>
        <div id="chapterStatusPills" style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:16px;"></div>

        <div id="groupOverdue" style="margin-bottom:20px;">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;">
                <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#ef4444;"></span>
                <span style="font-size:13px;font-weight:600;color:#6b7280;">Overdue</span>
                <span id="countOverdue" style="font-size:11px;background:#f3f4f6;border:1px solid #e5e7eb;border-radius:999px;padding:1px 8px;color:#6b7280;">0</span>
            </div>
            <table class="data-table" id="tableOverdue">
                <thead><tr>
                    <th class="th-sortable th-sort-no"><span class="th-sort-inner">No.<button class="btn small chapter-sort-btn" type="button" data-sort="no" title="Sort by chapter number" aria-label="Sort by chapter number">↕</button></span></th>
                    <th class="col-series">Series</th>
                    <th class="th-sortable"><span class="th-sort-inner">Title<button class="btn small chapter-sort-btn" type="button" data-sort="title" title="Sort by title" aria-label="Sort by title">↕</button></span></th>
                    <th class="th-sortable" style="min-width:96px;"><span class="th-sort-inner">Status<button class="btn small chapter-sort-btn" type="button" data-sort="status" title="Sort by status" aria-label="Sort by status">↕</button></span></th>
                    <th class="th-sortable" style="min-width:110px;"><span class="th-sort-inner">Deadline<button class="btn small chapter-sort-btn" type="button" data-sort="deadline" title="Sort by deadline" aria-label="Sort by deadline">↕</button></span></th>
                    <th style="width:160px;">Progress</th>
                    <th style="width:88px;">At Risk</th>
                    <th style="width:80px;" id="chapterActionHeader">Actions</th>
                </tr></thead>
                <tbody id="rowsOverdue"><tr><td colspan="8" style="color:#9ca3af;font-size:13px;">Loading...</td></tr></tbody>
            </table>
        </div>

        <div id="groupInProgress" style="margin-bottom:20px;">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;">
                <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#f59e0b;"></span>
                <span style="font-size:13px;font-weight:600;color:#6b7280;">In progress</span>
                <span id="countInProgress" style="font-size:11px;background:#f3f4f6;border:1px solid #e5e7eb;border-radius:999px;padding:1px 8px;color:#6b7280;">0</span>
            </div>
            <table class="data-table" id="tableInProgress">
                <thead><tr>
                    <th class="th-sortable th-sort-no"><span class="th-sort-inner">No.<button class="btn small chapter-sort-btn" type="button" data-sort="no" title="Sort by chapter number" aria-label="Sort by chapter number">↕</button></span></th>
                    <th class="col-series">Series</th>
                    <th class="th-sortable"><span class="th-sort-inner">Title<button class="btn small chapter-sort-btn" type="button" data-sort="title" title="Sort by title" aria-label="Sort by title">↕</button></span></th>
                    <th class="th-sortable" style="min-width:96px;"><span class="th-sort-inner">Status<button class="btn small chapter-sort-btn" type="button" data-sort="status" title="Sort by status" aria-label="Sort by status">↕</button></span></th>
                    <th class="th-sortable" style="min-width:110px;"><span class="th-sort-inner">Deadline<button class="btn small chapter-sort-btn" type="button" data-sort="deadline" title="Sort by deadline" aria-label="Sort by deadline">↕</button></span></th>
                    <th style="width:160px;">Progress</th>
                    <th style="width:88px;">At Risk</th>
                    <th style="width:80px;">Actions</th>
                </tr></thead>
                <tbody id="rowsInProgress"><tr><td colspan="8" style="color:#9ca3af;font-size:13px;">Loading...</td></tr></tbody>
            </table>
        </div>

        <div id="groupCompleted">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;">
                <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:#10b981;"></span>
                <span style="font-size:13px;font-weight:600;color:#6b7280;">Completed</span>
                <span id="countCompleted" style="font-size:11px;background:#f3f4f6;border:1px solid #e5e7eb;border-radius:999px;padding:1px 8px;color:#6b7280;">0</span>
                <button class="btn small" type="button" id="toggleCompleted" style="margin-left:auto;">Show</button>
            </div>
            <div id="completedBody" style="display:none;">
                <table class="data-table" id="tableCompleted">
                    <thead><tr>
                        <th class="th-sortable th-sort-no"><span class="th-sort-inner">No.<button class="btn small chapter-sort-btn" type="button" data-sort="no" title="Sort by chapter number" aria-label="Sort by chapter number">↕</button></span></th>
                        <th class="col-series">Series</th>
                        <th class="th-sortable"><span class="th-sort-inner">Title<button class="btn small chapter-sort-btn" type="button" data-sort="title" title="Sort by title" aria-label="Sort by title">↕</button></span></th>
                        <th class="th-sortable" style="min-width:96px;"><span class="th-sort-inner">Status<button class="btn small chapter-sort-btn" type="button" data-sort="status" title="Sort by status" aria-label="Sort by status">↕</button></span></th>
                        <th class="th-sortable" style="min-width:110px;"><span class="th-sort-inner">Deadline<button class="btn small chapter-sort-btn" type="button" data-sort="deadline" title="Sort by deadline" aria-label="Sort by deadline">↕</button></span></th>
                        <th style="width:160px;">Progress</th>
                        <th style="width:88px;">At Risk</th>
                        <th style="width:80px;">Actions</th>
                    </tr></thead>
                    <tbody id="rowsCompleted"><tr><td colspan="8" style="color:#9ca3af;font-size:13px;">None</td></tr></tbody>
                </table>
            </div>
        </div>
    </div>

    <div id="createSidebar" style="display:none;">
        <div class="panel" style="margin-bottom:16px;">
            <strong id="createSidebarTitle">New chapter</strong>
            <p class="section-desc" id="createSidebarSub" style="margin:4px 0 12px;"></p>
            <p class="section-desc" id="createSeriesDeadlineHint" style="margin:0 0 12px;"></p>
            <div id="createErrorBox" class="alert error" style="display:none;margin-bottom:8px;"></div>
            <form id="chapterCreateForm" class="form-grid">
                <label class="field-label" for="chapterCreateTitle">Title</label>
                <input id="chapterCreateTitle" name="title" type="text" placeholder="Chapter title" required />
                <label class="field-label" for="chapterCreateTotalPages">Số trang dự kiến</label>
                <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;">
                    <button class="btn small" type="button" data-total-pages-delta="-5">−5</button>
                    <button class="btn small" type="button" data-total-pages-delta="-1">−1</button>
                    <input id="chapterCreateTotalPages" name="totalPages" type="number" min="1" value="24" required style="width:80px;text-align:center;" />
                    <button class="btn small" type="button" data-total-pages-delta="1">+1</button>
                    <button class="btn small" type="button" data-total-pages-delta="5">+5</button>
                </div>
                <label class="field-label" for="chapterCreateDeadline">Submission deadline</label>
                <input id="chapterCreateDeadline" name="submissionDeadline" type="date" required />
                <button class="btn primary" type="submit" style="margin-top:4px;">Create chapter</button>
            </form>
        </div>

        <div class="panel">
            <strong>Series overview</strong>
            <div id="seriesOverviewStats" style="margin-top:12px;"></div>
        </div>
    </div>

</div>

<script>
(function () {
    var ctx = '${pageContext.request.contextPath}';
    var box = document.getElementById('chapterResult');
    var currentUser = null;
    var seriesList = [];
    var chapters = [];
    var seriesById = {};
    var filterSeriesId = new URLSearchParams(window.location.search).get('seriesId');
    var sortField = null;
    var sortDir = 'asc';
    var completedVisible = false;
    var chapterStatusFilter = 'ALL';

    function escapeHtml(value) {
        if (value === null || value === undefined) { return ''; }
        return String(value).replace(/[&<>\"]/g, function (ch) {
            return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[ch];
        });
    }

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

    function dateOnly(value) {
        var formatted = formatDate(value);
        return formatted ? new Date(formatted + 'T00:00:00') : null;
    }

    function daysUntilDate(value) {
        var due = dateOnly(value);
        if (!due) { return null; }
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        return Math.ceil((due - today) / 86400000);
    }

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

    function formatDeadlineCell(dateValue, isDone, isOverdue) {
        var formatted = formatDate(dateValue);
        if (!formatted) { return '<span style="color:#9ca3af;">—</span>'; }
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

    function isChapterDone(ch) {
        var st = String(ch.status || '').toUpperCase();
        return st === 'COMPLETE' || st === 'APPROVED' || Number(ch.completionPct || 0) >= 100;
    }

    function isChapterOverdue(ch) {
        if (isChapterDone(ch)) { return false; }
        var daysLeft = daysUntilDate(ch.submissionDeadline);
        return daysLeft !== null && daysLeft < 0;
    }

    function chapterRowClass(ch, forceOverdue) {
        if (forceOverdue || isChapterOverdue(ch)) { return ' class="task-row-overdue"'; }
        if (ch.atRisk && !isChapterDone(ch)) { return ' class="task-row-delayed"'; }
        return '';
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

    function latestChapterDeadline(series) {
        return series && series.publicationDate ? addDaysIso(series.publicationDate, -7) : '';
    }

    function hasRole(role) {
        var roles = currentUser && currentUser.roles ? currentUser.roles : [];
        return roles.indexOf(role) !== -1;
    }

    function formatStatus(status) {
        if (!status) { return ''; }
        return String(status).toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, function (ch) { return ch.toUpperCase(); });
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

    function renderChapterStatusCell(ch) {
        return '<span class="status-chip chapter-status-chip ' + chapterStatusClass(ch.status) + '">'
            + escapeHtml(formatStatus(ch.status))
            + '</span>';
    }

    function renderAtRiskCell(ch) {
        return '<span class="status-chip ' + (ch.atRisk ? 'status-rejected' : 'status-approved') + '">'
            + (ch.atRisk ? 'AT RISK' : 'NORMAL')
            + '</span>';
    }

    function renderStatusPill(id, label, count, cssClass, activeFilter) {
        var active = activeFilter === id ? ' is-active' : '';
        return '<button type="button" class="status-pill ' + cssClass + active + '" data-chapter-status-pill="' + id + '" aria-pressed="' + (activeFilter === id ? 'true' : 'false') + '">'
            + '<span class="status-pill-label">' + escapeHtml(label) + '</span>'
            + '<span class="status-pill-count">' + Number(count || 0) + '</span>'
            + '</button>';
    }

    function computeChapterCounts() {
        var counts = {
            ALL: chapters.length,
            OVERDUE: 0,
            PLANNING: 0,
            IN_PROGRESS: 0,
            COMPLETE: 0,
            EDITORIAL_REVIEW: 0,
            APPROVED: 0,
            REJECTED: 0,
            AT_RISK: 0
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

    function chapterMatchesFilter(ch, filter) {
        if (!filter || filter === 'ALL') { return true; }
        if (filter === 'OVERDUE') { return isChapterOverdue(ch); }
        if (filter === 'AT_RISK') { return !!ch.atRisk; }
        return String(ch.status || '').toUpperCase() === filter;
    }

    function renderChapterStatusPills(counts) {
        var el = document.getElementById('chapterStatusPills');
        if (!el) { return; }
        el.innerHTML = ''
            + renderStatusPill('ALL', 'All', counts.ALL, 'pill-all', chapterStatusFilter)
            + renderStatusPill('OVERDUE', 'Overdue', counts.OVERDUE, 'pill-overdue', chapterStatusFilter)
            + renderStatusPill('PLANNING', 'Planning', counts.PLANNING, 'pill-planning', chapterStatusFilter)
            + renderStatusPill('IN_PROGRESS', 'In Progress', counts.IN_PROGRESS, 'pill-progress', chapterStatusFilter)
            + renderStatusPill('COMPLETE', 'Complete', counts.COMPLETE, 'pill-complete', chapterStatusFilter)
            + renderStatusPill('EDITORIAL_REVIEW', 'Editorial Review', counts.EDITORIAL_REVIEW, 'pill-review', chapterStatusFilter)
            + renderStatusPill('APPROVED', 'Approved', counts.APPROVED, 'pill-approved', chapterStatusFilter)
            + renderStatusPill('REJECTED', 'Rejected', counts.REJECTED, 'pill-rejected', chapterStatusFilter)
            + renderStatusPill('AT_RISK', 'At Risk', counts.AT_RISK, 'pill-at-risk', chapterStatusFilter);
    }

    function trackerColspan() {
        return filterSeriesId ? 7 : 8;
    }

    function toggleSeriesColumns() {
        var show = !filterSeriesId;
        var cols = document.querySelectorAll('.col-series');
        for (var i = 0; i < cols.length; i++) {
            cols[i].style.display = show ? '' : 'none';
        }
    }

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

    function showMessage(msg, isError) {
        if (!box) { return; }
        if (!msg) {
            box.style.display = 'none';
            box.textContent = '';
            return;
        }
        box.style.display = 'block';
        box.className = isError ? 'alert error' : 'panel';
        box.textContent = msg;
    }

    function formToObject(form) {
        var data = {};
        var fd = new FormData(form);
        fd.forEach(function (v, k) { data[k] = v; });
        return data;
    }

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

    function nextChapterNumber(seriesId) {
        var next = 1;
        for (var i = 0; i < chapters.length; i++) {
            if (Number(chapters[i].seriesId) === Number(seriesId)) {
                next = Math.max(next, Number(chapters[i].chapterNumber || 0) + 1);
            }
        }
        return next;
    }

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

    function setTrackerLoading(msg) {
        var colspan = trackerColspan();
        var html = '<tr><td colspan="' + colspan + '" style="color:#9ca3af;font-size:13px;">' + escapeHtml(msg) + '</td></tr>';
        document.getElementById('rowsOverdue').innerHTML = html;
        document.getElementById('rowsInProgress').innerHTML = html;
        document.getElementById('rowsCompleted').innerHTML = html;
    }

    function renderChapterActions() {
        var header = document.getElementById('chapterActionHeader');
        var layout = document.getElementById('chapterLayoutGrid');
        var sidebar = document.getElementById('createSidebar');
        var showActions = hasRole('MANGAKA');

        if (header) {
            header.style.display = showActions ? '' : 'none';
        }

        if (!showActions || !filterSeriesId) {
            if (sidebar) { sidebar.style.display = 'none'; }
            if (layout) { layout.style.gridTemplateColumns = '1fr'; }
            return;
        }

        sidebar.style.display = '';
        if (layout) { layout.style.gridTemplateColumns = '1fr 280px'; }

        var nextNum = nextChapterNumber(filterSeriesId);
        var seriesName = (seriesById[String(filterSeriesId)] || {}).title || ('#' + filterSeriesId);

        document.getElementById('createSidebarTitle').textContent = 'New chapter';
        document.getElementById('createSidebarSub').textContent = seriesName + ' · #' + nextNum;
        updateCreateDeadlineConstraints();

        var total = chapters.length;
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        var overdueCount = 0;
        var inProgressCount = 0;
        var doneCount = 0;

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
            + '<div style="display:flex;justify-content:space-between;margin-bottom:6px;">'
            + '<span style="font-size:13px;color:#6b7280;">Overall progress</span>'
            + '<span style="font-size:13px;font-weight:600;">' + overallPct + '%</span></div>'
            + '<div class="progress" style="margin-bottom:12px;"><span style="width:' + overallPct + '%;background:#8b5cf6;"></span></div>'
            + '<div style="display:flex;justify-content:space-between;font-size:13px;margin-bottom:4px;">'
            + '<span style="color:#6b7280;">Completed</span><span style="color:#10b981;font-weight:500;">' + doneCount + '</span></div>'
            + '<div style="display:flex;justify-content:space-between;font-size:13px;margin-bottom:4px;">'
            + '<span style="color:#6b7280;">In progress</span><span style="color:#f59e0b;font-weight:500;">' + inProgressCount + '</span></div>'
            + '<div style="display:flex;justify-content:space-between;font-size:13px;">'
            + '<span style="color:#6b7280;">Overdue</span><span style="color:#ef4444;font-weight:500;">' + overdueCount + '</span></div>';
    }

    function renderGroup(tbodyId, list, isOverdueGroup) {
        var tbody = document.getElementById(tbodyId);
        var colspan = trackerColspan();
        if (!list.length) {
            var emptyMsg = chapterStatusFilter === 'ALL' ? 'None' : 'No chapters match this filter.';
            tbody.innerHTML = '<tr><td colspan="' + colspan + '" style="color:#9ca3af;font-size:13px;">' + emptyMsg + '</td></tr>';
            return;
        }
        var showActions = hasRole('MANGAKA');
        var showSeries = !filterSeriesId;
        tbody.innerHTML = list.map(function (ch) {
            var progress = Math.max(0, Math.min(100, Number(ch.completionPct || 0)));
            var done = isChapterDone(ch);
            var overdue = isChapterOverdue(ch);
            var deadlineText = formatDeadlineCell(ch.submissionDeadline, done, overdue);
            var seriesName = (seriesById[String(ch.seriesId)] || {}).title || ('#' + ch.seriesId);
            var progressColor = progress >= 100 ? '#10b981' : (progress >= 50 ? '#f59e0b' : '#ef4444');
            var rowBg = isOverdueGroup ? 'background:rgba(239,68,68,0.04);' : '';
            var seriesCell = showSeries
                ? '<td style="max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="' + escapeHtml(seriesName) + '">' + escapeHtml(seriesName) + '</td>'
                : '';
            var actionsCell = showActions
                ? '<td><a class="btn small" href="' + ctx + '/main/chapters/detail?id=' + ch.id + '">View</a></td>'
                : '<td></td>';
            return '<tr' + chapterRowClass(ch, isOverdueGroup) + ' style="' + rowBg + '">'
                + '<td style="color:#9ca3af;">' + ch.chapterNumber + '</td>'
                + seriesCell
                + '<td style="font-weight:500;">' + escapeHtml(ch.title) + '</td>'
                + '<td>' + renderChapterStatusCell(ch) + '</td>'
                + '<td>' + deadlineText + '</td>'
                + '<td style="min-width:140px;">'
                + '<div style="display:flex;justify-content:space-between;margin-bottom:4px;">'
                + '<span style="font-size:12px;color:#6b7280;">' + Math.round(progress) + '%</span>'
                + '</div>'
                + '<div class="progress' + (progress < 40 ? ' red' : '') + '" style="margin-top:0;">'
                + '<span style="width:' + progress + '%;background:' + progressColor + ';"></span></div>'
                + '</td>'
                + '<td>' + renderAtRiskCell(ch) + '</td>'
                + actionsCell
                + '</tr>';
        }).join('');
    }

    function renderChapters() {
        renderChapterStatusPills(computeChapterCounts());
        toggleSeriesColumns();

        var today = new Date();
        today.setHours(0, 0, 0, 0);
        var overdue = [];
        var inProgress = [];
        var completed = [];

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
        updateSortIndicators();
    }

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

    async function loadData() {
        setTrackerLoading('Loading...');
        try {
            var userRes = await callApi('GET', '/api/v1/auth/me');
            currentUser = userRes.data;
            var results = await Promise.all([
                callApi('GET', '/api/v1/series'),
                callApi('GET', filterSeriesId ? ('/api/v1/series/' + encodeURIComponent(filterSeriesId) + '/chapters') : '/api/v1/chapters')
            ]);
            seriesList = results[0].data || [];
            chapters = results[1].data || [];
            seriesById = {};
            for (var i = 0; i < seriesList.length; i++) {
                seriesById[String(seriesList[i].id)] = seriesList[i];
            }
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

    document.getElementById('toggleCompleted').addEventListener('click', function () {
        completedVisible = !completedVisible;
        document.getElementById('completedBody').style.display = completedVisible ? '' : 'none';
        this.textContent = completedVisible ? 'Hide' : 'Show';
    });

    document.addEventListener('click', function (e) {
        var sortBtn = e.target.closest ? e.target.closest('.chapter-sort-btn[data-sort]') : null;
        if (sortBtn) {
            var field = sortBtn.getAttribute('data-sort');
            if (sortField === field) {
                sortDir = sortDir === 'asc' ? 'desc' : 'asc';
            } else {
                sortField = field;
                sortDir = 'asc';
            }
            renderChapters();
            return;
        }

        var chapterPill = e.target.closest ? e.target.closest('#chapterStatusPills [data-chapter-status-pill]') : null;
        if (chapterPill) {
            chapterStatusFilter = chapterPill.getAttribute('data-chapter-status-pill') || 'ALL';
            renderChapters();
        }
    });

    document.addEventListener('click', function (e) {
        var deltaBtn = e.target.closest ? e.target.closest('[data-total-pages-delta]') : null;
        if (!deltaBtn) { return; }
        var input = document.getElementById('chapterCreateTotalPages');
        if (!input) { return; }
        var next = Number(input.value || 1) + Number(deltaBtn.getAttribute('data-total-pages-delta'));
        input.value = Math.max(1, next);
    });

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
                if (pagesInput) { pagesInput.value = '24'; }
                showMessage('Chapter created successfully.', false);
                if (createRes && createRes.data && createRes.data.id) {
                    window.location.href = ctx + '/main/chapters/detail?id=' + createRes.data.id;
                    return;
                }
                await loadData();
            } catch (err) {
                errorBox.style.display = 'block';
                errorBox.textContent = err.message;
            }
        }
    });

    loadData();
})();
</script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
