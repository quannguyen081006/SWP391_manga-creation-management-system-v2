<%--
  PURPOSE: Overview screen showing progress of ALL of a Mangaka's chapters, gathered across series.
  MAIN STRUCTURE:
    [1] HEAD         — CSS import (styles.css + chapter-list.css)
    [2] ALERT BOX    — Shows inline errors (chapterResult), hidden by default
    [3] LAYOUT GRID  — 2 columns: tracker table (left) + chapter creation sidebar (right)
    [4] TRACKER TABLE — 3 chapter groups, JS automatically classifies and fills in the data:
        [4a] groupOverdue    — Chapters past deadline, always shown, red warning color
        [4b] groupInProgress — Chapters in progress (PLANNING / IN_PROGRESS), always shown
        [4c] groupCompleted  — Completed chapters, hidden by default, click "Show" to expand
        Each table has columns: No. | Series | Title | Status | Deadline | Progress | At Risk | Actions
        Sortable by: No, Title, Status, Deadline (JS handles sorting client-side)
        tbody (rowsOverdue / rowsInProgress / rowsCompleted) start empty — chapter-list.js fills them after fetching the API
    [5] RIGHT SIDEBAR — Only shown to Mangaka (canCreateChapter checked from session)
        [5a] New chapter form: Title + Page count (default 24, with ±1 ±5 buttons) + Deadline
             createSeriesDeadlineHint: JS fills in the suggested max deadline based on the series deadline
        [5b] Series overview: JS fills in total Completed / In Progress / Overdue chapter counts + % progress
    [6] CONFIG SCRIPT — Passes contextPath + canCreateChapter down to chapter-list.js
                        canCreateChapter = true only when the session has the MANGAKA role
--%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<%-- [1] HEAD --%>
<head>
    <meta charset="UTF-8">
    <title>Chapters</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=20260525" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/chaptertask/chapter-list.css?v=20260605fix3" />
</head>
<body>
<jsp:include page="../common/header.jsp" />
<%-- Chapter/task note: tracker-specific CSS is in /assets/css/chaptertask/chapter-list.css; chapter grouping logic stays in this JSP. --%>

<%-- [2] ALERT BOX: shows API errors inline, JS shows/hides it as needed --%>
<div id="chapterResult" class="alert error chapter-alert-hidden"></div>

<%-- [3] LAYOUT GRID: 2 columns — tracker table (wide, left) + chapter creation sidebar (narrow, right) --%>
<div id="chapterLayoutGrid" class="chapter-layout-grid">

    <div class="section-card">
        <div class="section-head chapter-section-head">
            <div>
                <h3 class="section-title chapter-section-title">Chapter Tracker</h3>
                <p class="section-desc chapter-section-desc">Current chapter progress across your series</p>
            </div>
        </div>
        <%-- chapterStatusPills: JS fills in filter pills (All / per series) for quick filtering --%>
        <div id="chapterStatusPills" class="chapter-status-pills"></div>

        <%-- [4a] OVERDUE GROUP: chapters past deadline — always shown, JS counts and fills in countOverdue --%>
        <div id="groupOverdue" class="chapter-group">
            <div class="chapter-group-head">
                <span class="chapter-group-dot chapter-group-dot-overdue"></span>
                <span class="chapter-group-label">Overdue</span>
                <span id="countOverdue" class="chapter-group-count">0</span>
            </div>
            <table class="data-table chapter-tracker-table" id="tableOverdue">
                <colgroup>
                    <col class="col-no">
                    <col class="col-series">
                    <col class="col-title">
                    <col class="col-status">
                    <col class="col-deadline">
                    <col class="col-progress">
                    <col class="col-atrisk">
                    <col class="col-actions">
                </colgroup>
                <thead><tr>
                    <th class="th-sortable col-no"><span class="th-sort-inner">No.<button class="btn small chapter-sort-btn" type="button" data-sort="no" title="Sort by chapter number" aria-label="Sort by chapter number">↕</button></span></th>
                    <th class="col-series">Series</th>
                    <th class="th-sortable col-title"><span class="th-sort-inner">Title<button class="btn small chapter-sort-btn" type="button" data-sort="title" title="Sort by title" aria-label="Sort by title">↕</button></span></th>
                    <th class="th-sortable col-status"><span class="th-sort-inner">Status<button class="btn small chapter-sort-btn" type="button" data-sort="status" title="Sort by status" aria-label="Sort by status">↕</button></span></th>
                    <th class="th-sortable col-deadline"><span class="th-sort-inner">Deadline<button class="btn small chapter-sort-btn" type="button" data-sort="deadline" title="Sort by deadline" aria-label="Sort by deadline">↕</button></span></th>
                    <th class="col-progress">Progress</th>
                    <th class="col-atrisk">At Risk</th>
                    <%-- chapterActionHeader: JS can hide the Actions column for non-Mangaka roles --%>
                    <th class="col-actions" id="chapterActionHeader">Actions</th>
                </tr></thead>
                <%-- rowsOverdue: empty tbody, chapter-list.js fetches the API then renders rows here --%>
                <tbody id="rowsOverdue"><tr><td colspan="8" class="chapter-empty-cell">Loading...</td></tr></tbody>
            </table>
        </div>

        <%-- [4b] IN PROGRESS GROUP: chapters currently in progress — always shown --%>
        <div id="groupInProgress" class="chapter-group">
            <div class="chapter-group-head">
                <span class="chapter-group-dot chapter-group-dot-progress"></span>
                <span class="chapter-group-label">In progress</span>
                <span id="countInProgress" class="chapter-group-count">0</span>
            </div>
            <table class="data-table chapter-tracker-table" id="tableInProgress">
                <colgroup>
                    <col class="col-no">
                    <col class="col-series">
                    <col class="col-title">
                    <col class="col-status">
                    <col class="col-deadline">
                    <col class="col-progress">
                    <col class="col-atrisk">
                    <col class="col-actions">
                </colgroup>
                <thead><tr>
                    <th class="th-sortable col-no"><span class="th-sort-inner">No.<button class="btn small chapter-sort-btn" type="button" data-sort="no" title="Sort by chapter number" aria-label="Sort by chapter number">↕</button></span></th>
                    <th class="col-series">Series</th>
                    <th class="th-sortable col-title"><span class="th-sort-inner">Title<button class="btn small chapter-sort-btn" type="button" data-sort="title" title="Sort by title" aria-label="Sort by title">↕</button></span></th>
                    <th class="th-sortable col-status"><span class="th-sort-inner">Status<button class="btn small chapter-sort-btn" type="button" data-sort="status" title="Sort by status" aria-label="Sort by status">↕</button></span></th>
                    <th class="th-sortable col-deadline"><span class="th-sort-inner">Deadline<button class="btn small chapter-sort-btn" type="button" data-sort="deadline" title="Sort by deadline" aria-label="Sort by deadline">↕</button></span></th>
                    <th class="col-progress">Progress</th>
                    <th class="col-atrisk">At Risk</th>
                    <th class="col-actions">Actions</th>
                </tr></thead>
                <tbody id="rowsInProgress"><tr><td colspan="8" class="chapter-empty-cell">Loading...</td></tr></tbody>
            </table>
        </div>

<%--
            [4c] COMPLETED GROUP: completed chapters
            Hidden by default (completedBody is collapsed), click toggleCompleted to Show/Hide
            JS fills countCompleted into the count badge
        --%>
        <div id="groupCompleted">
            <div class="chapter-group-head">
                <span class="chapter-group-dot chapter-group-dot-complete"></span>
                <span class="chapter-group-label">Completed</span>
                <span id="countCompleted" class="chapter-group-count">0</span>
                <button class="btn small chapter-toggle-completed" type="button" id="toggleCompleted">Show</button>
            </div>
            <div id="completedBody" class="chapter-completed-body">
                <table class="data-table chapter-tracker-table" id="tableCompleted">
                    <colgroup>
                        <col class="col-no">
                        <col class="col-series">
                        <col class="col-title">
                        <col class="col-status">
                        <col class="col-deadline">
                        <col class="col-progress">
                        <col class="col-atrisk">
                        <col class="col-actions">
                    </colgroup>
                    <thead><tr>
                        <th class="th-sortable col-no"><span class="th-sort-inner">No.<button class="btn small chapter-sort-btn" type="button" data-sort="no" title="Sort by chapter number" aria-label="Sort by chapter number">↕</button></span></th>
                        <th class="col-series">Series</th>
                        <th class="th-sortable col-title"><span class="th-sort-inner">Title<button class="btn small chapter-sort-btn" type="button" data-sort="title" title="Sort by title" aria-label="Sort by title">↕</button></span></th>
                        <th class="th-sortable col-status"><span class="th-sort-inner">Status<button class="btn small chapter-sort-btn" type="button" data-sort="status" title="Sort by status" aria-label="Sort by status">↕</button></span></th>
                        <th class="th-sortable col-deadline"><span class="th-sort-inner">Deadline<button class="btn small chapter-sort-btn" type="button" data-sort="deadline" title="Sort by deadline" aria-label="Sort by deadline">↕</button></span></th>
                        <th class="col-progress">Progress</th>
                        <th class="col-atrisk">At Risk</th>
                        <th class="col-actions">Actions</th>
                    </tr></thead>
                    <tbody id="rowsCompleted"><tr><td colspan="8" class="chapter-empty-cell">None</td></tr></tbody>
                </table>
            </div>
        </div>
    </div>

    <%--
        [5] RIGHT SIDEBAR: create new chapter + series overview
        Only shown to Mangaka — canCreateChapter is checked server-side from the session,
        passed down to JS via CHAPTER_LIST_CONFIG so JS can show/hide the sidebar
    --%>
    <div id="createSidebar" class="chapter-create-sidebar">

        <%-- [5a] CREATE CHAPTER FORM --%>
        <div class="panel chapter-create-panel">
            <%-- createSidebarTitle: JS fills in "New chapter · {series name}" --%>
            <strong id="createSidebarTitle">New chapter</strong>
            <%-- createSidebarSub: JS fills in the currently selected series --%>
            <p class="section-desc chapter-create-sub" id="createSidebarSub"></p>
            <%-- createSeriesDeadlineHint: JS fills in the suggested max deadline (series deadline - 14 days, BR-CHP-02) --%>
            <p class="section-desc chapter-create-hint" id="createSeriesDeadlineHint"></p>
            <div id="createErrorBox" class="alert error chapter-create-error"></div>
            <form id="chapterCreateForm" class="form-grid">
                <label class="field-label" for="chapterCreateTitle">Title</label>
                <input id="chapterCreateTitle" name="title" type="text" placeholder="Chapter title" required />
                <label class="field-label" for="chapterCreateTotalPages">Expected page count</label>
                <%-- Stepper ±1 ±5 for quick adjustment, default 24 pages, JS handles the data-total-pages-delta event --%>
                <div class="chapter-page-total-controls">
                    <button class="btn small" type="button" data-total-pages-delta="-5">−5</button>
                    <button class="btn small" type="button" data-total-pages-delta="-1">−1</button>
                    <input id="chapterCreateTotalPages" name="totalPages" type="number" min="1" value="24" required class="chapter-page-total-input" />
                    <button class="btn small" type="button" data-total-pages-delta="1">+1</button>
                    <button class="btn small" type="button" data-total-pages-delta="5">+5</button>
                </div>
                <label class="field-label" for="chapterCreateDeadline">Submission deadline</label>
                <input id="chapterCreateDeadline" name="submissionDeadline" type="date" required />
                <button class="btn primary chapter-create-submit" type="submit">Create chapter</button>
            </form>
        </div>

<%-- [5b] SERIES OVERVIEW: JS fills in the Completed/In Progress/Overdue chapter counts + overall % progress --%>
        <div class="panel">
            <strong>Series overview</strong>
            <div id="seriesOverviewStats" class="chapter-overview-stats"></div>
        </div>
    </div>

</div>

<%--
    [6] CONFIG SCRIPT: passes 2 values down to chapter-list.js
    - contextPath: so JS fetches the correct API URL when deployed under a subdirectory
    - canCreateChapter: true if the session has the MANGAKA role, false for other roles
                        → JS uses this to hide the chapter creation sidebar for non-Mangaka
--%>
<script src="${pageContext.request.contextPath}/assets/js/chaptertask/chapter-list.js?v=20260608split"
        data-context-path="${pageContext.request.contextPath}"
        data-can-create-chapter="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('MANGAKA') ? 'true' : 'false'}"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
