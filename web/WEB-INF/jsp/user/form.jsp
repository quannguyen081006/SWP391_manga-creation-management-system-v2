<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>User Form</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/user.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<%-- Form error from create/update validation. --%>
<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

<%-- User form: edit mode updates profile fields, create mode collects account and role data. --%>
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
        </div>

        <div class="form-row">
            <label>Roles</label>
            <div class="role-choice-grid">
                <label class="role-choice">
                    <input type="radio" name="roleOption" value="MANGAKA" ${selectedRoleOption eq 'MANGAKA' ? 'checked' : ''} required />
                    <span>MANGAKA</span>
                </label>
                <label class="role-choice">
                    <input type="radio" name="roleOption" value="ASSISTANT" ${selectedRoleOption eq 'ASSISTANT' ? 'checked' : ''} required />
                    <span>ASSISTANT</span>
                </label>
                <label class="role-choice">
                    <input type="radio" name="roleOption" value="TANTOU_EDITOR" ${selectedRoleOption eq 'TANTOU_EDITOR' ? 'checked' : ''} required />
                    <span>TANTOU_EDITOR</span>
                </label>
                <label class="role-choice">
                    <input type="radio" name="roleOption" value="EDITORIAL_BOARD" ${selectedRoleOption eq 'EDITORIAL_BOARD' ? 'checked' : ''} required />
                    <span>EDITORIAL_BOARD</span>
                </label>
                <label class="role-choice">
                    <input type="radio" name="roleOption" value="TANTOU_EDITOR,EDITORIAL_BOARD" ${selectedRoleOption eq 'TANTOU_EDITOR,EDITORIAL_BOARD' ? 'checked' : ''} required />
                    <span>TANTOU_EDITOR + EDITORIAL_BOARD</span>
                </label>
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

<%-- Shared role-assignment behavior; create radios need no combination enforcement. --%>
<jsp:include page="../common/footer.jsp" />
</body>
</html>
