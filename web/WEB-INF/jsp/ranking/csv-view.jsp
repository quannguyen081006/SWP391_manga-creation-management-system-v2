<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>View CSV Upload</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ranking.css" />
    </head>
    <body>
        <jsp:include page="../common/header.jsp" />

        <div class="section-card">
            <c:if test="${not empty csvUpload}">
                <h3 class="section-title section-title-sm">📄 ${csvUpload.csvFileName}</h3>

                <c:if test="${not empty error}">
                    <div class="alert error">${error}</div>
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
                <div class="alert error">CSV upload not found</div>
            </c:if>
        </div>

        <div class="content-section-large">
            <a class="btn" href="javascript:history.back()">← Back</a>
        </div>

        <jsp:include page="../common/footer.jsp" />
    </body>
</html>
