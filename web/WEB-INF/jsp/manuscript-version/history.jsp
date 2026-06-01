<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
    <title>Version History - Chapter ${chapter.chapterNumber}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
    <style>
        .history-container {
            max-width: 1000px;
            margin: 40px auto;
            padding: 30px;
        }
        .chapter-header {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 30px;
        }
        .version-list {
            display: flex;
            flex-direction: column;
            gap: 20px;
        }
        .version-card {
            background: #fff;
            padding: 25px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            border-left: 4px solid #228be6;
            transition: transform 0.2s, box-shadow 0.2s;
        }
        .version-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        }
        .version-card.status-DRAFT { border-left-color: #ffd43b; }
        .version-card.status-UNDER_REVIEW { border-left-color: #74c0fc; }
        .version-card.status-APPROVED { border-left-color: #51cf66; }
        .version-card.status-REJECTED { border-left-color: #ff6b6b; }
        .version-card.status-PUBLISHED { border-left-color: #845ef7; }
        .version-card.status-ARCHIVED { border-left-color: #868e96; }
        .version-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
        }
        .version-number {
            font-size: 24px;
            font-weight: bold;
            color: #228be6;
        }
        .status-badge {
            display: inline-block;
            padding: 6px 16px;
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
        .version-details {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-bottom: 15px;
        }
        .detail-item {
            font-size: 14px;
        }
        .detail-label {
            color: #666;
            font-weight: 500;
        }
        .detail-value {
            color: #333;
            font-weight: bold;
        }
        .version-actions {
            display: flex;
            gap: 10px;
            margin-top: 15px;
            padding-top: 15px;
            border-top: 1px solid #dee2e6;
        }
        .btn {
            padding: 8px 16px;
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
        .feedback-section {
            background: #fff5f5;
            padding: 15px;
            border-radius: 4px;
            margin-top: 15px;
            border-left: 3px solid #ff6b6b;
        }
        .feedback-label {
            font-weight: bold;
            color: #c92a2a;
            margin-bottom: 5px;
        }
        .empty-state {
            text-align: center;
            padding: 60px;
            color: #666;
        }
        .empty-state h3 {
            margin-bottom: 10px;
        }
    </style>
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="history-container">
        <div class="chapter-header">
            <h2>Version History</h2>
            <p style="color: #666;">Chapter ${chapter.chapterNumber}: ${chapter.title}</p>
        </div>

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
                                    ${versionDates[version.id]}
                                </div>
                            </div>
                            <c:if test="${version.submittedAt != null}">
                                <div class="detail-item">
                                    <div class="detail-label">Submitted</div>
                                    <div class="detail-value">
                                        ${versionDates[version.id + '_submitted']}
                                    </div>
                                </div>
                            </c:if>
                            <c:if test="${version.approvedAt != null}">
                                <div class="detail-item">
                                    <div class="detail-label">Approved</div>
                                    <div class="detail-value">
                                        ${versionDates[version.id + '_approved']}
                                    </div>
                                </div>
                            </c:if>
                            <c:if test="${version.rejectedAt != null}">
                                <div class="detail-item">
                                    <div class="detail-label">Rejected</div>
                                    <div class="detail-value">
                                        ${versionDates[version.id + '_rejected']}
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
                                <div>${version.feedback}</div>
                            </div>
                        </c:if>

                        <c:if test="${version.revisionNotes != null && not empty version.revisionNotes}">
                            <div class="feedback-section" style="background: #f6fff9; border-left-color: #51cf66;">
                                <div class="feedback-label" style="color: #2b8a3e;">Revision Notes:</div>
                                <div>${version.revisionNotes}</div>
                            </div>
                        </c:if>

                        <div class="version-actions">
                            <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}" class="btn btn-primary">
                                View Workspace
                            </a>
                            <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/dashboard" class="btn btn-secondary">
                                Dashboard
                            </a>
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

        <div style="margin-top: 30px;">
            <a href="${pageContext.request.contextPath}/main/chapters/${chapter.id}" class="btn btn-secondary">Back to Chapter</a>
        </div>
    </div>
</body>
</html>
