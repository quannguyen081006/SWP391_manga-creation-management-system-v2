<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>User Management</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/user.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<%-- User management alerts: success and validation errors from controller actions. --%>
<c:if test="${not empty success}"><div class="alert success">${success}</div></c:if>
<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>
<%-- User creation action: opens the admin create-user form. --%>
<div class="page-actions">
    <a class="btn primary" href="${pageContext.request.contextPath}/main/users/new">+ New User</a>
</div>

<%-- User table: status actions, current roles, and allowed role additions. --%>
<div class="section-card">
    <table class="data-table">
        <thead><tr><th>ID</th><th>Username</th><th>Name</th><th>Email</th><th>Status</th><th>Roles</th><th>Add Role</th><th>Actions</th></tr></thead>
        <tbody>
            <c:forEach items="${users}" var="u">
                <tr class="${createdUserId == u.id ? 'row-created' : ''}">
                    <td>${u.id}</td>
                    <td>${u.username}</td>
                    <td>${u.fullName}</td>
                    <td>${u.email}</td>
                    <td>
                        <span class="status-badge ${u.status == 'ACTIVE' ? 'status-active' : 'status-inactive'}">${u.status}</span>
                    </td>
                    <td class="role-cell">
                        <c:choose>
                            <c:when test="${empty u.roles}">
                                <span class="muted">No roles</span>
                            </c:when>
                            <c:otherwise>
                                <div class="role-list">
                                    <c:forEach items="${u.roles}" var="r">
                                        <c:choose>
                                            <c:when test="${r eq 'ADMIN'}">
                                                <span class="role-chip locked">
                                                    <span>${r}</span>
                                                    <span class="role-lock">Locked</span>
                                                </span>
                                            </c:when>
                                            <c:otherwise>
                                                <form method="post" action="${pageContext.request.contextPath}/main/users/${u.id}/roles/remove" class="role-chip-form">
                                                    <input type="hidden" name="role" value="${r}" />
                                                    <span class="role-chip">
                                                        <span>${r}</span>
                                                        <button class="role-remove" type="submit" title="Remove ${r}" data-confirm="Remove role ${r} from ${u.username}?">x</button>
                                                    </span>
                                                </form>
                                            </c:otherwise>
                                        </c:choose>
                                    </c:forEach>
                                </div>
                            </c:otherwise>
                        </c:choose>
                    </td>
                    <td class="add-role-cell">
                        <c:set var="canAddRole" value="false" />
                        <c:forEach items="${u.roles}" var="existingRole">
                            <c:if test="${existingRole eq 'TANTOU_EDITOR' || existingRole eq 'EDITORIAL_BOARD'}">
                                <c:set var="canAddRole" value="true" />
                            </c:if>
                        </c:forEach>
                        <c:if test="${canAddRole}">
                            <details class="add-role-panel">
                                <summary class="btn small add-role-summary">Add</summary>
                                <form method="post" action="${pageContext.request.contextPath}/main/users/${u.id}/roles" class="role-check-form">
                                    <div class="role-check-grid compact">
                                        <c:forEach items="${availableRoles}" var="r">
                                            <label class="role-check"><input type="checkbox" name="roles" value="${r}" /> ${r}</label>
                                        </c:forEach>
                                    </div>
                                    <button class="btn small primary" type="submit">Apply</button>
                                </form>
                            </details>
                        </c:if>
                    </td>
                    <td>
                        <div class="row-actions">
                            <a class="btn small" href="${pageContext.request.contextPath}/main/users/${u.id}/edit">Edit</a>
                            <c:if test="${u.username ne 'admin'}">
                                <form method="post" action="${pageContext.request.contextPath}/main/users/${u.id}/status">
                                    <input type="hidden" name="status" value="${u.status == 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'}" />
                                    <button class="btn small ${u.status == 'ACTIVE' ? 'danger-soft' : 'success-soft'}" type="submit" data-confirm="${u.status == 'ACTIVE' ? 'Deactivate' : 'Activate'} ${u.username}?">${u.status == 'ACTIVE' ? 'Deactivate' : 'Activate'}</button>
                                </form>
                            </c:if>
                        </div>
                    </td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
</div>

<%-- Role checkbox behavior: keeps role combinations aligned with business rules. --%>
<jsp:include page="../common/footer.jsp" />
</body>
</html>
