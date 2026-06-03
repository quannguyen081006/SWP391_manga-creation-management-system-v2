<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Notifications</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<div class="section-head">
    <div>
        <h2 class="page-title">Notifications</h2>
        <p class="page-sub">${unreadCount} unread notification${unreadCount == 1 ? '' : 's'}</p>
    </div>
    <form method="post" action="${pageContext.request.contextPath}/main/notifications/mark-all-read">
        <button class="btn primary" type="submit" ${unreadCount == 0 ? 'disabled' : ''}>Mark all read</button>
    </form>
</div>

<div class="notification-list">
    <c:choose>
        <c:when test="${empty notifications}">
            <div class="section-card">
                <p class="muted">No notifications yet.</p>
            </div>
        </c:when>
        <c:otherwise>
            <c:forEach items="${notifications}" var="n">
                <article class="notification-row noti-item ${n.read ? 'is-read read' : 'is-unread unread'}">
                    <div class="notification-main">
                        <div class="notification-row-head">
                            <span class="notification-title">${empty n.title ? n.type : n.title}</span>
                            <span class="notification-time">${n.createdAt}</span>
                        </div>
                        <p>${n.message}</p>
                        <c:if test="${not empty n.referenceType}">
                            <span class="status-chip status-draft">${n.referenceType} #${n.referenceId}</span>
                        </c:if>
                    </div>
                    <div class="notification-actions">
                        <c:if test="${not empty n.viewUrl}">
                            <a class="btn small primary" href="${pageContext.request.contextPath}${n.viewUrl}">View</a>
                        </c:if>
                        <c:choose>
                            <c:when test="${n.read}">
                                <span class="status-badge status-active">READ</span>
                            </c:when>
                            <c:otherwise>
                                <form method="post" action="${pageContext.request.contextPath}/main/notifications/${n.id}/read">
                                    <button class="btn small" type="submit">Mark read</button>
                                </form>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </article>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
