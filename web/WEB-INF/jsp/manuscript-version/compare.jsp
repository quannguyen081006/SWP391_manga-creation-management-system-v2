<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
    <title>Version Comparison</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/manuscript-version.css" />
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="compare-container">
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
                <h3 class="change-subtitle">Added Pages</h3>
                <c:forEach var="page" items="${comparison.addedPages}">
                    <div class="change-item added">
                        <div class="change-label">ADDED: Page ${page.pageNumber}</div>
                        <div class="change-detail">Display Order: ${page.displayOrder}</div>
                    </div>
                </c:forEach>
            </c:if>

            <c:if test="${not empty comparison.removedPages}">
                <h3 class="change-subtitle change-subtitle-spaced">Removed Pages</h3>
                <c:forEach var="page" items="${comparison.removedPages}">
                    <div class="change-item removed">
                        <div class="change-label">REMOVED: Page ${page.pageNumber}</div>
                        <div class="change-detail">Display Order: ${page.displayOrder}</div>
                    </div>
                </c:forEach>
            </c:if>

            <c:if test="${not empty comparison.changedPages}">
                <h3 class="change-subtitle change-subtitle-spaced">Changed Pages</h3>
                <c:forEach var="page" items="${comparison.changedPages}">
                    <div class="change-item modified">
                        <div class="change-label">CHANGED: Page ${page.pageNumber}</div>
                        <div class="change-detail">Display Order: ${page.displayOrder}</div>
                    </div>
                </c:forEach>
            </c:if>

            <c:if test="${not empty comparison.reorderedPages}">
                <h3 class="change-subtitle change-subtitle-spaced">Reordered Pages</h3>
                <c:forEach var="page" items="${comparison.reorderedPages}">
                    <div class="change-item modified">
                        <div class="change-label">REORDERED: Page ${page.pageNumber}</div>
                        <div class="change-detail">Order: ${page.previousOrder} → ${page.newOrder}</div>
                    </div>
                </c:forEach>
            </c:if>
        </div>

        <div class="content-section-large">
            <a href="javascript:history.back()" class="btn btn-secondary">Back</a>
        </div>
    </div>
</body>
</html>
