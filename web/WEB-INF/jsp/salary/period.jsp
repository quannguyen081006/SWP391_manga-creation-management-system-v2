<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Assistant Salary Periods</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/salary/salary.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<c:if test="${not empty error}"><div class="alert error"><c:out value="${error}" /></div></c:if>

<div class="section-card">
    <h3 class="section-title">My assistant salary periods</h3>
    <c:forEach items="${periods}" var="p">
        <div class="period-card status-${p.status}">
            <div class="period-header">
                <div>
                    <div class="period-title"><c:out value="${p.name}" /></div>
                    <div class="period-meta">
                        <span class="status-badge ${p.status}">${p.status}</span>
                    </div>
                </div>
                <a class="btn small" href="${ctx}/main/salary/periods/${p.id}">View details</a>
            </div>
        </div>
    </c:forEach>

    <c:if test="${empty periods}">
        <div class="empty-state">
            <div class="title">No salary periods yet</div>
            <div class="subtitle">A salary period opens automatically once your assistants have approved tasks.</div>
        </div>
    </c:if>
</div>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
