<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
    <head>
        <title>Manuscript Workspace - Chapter ${chapter.chapterNumber}</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
        <script>
            window.isMangaka = ${isMangakaOwner};

            console.log(
                    "isMangaka =",
                    window.isMangaka
                    );
        </script>
        <script src="${pageContext.request.contextPath}/assets/manuscript-workspace.js"></script>
        <style>
            /* Professional Light Editorial Workspace Styles */

            .workspace-container {
                display: flex;
                height: calc(100vh - 64px);
                background: #f8f9fa;
            }

            /* Left Sidebar - Version & Metadata */
            .workspace-sidebar {
                width: 300px;
                background: #ffffff;
                border-right: 1px solid #e5e7eb;
                padding: 24px 20px;
                overflow-y: auto;
                flex-shrink: 0;
            }

            /* Right Sidebar - Annotations & Feedback */
            .workspace-right-sidebar {
                width: 340px;
                background: #ffffff;
                border-left: 1px solid #e5e7eb;
                padding: 24px 20px;
                overflow-y: auto;
                flex-shrink: 0;
            }

            /* Main Content - Manga Workspace */
            .workspace-main {
                flex: 1;
                display: flex;
                flex-direction: column;
                min-width: 0;
                background: #f1f3f4;
            }

            /* Toolbar */
            .workspace-toolbar {
                background: #ffffff;
                border-bottom: 1px solid #e5e7eb;
                padding: 16px 24px;
                display: flex;
                justify-content: space-between;
                align-items: center;
                box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
                flex-shrink: 0;
            }

            .workspace-toolbar > div:first-child {
                display: flex;
                align-items: center;
                gap: 16px;
                flex-wrap: wrap;
            }

            .workspace-toolbar > div:last-child {
                display: flex;
                align-items: center;
                gap: 12px;
                flex-wrap: wrap;
            }

            /* Pages Area */
            .workspace-pages {
                flex: 1;
                overflow-y: auto;
                padding: 32px 24px;
                background: #f1f3f4;
            }

            /* Page Card */
            .page-card {
                background: #ffffff;
                border: 1px solid #e5e7eb;
                border-radius: 12px;
                margin-bottom: 32px;
                overflow: hidden;
                position: relative;
                box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
                transition: box-shadow 0.2s ease, border-color 0.2s ease;
            }

            .page-card:hover {
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
                border-color: #d1d5db;
            }

            /* Page Image Container - Neutral Reading Area */
            .page-image-container {
                position: relative;
                width: 100%;
                min-height: 500px;
                background: #e8eaed;
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 24px;
            }

            .page-image {
                max-width: 100%;
                max-height: 700px;
                object-fit: contain;
                border-radius: 4px;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
            }

            /* Annotation Markers */
            .annotation-marker {
                position:absolute;
                transform: translate(-50%, -50%);
                border: 2px solid #ef4444;
                background: rgba(239, 68, 68, 0.15);
                cursor: pointer;
                transition: all 0.2s ease;
                border-radius: 2px;
            }

            .annotation-marker:hover {
                background: rgba(239, 68, 68, 0.3);
                border-width: 3px;
                box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.1);
            }

            .annotation-marker.resolved {
                border-color: #10b981;
                background: rgba(16, 185, 129, 0.15);
            }

            .annotation-marker.resolved:hover {
                background: rgba(16, 185, 129, 0.3);
                box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.1);
            }

            .annotation-marker.dismissed {
                border-color: #9ca3af;
                background: rgba(156, 163, 175, 0.15);
            }

            .annotation-marker.dismissed:hover {
                background: rgba(156, 163, 175, 0.3);
                box-shadow: 0 0 0 4px rgba(156, 163, 175, 0.1);
            }

            /* Page Info */
            .page-info {
                padding: 16px 20px;
                border-top: 1px solid #e5e7eb;
                background: #fafbfc;
            }

            /* Status Badges */
            .status-badge {
                display: inline-block;
                padding: 6px 14px;
                border-radius: 999px;
                font-size: 12px;
                font-weight: 600;
                text-transform: uppercase;
                letter-spacing: 0.05em;
            }

            .status-draft {
                background: #fef3c7;
                color: #92400e;
                border: 1px solid #fcd34d;
            }
            .status-under_review {
                background: #dbeafe;
                color: #1d4ed8;
                border: 1px solid #93c5fd;
            }
            .status-approved {
                background: #d1fae5;
                color: #065f46;
                border: 1px solid #6ee7b7;
            }
            .status-rejected {
                background: #fee2e2;
                color: #991b1b;
                border: 1px solid #fca5a5;
            }

            /* Buttons */
            .btn {
                padding: 10px 18px;
                border: 1px solid #d1d5db;
                border-radius: 8px;
                cursor: pointer;
                font-size: 14px;
                font-weight: 500;
                margin-right: 8px;
                transition: all 0.2s ease;
                background: #ffffff;
                color: #374151;
            }

            .btn:hover {
                background: #f9fafb;
                border-color: #9ca3af;
                transform: translateY(-1px);
            }

            .btn-primary {
                background: #2563eb;
                border-color: #2563eb;
                color: #ffffff;
            }

            .btn-primary:hover {
                background: #1d4ed8;
                border-color: #1d4ed8;
                box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
            }

            .btn-success {
                background: #059669;
                border-color: #059669;
                color: #ffffff;
            }

            .btn-success:hover {
                background: #047857;
                border-color: #047857;
                box-shadow: 0 4px 12px rgba(5, 150, 105, 0.25);
            }

            .btn-danger {
                background: #dc2626;
                border-color: #dc2626;
                color: #ffffff;
            }

            .btn-danger:hover {
                background: #b91c1c;
                border-color: #b91c1c;
                box-shadow: 0 4px 12px rgba(220, 38, 38, 0.25);
            }

            .btn-secondary {
                background: #6b7280;
                border-color: #6b7280;
                color: #ffffff;
            }

            .btn-secondary:hover {
                background: #4b5563;
                border-color: #4b5563;
            }

            .btn:disabled {
                opacity: 0.5;
                cursor: not-allowed;
                transform: none;
            }

            /* Sidebar Sections */
            .sidebar-section {
                margin-bottom: 28px;
            }

            .sidebar-title {
                font-weight: 700;
                font-size: 13px;
                text-transform: uppercase;
                letter-spacing: 0.08em;
                color: #6b7280;
                margin-bottom: 14px;
                padding-bottom: 8px;
                border-bottom: 2px solid #e5e7eb;
            }

            /* Dashboard Stats */
            .dashboard-stat {
                display: flex;
                justify-content: space-between;
                padding: 10px 0;
                border-bottom: 1px solid #f3f4f6;
                font-size: 14px;
            }

            .dashboard-stat:last-child {
                border-bottom: none;
            }

            .stat-label {
                color: #6b7280;
                font-weight: 500;
            }

            .stat-value {
                font-weight: 600;
                color: #111827;
            }

            /* Version Items */
            .version-item {
                padding: 14px 16px;
                border: 1px solid #e5e7eb;
                border-radius: 8px;
                margin-bottom: 10px;
                cursor: pointer;
                transition: all 0.2s ease;
                background: #ffffff;
            }

            .version-item:hover {
                background: #eff6ff;
                border-color: #bfdbfe;
                transform: translateX(2px);
            }

            .version-item.current {
                border-color: #3b82f6;
                background: #eff6ff;
                box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
            }

            .version-item strong {
                display: block;
                color: #111827;
                font-weight: 600;
            }

            /* Annotation List */
            .annotation-list {
                margin-top: 12px;
            }

            .annotation-item {
                padding: 14px 16px;
                border-left: 4px solid #ef4444;
                background: #fef2f2;
                margin-bottom: 10px;
                border-radius: 0 8px 8px 0;
                cursor: pointer;
                transition: all 0.2s ease;
            }

            .annotation-item:hover {
                background: #fee2e2;
                transform: translateX(2px);
            }

            .annotation-item.resolved {
                border-left-color: #10b981;
                background: #ecfdf5;
            }

            .annotation-item.resolved:hover {
                background: #d1fae5;
            }

            .annotation-item.dismissed {
                border-left-color: #9ca3af;
                background: #f3f4f6;
            }

            .annotation-item.dismissed:hover {
                background: #e5e7eb;
            }

            .annotation-item > div:first-child {
                font-weight: 600;
                color: #111827;
                margin-bottom: 4px;
            }

            .annotation-item > div:nth-child(2) {
                font-size: 13px;
                color: #4b5563;
                line-height: 1.4;
            }

            .annotation-item > div:last-child {
                font-size: 11px;
                color: #6b7280;
                margin-top: 6px;
                font-weight: 500;
            }

            /* Feedback Panel */
            .feedback-panel {
                background: #fffbeb;
                border: 1px solid #fcd34d;
                border-radius: 8px;
                padding: 16px;
                margin-bottom: 20px;
            }

            .feedback-panel.empty {
                background: #f9fafb;
                border: 1px dashed #d1d5db;
            }

            .feedback-title {
                font-weight: 600;
                font-size: 13px;
                color: #92400e;
                margin-bottom: 10px;
                text-transform: uppercase;
                letter-spacing: 0.05em;
            }

            .feedback-content {
                font-size: 14px;
                color: #78350f;
                line-height: 1.5;
                background: #ffffff;
                padding: 12px;
                border-radius: 6px;
                border: 1px solid #fde68a;
            }

            .feedback-empty {
                color: #9ca3af;
                font-size: 13px;
                font-style: italic;
                text-align: center;
                padding: 12px;
            }

            /* Error Message */
            .error-message {
                background: #fef2f2;
                color: #991b1b;
                padding: 14px 18px;
                border-radius: 8px;
                margin-bottom: 20px;
                border: 1px solid #fca5a5;
                font-weight: 500;
            }

            /* Empty States */
            .empty-state {
                text-align: center;
                padding: 80px 40px;
                color: #6b7280;
                background: #ffffff;
                border-radius: 12px;
                border: 2px dashed #e5e7eb;
            }

            .empty-state h3 {
                margin: 0 0 8px;
                color: #374151;
                font-size: 20px;
                font-weight: 600;
            }

            .empty-state p {
                margin: 0;
                font-size: 14px;
            }

            /*Annotation focus*/
            .annotation-marker.active {
                border: 4px solid blue;
                background: rgba(255,165,0,.3);

                transform: translate(-50%, -50%);

                box-shadow:
                    0 0 0 4px rgba(37,99,235,0.15),
                    0 0 12px rgba(37,99,235,0.35);

                animation: pulseFocus 1.2s infinite;
            }

            .image-wrapper{
                position:relative;
                display:inline-block;
            }

            @keyframes pulseFocus {
                0% {
                    transform: translate(-50%, -50%) scale(1);
                }
                50% {
                    transform: translate(-50%, -50%) scale(1.05);
                }
                100% {
                    transform: translate(-50%, -50%) scale(1);
                }
            }
            /* Responsive */
            @media (max-width: 1400px) {
                .workspace-sidebar {
                    width: 260px;
                }
                .workspace-right-sidebar {
                    width: 300px;
                }
            }

            @media (max-width: 1200px) {
                .workspace-sidebar {
                    width: 240px;
                    padding: 20px 16px;
                }
                .workspace-right-sidebar {
                    width: 280px;
                    padding: 20px 16px;
                }
            }
        </style>
    </head>
    <body>
        <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>

        <div class="workspace-container">
            <!-- Left Sidebar: Manuscript Info & Version History -->
            <div class="workspace-sidebar">
                <div class="sidebar-section">
                    <div class="sidebar-title">Manuscript Info</div>
                    <div class="dashboard-stat">
                        <span class="stat-label">Chapter</span>
                        <span class="stat-value">${chapter.chapterNumber}</span>
                    </div>
                    <div class="dashboard-stat">
                        <span class="stat-label">Version</span>
                        <span class="stat-value">v${version.version}</span>
                    </div>
                    <div class="dashboard-stat">
                        <span class="stat-label">Status</span>
                        <span class="status-badge status-${fn:toLowerCase(version.status)}">${version.status}</span>
                    </div>
                    <div class="dashboard-stat">
                        <span class="stat-label">Pages</span>
                        <span class="stat-value">${version.totalPageCount}</span>
                    </div>
                    <div class="dashboard-stat">
                        <span class="stat-label">Created</span>
                        <span class="stat-value" style="font-size: 12px;">${createdAtFormatted}</span>
                    </div>
                    <c:if test="${not empty submittedAtFormatted}">
                        <div class="dashboard-stat">
                            <span class="stat-label">Submitted</span>
                            <span class="stat-value" style="font-size: 12px;">${submittedAtFormatted}</span>
                        </div>
                    </c:if>
                </div>

                <div class="sidebar-section">
                    <div class="sidebar-title">Version History</div>
                    <c:forEach var="v" items="${versionHistory}">
                        <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${v.id}" class="version-item ${v.id == version.id ? 'current' : ''}" style="text-decoration: none; display: block;">
                            <div><strong>v${v.version}</strong> - ${v.status}</div>
                            <div style="font-size: 12px; color: #6b7280; margin-top: 4px;">
                                ${versionHistoryDates[v.id]}
                            </div>
                        </a>
                    </c:forEach>
                </div>
            </div>

            <!-- Main Content -->
            <div class="workspace-main">
                <div class="workspace-toolbar">
                    <div>
                        <strong>Chapter ${chapter.chapterNumber}: ${chapter.title}</strong>
                        <span style="margin-left: 15px; color: #666; font-size: 13px;">
                            v${version.version} - <span class="status-badge status-${version.status}">${version.status}</span>
                        </span>
                        <c:if test="${isReadonly}">
                            <span style="margin-left: 15px; color: #868e96; font-size: 12px;">
                                🔒 Readonly
                            </span>
                        </c:if>
                        <c:if test="${productionLocked}">
                            <span style="margin-left: 15px; color: #fa5252; font-size: 12px;">
                                🔒 Production Locked
                            </span>
                        </c:if>
                    </div>
                    <div style="font-size: 12px; color: #666;">
                        Pages: ${version.totalPageCount} | 
                        Open Annotations: <span style="color: ${dashboard.openAnnotations > 0 ? '#fa5252' : '#40c057'}">${dashboard.openAnnotations}</span> |
                        Progress: ${dashboard.reviewProgress}%
                    </div>
                    <div>
                        <c:if test="${!isReadonly && version.status == 'DRAFT' && empty pages}">
                            <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/import-pages" style="display: inline;">
                                <button type="submit" class="btn btn-primary">Import Pages</button>
                            </form>
                        </c:if>
                        <c:if test="${!isReadonly && version.status == 'DRAFT' && not empty pages}">
                            <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/submit" style="display: inline;">
                                <button type="submit" class="btn btn-primary">Submit for Review</button>
                            </form>
                        </c:if>
                        <c:if test="${!isReadonly && version.status == 'UNDER_REVIEW' && (isAssignedTantou || isAdmin)}">
                            <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/approve" style="display: inline;">
                                <button type="submit" class="btn btn-success" ${dashboard.openAnnotations > 0 ? 'disabled' : ''}>Approve</button>
                            </form>
                            <button type="button" class="btn btn-danger" onclick="showRejectModal()">Reject</button>
                        </c:if>
                        <c:if test="${!isReadonly && version.status == 'REJECTED' && isMangakaOwner}">
                            <form method="post" action="${pageContext.request.contextPath}/main/chapters/${chapter.id}/manuscript-workspace/new-version" style="display: inline;">
                                <button type="submit" class="btn btn-primary">Create New Version</button>
                            </form>
                        </c:if>
                        <c:if test="${!isReadonly && version.status == 'APPROVED'}">
                            <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/publish" style="display: inline;">
                                <button type="submit" class="btn btn-success">Publish</button>
                            </form>
                        </c:if>
                        <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/dashboard" class="btn btn-secondary">Dashboard</a>
                        <a href="${pageContext.request.contextPath}/main/chapters/${chapter.id}" class="btn btn-secondary">Back to Chapter</a>
                    </div>
                </div>

                <c:if test="${error != null}">
                    <div class="error-message">${error}</div>
                </c:if>

                <div class="workspace-pages">
                    <c:if test="${empty pages}">
                        <div class="empty-state">
                            <h3>No pages imported yet</h3>
                            <p>Import chapter pages to begin the manuscript workspace.</p>
                        </div>
                    </c:if>
                    <c:forEach var="page" items="${pages}">
                        <div class="page-card" id="page-${page.id}">
                            <div class="page-image-container">
                                <div class="image-wrapper">
                                    <img data-original-url="${page.snapshotFileUrl}" alt="Page ${page.pageNumber}" class="page-image" id="img-${page.id}">
                                    <!-- Annotation markers will be rendered here via JavaScript -->
                                </div>
                            </div>
                            <div class="page-info">
                                <div style="font-weight: 600; color: #111827; margin-bottom: 4px;">Page ${page.pageNumber}</div>
                                <div style="font-size: 12px; color: #6b7280;">
                                    Display Order: ${page.displayOrder} | Checksum: ${page.snapshotChecksum}
                                </div>
                            </div>
                        </div>
                    </c:forEach>
                </div>
            </div>

            <!-- Right Sidebar: Feedback & Annotations -->
            <div class="workspace-right-sidebar">
                <c:if test="${not empty version.feedback}">
                    <div class="sidebar-section">
                        <div class="sidebar-title">Version Feedback</div>
                        <div class="feedback-panel">
                            <div class="feedback-title">Feedback for v${version.version}</div>
                            <div class="feedback-content">${version.feedback}</div>
                        </div>
                    </div>
                </c:if>

                <div class="sidebar-section">
                    <div class="sidebar-title">Annotations</div>
                    <div class="annotation-list">
                        <c:if test="${empty annotations}">
                            <div class="feedback-empty">
                                No annotations yet
                            </div>
                        </c:if>
                        <c:forEach var="annotation" items="${annotations}">
                            <div
                                class="annotation-item ${annotation.status == 'RESOLVED' ? 'resolved' : annotation.status == 'DISMISSED' ? 'dismissed' : ''}"
                                style="cursor:pointer;"
                                onclick="
                                        focusAnnotation(
                                ${annotation.id},
                                ${annotation.manuscriptPageId},
                                                '${annotation.category}',
                                                '${annotation.content}',
                                                '${annotation.severity}'
                                                )
                                "
                                >

                                <div>${annotation.category}</div>
                                <div>${annotation.content}</div>

                                <div>
                                    Page ${annotation.pageNumber}
                                    -
                                    ${annotation.status}
                                </div>

                            </div>
                        </c:forEach>
                    </div>
                </div>
            </div>
        </div>

        <!-- Reject Modal -->
        <div id="rejectModal" style="display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 1000;">
            <div style="position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); background: #fff; padding: 32px; border-radius: 16px; width: 480px; box-shadow: 0 20px 60px rgba(0,0,0,0.3);">
                <h3 style="margin: 0 0 20px; font-size: 20px; font-weight: 700; color: #111827;">Reject Manuscript</h3>
                <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/reject">
                    <div style="margin-bottom: 20px;">
                        <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Feedback (required):</label>
                        <textarea name="feedback" rows="5" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; font-family: inherit; resize: vertical;" required placeholder="Please provide feedback for rejection..."></textarea>
                    </div>
                    <div style="text-align: right; display: flex; gap: 12px; justify-content: flex-end;">
                        <button type="button" class="btn btn-secondary" onclick="hideRejectModal()">Cancel</button>
                        <button type="submit" class="btn btn-danger">Reject Manuscript</button>
                    </div>
                </form>
            </div>
        </div>
        <script>
            window.contextPath = '${pageContext.request.contextPath}';

            function imageUrl(fileUrl) {
                var url = String(fileUrl || '');

                if (url.indexOf('http://') === 0 || url.indexOf('https://') === 0) {
                    return url;
                }

                if (url.indexOf(window.contextPath + '/') === 0) {
                    return url;
                }

                return window.contextPath + url;
            }

            function showRejectModal() {
                document.getElementById('rejectModal').style.display = 'block';
            }

            function hideRejectModal() {
                document.getElementById('rejectModal').style.display = 'none';
            }

            function scrollToPage(pageId) {
                var pageElement = document.getElementById('page-' + pageId);
                if (pageElement) {
                    pageElement.scrollIntoView({behavior: 'smooth', block: 'center'});
                }
            }

            // Normalize image URLs on page load
            (function () {
                var images = document.querySelectorAll('img.page-image[data-original-url]');
                for (var i = 0; i < images.length; i++) {
                    var img = images[i];
                    var originalUrl = img.getAttribute('data-original-url');
                    if (originalUrl) {
                        console.log('Manuscript workspace image normalization:');
                        console.log('  Original URL:', originalUrl);
                        var normalizedUrl = imageUrl(originalUrl);
                        console.log('  Normalized URL:', normalizedUrl);
                        img.src = normalizedUrl;
                    }
                }
            })();

            // Render annotation markers on pages
            <c:forEach var="ann" items="${annotations}">
                <c:if test="${ann.manuscriptPageId != null}">
            var pageImg = document.getElementById('img-${ann.manuscriptPageId}');
            if (pageImg) {
                var wrapper = pageImg.parentElement;
                var marker = document.createElement('div');
                marker.setAttribute(
                        'data-annotation-id',
                        '${ann.id}'
                        );
                marker.className = 'annotation-marker ${ann.status == 'RESOLVED' ? 'resolved' : ann.status == 'DISMISSED' ? 'dismissed' : ''}';
                marker.style.left = '${ann.getXPercent()}%';
                marker.style.top = '${ann.getYPercent()}%';
                marker.style.width = '${ann.getWidthPercent()}%';
                marker.style.height = '${ann.getHeightPercent()}%';
                marker.title = '${ann.category}: ${ann.content}';

                        marker.onclick = function () {
                            alert(
                                    'Category: ${ann.getCategory()}\n' +
                                    'Content: ${ann.getContent()}\n' +
                                    'Status: ${ann.getStatus()}\n' +
                                    'Severity: ${ann.getSeverity()}'
                                    );
                        };
                        wrapper.appendChild(marker);
                    }

                </c:if>
            </c:forEach>
        </script>
        <div id="annotationPopup"
             style="
             display:none;
             position:fixed;
             z-index:9999;
             background:white;
             border:1px solid #d1d5db;
             border-radius:8px;
             padding:12px;
             min-width:250px;
             box-shadow:0 4px 12px rgba(0,0,0,.15);
             ">

            <div id="annotationPopupContent"></div>

        </div>
    </body>
</html>
