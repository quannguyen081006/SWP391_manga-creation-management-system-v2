<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <title>Version Comparison</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
    <style>
        .compare-container {
            max-width: 1400px;
            margin: 40px auto;
            padding: 30px;
        }
        .compare-header {
            margin-bottom: 30px;
        }
        .versions-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 30px;
            margin-bottom: 30px;
        }
        .version-panel {
            background: #fff;
            padding: 25px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        .version-panel.version1 {
            border-top: 4px solid #228be6;
        }
        .version-panel.version2 {
            border-top: 4px solid #40c057;
        }
        .version-title {
            font-size: 20px;
            font-weight: bold;
            margin-bottom: 15px;
        }
        .version-info {
            margin-bottom: 20px;
        }
        .info-row {
            display: flex;
            justify-content: space-between;
            padding: 8px 0;
            border-bottom: 1px solid #dee2e6;
        }
        .info-row:last-child {
            border-bottom: none;
        }
        .info-label {
            color: #666;
            font-weight: 500;
        }
        .info-value {
            font-weight: bold;
            color: #333;
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
        .status-published { background: #845ef7; color: #fff; }
        .status-archived { background: #868e96; color: #fff; }
        .changes-section {
            background: #fff;
            padding: 25px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        .changes-title {
            font-size: 18px;
            font-weight: bold;
            margin-bottom: 20px;
        }
        .change-item {
            padding: 15px;
            border-left: 4px solid #228be6;
            background: #f8f9fa;
            margin-bottom: 15px;
            border-radius: 4px;
        }
        .change-item.added {
            border-left-color: #40c057;
            background: #f6fff9;
        }
        .change-item.removed {
            border-left-color: #fa5252;
            background: #fff5f5;
        }
        .change-item.modified {
            border-left-color: #ffd43b;
            background: #fffbf0;
        }
        .change-label {
            font-weight: bold;
            margin-bottom: 5px;
        }
        .change-detail {
            font-size: 14px;
            color: #666;
        }
        .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
            text-decoration: none;
            display: inline-block;
        }
        .btn-primary { background: #228be6; color: #fff; }
        .btn-secondary { background: #868e96; color: #fff; }
        .btn:hover { opacity: 0.9; }
        .no-changes {
            text-align: center;
            padding: 40px;
            color: #666;
        }
    </style>
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="compare-container">
        <div class="compare-header">
            <h2>Version Comparison</h2>
            <p style="color: #666;">Compare two manuscript versions to see changes</p>
        </div>

        <div class="versions-grid">
            <div class="version-panel version1">
                <div class="version-title">Version ${comparison.version1.version}</div>
                <div class="version-info">
                    <div class="info-row">
                        <span class="info-label">Status</span>
                        <span class="info-value">
                            <span class="status-badge status-${fn:toLowerCase(comparison.version1.status).replace('_', '_')}">${comparison.version1.status}</span>
                        </span>
                    </div>
                    <div class="info-row">
                        <span class="info-label">Created</span>
                        <span class="info-value">
                            ${v1CreatedAtFormatted}
                        </span>
                    </div>
                    <div class="info-row">
                        <span class="info-label">Pages</span>
                        <span class="info-value">${comparison.version1.totalPageCount}</span>
                    </div>
                    <c:if test="${comparison.version1.submittedAt != null}">
                        <div class="info-row">
                            <span class="info-label">Submitted</span>
                            <span class="info-value">
                                ${v1SubmittedAtFormatted}
                            </span>
                        </div>
                    </c:if>
                </div>
                <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${comparison.version1.id}" class="btn btn-primary">
                    View Workspace
                </a>
            </div>

            <div class="version-panel version2">
                <div class="version-title">Version ${comparison.version2.version}</div>
                <div class="version-info">
                    <div class="info-row">
                        <span class="info-label">Status</span>
                        <span class="info-value">
                            <span class="status-badge status-${fn:toLowerCase(comparison.version2.status).replace('_', '_')}">${comparison.version2.status}</span>
                        </span>
                    </div>
                    <div class="info-row">
                        <span class="info-label">Created</span>
                        <span class="info-value">
                            ${v2CreatedAtFormatted}
                        </span>
                    </div>
                    <div class="info-row">
                        <span class="info-label">Pages</span>
                        <span class="info-value">${comparison.version2.totalPageCount}</span>
                    </div>
                    <c:if test="${comparison.version2.submittedAt != null}">
                        <div class="info-row">
                            <span class="info-label">Submitted</span>
                            <span class="info-value">
                                ${v2SubmittedAtFormatted}
                            </span>
                        </div>
                    </c:if>
                </div>
                <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${comparison.version2.id}" class="btn btn-primary">
                    View Workspace
                </a>
            </div>
        </div>

        <div class="changes-section">
            <div class="changes-title">Changes Summary</div>
            
            <c:if test="${empty comparison.addedPages && empty comparison.removedPages && empty comparison.changedPages && empty comparison.reorderedPages}">
                <div class="no-changes">
                    <h3>No differences found between versions</h3>
                    <p>These versions appear to be identical.</p>
                </div>
            </c:if>

            <c:if test="${not empty comparison.addedPages}">
                <h3 style="margin-bottom: 15px;">Added Pages</h3>
                <c:forEach var="page" items="${comparison.addedPages}">
                    <div class="change-item added">
                        <div class="change-label">ADDED: Page ${page.pageNumber}</div>
                        <div class="change-detail">Display Order: ${page.displayOrder}</div>
                    </div>
                </c:forEach>
            </c:if>

            <c:if test="${not empty comparison.removedPages}">
                <h3 style="margin-bottom: 15px; margin-top: 30px;">Removed Pages</h3>
                <c:forEach var="page" items="${comparison.removedPages}">
                    <div class="change-item removed">
                        <div class="change-label">REMOVED: Page ${page.pageNumber}</div>
                        <div class="change-detail">Display Order: ${page.displayOrder}</div>
                    </div>
                </c:forEach>
            </c:if>

            <c:if test="${not empty comparison.changedPages}">
                <h3 style="margin-bottom: 15px; margin-top: 30px;">Changed Pages</h3>
                <c:forEach var="page" items="${comparison.changedPages}">
                    <div class="change-item modified">
                        <div class="change-label">CHANGED: Page ${page.pageNumber}</div>
                        <div class="change-detail">Display Order: ${page.displayOrder}</div>
                    </div>
                </c:forEach>
            </c:if>

            <c:if test="${not empty comparison.reorderedPages}">
                <h3 style="margin-bottom: 15px; margin-top: 30px;">Reordered Pages</h3>
                <c:forEach var="page" items="${comparison.reorderedPages}">
                    <div class="change-item modified">
                        <div class="change-label">REORDERED: Page ${page.pageNumber}</div>
                        <div class="change-detail">Order: ${page.previousOrder} → ${page.newOrder}</div>
                    </div>
                </c:forEach>
            </c:if>
        </div>

        <div style="margin-top: 30px;">
            <a href="javascript:history.back()" class="btn btn-secondary">Back</a>
        </div>
    </div>
</body>
</html>
