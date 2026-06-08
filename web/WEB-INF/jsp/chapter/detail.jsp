<%--
  1. HEAD         — CSS imports
  2. HEADER       — Shared navigation header
  3. BREADCRUMB   — Điều hướng My Series › Series › Chapter
  4. ACTION BAR   — Nút Delete / Submit for review / Manuscript Workspace
  5. TAB BAR      — 3 tab: Pages | Tasks | Edit details
     5a. TAB PAGES     — Lưới page slots + thanh tiến độ
     5b. TAB TASKS     — Bảng task + popover Approve/Reject
     5c. TAB EDIT      — Form sửa title & deadline
  6. SIDEBAR      — Meta panel + legend màu + task list rút gọn
  7. MODAL: pageCompareModal        — So sánh version page
  8. MODAL: pageUploadModal         — Upload ảnh page slot
  9. MODAL: assignTaskModal         — Gán task cho assistant
  10. MODAL: taskReassignModal      — Reassign task
  11. MODAL: taskOverdueDecisionModal — Xử lý task overdue
  12. CONFIG SCRIPT — Truyền contextPath xuống JS
--%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<%-- [1] HEAD: import global CSS (styles.css) và CSS riêng trang này (chapter-detail.css) --%>
<head>
    <meta charset="UTF-8">
    <title>Chapter Detail</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=20260525" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/chaptertask/chapter-detail.css?v=20260605buttons" />
</head>
<body>
<%-- [2] HEADER: shared navigation bar dùng chung toàn app, xem common/header.jsp --%>
<jsp:include page="../common/header.jsp" />

<div id="detailResult" class="alert error chapter-detail-inline-1"></div>
<%-- 
    [3] BREADCRUMB: thanh điều hướng vị trí hiện tại
    Hiển thị: My Series › {tên series} › {tên chapter} [STATUS]
    breadcrumbSeries và breadcrumbChapter để trống — SJS tự điền sau khi fetch API
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
    [4] ACTION BAR: 3 nút hành động chính, mặc định ẩn, JS show tuỳ điều kiện
    - btnDelete: chỉ hiện khi chapter ở PLANNING và chưa có task (BR xóa chapter)
    - btnMarkDone: chỉ hiện khi chapter đủ điều kiện submit manuscript
    - btnManuscriptWorkspace: link sang trang review manuscript
--%>
<div class="chapter-detail-inline-7">
    <button id="btnDelete" class="btn small chapter-detail-inline-8" type="button" style="display:none;">Delete chapter</button>
    <button id="btnMarkDone" class="btn primary chapter-detail-inline-9" type="button" style="display:none;">Submit for review</button>
    <a id="btnManuscriptWorkspace" href="#" class="btn small chapter-detail-inline-10" style="display:none;">📝 Manuscript Workspace</a>
</div>

<div class="chapter-workspace">
    <div class="section-card chapter-main-card">
<%-- 
    [5] TAB BAR: 3 tab điều hướng nội dung chính
    - Pages (5a): quản lý page slots, gán task
    - Tasks (5b): xem & duyệt task
    - Edit details (5c): sửa thông tin chapter
    Số trên badge (tabPageCount, tabTaskCount) do JS cập nhật
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
    [5a] TAB PAGES: lưới thumbnail tất cả page slots của chapter
    - Mangaka có thể chọn nhiều slot → bấm "Gán task" → mở assignTaskModal
    - Bấm vào từng slot → mở pageUploadModal để upload/xem ảnh
    - pagesOwnerActions (nút + Thêm trang) chỉ hiện với Mangaka chủ series
--%>
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
<%-- 
    [5a.1] PROGRESS BAR: thanh tiến độ chapter
    Công thức BR-TSK-11: (Approved tasks / Total tasks) × 100%
    JS tự cập nhật mỗi khi có task đổi trạng thái
--%>
            <div id="progressSection">
                <div class="chapter-detail-inline-20">
                    <span class="chapter-detail-inline-21">Tiến độ page</span>
                    <span id="progressLabel" class="chapter-detail-inline-22"></span>
                </div>
                <div class="progress"><span id="progressFill" class="chapter-detail-inline-23"></span></div>
            </div>
        </div>
<%-- 
    [5b] TAB TASKS: bảng danh sách tất cả task thuộc chapter này
    Cột: ID | Pages | Type | Assigned To | Status | Due Date | Action
    taskPopoverHost chứa 2 popover inline (không phải modal):
      - taskApprovePopover: Mangaka approve task, comment tuỳ chọn
      - taskRejectPopover: Mangaka reject task, bắt buộc nhập lý do (BR-TSK-05)
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
<%-- 
    [5c] TAB EDIT: form sửa thông tin chapter
    Chỉ Mangaka chủ series mới thao tác được
    Trường: Title, Submission deadline
    BR-CHP-02: deadline phải cách publication date ít nhất 14 ngày
--%>
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
<%-- 
    [6] SIDEBAR PHẢI: 3 panel thông tin phụ trợ
    - Meta panel: deadline, tổng số trang, status, % tiến độ (JS điền)
    - Legend màu: giải thích 4 trạng thái page slot (Trống/Đang làm/Chờ duyệt/Hoàn tất)
    - Task list rút gọn: danh sách nhanh các task (JS điền vào sidebarTaskList)
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
<%-- [7] MODAL pageCompareModal: so sánh các version ảnh của một page
Mở khi: click "Compare" trên page slot đã có lịch sử upload--%>
<div id="pageCompareModal" class="chapter-detail-inline-51">
  <div class="chapter-detail-inline-52">
    <button id="pageCompareClose" class="chapter-detail-inline-53">&times;</button>
    <div id="pageCompareTitle" class="chapter-detail-inline-54"></div>
    <div id="pageCompareBody"></div>
  </div>
</div>
<%-- 
    [8] MODAL pageUploadModal: upload/xem/xóa ảnh một page slot
    Stage picker: tick 5 stage (Sketching→Inking→Coloring→Screentone→Lettering)
    pageUploadDelete chỉ hiện với Mangaka (JS kiểm tra isOwner())
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
    [9] MODAL assignTaskModal: gán task mới cho assistant
    Điền sẵn trang đã chọn từ lưới Pages vào assignPageChips
    BR-CHP-03: chỉ Mangaka gán | BR-CHP-05: không tự gán cho mình
--%>
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
<%-- 
    [10] MODAL taskReassignModal: đổi assistant đang làm task
    BR-TSK-03: khi reassign → task reset về In Progress, xóa submission cũ
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
            <textarea id="taskReassignReason" rows="3" maxlength="300" required placeholder="Lý do reassign..."></textarea>
            <div id="taskReassignError" class="alert error chapter-detail-inline-65"></div>
            <button class="btn primary" type="submit">Confirm reassign</button>
        </form>
    </div>
</div>
<%-- 
    [11] MODAL taskOverdueDecisionModal: xử lý task quá hạn (BR-TSK-10)
    3 lựa chọn:
      - Extend: gia hạn due date
      - Reassign: đổi assistant + due date mới
      - Delete: xóa task khỏi production tracking (cần nhập lý do)
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
    [12] CONFIG SCRIPT: truyền contextPath từ server xuống JS
    Cần thiết để fetch() gọi đúng API URL khi app deploy trên subdirectory
    Đặt trước chapter-detail.js để JS đọc được ngay khi load
--%>
<script>
window.CHAPTER_DETAIL_CONFIG = {
    contextPath: '${pageContext.request.contextPath}'
};
</script>
<script src="${pageContext.request.contextPath}/assets/js/chaptertask/chapter-detail.js?v=20260608split"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
