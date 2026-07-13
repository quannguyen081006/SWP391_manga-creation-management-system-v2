<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>View CSV Upload</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
        <style>
            .csv-view-container {
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
                margin-bottom: 30px;
            }

            .section-title {
                font-size: 28px;
                font-weight: 700;
                color: #2c3e50;
            }

            .csv-meta {
                background: #f8f9fa;
                padding: 15px 20px;
                border-radius: 8px;
                margin-bottom: 20px;
                display: flex;
                gap: 30px;
                font-size: 14px;
                color: #7f8c8d;
            }

            .csv-meta-item {
                display: flex;
                flex-direction: column;
                gap: 5px;
            }

            .csv-meta-label {
                font-weight: 600;
                color: #2c3e50;
            }

            .csv-content {
                background: white;
                border: 1px solid #e0e0e0;
                border-radius: 8px;
                padding: 20px;
                overflow: auto;
                max-height: 600px;
            }

            .csv-content pre {
                margin: 0;
                white-space: pre-wrap;
                font-family: 'Courier New', monospace;
                font-size: 13px;
                line-height: 1.5;
                color: #2c3e50;
            }

            .error-message {
                background: #fee;
                color: #c33;
                padding: 20px;
                border-radius: 8px;
                border-left: 4px solid #c33;
            }
        </style>
    </head>
    <body>
        <jsp:include page="../common/header.jsp" />

        <div class="csv-view-container">
            <c:if test="${not empty csvUpload}">
                <a href="javascript:history.back()" class="back-link">← Back</a>

                <div class="section-header">
                    <h1 class="section-title">📄 ${csvUpload.csvFileName}</h1>
                </div>

                <c:if test="${not empty error}">
                    <div class="error-message">${error}</div>
                </c:if>

                <div class="csv-meta">
                    <div class="csv-meta-item">
                        <span class="csv-meta-label">Period ID</span>
                        <span>${csvUpload.periodId}</span>
                    </div>
                    <div class="csv-meta-item">
                        <span class="csv-meta-label">Board Member ID</span>
                        <span>${csvUpload.boardMemberId}</span>
                    </div>
                    <div class="csv-meta-item">
                        <span class="csv-meta-label">Uploaded At</span>
                        <span>${csvUpload.uploadedAt}</span>
                    </div>
                </div>

                <div class="csv-content">
                    <pre>${csvUpload.csvContent}</pre>
                </div>
            </c:if>

            <c:if test="${empty csvUpload}">
                <div class="error-message">CSV upload not found</div>
            </c:if>
        </div>

        <jsp:include page="../common/footer.jsp" />
    </body>
</html>
