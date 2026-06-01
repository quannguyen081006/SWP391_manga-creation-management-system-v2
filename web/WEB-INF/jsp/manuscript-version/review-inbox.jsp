<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
    <title>Manuscript Review Inbox</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
    <style>
        .inbox-container {
            max-width: 1200px;
            margin: 40px auto;
            padding: 30px;
        }
        .inbox-header {
            margin-bottom: 30px;
        }
        .inbox-table {
            width: 100%;
            border-collapse: collapse;
            background: #fff;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            border-radius: 8px;
            overflow: hidden;
        }
        .inbox-table th {
            background: #f8f9fa;
            padding: 15px;
            text-align: left;
            font-weight: bold;
            color: #333;
            border-bottom: 2px solid #dee2e6;
        }
        .inbox-table td {
            padding: 15px;
            border-bottom: 1px solid #dee2e6;
        }
        .inbox-table tr:last-child td {
            border-bottom: none;
        }
        .inbox-table tr:hover {
            background: #f8f9fa;
        }
        .status-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: bold;
            text-transform: uppercase;
        }
        .status-under_review { background: #74c0fc; color: #fff; }
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
        .btn-primary:hover { background: #1c7ed6; }
        .empty-state {
            text-align: center;
            padding: 60px;
            color: #666;
            background: #fff;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        .empty-state h3 {
            margin-bottom: 10px;
            color: #333;
        }
        .chapter-info {
            font-size: 14px;
        }
        .chapter-series {
            color: #666;
            font-size: 12px;
        }
    </style>
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="inbox-container">
        <div class="inbox-header">
            <h1>Manuscript Review Inbox</h1>
            <p style="color: #666;">
                <c:if test="${isAdmin}">
                    All manuscripts waiting for review
                </c:if>
                <c:if test="${not isAdmin}">
                    Manuscripts from your assigned series waiting for review
                </c:if>
            </p>
        </div>

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
                                    <strong>Chapter ${chapter.chapterNumber}</strong>: ${chapter.title}
                                </div>
                                <div class="chapter-series">
                                    Series ID: ${chapter.seriesId}
                                </div>
                            </td>
                            <td>
                                <strong>v${version.version}</strong>
                            </td>
                            <td>
                                <span class="status-badge status-under_review">${version.status}</span>
                            </td>
                            <td>
                                ${mangakaNames[version.id]}
                            </td>
                            <td>
                                ${submittedAtMap[version.id]}
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
