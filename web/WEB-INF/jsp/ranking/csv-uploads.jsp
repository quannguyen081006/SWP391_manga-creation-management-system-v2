<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>CSV Uploads - Ranking Period</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
        <style>
            .csv-uploads-container {
                max-width: 1200px;
                margin: 0 auto;
                padding: 20px;
            }

            .back-link {
                display: inline-block;
                margin-bottom: 20px;
                color: #667eea;
                text-decoration: none;
                font-weight: 600;
            }

            .back-link:hover {
                text-decoration: underline;
            }

            .section-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 30px;
            }

            .section-title {
                font-size: 28px;
                font-weight: 700;
                color: #2c3e50;
            }

            .csv-upload-item {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 20px;
                background: white;
                border-radius: 8px;
                margin-bottom: 15px;
                box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
                border-left: 4px solid #667eea;
            }

            .csv-upload-info {
                display: flex;
                align-items: center;
                gap: 20px;
            }

            .csv-upload-icon {
                font-size: 32px;
            }

            .csv-upload-details {
                display: flex;
                flex-direction: column;
                gap: 5px;
            }

            .csv-upload-filename {
                font-weight: 600;
                font-size: 16px;
                color: #2c3e50;
            }

            .csv-upload-meta {
                font-size: 13px;
                color: #7f8c8d;
            }

            .view-btn {
                background: #667eea;
                color: white;
                border: none;
                padding: 10px 20px;
                border-radius: 6px;
                cursor: pointer;
                font-weight: 600;
                text-decoration: none;
                display: inline-block;
            }

            .view-btn:hover {
                background: #5568d3;
            }

            .empty-state {
                text-align: center;
                padding: 60px 20px;
                color: #95a5a6;
            }

            .empty-state-icon {
                font-size: 48px;
                margin-bottom: 16px;
            }

            .empty-state-text {
                font-size: 18px;
                margin-bottom: 8px;
            }

            .empty-state-subtext {
                font-size: 14px;
            }
        </style>
    </head>
    <body>
        <jsp:include page="../common/header.jsp" />

        <div class="csv-uploads-container">
            <a href="${pageContext.request.contextPath}/main/ranking/periods" class="back-link">← Back to Ranking Periods</a>

            <div class="section-header">
                <h1 class="section-title">📄 CSV Uploads for Period #${periodId}</h1>
            </div>

            <c:if test="${not empty error}">
                <div class="alert error">${error}</div>
            </c:if>

            <c:choose>
                <c:when test="${empty csvUploads}">
                    <div class="empty-state">
                        <div class="empty-state-icon">📁</div>
                        <div class="empty-state-text">No CSV uploads yet</div>
                        <div class="empty-state-subtext">Board members have not submitted any CSV files for this period.</div>
                    </div>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${csvUploads}" var="upload">
                        <div class="csv-upload-item">
                            <div class="csv-upload-info">
                                <div class="csv-upload-icon">📄</div>
                                <div class="csv-upload-details">
                                    <div class="csv-upload-filename">${upload.csvFileName}</div>
                                    <div class="csv-upload-meta">
                                        Uploaded by <strong>${upload.username}</strong> on ${upload.uploadedAt}
                                    </div>
                                </div>
                            </div>
                            <a href="${pageContext.request.contextPath}/main/ranking/csv-uploads/${upload.id}" class="view-btn">View</a>
                        </div>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </div>

        <jsp:include page="../common/footer.jsp" />
    </body>
</html>
