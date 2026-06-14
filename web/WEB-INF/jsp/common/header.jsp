<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<c:set var="uri" value="${pageContext.request.requestURI}" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<link rel="stylesheet" href="${ctx}/assets/css/header.css" />
<script src="${ctx}/assets/auth-session.js" data-context-path="${ctx}"></script>
<script src="${ctx}/assets/js/common.js"></script>
<script src="${ctx}/assets/role-assignment.js"></script>
<script src="${ctx}/assets/js/header.js"></script>

<%-- Current user context: derive display role, role flags, and avatar text. --%>
<c:set var="displayRole" value="User" />
<c:set var="roleKey" value="user" />
<c:choose>
    <c:when test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('ADMIN')}">
        <c:set var="displayRole" value="Admin" />
        <c:set var="roleKey" value="admin" />
    </c:when>
    <c:when test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('MANGAKA')}">
        <c:set var="displayRole" value="Mangaka" />
        <c:set var="roleKey" value="mangaka" />
    </c:when>
    <c:when test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('ASSISTANT')}">
        <c:set var="displayRole" value="Assistant" />
        <c:set var="roleKey" value="assistant" />
    </c:when>
    <c:when test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('TANTOU_EDITOR')}">
        <c:set var="displayRole" value="Tantou Editor" />
        <c:set var="roleKey" value="tantou" />
    </c:when>
    <c:when test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('EDITORIAL_BOARD')}">
        <c:set var="displayRole" value="Editorial Board" />
        <c:set var="roleKey" value="board" />
    </c:when>
</c:choose>

<c:set var="displayName" value="${empty sessionScope.AUTH_USER.fullName ? 'Yuki Tanaka' : sessionScope.AUTH_USER.fullName}" />
<c:set var="isAdmin" value="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('ADMIN')}" />
<c:set var="isMangaka" value="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('MANGAKA')}" />
<c:set var="isAssistant" value="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('ASSISTANT')}" />
<c:set var="isTantou" value="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('TANTOU_EDITOR')}" />
<c:set var="isBoard" value="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('EDITORIAL_BOARD')}" />
<c:set var="trimmedName" value="${fn:trim(displayName)}" />
<c:set var="nameParts" value="${fn:split(trimmedName, ' ')}" />
<c:set var="firstPart" value="${nameParts[0]}" />
<c:set var="lastPart" value="${nameParts[fn:length(nameParts)-1]}" />
<c:set var="avatarText" value="${fn:toUpperCase(fn:substring(firstPart, 0, 1))}" />
<c:choose>
    <c:when test="${fn:length(nameParts) > 1}">
        <c:set var="avatarText" value="${avatarText}${fn:toUpperCase(fn:substring(lastPart, 0, 1))}" />
    </c:when>
    <c:when test="${fn:length(firstPart) >= 2}">
        <c:set var="avatarText" value="${avatarText}${fn:toUpperCase(fn:substring(firstPart, 1, 2))}" />
    </c:when>
    <c:otherwise>
        <c:set var="avatarText" value="${avatarText}X" />
    </c:otherwise>
</c:choose>
<%
    Object forwardedUri = request.getAttribute("javax.servlet.forward.request_uri");
    String uri = forwardedUri == null ? request.getRequestURI() : forwardedUri.toString();
    String pageName = "";
    if (uri.contains("/dashboard")) pageName = "Dashboard";
    else if (uri.contains("/notifications")) pageName = "Notifications";
    else if (uri.contains("/proposals")) pageName = "Proposals";
    else if (uri.contains("/series")) pageName = "Series";
    else if (uri.contains("/chapters")) pageName = "Chapters";
    else if (uri.contains("/tasks")) pageName = "Tasks";
    else if (uri.contains("/manuscript-review") || uri.contains("/manuscripts")) pageName = "Manuscript Reviews";
    else if (uri.contains("/ranking")) pageName = "Ranking";
    else if (uri.contains("/decisions")) pageName = "Decisions";
    else if (uri.contains("/profile")) pageName = "Profile";
    else if (uri.contains("/users")) pageName = "Users";
    else if (uri.contains("/analytics")) pageName = "Analytics";
    request.setAttribute("_pageName", pageName);
%>

<%-- App shell sidebar: role-based navigation for authenticated users. --%>
<div class="app-shell">
    <aside class="side-nav">
        <a class="side-brand" href="${ctx}/main/dashboard" title="Back to Dashboard">
            <div class="brand-icon">MF</div>
            <div>
                <div class="brand-name">MangaFlow <span class="brand-version">v2.3</span></div>
                <div class="brand-sub">Manga Studio Ops</div>
            </div>
        </a>

        <button class="sidebar-pin" type="button" title="Collapse sidebar" aria-label="Collapse sidebar" aria-pressed="false">
            <span class="pin-icon" aria-hidden="true"></span>
            <span class="pin-label">Collapse</span>
        </button>

        <div class="side-title">Navigation</div>
        <%-- BR-SYS role-based UI hiding; AuthInterceptor still enforces access on the server. --%>
        <a class="nav-item nav-dashboard ${fn:contains(uri, '/main/dashboard') ? 'active' : ''}" href="${ctx}/main/dashboard" title="Dashboard">
            <span class="nav-icon" aria-hidden="true"></span>
            <span class="nav-label">Dashboard</span>
        </a>
        <c:if test="${isAdmin || isMangaka || isTantou || isBoard}">
            <a class="nav-item nav-proposals ${fn:contains(uri, '/main/proposals') ? 'active' : ''}" href="${ctx}/main/proposals" title="Proposals">
                <span class="nav-icon" aria-hidden="true"></span>
                <span class="nav-label">Proposals</span>
            </a>
        </c:if>
        <c:if test="${isAdmin || isMangaka || isTantou}">
            <a class="nav-item nav-series ${fn:contains(uri, '/main/series') ? 'active' : ''}" href="${ctx}/main/series" title="Series">
                <span class="nav-icon" aria-hidden="true"></span>
                <span class="nav-label">Series</span>
            </a>
        </c:if>
        <c:if test="${isAdmin || isAssistant || isTantou}">
            <a class="nav-item nav-tasks ${fn:contains(uri, '/main/tasks') ? 'active' : ''}" href="${ctx}/main/tasks" title="Tasks">
                <span class="nav-icon" aria-hidden="true"></span>
                <span class="nav-label">Tasks</span>
            </a>
        </c:if>
        <c:if test="${isAdmin || isBoard}">
            <a class="nav-item nav-decisions ${fn:contains(uri, '/main/decisions') ? 'active' : ''}" href="${ctx}/main/decisions" title="Decisions">
                <span class="nav-icon" aria-hidden="true"></span>
                <span class="nav-label">Decisions</span>
            </a>
        </c:if>
        <c:if test="${isTantou}">
            <a class="nav-item nav-manuscripts nav-manuscript-review ${fn:contains(uri, '/main/manuscript-review') ? 'active' : ''}" href="${ctx}/main/manuscript-review" title="Manuscript Reviews">
                <span class="nav-icon" aria-hidden="true"></span>
                <span class="nav-label">Manuscript Reviews</span>
            </a>
        </c:if>
        <a class="nav-item nav-ranking ${fn:contains(uri, '/main/ranking') ? 'active' : ''}" href="${ctx}/main/ranking/periods" title="Ranking">
            <span class="nav-icon" aria-hidden="true"></span>
            <span class="nav-label">Ranking</span>
        </a>

        <c:if test="${isAdmin}">
            <a class="nav-item nav-users ${fn:contains(uri, '/main/users') ? 'active' : ''}" href="${ctx}/main/users" title="Users">
                <span class="nav-icon" aria-hidden="true"></span>
                <span class="nav-label">Users</span>
            </a>
        </c:if>

    </aside>

    <section class="main-shell">
        <header class="top-shell">
            <%-- Top header: dashboard title, active role pills, notifications, and account actions. --%>
            <div class="page-head">
                <span class="page-heading-label"><%= pageName %></span>
                <c:if test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('ADMIN')}">
                    <span class="role-pill role-admin">Admin</span>
                </c:if>
                <c:if test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('MANGAKA')}">
                    <span class="role-pill role-mangaka">Mangaka</span>
                </c:if>
                <c:if test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('ASSISTANT')}">
                    <span class="role-pill role-assistant">Assistant</span>
                </c:if>
                <c:if test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('TANTOU_EDITOR')}">
                    <span class="role-pill role-tantou">Tantou Editor</span>
                </c:if>
                <c:if test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('EDITORIAL_BOARD')}">
                    <span class="role-pill role-board">Editorial Board</span>
                </c:if>
            </div>
            <div class="top-user">
                <%-- Notification dropdown: click item to mark read and redirect through web controller. --%>
                <details class="notify-switcher">
                    <summary class="notify-toggle" title="Notifications">
    <svg class="notify-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d="M15 17h5l-1.4-1.4A2 2 0 0 1 18 14.2V11a6 6 0 1 0-12 0v3.2a2 2 0 0 1-.6 1.4L4 17h11"></path>
        <path d="M9 21a3 3 0 0 0 6 0"></path>
    </svg>
    <c:if test="${headerUnreadNotificationCount gt 0}">
        <span class="notify-count noti-badge">${headerUnreadNotificationCount}</span>
    </c:if>
</summary>
                    <div class="notify-menu">
                        <div class="notify-menu-head">
                            <span>Notifications</span>
                            <form method="post" action="${ctx}/main/notifications/mark-all-read" class="notify-mark-all-form">
                                <button type="submit" ${headerUnreadNotificationCount == 0 ? 'disabled' : ''}>Mark all read</button>
                            </form>
                        </div>
                        <c:choose>
                            <c:when test="${empty headerNotifications}">
                                <div class="notify-empty">No notifications yet.</div>
                            </c:when>
                            <c:otherwise>
                                <c:forEach items="${headerNotifications}" var="n">
                                    <div class="notify-item noti-item ${n.read ? 'is-read read' : 'is-unread unread'}" data-noti-id="${n.id}" data-is-read="${n.read}">
                                        <a href="${ctx}/main/notifications/${n.id}/click" class="notify-item-main text-decoration-none">
                                            <div class="noti-title">${empty n.title ? n.type : n.title}</div>
                                            <div class="noti-message">${n.message}</div>
                                            <div class="noti-time" data-time="${n.createdAt}"></div>
                                        </a>
                                        <c:if test="${!n.read}">
                                            <span class="noti-dot" aria-hidden="true"></span>
                                        </c:if>
                                        <div class="noti-actions ms-2">
                                            <button type="button"
                                                    class="btn btn-sm p-0 text-muted noti-menu-btn"
                                                    data-id="${n.id}"
                                                    data-read="${n.read}"
                                                    data-menu-id="header-noti-menu-${n.id}"
                                                    data-notification-menu>...</button>
                                            <div class="noti-actions-menu" id="header-noti-menu-${n.id}">
                                                <button type="button" class="noti-menu-item noti-menu-delete"
                                                        data-notification-delete="${n.id}">Delete</button>
                                                <button type="button" class="noti-menu-item noti-menu-toggle"
                                                        data-notification-toggle="${n.id}" data-read="${n.read}">
                                                    ${n.read ? 'Mark as unread' : 'Mark as read'}
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                        <%-- Full notification list link; individual items redirect through stored viewUrl validation. --%>
                        <div class="dropdown-divider"></div>
                        <a href="${ctx}/main/notifications" class="dropdown-item text-center text-primary fw-semibold py-2 notify-see-all">
                            View all notifications
                        </a>
                    </div>
                </details>

                <a class="profile-avatar-link" href="${ctx}/main/profile" title="Open profile">
                    <c:choose>
                        <c:when test="${not empty sessionScope.AUTH_USER.avatarUrl}">
                            <img class="avatar header-avatar-image" src="${ctx}${sessionScope.AUTH_USER.avatarUrl}" alt="Profile avatar" />
                        </c:when>
                        <c:otherwise>
                            <span class="avatar role-${roleKey}">${avatarText}</span>
                        </c:otherwise>
                    </c:choose>
                    <span class="user-name profile-name-link"><c:out value="${displayName}" default="Yuki Tanaka"/></span>
                </a>
            </div>
        </header>
        <main class="page-wrap">
