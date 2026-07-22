<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
    <title>Version History - Chapter ${chapter.chapterNumber}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/manuscript-version.css" />
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="history-container">
        <%-- Server-rendered manuscript version timeline; no JavaScript is required. --%>
        <c:if test="${empty versions}">
            <div class="empty-state">
                <h3>No manuscript versions found</h3>
                <p>Create a manuscript workspace to begin the review process.</p>
                <a href="${pageContext.request.contextPath}/main/chapters/${chapter.id}/manuscript-workspace/create" class="btn btn-primary">
                    Create Workspace
                </a>
            </div>
        </c:if>

        <c:if test="${not empty versions}">
            <div class="version-list">
                <c:forEach var="version" items="${versions}">
                    <div class="version-card status-${version.status}">
                        <div class="version-header">
                            <div class="version-number">Version ${version.version}</div>
                            <span class="status-badge status-${fn:toLowerCase(version.status).replace('_', '_')}">${version.status}</span>
                        </div>
                        
                        <div class="version-details">
                            <div class="detail-item">
                                <div class="detail-label">Created</div>
                                <div class="detail-value">
                                    ${createdDates[version.id]}
                                </div>
                            </div>
                            <c:if test="${version.submittedAt != null}">
                                <div class="detail-item">
                                    <div class="detail-label">Submitted</div>
                                    <div class="detail-value">
                                        ${submittedDates[version.id]}
                                    </div>
                                </div>
                            </c:if>
                            <c:if test="${version.approvedAt != null}">
                                <div class="detail-item">
                                    <div class="detail-label">Approved</div>
                                    <div class="detail-value">
                                        ${approvedDates[version.id]}
                                    </div>
                                </div>
                            </c:if>
                            <c:if test="${version.rejectedAt != null}">
                                <div class="detail-item">
                                    <div class="detail-label">Rejected</div>
                                    <div class="detail-value">
                                        ${rejectedDates[version.id]}
                                    </div>
                                </div>
                            </c:if>
                            <div class="detail-item">
                                <div class="detail-label">Pages</div>
                                <div class="detail-value">${version.totalPageCount}</div>
                            </div>
                        </div>

                        <c:if test="${version.feedback != null && not empty version.feedback}">
                            <div class="feedback-section">
                                <div class="feedback-label">Feedback:</div>
                                <div><c:out value="${version.feedback}" /></div>
                            </div>
                        </c:if>

                        <%-- Revision notes explain why this version follows the previous one. --%>
                        <c:if test="${version.revisionNotes != null && not empty version.revisionNotes}">
                            <div class="feedback-section revision-notes">
                                <div class="feedback-label">Revision Notes:</div>
                                <div><c:out value="${version.revisionNotes}" /></div>
                            </div>
                        </c:if>

                        <div class="version-actions">
                            <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}" class="btn btn-primary">
                                View Workspace
                            </a>
                            <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/dashboard" class="btn btn-secondary">
                                Dashboard
                            </a>
                            <%-- previousVersionId links this version to its parent for comparison. --%>
                            <c:if test="${version.previousVersionId != null}">
                                <a href="${pageContext.request.contextPath}/main/manuscript-workspace/compare?versionId1=${version.previousVersionId}&versionId2=${version.id}" class="btn btn-secondary">
                                    Compare With Previous
                                </a>
                            </c:if>
                        </div>
                    </div>
                </c:forEach>
            </div>
        </c:if>

        <div class="content-section-large">
            <a href="${pageContext.request.contextPath}/main/chapters/${chapter.id}" class="btn btn-secondary">Back to Chapter</a>
        </div>
    </div>
</body>
</html>
