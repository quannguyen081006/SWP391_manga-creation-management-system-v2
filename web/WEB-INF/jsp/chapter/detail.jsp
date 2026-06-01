<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Chapter Detail</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=20260525" />
    <style>
        .chapter-workspace { display: grid; grid-template-columns: 3fr 1fr; gap: 20px; align-items: start; }
        @media (max-width: 960px) { .chapter-workspace { grid-template-columns: 1fr; } }
        .chapter-main-card { padding: 0; overflow: hidden; }
        .chapter-tab-bar { display: flex; border-bottom: 1px solid #e5e7eb; }
        .chapter-tab-btn {
            padding: 12px 18px; font-size: 14px; background: none; border: none; cursor: pointer;
            border-bottom: 2px solid transparent; color: #6b7280;
        }
        .chapter-tab-btn.active { border-bottom-color: #e11d48; color: #111827; font-weight: 500; }
        .chapter-tab-panel { padding: 20px; }
        .pages-toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
        .pages-hint {
            background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 10px; padding: 10px 14px;
            font-size: 13px; color: #0369a1; margin-bottom: 12px;
        }
        .pages-selection-bar {
            display: none; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px;
            background: #f5f3ff; border: 1px solid #c4b5fd; border-radius: 10px; padding: 10px 14px; margin-bottom: 12px;
        }
        .pages-selection-bar.visible { display: flex; }
        .page-slot-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; margin-bottom: 16px; }
        @media (max-width: 1100px) { .page-slot-grid { grid-template-columns: repeat(4, 1fr); } }
        @media (max-width: 720px) { .page-slot-grid { grid-template-columns: repeat(3, 1fr); } }
        .page-slot {
            position: relative; aspect-ratio: 3/4; border-radius: 10px; cursor: pointer;
            display: flex; flex-direction: column; align-items: center; justify-content: center;
            overflow: hidden; transition: box-shadow 0.15s, border-color 0.15s;
        }
        .page-slot-num { position: absolute; top: 6px; left: 8px; font-size: 11px; font-weight: 700; color: #374151; z-index: 2; }
        .page-slot.state-empty { border: 2px dashed #d1d5db; background: #f9fafb; color: #9ca3af; }
        .page-slot.state-empty:hover { border-color: #9ca3af; background: #f3f4f6; }
        .page-slot.state-uploaded { border: 2px solid #3b82f6; background: #eff6ff; }
        .page-slot.state-task { border: 2px solid #14b8a6; background: #f0fdfa; }
        .page-slot.state-selected { box-shadow: 0 0 0 3px #8b5cf6; border-color: #7c3aed !important; }
        .page-slot img { width: 100%; height: 100%; object-fit: cover; display: block; }
        .page-slot-initials {
            position: absolute; bottom: 34px; right: 8px; width: 28px; height: 28px; border-radius: 50%;
            background: #059669; color: #fff; font-size: 10px; font-weight: 700;
            display: flex; align-items: center; justify-content: center; z-index: 6;
        }
        .page-slot-add {
            border: 2px dashed #e11d48; background: #fff1f2; color: #e11d48; font-size: 24px; font-weight: 300;
        }
        .page-slot-add:hover { background: #ffe4e6; }
        .page-slot-upload-label { font-size: 11px; text-align: center; padding: 8px; }
        .page-download-btn {
            position: absolute; right: 36px; top: 6px; z-index: 5;
            width: 24px; height: 24px; border-radius: 50%; background: rgba(255,255,255,0.92);
            border: 1px solid #d1d5db; color: #111827; display: flex; align-items: center; justify-content: center;
            font-size: 13px; text-decoration: none; box-shadow: 0 2px 8px rgba(15,23,42,0.12);
        }
        .page-download-btn:hover { background: #fff; border-color: #9ca3af; }
        .page-stage-track {
            position: absolute; left: 6px; right: 6px; bottom: 6px; z-index: 3;
            display: grid; grid-template-columns: repeat(5, 1fr); gap: 3px;
            background: rgba(255,255,255,0.82); border-radius: 999px; padding: 3px;
        }
        .page-stage-dot {
            height: 18px; border-radius: 999px; display: flex; align-items: center; justify-content: center;
            font-size: 9px; font-weight: 800; color: #9ca3af; background: #e5e7eb;
        }
        .page-stage-dot.done { color: #fff; background: #059669; }
        .page-stage-dot.current { color: #92400e; background: #fde68a; opacity: 0.72; }
        .page-stage-picker {
            display: flex; flex-wrap: wrap; gap: 6px; align-items: center;
            padding: 5px 8px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff;
        }
        .page-stage-picker label { display: flex; gap: 4px; align-items: center; font-size: 11px; font-weight: 700; color: #4b5563; }
        .page-stage-picker input { margin: 0; }
        .page-upload-preview {
            min-height: 220px; border: 1px solid #e5e7eb; border-radius: 10px;
            display: flex; align-items: center; justify-content: center; background: #f9fafb; overflow: hidden;
        }
        .page-upload-preview img { width: 100%; max-height: 48vh; object-fit: contain; display: block; }
        .page-upload-modal-actions { display: flex; justify-content: space-between; gap: 8px; align-items: center; flex-wrap: wrap; }
        .page-status-legend { display: grid; gap: 9px; margin-top: 12px; }
        .legend-row { display: grid; grid-template-columns: 28px 1fr; gap: 10px; align-items: center; font-size: 12px; color: #4b5563; }
        .legend-row strong { display: block; font-size: 12px; color: #111827; margin-bottom: 1px; }
        .legend-swatch {
            width: 24px; height: 24px; border-radius: 7px; background: #f9fafb; box-sizing: border-box;
            box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.04);
        }
        .legend-empty { border: 2px dashed #d1d5db; }
        .legend-uploaded { border: 2px solid #3b82f6; background: #eff6ff; }
        .legend-task { border: 2px solid #14b8a6; background: #f0fdfa; }
        .legend-selected { border: 2px solid #7c3aed; background: #f5f3ff; box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.22); }
        .legend-progress { border: 2px solid #e11d48; background: #fff1f2; }
        .legend-submitted { border: 2px solid #f59e0b; background: #fffbeb; }
        .legend-complete {
            border: 2px solid transparent;
            background:
                linear-gradient(#fff, #fff) padding-box,
                conic-gradient(#ef4444, #f59e0b, #22c55e, #06b6d4, #6366f1, #ec4899, #ef4444) border-box;
        }
        .sidebar-task-mini { font-size: 12px; padding: 8px 0; border-bottom: 1px solid #f3f4f6; }
        .sidebar-task-mini:last-child { border-bottom: none; }
        .assign-chips, .assign-stage-summary {
            display: flex; flex-wrap: wrap; gap: 6px; min-height: 28px; max-height: 86px;
            overflow-y: auto; align-content: flex-start;
        }
        .assign-chip {
            background: #ede9fe; border: 1px solid #c4b5fd; color: #5b21b6; border-radius: 999px;
            padding: 4px 10px; font-size: 12px; font-weight: 600; line-height: 1.25;
            max-width: 100%; overflow-wrap: anywhere;
        }
        .task-actions-cell { position: relative; vertical-align: top; }
        .task-row-actions { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
        .task-action-popover {
            display: none; position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%);
            width: min(360px, calc(100vw - 32px)); z-index: 1200; pointer-events: auto;
            background: #fff; border: 1px solid #e5e7eb; border-radius: 12px;
            padding: 16px; box-shadow: 0 24px 60px rgba(15, 23, 42, 0.22);
        }
        .task-action-popover.open { display: block; }
        .task-action-popover strong { display: block; font-size: 14px; margin-bottom: 10px; }
        .task-action-popover textarea {
            width: 100%; min-height: 72px; resize: vertical; border: 1px solid #e5e7eb;
            border-radius: 8px; padding: 8px 10px; font-size: 13px; box-sizing: border-box;
        }
        .task-action-popover .popover-helper { font-size: 12px; color: #6b7280; margin: 6px 0 10px; }
        .task-action-popover .popover-counter { font-size: 11px; color: #9ca3af; text-align: right; margin-top: 4px; }
        .task-action-popover .popover-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
        .task-popover-scrim {
            display: none; position: fixed; inset: 0; z-index: 1190;
            background: rgba(15, 23, 42, 0.24);
        }
        .task-popover-scrim.open { display: block; }
        .task-decision-label { font-size: 12px; font-weight: 700; padding: 4px 10px; border-radius: 999px; }
        .task-decision-label.approved { color: #047857; background: #ecfdf5; border: 1px solid #a7f3d0; }
        .task-decision-label.rejected { color: #b91c1c; background: #fef2f2; border: 1px solid #fecaca; }
        #chapterTaskTableWrap { overflow: visible; }
        .page-slot.task-in-progress { border-color: #e11d48 !important; }
        .page-slot.task-submitted { border-color: #f59e0b !important; }
        .page-slot.task-approved { border-color: #3b82f6 !important; }
        .page-slot.stage-complete {
            border: 2px solid transparent !important;
            background:
                linear-gradient(#fff, #fff) padding-box,
                conic-gradient(#ef4444, #f59e0b, #22c55e, #06b6d4, #6366f1, #ec4899, #ef4444) border-box;
            box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.18);
        }
        .page-slot-status-icon {
            position: absolute; top: 5px; right: 6px;
            width: 16px; height: 16px; border-radius: 50%;
            display: flex; align-items: center; justify-content: center;
            font-size: 8px; z-index: 3; cursor: default;
        }
        .page-slot-status-icon.icon-in-progress { background: #e11d48; color: #fff; }
        .page-slot-status-icon.icon-submitted { background: #f59e0b; color: #fff; }
        .page-slot-status-icon.icon-approved { background: #3b82f6; color: #fff; }
        .page-slot-status-icon .icon-tooltip {
            display: none; position: absolute; top: 20px; right: 0;
            background: #1f2937; color: #fff; font-size: 10px;
            padding: 3px 7px; border-radius: 5px; white-space: nowrap; z-index: 20;
        }
        .page-slot-status-icon:hover .icon-tooltip { display: block; }
        .page-slot-lock {
            position: absolute; top: 4px; left: 20px;
            font-size: 10px; z-index: 4; line-height: 1;
            pointer-events: none; opacity: 0.7;
        }
        .task-expand-btn {
            background: none; border: 1px solid #e5e7eb; cursor: pointer;
            font-size: 11px; color: #6b7280; padding: 2px 8px; border-radius: 5px; margin-left: 6px;
        }
        .task-expand-btn:hover { background: #f3f4f6; }
        .task-inline-row td { padding: 0 !important; }
        .task-inline-body {
            padding: 12px 16px; background: #f8fafc; border-top: 1px solid #e5e7eb;
            display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-start;
        }
        .task-page-mini {
            width: 72px; text-align: center; font-size: 10px; color: #6b7280;
        }
        .task-page-mini img {
            width: 72px; aspect-ratio: 3/4; object-fit: cover;
            border-radius: 6px; border: 1.5px solid #e5e7eb; display: block;
            margin-bottom: 3px;
        }
        .task-page-mini .no-thumb {
            width: 72px; aspect-ratio: 3/4; display: flex; align-items: center;
            justify-content: center; background: #f1f5f9; border-radius: 6px;
            border: 1.5px dashed #cbd5e1; color: #94a3b8; font-size: 18px; margin-bottom: 3px;
        }
    </style>
</head>
<body>
<jsp:include page="../common/header.jsp" />

<div id="detailResult" class="alert error" style="display:none;margin-bottom:12px;"></div>

<div id="breadcrumb" style="font-size:14px;color:#6b7280;margin-bottom:16px;display:flex;align-items:center;gap:6px;flex-wrap:wrap;">
    <a href="${pageContext.request.contextPath}/main/series" style="color:#6b7280;">My Series</a>
    <span>›</span>
    <a id="breadcrumbSeries" href="#" style="color:#6b7280;"></a>
    <span>›</span>
    <span id="breadcrumbChapter" style="color:#111827;font-weight:500;"></span>
    <span id="breadcrumbStatusPill" style="margin-left:6px;"></span>
</div>

<div style="display:flex;justify-content:flex-end;align-items:center;margin-bottom:20px;flex-wrap:wrap;gap:8px;">
    <button id="btnDelete" class="btn small" type="button" style="color:#ef4444;border-color:#fecaca;display:none;">Delete chapter</button>
    <button id="btnMarkDone" class="btn primary" type="button" style="display:none;">Submit for review</button>
    <a id="btnManuscriptWorkspace" href="#" class="btn small" style="display:none;">📝 Manuscript Workspace</a>
</div>

<div class="chapter-workspace">
    <div class="section-card chapter-main-card">
        <div id="tabBar" class="chapter-tab-bar">
            <button class="chapter-tab-btn active" type="button" data-tab="pages">
                Pages <span id="tabPageCount" class="status-chip" style="font-size:11px;margin-left:4px;">0</span>
            </button>
            <button class="chapter-tab-btn" type="button" data-tab="tasks">
                Tasks <span id="tabTaskCount" class="status-chip" style="font-size:11px;margin-left:4px;">0</span>
            </button>
            <button class="chapter-tab-btn" type="button" data-tab="edit">Edit details</button>
        </div>

        <div id="tabPages" class="chapter-tab-panel">
            <div class="pages-toolbar">
                <span id="pageCountLabel" style="font-size:13px;color:#6b7280;font-weight:500;">Đang tải...</span>
                <div id="pagesOwnerActions" style="display:none;gap:8px;">
                    <button class="btn small primary" type="button" id="btnAddPage">+ Thêm trang</button>
                    <input id="singleFileInput" type="file" accept="image/*" style="display:none;" />
                </div>
            </div>
            <div class="pages-hint" id="pagesHint">
                Nhấp ô trang để upload/thay ảnh và chọn stage đã hoàn thành.
                Giữ <strong>Shift</strong> + nhấp để chọn dải trang, rồi <strong>Gán task</strong>.
                Ảnh lưu đường dẫn trên database; file nằm <code>web/img/chapter/</code> (không đi theo git pull).
            </div>
            <div id="selectionBar" class="pages-selection-bar">
                <span id="selectionLabel" style="font-size:13px;font-weight:500;color:#5b21b6;">0 trang đã chọn</span>
                <div style="display:flex;gap:8px;">
                    <button class="btn small primary" type="button" id="btnAssignFromSelection">Gán task</button>
                    <button class="btn small" type="button" id="btnClearSelection">Bỏ chọn</button>
                </div>
            </div>
            <div id="pageSlotGrid" class="page-slot-grid">
                <p style="color:#9ca3af;grid-column:1/-1;font-size:13px;">Đang tải trang...</p>
            </div>
            <div id="progressSection">
                <div style="display:flex;justify-content:space-between;font-size:13px;margin-bottom:6px;">
                    <span style="color:#6b7280;">Tiến độ task trên trang</span>
                    <span id="progressLabel" style="font-weight:500;"></span>
                </div>
                <div class="progress"><span id="progressFill" style="width:0%;background:#8b5cf6;"></span></div>
            </div>
        </div>

        <div id="tabTasks" class="chapter-tab-panel" style="display:none;">
            <div id="chapterTaskTableWrap" class="section-card" style="padding:0;overflow:visible;border:none;box-shadow:none;">
                <table class="data-table" style="overflow:visible;">
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
            <div id="taskPopoverHost" style="position:absolute;left:-9999px;width:0;height:0;overflow:hidden;" aria-hidden="true">
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

        <div id="tabEdit" class="chapter-tab-panel" style="display:none;">
            <form id="chapterUpdateForm" class="form-grid chapter-inline-update-form" onsubmit="return false;">
                <input name="chapterId" type="hidden" id="updateChapterId" />
                <label class="field-label" for="updateTitle">Title</label>
                <input id="updateTitle" name="title" type="text" required />
                <label class="field-label" for="updateDeadline">Submission deadline</label>
                <input id="updateDeadline" name="submissionDeadline" type="date" required />
                <div id="updateError" class="alert error" style="display:none;"></div>
            </form>
        </div>
    </div>

    <aside>
        <div class="panel" style="margin-bottom:14px;">
            <strong id="panelChapterTitle" style="font-size:15px;"></strong>
            <p id="panelSeriesName" class="section-desc" style="margin:4px 0 14px;"></p>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
                <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:10px;">
                    <div style="font-size:11px;color:#9ca3af;margin-bottom:3px;">Deadline</div>
                    <div id="metaDeadline"></div>
                    <div id="metaDeadlineSub" style="font-size:11px;margin-top:4px;"></div>
                </div>
                <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:10px;">
                    <div style="font-size:11px;color:#9ca3af;margin-bottom:3px;">Pages</div>
                    <div id="metaPages" style="font-size:14px;font-weight:600;"></div>
                </div>
                <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:10px;">
                    <div style="font-size:11px;color:#9ca3af;margin-bottom:3px;">Status</div>
                    <div id="metaStatus" style="margin-top:4px;"></div>
                </div>
                <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:10px;">
                    <div style="font-size:11px;color:#9ca3af;margin-bottom:3px;">Task progress</div>
                    <div id="metaProgress" style="font-size:14px;font-weight:600;"></div>
                </div>
            </div>
        </div>
        <div class="panel" style="margin-bottom:14px;">
            <strong style="font-size:13px;">Chú thích trạng thái trang</strong>
            <div class="page-status-legend">
                <div class="legend-row">
                    <span class="legend-swatch legend-empty"></span>
                    <span><strong>Trống</strong>Chưa có ảnh page.</span>
                </div>
                <div class="legend-row">
                    <span class="legend-swatch legend-uploaded"></span>
                    <span><strong>Đã upload</strong>Có ảnh, chưa gán task.</span>
                </div>
                <div class="legend-row">
                    <span class="legend-swatch legend-task"></span>
                    <span><strong>Đã gán task</strong>Trang đang thuộc một task.</span>
                </div>
                <div class="legend-row">
                    <span class="legend-swatch legend-selected"></span>
                    <span><strong>&#272;ang ch&#7885;n &#273;&#7875; giao task</strong>Ch&#7881; l&#224; page &#273;ang &#273;&#432;&#7907;c ch&#7885;n, ch&#432;a t&#7841;o task.</span>
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
                    <span class="legend-swatch legend-complete"></span>
                    <span><strong>Hoàn tất</strong>Page đã xong đủ 5 stage.</span>
                </div>
            </div>
        </div>
        <div class="panel">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
                <strong style="font-size:13px;">Tasks</strong>
            </div>
            <div id="sidebarTaskList"><p class="section-desc" style="margin:0;">Loading...</p></div>
        </div>
    </aside>
</div>

<div id="pageCompareModal" style="display:none;position:fixed;inset:0;background:rgba(0,0,0,0.6);z-index:200;align-items:center;justify-content:center;">
  <div style="background:#fff;border-radius:14px;padding:22px;max-width:860px;width:95vw;max-height:90vh;overflow-y:auto;position:relative;">
    <button id="pageCompareClose" style="position:absolute;top:12px;right:16px;background:none;border:none;font-size:22px;cursor:pointer;color:#6b7280;">&times;</button>
    <div id="pageCompareTitle" style="font-size:15px;font-weight:700;margin-bottom:14px;"></div>
    <div id="pageCompareBody"></div>
  </div>
</div>

<div id="pageUploadModal" class="modal-backdrop" aria-hidden="true">
    <div class="modal-card modal-card-wide" role="dialog" aria-modal="true" aria-labelledby="pageUploadTitle">
        <button class="modal-close" type="button" data-modal-close aria-label="Close">&times;</button>
        <h3 id="pageUploadTitle" class="section-title compact-title">Upload page</h3>
        <p id="pageUploadSubtitle" class="section-desc"></p>
        <div id="pageUploadPreview" class="page-upload-preview"></div>
        <label class="field-label" for="pageModalFileInput" style="margin-top:12px;">Image file</label>
        <input id="pageModalFileInput" type="file" accept="image/*" />
        <label class="field-label" style="margin-top:12px;">Stages completed</label>
        <div id="pageUploadStagePicker" class="page-stage-picker" title="Tick stages completed by this page image">
            <label><input type="checkbox" value="SKETCHING" />Sketching</label>
            <label><input type="checkbox" value="INKING" />Inking</label>
            <label><input type="checkbox" value="COLORING" />Coloring</label>
            <label><input type="checkbox" value="SCREENTONE" />Screentone</label>
            <label><input type="checkbox" value="LETTERING" />Lettering</label>
        </div>
        <div id="pageUploadError" class="alert error" style="display:none;margin-top:12px;"></div>
        <div class="page-upload-modal-actions" style="margin-top:14px;">
            <a id="pageUploadDownload" class="btn small" href="#" download style="display:none;">Download current</a>
            <button class="btn small danger-soft" type="button" id="pageUploadDelete" style="display:none;">Delete page</button>
            <div style="display:flex;gap:8px;margin-left:auto;">
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
            <div id="assignPageChips" class="assign-chips"><span class="section-desc" style="margin:0;">Chưa chọn trang — chọn trên lưới Pages hoặc mở từ sidebar sau khi chọn.</span></div>
            <label class="field-label">Work to do</label>
            <div id="assignTaskTypeSummary" class="assign-stage-summary section-desc" style="margin:0;">Tự tính theo stage tiếp theo của từng trang.</div>
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
            <div id="assignTaskError" class="alert error" style="display:none;"></div>
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
            <div id="taskReassignError" class="alert error" style="display:none;"></div>
            <button class="btn primary" type="submit">Confirm reassign</button>
        </form>
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
        return currentUser && currentUser.roles && currentUser.roles.indexOf(role) !== -1;
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
        if (!formatted) { return '<span style="color:#9ca3af;">—</span>'; }
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

    function countWithTask() {
        var n = 0;
        for (var i = 0; i < pageSlots.length; i++) {
            if (pageSlots[i].taskId) { n++; }
        }
        return n;
    }

    function slotStateClass(slot) {
        if (slot.taskId) { return 'state-task'; }
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
            el.innerHTML = '<span class="section-desc" style="margin:0;">Chưa chọn trang — chọn trên lưới Pages trước khi gán.</span>';
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
            grid.innerHTML = '<p style="color:#9ca3af;grid-column:1/-1;font-size:13px;">Chưa có ô trang. '
                + (owner ? 'Nhấn + Thêm trang để bắt đầu.' : 'No page slots yet.') + '</p>'
                + (owner ? '<div class="page-slot page-slot-add" data-add-page="1" title="Thêm trang" style="grid-column:span 1;">+</div>' : '');
            return;
        }

        var html = pageSlots.map(function (slot, index) {
            var selectable = isAssignablePage(slot);
            if (!selectable && selectedPageIds[String(slot.id)]) {
                delete selectedPageIds[String(slot.id)];
            }
            var selected = selectable && !!selectedPageIds[String(slot.id)];
            var state = slotStateClass(slot);
            var taskStatusCls = '';
            var statusIconHtml = '';
            if (slot.taskId) {
                var ts = String(slot.taskStatus || '').toUpperCase();
                if (ts === 'IN_PROGRESS') {
                    taskStatusCls = ' task-in-progress';
                    statusIconHtml = '<span class="page-slot-status-icon icon-in-progress">●<span class="icon-tooltip">Đang làm</span></span>';
                } else if (ts === 'SUBMITTED') {
                    taskStatusCls = ' task-submitted';
                    statusIconHtml = '<span class="page-slot-status-icon icon-submitted">●<span class="icon-tooltip">Đã nộp</span></span>';
                } else if (ts === 'APPROVED') {
                    taskStatusCls = ' task-approved';
                    statusIconHtml = '<span class="page-slot-status-icon icon-approved">●<span class="icon-tooltip">Đã duyệt</span></span>';
                }
            }
            var completeStageCls = normalizeStage(slot.completedStage) === 'LETTERING' ? ' stage-complete' : '';
            var cls = 'page-slot ' + state + taskStatusCls + completeStageCls + (selected ? ' state-selected' : '');
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
            return '<div class="' + cls + '" data-page-id="' + slot.id + '" data-slot-index="' + index + '" data-page-number="' + slot.pageNumber + '">' + num + lockIconHtml + statusIconHtml + inner + '</div>';
        }).join('');

        if (owner) {
            html += '<div class="page-slot page-slot-add" data-add-page="1" title="Thêm trang">+</div>';
        }
        grid.innerHTML = html;
        renderSelectionBar();
    }

    function renderPageProgress() {
        var total = pageSlots.length;
        var withTask = countWithTask();
        var uploaded = countUploaded();
        var pct = total > 0 ? Math.round((withTask / total) * 100) : 0;
        document.getElementById('progressLabel').textContent = withTask + ' / ' + total + ' pages có task';
        document.getElementById('progressFill').style.width = pct + '%';
        document.getElementById('pageCountLabel').textContent = uploaded + ' / ' + total + ' đã upload';
        document.getElementById('tabPageCount').textContent = total;
        document.getElementById('metaPages').textContent = uploaded + ' / ' + total;
        document.getElementById('metaProgress').textContent = withTask + ' / ' + total + ' có task';
    }

    function renderSidebarTasks() {
        var el = document.getElementById('sidebarTaskList');
        if (!chapterTasks.length) {
            el.innerHTML = '<p class="section-desc" style="margin:0;">Chưa có task.</p>';
            return;
        }
        var preview = chapterTasks.slice(0, 5);
        el.innerHTML = preview.map(function (t) {
            return '<div class="sidebar-task-mini">'
                + '<strong>#' + t.id + '</strong> p.' + t.pageRangeStart + '-' + t.pageRangeEnd
                + ' · ' + escapeHtml(formatStatus(t.taskType))
                + '<br/><span class="status-chip ' + taskStatusClass(t.status) + '" style="font-size:10px;">' + formatStatus(t.status) + '</span>'
                + '</div>';
        }).join('')
            + (chapterTasks.length > 5 ? '<p class="section-desc" style="margin:8px 0 0;">+' + (chapterTasks.length - 5) + ' task khác — xem tab Tasks</p>' : '');
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
        var canSubmit = owner && progress >= 100 && String(chapter.status || '').toUpperCase() === 'COMPLETE'
            && seriesData && String(seriesData.status || '').toUpperCase() !== 'CANCELLED';

        document.getElementById('btnDelete').style.display = (owner && String(chapter.status || '').toUpperCase() === 'PLANNING') ? '' : 'none';
        document.getElementById('btnMarkDone').style.display = canSubmit ? '' : 'none';
        
        // Show manuscript workspace button for EDITORIAL_REVIEW status
        var isEditorialReview = String(chapter.status || '').toUpperCase() === 'EDITORIAL_REVIEW';
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
                + '<tr class="task-inline-row" id="task-inline-' + task.id + '" style="display:none;">'
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
            bodyEl.innerHTML = '<span style="color:#9ca3af;font-size:12px;">Đang tải...</span>';
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
                bodyEl.innerHTML = html || '<span style="color:#9ca3af;font-size:12px;">Chưa có ảnh nào.</span>';
                taskInlineLoaded[taskId] = true;
            } catch (e) {
                bodyEl.innerHTML = '<span style="color:#ef4444;font-size:12px;">' + escapeHtml(e.message) + '</span>';
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
                ? (isOwner() && !slot.taskId ? '<div style="display:flex;justify-content:flex-end;margin-bottom:10px;"><button class="btn small primary" type="button" id="pageCompareEdit">Upload / replace</button></div>' : '')
                    + '<img src="' + escapeHtml(origUrl) + '" style="width:100%;border-radius:8px;max-height:70vh;object-fit:contain;" />'
                : '<div style="color:#9ca3af;text-align:center;padding:40px;">Chưa có ảnh</div>';
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
            body.innerHTML = '<div style="padding:30px;text-align:center;color:#6b7280;">Đang tải ảnh...</div>';
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
                '<div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;">'
                + '<div><div style="font-size:11px;font-weight:700;color:#6b7280;text-transform:uppercase;margin-bottom:6px;">Bản gốc (Mangaka)</div>'
                + (origUrl ? '<img src="' + escapeHtml(origUrl) + '" style="width:100%;border-radius:8px;border:1px solid #e5e7eb;object-fit:contain;max-height:60vh;" />' : '<div style="height:180px;display:flex;align-items:center;justify-content:center;background:#f9fafb;border-radius:8px;border:1.5px dashed #d1d5db;color:#9ca3af;">Không có ảnh gốc</div>')
                + '</div>'
                + '<div><div style="font-size:11px;font-weight:700;color:#f59e0b;text-transform:uppercase;margin-bottom:6px;">Bản assistant nộp</div>'
                + (assistantUrl ? '<img src="' + escapeHtml(assistantUrl) + '" style="width:100%;border-radius:8px;border:2px solid #f59e0b;object-fit:contain;max-height:60vh;" />' : '<div style="height:180px;display:flex;align-items:center;justify-content:center;background:#f9fafb;border-radius:8px;border:1.5px dashed #d1d5db;color:#9ca3af;">Chưa có ảnh</div>')
                + '</div></div>';
            return;
        }
        var finalUrl = assistantUrl || origUrl;
        body.innerHTML = finalUrl
            ? '<div style="text-align:center;margin-bottom:8px;"><span style="background:#dbeafe;color:#1d4ed8;font-size:11px;padding:3px 10px;border-radius:999px;font-weight:600;">✓ Đã được duyệt</span></div>'
                + '<img src="' + escapeHtml(finalUrl) + '" style="width:100%;border-radius:8px;border:2px solid #3b82f6;object-fit:contain;max-height:65vh;" />'
            : '<div style="color:#9ca3af;text-align:center;padding:40px;">Không có ảnh</div>';
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

    async function fillAssistantSelect() {
        var select = document.getElementById('assignAssistantId');
        var reassignSelect = document.getElementById('taskReassignAssistantId');
        if (!chapter || !select) { return; }
        select.innerHTML = '<option value="">Loading assistants...</option>';
        if (reassignSelect) { reassignSelect.innerHTML = '<option value="">Loading assistants...</option>'; }
        try {
            var res = await callApi('GET', '/api/v1/series/' + chapter.seriesId + '/assistants');
            var assistants = res.data || [];
            var options = '<option value="">Select Assistant</option>' + assistants.map(function (a) {
                return '<option value="' + a.id + '">#' + a.id + ' - ' + escapeHtml(a.fullName || a.username) + '</option>';
            }).join('');
            select.innerHTML = options;
            if (reassignSelect) { reassignSelect.innerHTML = options; }
        } catch (err) {
            select.innerHTML = '<option value="">Cannot load assistants</option>';
            if (reassignSelect) { reassignSelect.innerHTML = '<option value="">Cannot load assistants</option>'; }
            showError(err.message);
        }
    }

    function updateAssignDueConstraints() {
        var dueInput = document.getElementById('assignDueDate');
        var hint = document.getElementById('assignDueHint');
        if (!dueInput) { return; }
        dueInput.min = todayIso();
        dueInput.removeAttribute('max');
        var latest = chapter && chapter.submissionDeadline ? addDaysIso(chapter.submissionDeadline, -3) : '';
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

        if (!isOwner()) {
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

    document.addEventListener('click', async function (e) {
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

    loadData();
})();
</script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
