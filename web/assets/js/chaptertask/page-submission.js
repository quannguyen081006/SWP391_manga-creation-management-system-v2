/**
 * page-submission.js
 * Page image upload/submit grid for the Assistant (Task Detail view).
 * Config: global PAGE_TASK (injected from JSP, includes taskId, chapterId, pageStart,
 *         pageEnd, taskType, status, canUpdate, canSubmit, ctx)
 *
 * TABLE OF CONTENTS
 * ──────────────────────────────────────────────────────────
 * 1. INITIALIZATION & STATE VARIABLES
 * 2. UTILITIES
 *    2a. escapeHtml, imageUrl
 *    2b. API helpers (readJson, apiGet, apiPost, apiDelete)
 *    2c. Page counting (totalPages, uploadedCount)
 * 3. STAGE WORKFLOW          (stageOrder, nextStageForPage)
 * 4. RENDER UI
 *    4a. Progress bar        (renderProgressBar)
 *    4b. Per-page card       (renderCard)
 *    4c. Overall grid        (renderGrid, renderAll, updateCard)
 *    4d. Submit bar          (renderSubmitBar)
 *    4e. Light-box preview   (openImagePreview)
 * 5. FILE HANDLING & UPLOAD
 *    5a. Choose file         (pickFile)
 *    5b. Initial upload      (handleUpload)
 *    5c. Replace image       (handleReplace)
 *    5d. Delete image        (handleDelete)
 * 6. SUBMIT TASK             (handleSubmit)
 * 7. STARTUP                 (initPageGrid)
 * 8. EVENT LISTENERS
 * ──────────────────────────────────────────────────────────
 */

(function () {
    'use strict';

    var configScript = document.currentScript;
    if (!configScript) {
        return;
    }

    var PAGE_TASK = {
        taskId: Number(configScript.getAttribute('data-task-id')),
        chapterId: Number(configScript.getAttribute('data-chapter-id')),
        pageStart: Number(configScript.getAttribute('data-page-start')),
        pageEnd: Number(configScript.getAttribute('data-page-end')),
        taskTypes: configScript.getAttribute('data-task-types') || '',
        status: configScript.getAttribute('data-status') || '',
        canUpdate: configScript.getAttribute('data-can-update') === 'true',
        canSubmit: configScript.getAttribute('data-can-submit') === 'true',
        ctx: configScript.getAttribute('data-context-path') || ''
    };

    // ─── 1. INITIALIZATION & STATE VARIABLES ────────────────────────────────
    // pageImages: map of pageNumber → image object (null if not yet uploaded)
    var pageImages = {};
    // pageSlots: map of pageNumber → slot object from the chapter (contains completedStage)
    var pageSlots = {};
    // loadingPage: page number currently uploading/deleting (null = nothing running)
    var loadingPage = null;
    // pendingPageNum/pendingAction: remembers the page and action when the file input is triggered
    var pendingPageNum = null;
    var pendingAction = 'upload';  // 'upload' or 'replace'

    var gridEl         = document.getElementById('pageGrid');
    var progressEl     = document.getElementById('pageProgressBar');
    var submitBarEl    = document.getElementById('stickySubmitBar');
    var fileInput      = document.getElementById('pageFileInput');  // hidden input[type=file]
    var toastContainer = document.getElementById('toastContainer');

    // Cache the approved state to avoid recomputing it repeatedly
    var isApproved = String(PAGE_TASK.status || '').toUpperCase() === 'APPROVED';

    // ─── 2c. UTILITIES: PAGE COUNTING ───────────────────────────────────────
    function totalPages() {
        return PAGE_TASK.pageEnd - PAGE_TASK.pageStart + 1;
    }

    // Number of pages that already have an image (has an id → saved on the server)
    function uploadedCount() {
        var count = 0;
        for (var p = PAGE_TASK.pageStart; p <= PAGE_TASK.pageEnd; p++) {
            if (pageImages[p] && pageImages[p].id) {
                count++;
            }
        }
        return count;
    }

    // ─── 2a. UTILITIES: ESCAPE HTML & IMAGE URL ─────────────────────────────
    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value).replace(/[&<>"']/g, function (ch) {
            return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch];
        });
    }

    // Normalize the image URL: prepend contextPath if missing
    function imageUrl(fileUrl) {
        var url = String(fileUrl || '');
        if (url.indexOf('http://') === 0 || url.indexOf('https://') === 0) {
            return url;
        }
        if (url.indexOf(PAGE_TASK.ctx + '/') === 0) {
            return url;
        }
        return PAGE_TASK.ctx + url;
    }

    // ─── 2b. UTILITIES: API HELPERS ─────────────────────────────────────────
    // Parse the response JSON; throw an Error if the HTTP call failed or body.success === false
    async function readJson(res) {
        var text = await res.text();
        var body = null;
        try {
            body = text ? JSON.parse(text) : null;
        } catch (e) {
            /* ignore parse error */
        }
        if (!res.ok || (body && body.success === false)) {
            var msg = (body && (body.message || (body.errors && body.errors[0])))
                || text
                || ('HTTP ' + res.status);
            throw new Error(msg);
        }
        return body;
    }

    // Return body.data if present, otherwise the whole body
    async function apiGet(url) {
        var body = await readJson(await fetch(url, { headers: { Accept: 'application/json' } }));
        return body && body.data !== undefined ? body.data : body;
    }

    // POST multipart/form-data (used for image uploads)
    async function apiPost(url, formData) {
        var body = await readJson(await fetch(url, {
            method: 'POST',
            headers: { Accept: 'application/json' },
            body: formData
        }));
        return body && body.data !== undefined ? body.data : body;
    }

    async function apiDelete(url) {
        await readJson(await fetch(url, {
            method: 'DELETE',
            headers: { Accept: 'application/json' }
        }));
    }

    // CSS class for the progress bar based on the task status
    function statusProgressClass() {
        var s = String(PAGE_TASK.status || '').toUpperCase();
        if (s === 'PENDING') return 'status-pending';
        if (s === 'IN_PROGRESS') return 'status-in-progress';
        if (s === 'SUBMITTED') return 'status-submitted';
        if (s === 'APPROVED') return 'status-approved';
        if (s === 'REJECTED') return 'status-rejected';
        if (s === 'OVERDUE') return 'status-overdue';
        return 'status-in-progress';
    }

    // Toast notification that auto-dismisses after 3 seconds
    function showToast(message, type) {
        if (!toastContainer) {
            return;
        }
        var toast = document.createElement('div');
        toast.className = 'toast ' + (type === 'error' ? 'error' : 'success');
        toast.textContent = message;
        toastContainer.appendChild(toast);
        setTimeout(function () {
            toast.remove();
        }, 3000);
    }

    // Load all images for the task from the server, storing them into pageImages by pageNumber
    async function loadImages() {
        var data = await apiGet(PAGE_TASK.ctx + '/api/v1/tasks/' + PAGE_TASK.taskId + '/images');
        (data || []).forEach(function (img) {
            var pn = img.pageNumber;
            // Only keep images that fall within this task's page range
            if (pn >= PAGE_TASK.pageStart && pn <= PAGE_TASK.pageEnd) {
                pageImages[pn] = img;
            }
        });
    }

    // Load the chapter's page slots to determine the completedStage of each page
    async function loadPageSlots() {
        var data = await apiGet(PAGE_TASK.ctx + '/api/v1/chapters/' + PAGE_TASK.chapterId + '/pages');
        (data || []).forEach(function (slot) {
            if (slot.pageNumber >= PAGE_TASK.pageStart && slot.pageNumber <= PAGE_TASK.pageEnd) {
                pageSlots[slot.pageNumber] = slot;
            }
        });
    }

    // ─── 3. STAGE WORKFLOW ────────────────────────────────────────────────────
    // Order of production stages for a manga page
    var stageOrder = ['SKETCHING', 'INKING', 'COLORING', 'SCREENTONE', 'LETTERING'];

    function normalizeStage(stage) {
        var s = String(stage || '').trim().toUpperCase();
        return stageOrder.indexOf(s) >= 0 ? s : '';
    }

    // Return the next stage for the page based on completedStage in pageSlots
    // If no stage yet → SKETCHING; if all stages are done → keep the last stage
    function nextStageForPage(pageNum) {
        var slot = pageSlots[pageNum] || {};
        var current = normalizeStage(slot.completedStage);
        if (!current) {
            return stageOrder[0];
        }
        return stageOrder[Math.min(stageOrder.indexOf(current) + 1, stageOrder.length - 1)];
    }

    // ─── 4a. RENDER: PROGRESS BAR ─────────────────────────────────────────────
    // Display the progress bar: X/Y pages uploaded, colored based on the task status
    function renderProgressBar() {
        if (!progressEl) {
            return;
        }
        var total = totalPages();
        var uploaded = uploadedCount();
        var pct = total === 0 ? 0 : Math.round((uploaded / total) * 100);
        progressEl.innerHTML =
            '<div class="page-progress-wrap">'
            + '<div class="page-progress-label">Task #' + escapeHtml(PAGE_TASK.taskId)
            + ' · ' + escapeHtml(PAGE_TASK.taskTypes)
            + ' · Pages ' + PAGE_TASK.pageStart + '–' + PAGE_TASK.pageEnd
            + ' (' + uploaded + '/' + total + ' uploaded) · ' + pct + '%</div>'
            + '<div class="page-progress-bar-bg">'
            + '<div class="page-progress-bar-fill ' + statusProgressClass()
            + '" style="width:' + pct + '%"></div>'
            + '</div></div>';
    }

    // ─── 4b. RENDER: PER-PAGE CARD ────────────────────────────────────────────
    // Each card displays:
    // - Has an image: thumbnail + file name + Download / Replace / Delete buttons
    // - No image yet: clickable area to upload (+ / Page N / stage)
    // - isApproved: only the Download button remains, no edits allowed
    // - loadingPage === pageNum: "Uploading…" overlay
    function renderCard(pageNum) {
        var img = pageImages[pageNum];
        var loading = loadingPage === pageNum;
        var cardClass = 'page-card' + (loading ? ' loading' : '');
        if (img) {
            cardClass += ' filled';
        }
        if (isApproved && img) {
            cardClass += ' approved';
        }

        var html = '<div class="' + cardClass + '" data-page="' + pageNum + '">';

        if (img) {
            var url = imageUrl(img.fileUrl);
            // "inherited" = image taken from the chapter (base image), not uploaded directly by the task
            var inherited = String(img.note || '').toUpperCase() === 'CHAPTER_PAGE' || !img.id;
            var approvedBadge = isApproved
                ? '<span class="approved-badge" title="Approved by Mangaka, image has been updated to the chapter">✓ Approved</span>'
                : (inherited ? '<span class="approved-badge" title="Base image from chapter">Base image</span>' : '');
            html += approvedBadge
                + '<img class="page-card-thumb" src="' + escapeHtml(url) + '" alt="Page ' + pageNum
                + (isApproved ? ' title="Approved by Mangaka, image has been updated to the chapter"' : '')
                + ' />'
                + '<div class="page-card-footer">'
                + '<div class="page-card-meta"><strong>Page ' + pageNum + '</strong>'
                + '<span>' + escapeHtml(nextStageForPage(pageNum)) + '</span>'
                + '<span>' + escapeHtml(img.originalFileName || '') + '</span></div>';

            if (PAGE_TASK.canUpdate && !isApproved) {
                // Base image không có nút Delete (không xóa được ảnh của chapter)
                html += '<div class="page-card-actions">'
                    + '<a class="btn small" href="' + escapeHtml(url) + '" download title="Download">↓</a>'
                    + '<button type="button" class="btn small" data-page-replace="' + pageNum + '" title="Replace">🔄</button>'
                    + (inherited ? '' : '<button type="button" class="btn small danger-soft" data-page-delete="' + pageNum + '" title="Delete">🗑</button>')
                    + '</div>';
            } else {
                // Đã approved hoặc không có quyền: chỉ download
                html += '<div class="page-card-actions">'
                    + '<a class="btn small" href="' + escapeHtml(url) + '" download title="Download">↓</a>'
                    + '</div>';
            }
            html += '</div>';
        } else {
            // Card rỗng: click để upload (nếu có quyền và không đang loading)
            var emptyClass = 'page-card-empty-body';
            var canClick = PAGE_TASK.canUpdate && !isApproved && !loadingPage;
            if (!canClick) {
                emptyClass += ' readonly';
            }
            html += '<div class="' + emptyClass + '"' + (canClick ? ' data-page-upload="' + pageNum + '"' : '') + '>'
                + (PAGE_TASK.canUpdate && !isApproved
                    ? '<span style="font-size:28px;line-height:1;">+</span><strong>Page ' + pageNum + '</strong><span>' + escapeHtml(nextStageForPage(pageNum)) + '</span><span>Click to upload</span>'
                    : '<strong>Page ' + pageNum + '</strong><span>' + escapeHtml(nextStageForPage(pageNum)) + '</span><span>No image</span>')
                + '</div>';
        }

        // Overlay khi đang upload
        if (loading) {
            html += '<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(255,255,255,0.7);font-size:13px;color:#6b7280;">Uploading…</div>';
        }

        html += '</div>';
        return html;
    }

    // ─── 4c. RENDER: GRID & UPDATE ────────────────────────────────────────────
    function renderGrid() {
        if (!gridEl) {
            return;
        }
        var html = '';
        for (var p = PAGE_TASK.pageStart; p <= PAGE_TASK.pageEnd; p++) {
            html += renderCard(p);
        }
        gridEl.innerHTML = html;
    }

    function renderAll() {
        renderProgressBar();
        renderGrid();
        renderSubmitBar();
    }

    // Cập nhật chỉ một card thay vì re-render toàn bộ grid (hiệu suất tốt hơn)
    function updateCard(pageNum) {
        if (!gridEl) {
            return;
        }
        var card = gridEl.querySelector('[data-page="' + pageNum + '"]');
        if (card) {
            var tmp = document.createElement('div');
            tmp.innerHTML = renderCard(pageNum);
            card.replaceWith(tmp.firstElementChild);
        } else {
            renderGrid();  // fallback nếu không tìm thấy card
        }
        renderProgressBar();
        renderSubmitBar();
    }

    // ─── 4d. RENDER: SUBMIT BAR ───────────────────────────────────────────────
    // Thanh sticky ở cuối trang: nút Submit chỉ enabled khi đã upload đủ trang
    // Chỉ hiển thị nếu PAGE_TASK.canSubmit = true
    function renderSubmitBar() {
        if (!submitBarEl) {
            return;
        }
        if (!PAGE_TASK.canSubmit) {
            submitBarEl.innerHTML = '';
            submitBarEl.style.display = 'none';
            return;
        }
        submitBarEl.style.display = '';
        var total = totalPages();
        var uploaded = uploadedCount();
        var complete = uploaded >= total;
        var disabledAttr = complete ? '' : ' disabled';
        var tooltip = complete
            ? ''
            : ' title="Please upload all ' + total + ' pages before submitting"';

        submitBarEl.innerHTML =
            '<div class="sticky-submit-bar">'
            + '<span class="submit-hint">' + uploaded + ' / ' + total + ' pages uploaded</span>'
            + '<button type="button" class="btn primary" id="pageSubmitBtn"' + disabledAttr + tooltip + '>Submit for Review</button>'
            + '</div>';
    }

    // ─── 4e. RENDER: LIGHT-BOX PREVIEW ───────────────────────────────────────
    function openImagePreview(pageNum) {
        var img = pageImages[pageNum];
        if (!img) {
            return;
        }
        var overlay = document.createElement('div');
        overlay.className = 'lightbox-overlay';
        overlay.innerHTML =
            '<button type="button" class="lightbox-close" aria-label="Close">&times;</button>'
            + '<img class="lightbox-img" src="' + escapeHtml(imageUrl(img.fileUrl)) + '" alt="Page ' + pageNum + '" />'
            + '<div class="lightbox-caption">Page ' + pageNum + ' - ' + escapeHtml(img.originalFileName || '') + '</div>';
        function close() {
            overlay.remove();
            document.removeEventListener('keydown', onKey);
        }
        function onKey(e) {
            if (e.key === 'Escape') {
                close();
            }
        }
        overlay.querySelector('.lightbox-close').addEventListener('click', close);
        overlay.addEventListener('click', function (e) {
            if (e.target === overlay) {
                close();
            }
        });
        document.addEventListener('keydown', onKey);
        document.body.appendChild(overlay);
    }

    // ─── 5a. XỬ LÝ FILE: CHỌN FILE ───────────────────────────────────────────
    // Lưu pageNum và action vào pending, rồi trigger click input file ẩn
    // Chặn nếu đang có upload khác đang chạy
    function pickFile(pageNum, action) {
        if (loadingPage !== null) {
            showToast('Another page is uploading, please wait.', 'error');
            return;
        }
        pendingPageNum = pageNum;
        pendingAction = action || 'upload';
        if (fileInput) {
            fileInput.value = '';  // reset để onChange fire lại ngay cả khi chọn cùng file
            fileInput.click();
        }
    }

    // ─── 5b. XỬ LÝ FILE: UPLOAD LẦN ĐẦU ─────────────────────────────────────
    // POST multipart đến /api/v1/chapters/{chapterId}/images
    // Sau khi thành công: cập nhật pageImages[pageNum] với data trả về
    async function handleUpload(pageNum, file) {
        loadingPage = pageNum;
        updateCard(pageNum);  // hiện overlay loading
        try {
            var fd = new FormData();
            fd.append('file', file);
            fd.append('pageNumber', String(pageNum));
            fd.append('imageType', 'PAGE');
            fd.append('pageTaskId', String(PAGE_TASK.taskId));

            var uploaded = await apiPost(
                PAGE_TASK.ctx + '/api/v1/chapters/' + PAGE_TASK.chapterId + '/images',
                fd
            );
            pageImages[pageNum] = {
                id: uploaded.id,
                pageNumber: uploaded.pageNumber != null ? uploaded.pageNumber : pageNum,
                fileUrl: uploaded.fileUrl,
                originalFileName: uploaded.originalFileName
            };
            showToast('Page ' + pageNum + ' uploaded.', 'success');
        } finally {
            // Luôn tắt loading dù thành công hay thất bại
            loadingPage = null;
            updateCard(pageNum);
            renderProgressBar();
            renderSubmitBar();
        }
    }

    // ─── 5c. XỬ LÝ FILE: THAY THẾ ẢNH ───────────────────────────────────────
    // Xóa ảnh cũ trước, rồi upload ảnh mới
    // Nếu chưa có ảnh cũ → fallback về handleUpload
    // Nếu xóa thành công nhưng upload thất bại: reload lại từ server để đồng bộ
    async function handleReplace(pageNum, file) {
        var old = pageImages[pageNum];
        if (!old || !old.id) {
            await handleUpload(pageNum, file);
            return;
        }
        loadingPage = pageNum;
        updateCard(pageNum);
        try {
            await apiDelete(PAGE_TASK.ctx + '/api/v1/images/' + old.id);
            pageImages[pageNum] = null;
            var fd = new FormData();
            fd.append('file', file);
            fd.append('pageNumber', String(pageNum));
            fd.append('imageType', 'PAGE');
            fd.append('pageTaskId', String(PAGE_TASK.taskId));
            var uploaded = await apiPost(
                PAGE_TASK.ctx + '/api/v1/chapters/' + PAGE_TASK.chapterId + '/images',
                fd
            );
            pageImages[pageNum] = {
                id: uploaded.id,
                pageNumber: uploaded.pageNumber != null ? uploaded.pageNumber : pageNum,
                fileUrl: uploaded.fileUrl,
                originalFileName: uploaded.originalFileName
            };
            showToast('Page ' + pageNum + ' replaced.', 'success');
        } catch (err) {
            // Upload thất bại sau khi đã xóa → reload từ server để tránh trạng thái không nhất quán
            await loadImages();
            renderAll();
            throw err;
        } finally {
            loadingPage = null;
            updateCard(pageNum);
            renderProgressBar();
            renderSubmitBar();
        }
    }

    // ─── 5d. XỬ LÝ FILE: XÓA ẢNH ────────────────────────────────────────────
    async function handleDelete(pageNum) {
        var img = pageImages[pageNum];
        if (!img || !img.id) {
            return;
        }
        if (!confirm('Delete image for page ' + pageNum + '?')) {
            return;
        }
        loadingPage = pageNum;
        updateCard(pageNum);
        try {
            await apiDelete(PAGE_TASK.ctx + '/api/v1/images/' + img.id);
            pageImages[pageNum] = null;
            showToast('Page ' + pageNum + ' deleted.', 'success');
        } finally {
            loadingPage = null;
            updateCard(pageNum);
            renderProgressBar();
            renderSubmitBar();
        }
    }

    // ─── 6. SUBMIT TASK ───────────────────────────────────────────────────────
    // Gửi form POST đến /main/tasks/{id}/assistant-status với status=SUBMITTED
    // Dùng form POST thay vì fetch JSON vì server trả về redirect (302)
    // Sau khi thành công: redirect về trang detail task
    async function handleSubmit() {
        var total = totalPages();
        if (uploadedCount() < total) {
            showToast('Please upload all ' + total + ' pages before submitting.', 'error');
            return;
        }
        var btn = document.getElementById('pageSubmitBtn');
        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Submitting…';
        }
        try {
            var fd = new FormData();
            fd.append('status', 'SUBMITTED');
            var res = await fetch(
                PAGE_TASK.ctx + '/main/tasks/' + PAGE_TASK.taskId + '/assistant-status',
                { method: 'POST', body: fd, redirect: 'follow' }
            );
            if (!res.ok && res.status >= 400) {
                throw new Error('Submit failed (HTTP ' + res.status + ')');
            }
            window.location.href = PAGE_TASK.ctx + '/main/tasks/' + PAGE_TASK.taskId;
        } catch (err) {
            showToast(err.message, 'error');
            if (btn) {
                btn.disabled = uploadedCount() < total;
                btn.textContent = 'Submit for Review';
            }
        }
    }

    // ─── 7. KHỞI ĐỘNG ─────────────────────────────────────────────────────────
    // Load song song ảnh + page slots, sau đó render toàn bộ UI
    async function initPageGrid() {
        // Khởi tạo pageImages với null cho mọi trang trong range
        for (var p = PAGE_TASK.pageStart; p <= PAGE_TASK.pageEnd; p++) {
            pageImages[p] = null;
        }
        try {
            await Promise.all([loadImages(), loadPageSlots()]);
            renderAll();
        } catch (err) {
            if (gridEl) {
                gridEl.innerHTML = '<div class="alert error">' + escapeHtml(err.message) + '</div>';
            }
        }
    }

    // ─── 8. EVENT LISTENERS ───────────────────────────────────────────────────
    // Click trên grid: event delegation cho upload / replace / delete / preview
    if (gridEl) {
        gridEl.addEventListener('click', function (e) {
            // Click vùng trống → upload ảnh mới
            var uploadTarget = e.target.closest('[data-page-upload]');
            if (uploadTarget) {
                pickFile(Number(uploadTarget.getAttribute('data-page-upload')), 'upload');
                return;
            }
            // Nút Replace (🔄)
            var replaceBtn = e.target.closest('[data-page-replace]');
            if (replaceBtn) {
                pickFile(Number(replaceBtn.getAttribute('data-page-replace')), 'replace');
                return;
            }
            // Nút Delete (🗑)
            var deleteBtn = e.target.closest('[data-page-delete]');
            if (deleteBtn) {
                handleDelete(Number(deleteBtn.getAttribute('data-page-delete'))).catch(function (err) {
                    showToast(err.message, 'error');
                });
                return;
            }
            // Click thumbnail → mở light-box preview
            var thumb = e.target.closest('.page-card-thumb');
            if (thumb) {
                var card = thumb.closest('[data-page]');
                if (card) {
                    openImagePreview(Number(card.getAttribute('data-page')));
                }
            }
        });
    }

    // Input file ẩn onChange: lấy file + pendingPageNum/Action, rồi upload/replace
    if (fileInput) {
        fileInput.addEventListener('change', async function () {
            var file = fileInput.files && fileInput.files[0];
            var pageNum = pendingPageNum;
            var action = pendingAction;
            // Reset pending để tránh xử lý lại nếu event fire lần nữa
            pendingPageNum = null;
            pendingAction = 'upload';
            if (!file || pageNum === null) {
                return;
            }
            // Cảnh báo file > 10MB nhưng vẫn cho upload nếu user confirm
            if (file.size > 10 * 1024 * 1024) {
                if (!confirm('File is larger than 10MB. Are you sure you want to upload?')) {
                    return;
                }
            }
            try {
                if (action === 'replace') {
                    await handleReplace(pageNum, file);
                } else {
                    await handleUpload(pageNum, file);
                }
            } catch (err) {
                showToast(err.message, 'error');
            }
        });
    }

    // Nút Submit trong sticky bar
    if (submitBarEl) {
        submitBarEl.addEventListener('click', function (e) {
            if (e.target && e.target.id === 'pageSubmitBtn') {
                handleSubmit();
            }
        });
    }

    // Chờ DOM sẵn sàng rồi mới init (phòng trường hợp script load trước DOM)
    document.addEventListener('DOMContentLoaded', initPageGrid);
})();
