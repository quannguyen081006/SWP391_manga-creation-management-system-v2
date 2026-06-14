<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Something went wrong</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/error.css" />
</head>
<body>
<jsp:include page="header.jsp" />

<div class="section-card">
    <h2 class="page-title">Something went wrong</h2>
    <p class="page-sub">Request could not be completed. Please try again.</p>

    <c:if test="${not empty ex}">
        <div class="alert-box">
            <strong>Error:</strong>
            <c:out value="${ex.message}" default="Unexpected error"/>
        </div>
    </c:if>

    <div class="error-actions">
        <a class="btn" href="${pageContext.request.contextPath}/main/dashboard">Back to Dashboard</a>
        <a class="btn" href="${pageContext.request.contextPath}/main/logout">Re-login</a>
    </div>
</div>

<jsp:include page="footer.jsp" />
</body>
</html>


