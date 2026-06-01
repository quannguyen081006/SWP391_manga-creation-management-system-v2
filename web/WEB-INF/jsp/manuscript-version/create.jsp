<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
    <title>Create Manuscript Workspace - Chapter ${chapter.chapterNumber}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
    <style>
        .create-container {
            max-width: 800px;
            margin: 40px auto;
            padding: 30px;
            background: #fff;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .chapter-info {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 4px;
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
            font-weight: bold;
            color: #495057;
        }
        .info-value {
            color: #212529;
        }
        .status-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: bold;
            text-transform: uppercase;
        }
        .status-editorial_review { background: #74c0fc; color: #fff; }
        .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
            margin-right: 10px;
        }
        .btn-primary { background: #228be6; color: #fff; }
        .btn-secondary { background: #868e96; color: #fff; }
        .btn:hover { opacity: 0.9; }
        .error-message {
            background: #ffe3e3;
            color: #c92a2a;
            padding: 12px;
            border-radius: 4px;
            margin-bottom: 20px;
        }
        .info-box {
            background: #e7f5ff;
            border-left: 4px solid #228be6;
            padding: 15px;
            margin-bottom: 20px;
            border-radius: 4px;
        }
        .info-box h4 {
            margin: 0 0 10px 0;
            color: #1864ab;
        }
        .info-box ul {
            margin: 0;
            padding-left: 20px;
        }
        .info-box li {
            margin-bottom: 5px;
            color: #495057;
        }
    </style>
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="create-container">
        <h2>Create Manuscript Workspace</h2>
        
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
                <span class="info-value">${chapter.title}</span>
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
