/**
 * submission-history.js
 * Hiển thị timeline toàn bộ các round submit/review của 1 PageTask (Task Detail view).
 * Config: đọc từ data-* attribute trên <script> (taskId, contextPath).
 */
(function () {
    'use strict';

    var configScript = document.currentScript;
    if (!configScript) {
        return;
    }

    var TASK_ID = Number(configScript.getAttribute('data-task-id'));
    var CTX = configScript.getAttribute('data-context-path') || '';
    var listEl = document.getElementById('submissionHistoryList');
    if (!listEl) {
        return;
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
        if (url.indexOf(CTX + '/') === 0) {
            return url;
        }
        return CTX + url;
    }

    function decisionBadge(decision) {
        if (decision === 'APPROVED') {
            return '<span class="status-chip status-approved">Approved</span>';
        }
        if (decision === 'REJECTED') {
            return '<span class="status-chip status-overdue">Rejected</span>';
        }
        return '<span class="status-chip status-progress">Pending review</span>';
    }

    function renderEntry(entry) {
        var images = entry.images || [];
        var thumbs = images.map(function (img) {
            return '<a href="' + escapeHtml(imageUrl(img.fileUrl)) + '" target="_blank" rel="noopener">'
                + '<img src="' + escapeHtml(imageUrl(img.fileUrl)) + '" alt="'
                + escapeHtml(img.originalFileName || 'page') + '" class="submission-history-thumb" /></a>';
        }).join('');

        var reviewLine = '';
        if (entry.decision) {
            reviewLine = '<div class="submission-history-review">'
                + (entry.reviewedByName ? escapeHtml(entry.reviewedByName) + ' &middot; ' : '')
                + (entry.reviewedAt ? escapeHtml(entry.reviewedAt) + ' &middot; ' : '')
                + decisionBadge(entry.decision)
                + (entry.reviewComment ? '<div class="submission-history-comment">' + escapeHtml(entry.reviewComment) + '</div>' : '')
                + '</div>';
        } else {
            reviewLine = '<div class="submission-history-review">' + decisionBadge(null) + '</div>';
        }

        return '<div class="section-card submission-history-entry">'
            + '<div class="submission-history-head">'
            + '<strong>Round ' + escapeHtml(entry.roundNumber) + '</strong>'
            + '<span class="submission-history-meta">'
            + (entry.submittedByName ? escapeHtml(entry.submittedByName) + ' &middot; ' : '')
            + escapeHtml(entry.submittedAt) + '</span>'
            + '</div>'
            + (thumbs ? '<div class="submission-history-thumbs">' + thumbs + '</div>' : '')
            + reviewLine
            + '</div>';
    }

    function render(entries) {
        if (!entries || entries.length === 0) {
            listEl.innerHTML = '<p class="section-desc">No submission yet.</p>';
            return;
        }
        listEl.innerHTML = entries.map(renderEntry).join('');
    }

    fetch(CTX + '/api/v1/tasks/' + TASK_ID + '/submission-history', { headers: { Accept: 'application/json' } })
        .then(function (res) { return res.json(); })
        .then(function (body) {
            render(body && body.data);
        })
        .catch(function () {
            listEl.innerHTML = '<p class="section-desc">Cannot load submission history.</p>';
        });
})();
