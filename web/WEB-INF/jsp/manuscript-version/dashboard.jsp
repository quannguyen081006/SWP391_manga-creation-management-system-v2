<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <title>Review Dashboard</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/manuscript-version.css" />
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="dashboard-container">
        <c:if test="${dashboard == null || dashboard.totalPages == null || dashboard.totalPages == 0}">
            <div class="no-changes empty-state section-card">
                <h3>No review data available</h3>
                <p>The manuscript version has no pages or review data yet.</p>
            </div>
        </c:if>

        <c:if test="${dashboard != null && dashboard.totalPages != null && dashboard.totalPages > 0}">
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-value">${dashboard.totalPages}</div>
                <div class="stat-label">Total Pages</div>
            </div>
            <div class="stat-card">
                <div class="stat-value ${dashboard.openAnnotations > 0 ? 'warning' : 'success'}">${dashboard.openAnnotations}</div>
                <div class="stat-label">Open Annotations</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${dashboard.resolvedAnnotations}</div>
                <div class="stat-label">Resolved</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${dashboard.totalAnnotations}</div>
                <div class="stat-label">Total Annotations</div>
            </div>
        </div>

        <div class="progress-section">
            <h3>Review Progress</h3>
            <div class="progress-bar">
                <c:choose>
                    <c:when test="${dashboard.reviewProgress >= 80}">
                        <div class="progress-fill high" data-progress-width="${dashboard.reviewProgress}">
                            ${dashboard.reviewProgress}%
                        </div>
                    </c:when>
                    <c:when test="${dashboard.reviewProgress < 50}">
                        <div class="progress-fill low" data-progress-width="${dashboard.reviewProgress}">
                            ${dashboard.reviewProgress}%
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="progress-fill" data-progress-width="${dashboard.reviewProgress}">
                            ${dashboard.reviewProgress}%
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
            <p class="progress-copy">
                <c:choose>
                    <c:when test="${dashboard.reviewProgress == 100}">
                        ✅ Review complete - all annotations resolved
                    </c:when>
                    <c:when test="${dashboard.reviewProgress >= 80}">
                        🟢 Nearly complete - ${dashboard.openAnnotations} annotations remaining
                    </c:when>
                    <c:when test="${dashboard.reviewProgress >= 50}">
                        🟡 In progress - ${dashboard.openAnnotations} annotations remaining
                    </c:when>
                    <c:otherwise>
                        🔴 Early stage - ${dashboard.openAnnotations} annotations remaining
                    </c:otherwise>
                </c:choose>
            </p>
        </div>

        <div class="annotations-section">
            <h3>Annotation Summary</h3>
            <div class="annotation-summary">
                <div class="annotation-stat">
                    <div class="annotation-stat-value open">${dashboard.openAnnotations}</div>
                    <div class="annotation-stat-label">Open</div>
                </div>
                <div class="annotation-stat">
                    <div class="annotation-stat-value resolved">${dashboard.resolvedAnnotations}</div>
                    <div class="annotation-stat-label">Resolved</div>
                </div>
                <div class="annotation-stat">
                    <div class="annotation-stat-value total">${dashboard.totalAnnotations}</div>
                    <div class="annotation-stat-label">Total</div>
                </div>
            </div>
        </div>

        <div class="dashboard-actions">
            <a href="javascript:history.back()" class="btn btn-secondary">Back</a>
        </div>
        </c:if>
    </div>
</body>
</html>
