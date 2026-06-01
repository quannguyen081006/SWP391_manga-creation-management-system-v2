<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
    <title>Manuscript Workspace - Chapter ${chapter.chapterNumber}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
    <script src="${pageContext.request.contextPath}/assets/manuscript-workspace.js"></script>
    <style>
        .workspace-container {
            display: flex;
            height: calc(100vh - 60px);
        }
        .workspace-sidebar {
            width: 300px;
            background: #f5f5f5;
            border-right: 1px solid #ddd;
            padding: 20px;
            overflow-y: auto;
        }
        .workspace-main {
            flex: 1;
            display: flex;
            flex-direction: column;
        }
        .workspace-toolbar {
            background: #fff;
            border-bottom: 1px solid #ddd;
            padding: 15px 20px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .workspace-pages {
            flex: 1;
            overflow-y: auto;
            padding: 20px;
            background: #f9f9f9;
        }
        .page-card {
            background: #fff;
            border: 1px solid #ddd;
            border-radius: 8px;
            margin-bottom: 20px;
            overflow: hidden;
            position: relative;
        }
        .page-image-container {
            position: relative;
            width: 100%;
            min-height: 400px;
            background: #f0f0f0;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .page-image {
            max-width: 100%;
            max-height: 600px;
            object-fit: contain;
        }
        .annotation-marker {
            position: absolute;
            border: 2px solid #ff6b6b;
            background: rgba(255, 107, 107, 0.2);
            cursor: pointer;
            transition: all 0.2s;
        }
        .annotation-marker:hover {
            background: rgba(255, 107, 107, 0.4);
            border-width: 3px;
        }
        .annotation-marker.resolved {
            border-color: #51cf66;
            background: rgba(81, 207, 102, 0.2);
        }
        .annotation-marker.dismissed {
            border-color: #868e96;
            background: rgba(134, 142, 150, 0.2);
        }
        .page-info {
            padding: 15px;
            border-top: 1px solid #ddd;
        }
        .status-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: bold;
            text-transform: uppercase;
        }
        .status-draft { background: #ffd43b; color: #333; }
        .status-under_review { background: #74c0fc; color: #fff; }
        .status-approved { background: #51cf66; color: #fff; }
        .status-rejected { background: #ff6b6b; color: #fff; }
        .btn {
            padding: 8px 16px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
            margin-right: 8px;
        }
        .btn-primary { background: #228be6; color: #fff; }
        .btn-success { background: #40c057; color: #fff; }
        .btn-danger { background: #fa5252; color: #fff; }
        .btn-secondary { background: #868e96; color: #fff; }
        .btn:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
        .sidebar-section {
            margin-bottom: 25px;
        }
        .sidebar-title {
            font-weight: bold;
            margin-bottom: 10px;
            color: #333;
        }
        .dashboard-stat {
            display: flex;
            justify-content: space-between;
            padding: 8px 0;
            border-bottom: 1px solid #eee;
        }
        .stat-label { color: #666; }
        .stat-value { font-weight: bold; }
        .version-item {
            padding: 10px;
            border: 1px solid #ddd;
            border-radius: 4px;
            margin-bottom: 8px;
            cursor: pointer;
        }
        .version-item:hover {
            background: #e7f5ff;
        }
        .version-item.current {
            border-color: #228be6;
            background: #e7f5ff;
        }
        .annotation-list {
            margin-top: 10px;
        }
        .annotation-item {
            padding: 10px;
            border-left: 3px solid #ff6b6b;
            background: #fff5f5;
            margin-bottom: 8px;
            border-radius: 4px;
        }
        .annotation-item.resolved {
            border-left-color: #51cf66;
            background: #f6fff9;
        }
        .annotation-item.dismissed {
            border-left-color: #868e96;
            background: #f8f9fa;
        }
        .error-message {
            background: #ffe3e3;
            color: #c92a2a;
            padding: 12px;
            border-radius: 4px;
            margin-bottom: 20px;
        }
    </style>
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="workspace-container">
        <!-- Sidebar -->
        <div class="workspace-sidebar">
            <div class="sidebar-section">
                <div class="sidebar-title">Manuscript Info</div>
                <div><strong>Version:</strong> ${version.version}</div>
                <div><strong>Status:</strong> 
                    <span class="status-badge status-${version.status}">${version.status}</span>
                </div>
                <div><strong>Pages:</strong> ${version.totalPageCount}</div>
                <c:if test="${not empty createdAtFormatted}">
                    <div><strong>Created:</strong> ${createdAtFormatted}</div>
                </c:if>
                <c:if test="${not empty submittedAtFormatted}">
                    <div><strong>Submitted:</strong> ${submittedAtFormatted}</div>
                </c:if>
            </div>

            <div class="sidebar-section">
                <div class="sidebar-title">Review Dashboard</div>
                <div class="dashboard-stat">
                    <span class="stat-label">Total Pages</span>
                    <span class="stat-value">${dashboard.totalPages}</span>
                </div>
                <div class="dashboard-stat">
                    <span class="stat-label">Open Annotations</span>
                    <span class="stat-value" style="color: ${dashboard.openAnnotations > 0 ? '#fa5252' : '#40c057'}">${dashboard.openAnnotations}</span>
                </div>
                <div class="dashboard-stat">
                    <span class="stat-label">Resolved</span>
                    <span class="stat-value">${dashboard.resolvedAnnotations}</span>
                </div>
                <div class="dashboard-stat">
                    <span class="stat-label">Progress</span>
                    <span class="stat-value">${dashboard.reviewProgress}%</span>
                </div>
            </div>

            <div class="sidebar-section">
                <div class="sidebar-title">Version History</div>
                <c:forEach var="v" items="${versionHistory}">
                    <div class="version-item ${v.id == version.id ? 'current' : ''}">
                        <div><strong>v${v.version}</strong> - ${v.status}</div>
                        <div style="font-size: 12px; color: #666;">
                            ${versionHistoryDates[v.id]}
                        </div>
                    </div>
                </c:forEach>
            </div>

            <div class="sidebar-section">
                <div class="sidebar-title">Recent Annotations</div>
                <div class="annotation-list">
                    <c:forEach var="annotation" items="${annotations}" end="4">
                        <div class="annotation-item ${annotation.status == 'RESOLVED' ? 'resolved' : annotation.status == 'DISMISSED' ? 'dismissed' : ''}">
                            <div style="font-weight: bold;">${annotation.category}</div>
                            <div style="font-size: 13px;">${annotation.content}</div>
                            <div style="font-size: 11px; color: #666; margin-top: 4px;">
                                Page ${annotation.pageNumber} - ${annotation.status}
                            </div>
                        </div>
                    </c:forEach>
                    <c:if test="${fn:length(annotations) > 5}">
                        <div style="text-align: center; color: #666; font-size: 12px;">
                            +${fn:length(annotations) - 5} more annotations
                        </div>
                    </c:if>
                </div>
            </div>
        </div>

        <!-- Main Content -->
        <div class="workspace-main">
            <div class="workspace-toolbar">
                <div>
                    <strong>Chapter ${chapter.chapterNumber}: ${chapter.title}</strong>
                    <c:if test="${productionLocked}">
                        <span style="margin-left: 15px; color: #fa5252; font-size: 12px;">
                            🔒 Production Locked
                        </span>
                    </c:if>
                </div>
                <div>
                    <c:if test="${version.status == 'DRAFT' && empty pages}">
                        <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/import-pages" style="display: inline;">
                            <button type="submit" class="btn btn-primary">Import Pages</button>
                        </form>
                    </c:if>
                    <c:if test="${version.status == 'DRAFT' && not empty pages}">
                        <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/submit" style="display: inline;">
                            <button type="submit" class="btn btn-primary">Submit for Review</button>
                        </form>
                    </c:if>
                    <c:if test="${version.status == 'UNDER_REVIEW' && (isAssignedTantou || isAdmin)}">
                        <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/approve" style="display: inline;">
                            <button type="submit" class="btn btn-success" ${dashboard.openAnnotations > 0 ? 'disabled' : ''}>Approve</button>
                        </form>
                        <button type="button" class="btn btn-danger" onclick="showRejectModal()">Reject</button>
                    </c:if>
                    <c:if test="${version.status == 'REJECTED' && isMangakaOwner}">
                        <form method="post" action="${pageContext.request.contextPath}/main/chapters/${chapter.id}/manuscript-workspace/new-version" style="display: inline;">
                            <button type="submit" class="btn btn-primary">Create New Version</button>
                        </form>
                    </c:if>
                    <c:if test="${version.status == 'APPROVED'}">
                        <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/publish" style="display: inline;">
                            <button type="submit" class="btn btn-success">Publish</button>
                        </form>
                    </c:if>
                    <a href="${pageContext.request.contextPath}/main/chapters/${chapter.id}/manuscript-workspace/history" class="btn btn-secondary">Version History</a>
                    <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/dashboard" class="btn btn-secondary">Dashboard</a>
                    <a href="${pageContext.request.contextPath}/main/chapters/${chapter.id}" class="btn btn-secondary">Back to Chapter</a>
                </div>
            </div>

            <c:if test="${error != null}">
                <div class="error-message">${error}</div>
            </c:if>

            <div class="workspace-pages">
                <c:if test="${empty pages}">
                    <div style="text-align: center; padding: 60px; color: #666;">
                        <h3>No pages imported yet</h3>
                        <p>Import chapter pages to begin the review workspace.</p>
                    </div>
                </c:if>
                <c:forEach var="page" items="${pages}">
                    <div class="page-card" id="page-${page.id}">
                        <div class="page-image-container">
                            <img src="${page.snapshotFileUrl}" alt="Page ${page.pageNumber}" class="page-image" id="img-${page.id}">
                            <!-- Annotation markers will be rendered here via JavaScript -->
                        </div>
                        <div class="page-info">
                            <div><strong>Page ${page.pageNumber}</strong> (Display Order: ${page.displayOrder})</div>
                            <div style="font-size: 12px; color: #666;">
                                Checksum: ${page.snapshotChecksum}
                            </div>
                        </div>
                    </div>
                </c:forEach>
            </div>
        </div>
    </div>

    <!-- Reject Modal -->
    <div id="rejectModal" style="display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 1000;">
        <div style="position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); background: #fff; padding: 30px; border-radius: 8px; width: 400px;">
            <h3>Reject Manuscript</h3>
            <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/reject">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">Feedback (required):</label>
                    <textarea name="feedback" rows="4" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" required></textarea>
                </div>
                <div style="text-align: right;">
                    <button type="button" class="btn btn-secondary" onclick="hideRejectModal()">Cancel</button>
                    <button type="submit" class="btn btn-danger">Reject</button>
                </div>
            </form>
        </div>
    </div>

    <script>
        function showRejectModal() {
            document.getElementById('rejectModal').style.display = 'block';
        }

        function hideRejectModal() {
            document.getElementById('rejectModal').style.display = 'none';
        }

        // Render annotation markers on pages
        <c:forEach var="annotation" items="${annotations}">
            <c:if test="${annotation.manuscriptPageId != null}">
                var pageImg = document.getElementById('img-${annotation.manuscriptPageId}');
                if (pageImg) {
                    var container = pageImg.parentElement;
                    var marker = document.createElement('div');
                    marker.className = 'annotation-marker ${annotation.status == 'RESOLVED' ? 'resolved' : annotation.status == 'DISMISSED' ? 'dismissed' : ''}';
                    marker.style.left = '${annotation.xPercent}%';
                    marker.style.top = '${annotation.yPercent}%';
                    marker.style.width = '${annotation.widthPercent}%';
                    marker.style.height = '${annotation.heightPercent}%';
                    marker.title = '${annotation.category}: ${annotation.content}';
                    marker.onclick = function() {
                        alert('${annotation.category}: ${annotation.content}\nStatus: ${annotation.status}\nSeverity: ${annotation.severity}');
                    };
                    container.appendChild(marker);
                }
            </c:if>
        </c:forEach>
    </script>
</body>
</html>
