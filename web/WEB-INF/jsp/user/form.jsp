<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>User Form</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<h2 class="page-title">${editing ? 'Edit User' : 'Create User'}</h2>
<p class="page-sub">Admin account management</p>

<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

<div class="section-card">
<c:choose>
<c:when test="${editing}">
    <form class="user-form" method="post" action="${pageContext.request.contextPath}/main/users/${editUser.id}/update">
        <div class="form-row">
            <label>Username</label>
            <input type="text" value="${editUser.username}" disabled />
        </div>
        <div class="form-row">
            <label>Full Name</label>
            <input type="text" name="fullName" value="${editUser.fullName}" required />
        </div>
        <div class="form-row">
            <label>Email</label>
            <input type="email" name="email" value="${editUser.email}" required />
        </div>
        <div class="form-actions">
            <button class="btn primary" type="submit">Update User</button>
            <a class="btn" href="${pageContext.request.contextPath}/main/users">Cancel</a>
        </div>
    </form>
</c:when>
<c:otherwise>
    <form class="user-form" method="post" action="${pageContext.request.contextPath}/main/users/create">
        <div class="form-grid two-col">
            <div class="form-row">
                <label>Username</label>
                <input type="text" name="username" value="${formUsername}" required autocomplete="off" />
            </div>
            <div class="form-row">
                <label>Full Name</label>
                <input type="text" name="fullName" value="${formFullName}" required />
            </div>
            <div class="form-row">
                <label>Email</label>
                <input type="email" name="email" value="${formEmail}" required />
            </div>
            <div class="form-row">
                <label>Password</label>
                <input type="password" name="password" value="${formPassword}" required autocomplete="new-password" />
            </div>
            <div class="form-row">
                <label>Confirm Password</label>
                <input type="password" name="confirmPassword" required autocomplete="new-password" />
            </div>
        </div>

        <div class="form-row">
            <label>Roles</label>
            <div class="role-choice-grid">
                <c:forEach items="${availableRoles}" var="r">
                    <c:if test="${r ne 'ADMIN'}">
                        <label class="role-choice">
                            <input type="checkbox" name="roles" value="${r}" ${fn:contains(selectedRolesCsv, r) ? 'checked' : ''} />
                            <span>${r}</span>
                        </label>
                    </c:if>
                </c:forEach>
            </div>
            <c:if test="${adminRoleLocked}">
                <p class="form-note">ADMIN is reserved for the single system administrator.</p>
            </c:if>
        </div>

        <div class="form-actions">
            <button class="btn primary" type="submit">Create User</button>
            <a class="btn" href="${pageContext.request.contextPath}/main/users">Cancel</a>
        </div>
    </form>
</c:otherwise>
</c:choose>
</div>

<script src="${pageContext.request.contextPath}/assets/role-assignment.js"></script>
<jsp:include page="../common/footer.jsp" />
</body>
</html>
