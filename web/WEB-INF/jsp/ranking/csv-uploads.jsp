<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>CSV Uploads - Ranking Period</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ranking.css" />
    </head>
    <body>
        <jsp:include page="../common/header.jsp" />

        <c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

        <div class="section-card">
            <h3 class="section-title section-title-sm">📄 CSV Uploads for Period #${periodId}</h3>

            <c:choose>
                <c:when test="${empty csvUploads}">
                    <div class="empty-state">
                        <div class="empty-state-icon">📁</div>
                        <div class="empty-state-title">No CSV uploads yet</div>
                        <div class="empty-state-copy">Board members have not submitted any CSV files for this period.</div>
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
                            <a href="${pageContext.request.contextPath}/main/ranking/csv-uploads/${upload.id}" class="btn small">View</a>
                        </div>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </div>

        <div class="content-section-large">
            <a class="btn" href="${pageContext.request.contextPath}/main/ranking/periods">← Back to Ranking Periods</a>
        </div>

        <jsp:include page="../common/footer.jsp" />
    </body>
</html>
