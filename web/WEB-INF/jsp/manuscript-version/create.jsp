<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
    <title>Create Manuscript Workspace - Chapter ${chapter.chapterNumber}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/manuscript-version.css" />
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="create-container">
        <c:if test="${error != null}">
            <div class="error-message">${error}</div>
        </c:if>

        <div class="chapter-info">
            <div class="info-row">
                <span class="info-label">Chapter Number:</span>
                <span class="info-value">${chapter.chapterNumber}</span>
            </div>
            <div class="info-row">
                <span class="info-label">Title:</span>
                <span class="info-value"><c:out value="${chapter.title}" /></span>
            </div>
            <div class="info-row">
                <span class="info-label">Status:</span>
                <span class="info-value">
                    <span class="status-badge status-${fn:toLowerCase(chapter.status)}">${chapter.status}</span>
                </span>
            </div>
            <div class="info-row">
                <span class="info-label">Completion:</span>
                <span class="info-value">${chapter.completionPct}%</span>
            </div>
            <c:if test="${chapter.submissionDeadline != null}">
                <div class="info-row">
                    <span class="info-label">Submission Deadline:</span>
                    <span class="info-value">
                        <fmt:formatDate value="${chapter.submissionDeadline}" pattern="yyyy-MM-dd"/>
                    </span>
                </div>
            </c:if>
        </div>

        <div class="info-box">
            <h4>About Manuscript Workspace</h4>
            <ul>
                <li>This creates a visual editorial review workspace for the chapter</li>
                <li>You can import chapter pages and add inline annotations</li>
                <li>Supports version tracking and comparison</li>
                <li>Only chapters in EDITORIAL_REVIEW status can create workspaces</li>
            </ul>
        </div>

        <form method="post">
            <button type="submit" class="btn btn-primary">Create Workspace</button>
            <a href="${pageContext.request.contextPath}/main/chapters/${chapter.id}" class="btn btn-secondary">Cancel</a>
        </form>
    </div>
</body>
</html>
