<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
    <title>Manuscript Review Inbox</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/manuscript-version.css" />
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="inbox-container">
        <c:if test="${empty underReviewVersions}">
            <div class="empty-state">
                <h3>No manuscript submissions waiting for review</h3>
                <p>When manuscripts are submitted for review, they will appear here.</p>
            </div>
        </c:if>

        <c:if test="${not empty underReviewVersions}">
            <table class="inbox-table">
                <thead>
                    <tr>
                        <th>Chapter</th>
                        <th>Version</th>
                        <th>Status</th>
                        <th>Due</th>
                        <th>Mangaka</th>
                        <th>Submitted At</th>
                        <th>Action</th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="version" items="${underReviewVersions}">
                        <c:set var="chapter" value="${chapterMap[version.id]}"/>
                        <tr>
                            <td>
                                <div class="chapter-info">
                                    <strong>Chapter <c:out value="${chapter.chapterNumber}" /></strong>: <c:out value="${chapter.title}" />
                                </div>
                                <div class="chapter-series">
                                    Series ID: ${chapter.seriesId}
                                </div>
                            </td>
                            <td>
                                <span class="version-label">v${version.version}</span>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty urgencyMap[version.id]}">
                                        <span class="urgency-dot urgency-${urgencyMap[version.id]}"></span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="urgency-spacer"></span>
                                    </c:otherwise>
                                </c:choose>
                                <span class="status-badge status-under_review">${version.status}</span>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${urgencyMap[version.id] == 'OVERDUE'}">
                                        <span class="countdown countdown-overdue">OVERDUE</span>
                                    </c:when>
                                    <c:when test="${not empty dueAtMap[version.id]}">
                                        <span class="countdown"><c:out value="${dueAtMap[version.id]}" /></span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="text-muted">—</span>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <span class="mangaka-name">${mangakaNames[version.id]}</span>
                            </td>
                            <td>
                                <span class="submitted-time">${submittedAtMap[version.id]}</span>
                            </td>
                            <td>
                                <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}" class="btn btn-primary">
                                    Review Workspace
                                </a>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </c:if>
    </div>
</body>
</html>
