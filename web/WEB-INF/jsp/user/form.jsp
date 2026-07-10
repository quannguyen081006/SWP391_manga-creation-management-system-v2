<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>User Form</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/user.css" />
    <style>
        .password-wrapper {
            position: relative;
        }
        .password-wrapper input {
            width: 100%;
            padding-right: 44px;
            box-sizing: border-box;
        }
        .password-wrapper button {
            position: absolute;
            right: 8px;
            top: 50%;
            transform: translateY(-50%);
            background: none;
            border: none;
            cursor: pointer;
            color: #888;
            padding: 0;
            display: flex;
            align-items: center;
        }
        .password-wrapper button:hover {
            color: #333;
        }
    </style>
</head>
<body>
<jsp:include page="../common/header.jsp" />

<%-- Form error from create/update validation. --%>
<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

<%-- User form: edit mode updates profile fields, create mode collects account and role data. --%>
<div class="section-card">
<c:choose>
<%-- Edit mode intentionally keeps username read-only and leaves roles to the list page. --%>
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
                <label>Full Name</label>
                <input type="text" name="fullName" value="${formFullName}" required />
            </div>
            <div class="form-row">
                <label>Email</label>
                <input type="email" name="email" value="${formEmail}" required />
            </div>
            <div class="form-row">
                <label>Username</label>
                <input type="text" name="username" value="${formUsername}" required autocomplete="off" />
            </div>
            <div class="form-row">
                <label>Password</label>
                <div class="password-wrapper">
                    <input type="password" id="passwordInput" name="password" value="12345" autocomplete="new-password" />
                    <button type="button" id="togglePasswordBtn" onclick="togglePasswordVisibility()" aria-label="Toggle password visibility">
                        <svg id="eyeOpen" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24"
                             fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                        </svg>
                        <svg id="eyeClosed" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24"
                             fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                             style="display:none">
                            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                            <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                            <line x1="1" y1="1" x2="23" y2="23"/>
                        </svg>
                    </button>
                </div>
            </div>
        </div>

        <div class="form-row">
            <label>Roles</label>
            <%-- One radio group mirrors the allowed role combinations from RoleCombinationValidator. --%>
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
            <%-- ADMIN is singleton, so create mode explains why it is not offered here. --%>
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
<script>
    function togglePasswordVisibility() {
        var input = document.getElementById('passwordInput');
        var eyeOpen = document.getElementById('eyeOpen');
        var eyeClosed = document.getElementById('eyeClosed');
        if (input.type === 'password') {
            input.type = 'text';
            eyeOpen.style.display = 'none';
            eyeClosed.style.display = 'inline';
        } else {
            input.type = 'password';
            eyeOpen.style.display = 'inline';
            eyeClosed.style.display = 'none';
        }
    }
</script>
</body>
</html>
