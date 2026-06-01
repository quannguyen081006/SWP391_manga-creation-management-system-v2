/**
 * page-submission.js
 * Page Submission Grid for Task Detail (Assistant view)
 * Config: global PAGE_TASK (injected from JSP)
 */

(function () {
    'use strict';

    if (typeof PAGE_TASK === 'undefined') {
        return;
    }

    var pageImages = {};
    var pageSlots = {};
    var loadingPage = null;
    var pendingPageNum = null;
    var pendingAction = 'upload';

    var gridEl = document.getElementById('pageGrid');
    var progressEl = document.getElementById('pageProgressBar');
    var submitBarEl = document.getElementById('stickySubmitBar');
    var fileInput = document.getElementById('pageFileInput');
    var toastContainer = document.getElementById('toastContainer');

    var isApproved = String(PAGE_TASK.status || '').toUpperCase() === 'APPROVED';

    function totalPages() {
        return PAGE_TASK.pageEnd - PAGE_TASK.pageStart + 1;
    }

    function uploadedCount() {
        var count = 0;
        for (var p = PAGE_TASK.pageStart; p <= PAGE_TASK.pageEnd; p++) {
            if (pageImages[p] && pageImages[p].id) {
                count++;
            }
        }
        return count;
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value).replace(/[&<>"']/g, function (ch) {
            return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch];
        });
    }

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

    async function readJson(res) {
        var text = await res.text();
        var body = null;
        try {
            body = text ? JSON.parse(text) : null;
        } catch (e) {
            /* ignore */
        }
        if (!res.ok || (body && body.success === false)) {
            var msg = (body && (body.message || (body.errors && body.errors[0])))
                || text
                || ('HTTP ' + res.status);
            throw new Error(msg);
        }
        return body;
    }

    async function apiGet(url) {
        var body = await readJson(await fetch(url, { headers: { Accept: 'application/json' } }));
        return body && body.data !== undefined ? body.data : body;
    }

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

    async function loadImages() {
        var data = await apiGet(PAGE_TASK.ctx + '/api/v1/tasks/' + PAGE_TASK.taskId + '/images');
        (data || []).forEach(function (img) {
            var pn = img.pageNumber;
            if (pn >= PAGE_TASK.pageStart && pn <= PAGE_TASK.pageEnd) {
                pageImages[pn] = img;
            }
        });
    }

    async function loadPageSlots() {
        var data = await apiGet(PAGE_TASK.ctx + '/api/v1/chapters/' + PAGE_TASK.chapterId + '/pages');
        (data || []).forEach(function (slot) {
            if (slot.pageNumber >= PAGE_TASK.pageStart && slot.pageNumber <= PAGE_TASK.pageEnd) {
                pageSlots[slot.pageNumber] = slot;
            }
        });
    }

    var stageOrder = ['SKETCHING', 'INKING', 'COLORING', 'SCREENTONE', 'LETTERING'];

    function normalizeStage(stage) {
        var s = String(stage || '').trim().toUpperCase();
        return stageOrder.indexOf(s) >= 0 ? s : '';
    }

    function nextStageForPage(pageNum) {
        var slot = pageSlots[pageNum] || {};
        var current = normalizeStage(slot.completedStage);
        if (!current) {
            return stageOrder[0];
        }
        return stageOrder[Math.min(stageOrder.indexOf(current) + 1, stageOrder.length - 1)];
    }

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
            + ' · ' + escapeHtml(PAGE_TASK.taskType)
            + ' · Pages ' + PAGE_TASK.pageStart + '–' + PAGE_TASK.pageEnd
            + ' (' + uploaded + '/' + total + ' uploaded) · ' + pct + '%</div>'
            + '<div class="page-progress-bar-bg">'
            + '<div class="page-progress-bar-fill ' + statusProgressClass()
            + '" style="width:' + pct + '%"></div>'
            + '</div></div>';
    }

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
            var inherited = String(img.note || '').toUpperCase() === 'CHAPTER_PAGE' || !img.id;
            var approvedBadge = isApproved
                ? '<span class="approved-badge" title="Đã được Mangaka duyệt, ảnh đã cập nhật vào chapter">✓ Approved</span>'
                : (inherited ? '<span class="approved-badge" title="Base image from chapter">Base image</span>' : '');
            html += approvedBadge
                + '<img class="page-card-thumb" src="' + escapeHtml(url) + '" alt="Page ' + pageNum
                + (isApproved ? ' title="Đã được Mangaka duyệt, ảnh đã cập nhật vào chapter"' : '')
                + ' />'
                + '<div class="page-card-footer">'
                + '<div class="page-card-meta"><strong>Page ' + pageNum + '</strong>'
                + '<span>' + escapeHtml(nextStageForPage(pageNum)) + '</span>'
                + '<span>' + escapeHtml(img.originalFileName || '') + '</span></div>';

            if (PAGE_TASK.canUpdate && !isApproved) {
                html += '<div class="page-card-actions">'
                    + '<a class="btn small" href="' + escapeHtml(url) + '" download title="Download">↓</a>'
                    + '<button type="button" class="btn small" data-page-replace="' + pageNum + '" title="Replace">🔄</button>'
                    + (inherited ? '' : '<button type="button" class="btn small danger-soft" data-page-delete="' + pageNum + '" title="Delete">🗑</button>')
                    + '</div>';
            } else {
                html += '<div class="page-card-actions">'
                    + '<a class="btn small" href="' + escapeHtml(url) + '" download title="Download">↓</a>'
                    + '</div>';
            }
            html += '</div>';
        } else {
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

        if (loading) {
            html += '<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(255,255,255,0.7);font-size:13px;color:#6b7280;">Uploading…</div>';
        }

        html += '</div>';
        return html;
    }

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
            : ' title="Vui lòng upload đủ ' + total + ' trang trước khi nộp"';

        submitBarEl.innerHTML =
            '<div class="sticky-submit-bar">'
            + '<span class="submit-hint">' + uploaded + ' / ' + total + ' pages uploaded</span>'
            + '<button type="button" class="btn primary" id="pageSubmitBtn"' + disabledAttr + tooltip + '>Submit for Review</button>'
            + '</div>';
    }

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

    function renderAll() {
        renderProgressBar();
        renderGrid();
        renderSubmitBar();
    }

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
            renderGrid();
        }
        renderProgressBar();
        renderSubmitBar();
    }

    function pickFile(pageNum, action) {
        if (loadingPage !== null) {
            showToast('Đang upload trang khác, vui lòng đợi.', 'error');
            return;
        }
        pendingPageNum = pageNum;
        pendingAction = action || 'upload';
        if (fileInput) {
            fileInput.value = '';
            fileInput.click();
        }
    }

    async function handleUpload(pageNum, file) {
        loadingPage = pageNum;
        updateCard(pageNum);
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
            loadingPage = null;
            updateCard(pageNum);
            renderProgressBar();
            renderSubmitBar();
        }
    }

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

    async function handleSubmit() {
        var total = totalPages();
        if (uploadedCount() < total) {
            showToast('Vui lòng upload đủ ' + total + ' trang trước khi nộp.', 'error');
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

    async function initPageGrid() {
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

    if (gridEl) {
        gridEl.addEventListener('click', function (e) {
            var uploadTarget = e.target.closest('[data-page-upload]');
            if (uploadTarget) {
                pickFile(Number(uploadTarget.getAttribute('data-page-upload')), 'upload');
                return;
            }
            var replaceBtn = e.target.closest('[data-page-replace]');
            if (replaceBtn) {
                pickFile(Number(replaceBtn.getAttribute('data-page-replace')), 'replace');
                return;
            }
            var deleteBtn = e.target.closest('[data-page-delete]');
            if (deleteBtn) {
                handleDelete(Number(deleteBtn.getAttribute('data-page-delete'))).catch(function (err) {
                    showToast(err.message, 'error');
                });
                return;
            }
            var thumb = e.target.closest('.page-card-thumb');
            if (thumb) {
                var card = thumb.closest('[data-page]');
                if (card) {
                    openImagePreview(Number(card.getAttribute('data-page')));
                }
            }
        });
    }

    if (fileInput) {
        fileInput.addEventListener('change', async function () {
            var file = fileInput.files && fileInput.files[0];
            var pageNum = pendingPageNum;
            var action = pendingAction;
            pendingPageNum = null;
            pendingAction = 'upload';
            if (!file || pageNum === null) {
                return;
            }
            if (file.size > 10 * 1024 * 1024) {
                if (!confirm('File lớn hơn 10MB. Bạn có chắc muốn upload?')) {
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

    if (submitBarEl) {
        submitBarEl.addEventListener('click', function (e) {
            if (e.target && e.target.id === 'pageSubmitBtn') {
                handleSubmit();
            }
        });
    }

    document.addEventListener('DOMContentLoaded', initPageGrid);
})();
