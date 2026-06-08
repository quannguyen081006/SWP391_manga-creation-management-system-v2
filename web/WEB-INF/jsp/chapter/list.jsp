<%--
  MỤC ĐÍCH: Màn hình tổng quan tiến độ TẤT CẢ chapter của Mangaka, gom từ nhiều series.
  CẤU TRÚC CHÍNH:
    [1] HEAD         — CSS import (styles.css + chapter-list.css)
    [2] ALERT BOX    — Hiển thị lỗi inline (chapterResult), mặc định ẩn
    [3] LAYOUT GRID  — Chia 2 cột: bảng tracker (trái) + sidebar tạo chapter (phải)
    [4] BẢNG TRACKER — 3 nhóm chapter, JS tự phân loại và điền dữ liệu vào:
        [4a] groupOverdue    — Chapter quá deadline, luôn hiện, màu đỏ cảnh báo
        [4b] groupInProgress — Chapter đang làm (PLANNING / IN_PROGRESS), luôn hiện
        [4c] groupCompleted  — Chapter đã xong, mặc định ẩn, bấm "Show" mới mở ra
        Mỗi bảng có các cột: No. | Series | Title | Status | Deadline | Progress | At Risk | Actions
        Có thể sort theo: No, Title, Status, Deadline (JS xử lý sort phía client)
        tbody (rowsOverdue / rowsInProgress / rowsCompleted) để trống — chapter-list.js điền vào sau khi fetch API
    [5] SIDEBAR PHẢI — Chỉ hiện với Mangaka (canCreateChapter kiểm tra từ session)
        [5a] Form tạo chapter mới: Title + Số trang (mặc định 24, có nút ±1 ±5) + Deadline
             createSeriesDeadlineHint: JS tự điền gợi ý deadline tối đa dựa theo series deadline
        [5b] Series overview: JS điền tổng số chapter Completed / In Progress / Overdue + % tiến độ
    [6] CONFIG SCRIPT — Truyền contextPath + canCreateChapter xuống chapter-list.js
                        canCreateChapter = true chỉ khi session có role MANGAKA
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

<%-- [2] ALERT BOX: hiển thị lỗi API inline, JS show/hide tuỳ tình huống --%>
<div id="chapterResult" class="alert error chapter-alert-hidden"></div>

<%-- [3] LAYOUT GRID: 2 cột — bảng tracker (trái rộng) + sidebar tạo chapter (phải hẹp) --%>
<div id="chapterLayoutGrid" class="chapter-layout-grid">

    <div class="section-card">
        <div class="section-head chapter-section-head">
            <div>
                <h3 class="section-title chapter-section-title">Chapter Tracker</h3>
                <p class="section-desc chapter-section-desc">Current chapter progress across your series</p>
            </div>
        </div>
        <%-- chapterStatusPills: JS điền filter pills (All / từng series) để lọc nhanh --%>
        <div id="chapterStatusPills" class="chapter-status-pills"></div>

        <%-- [4a] NHÓM OVERDUE: chapter quá deadline — luôn hiện, JS đếm và điền countOverdue --%>
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
                    <%-- chapterActionHeader: JS có thể ẩn cột Actions với role không phải Mangaka --%>
                    <th class="col-actions" id="chapterActionHeader">Actions</th>
                </tr></thead>
                <%-- rowsOverdue: tbody trống, chapter-list.js fetch API rồi render row vào đây --%>
                <tbody id="rowsOverdue"><tr><td colspan="8" class="chapter-empty-cell">Loading...</td></tr></tbody>
            </table>
        </div>

        <%-- [4b] NHÓM IN PROGRESS: chapter đang làm — luôn hiện --%>
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
            [4c] NHÓM COMPLETED: chapter đã xong
            Mặc định ẩn (completedBody bị collapse), bấm toggleCompleted mới Show/Hide
            JS điền countCompleted vào badge đếm
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
        [5] SIDEBAR PHẢI: tạo chapter mới + series overview
        Chỉ hiện với Mangaka — canCreateChapter kiểm tra từ session server-side,
        truyền xuống JS qua CHAPTER_LIST_CONFIG để JS ẩn/hiện sidebar
    --%>
    <div id="createSidebar" class="chapter-create-sidebar">

        <%-- [5a] FORM TẠO CHAPTER --%>
        <div class="panel chapter-create-panel">
            <%-- createSidebarTitle: JS điền "New chapter · {tên series}" --%>
            <strong id="createSidebarTitle">New chapter</strong>
            <%-- createSidebarSub: JS điền series đang được chọn --%>
            <p class="section-desc chapter-create-sub" id="createSidebarSub"></p>
            <%-- createSeriesDeadlineHint: JS điền gợi ý deadline tối đa (series deadline - 14 ngày, BR-CHP-02) --%>
            <p class="section-desc chapter-create-hint" id="createSeriesDeadlineHint"></p>
            <div id="createErrorBox" class="alert error chapter-create-error"></div>
            <form id="chapterCreateForm" class="form-grid">
                <label class="field-label" for="chapterCreateTitle">Title</label>
                <input id="chapterCreateTitle" name="title" type="text" placeholder="Chapter title" required />
                <label class="field-label" for="chapterCreateTotalPages">Số trang dự kiến</label>
                <%-- Stepper ±1 ±5 để điều chỉnh nhanh, mặc định 24 trang, JS bắt sự kiện data-total-pages-delta --%>
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

        <%-- [5b] SERIES OVERVIEW: JS điền số chapter Completed/In Progress/Overdue + % tiến độ tổng --%>
        <div class="panel">
            <strong>Series overview</strong>
            <div id="seriesOverviewStats" class="chapter-overview-stats"></div>
        </div>
    </div>

</div>

<%--
    [6] CONFIG SCRIPT: truyền 2 giá trị xuống chapter-list.js
    - contextPath: để JS fetch đúng API URL khi deploy trên subdirectory
    - canCreateChapter: true nếu session có role MANGAKA, false với role khác
                        → JS dùng để ẩn sidebar tạo chapter với non-Mangaka
--%>
<script>
window.CHAPTER_LIST_CONFIG = {
    contextPath: '${pageContext.request.contextPath}',
    canCreateChapter: ${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('MANGAKA') ? 'true' : 'false'}
};
</script>
<script src="${pageContext.request.contextPath}/assets/js/chaptertask/chapter-list.js?v=20260608split"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
