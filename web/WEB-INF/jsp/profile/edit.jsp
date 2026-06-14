<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>User Profile</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/profile.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<c:if test="${not empty success}"><div class="alert success profile-flash"><c:out value="${success}" /></div></c:if>
<c:if test="${not empty error}"><div class="alert error profile-flash"><c:out value="${error}" /></div></c:if>

<c:if test="${not empty user.avatarUrl}">
    <c:url value="${user.avatarUrl}" var="currentAvatarUrl" />
</c:if>

<div class="profile-layout">
    <section class="section-card">
        <h1 class="profile-card-title">Profile Information</h1>
        <p class="profile-card-desc">Update your name, email address, and profile picture.</p>

        <form class="profile-form" method="post" action="${pageContext.request.contextPath}/main/profile/update" enctype="multipart/form-data">
            <div class="avatar-editor">
                <c:choose>
                    <c:when test="${not empty currentAvatarUrl}">
                        <img id="avatarPreview" class="profile-avatar-preview" src="${currentAvatarUrl}" alt="Current avatar" />
                        <div id="avatarEmpty" class="profile-avatar-empty is-hidden">No avatar</div>
                    </c:when>
                    <c:otherwise>
                        <img id="avatarPreview" class="profile-avatar-preview is-hidden" src="" alt="Current avatar" />
                        <div id="avatarEmpty" class="profile-avatar-empty">No avatar</div>
                    </c:otherwise>
                </c:choose>
                <div class="profile-field avatar-input-field">
                    <label for="avatar">Avatar</label>
                    <input id="avatar" type="file" name="avatar" accept=".jpg,.jpeg,.png,image/jpeg,image/png" />
                    <p class="avatar-help">JPG, JPEG, or PNG. Maximum file size: 2MB.</p>
                </div>
            </div>

            <div class="profile-field">
                <label for="username">Username</label>
                <input id="username" type="text" value="<c:out value='${user.username}' />" readonly />
            </div>
            <div class="profile-field">
                <label for="fullName">Full Name</label>
                <input id="fullName" type="text" name="fullName" value="<c:out value='${user.fullName}' />" maxlength="255" required />
            </div>
            <div class="profile-field">
                <label for="email">Email</label>
                <input id="email" type="email" name="email" value="<c:out value='${user.email}' />" maxlength="255" required />
            </div>
            <div class="profile-actions">
                <button class="btn primary" type="submit">Save Profile</button>
            </div>
        </form>
    </section>

    <section class="section-card">
        <h2 class="profile-card-title">Change Password</h2>
        <p class="profile-card-desc">Use at least 5 characters for your new password.</p>

        <form class="profile-form" method="post" action="${pageContext.request.contextPath}/main/profile/change-password">
            <div class="profile-field">
                <label for="currentPassword">Current Password</label>
                <input id="currentPassword" type="password" name="currentPassword" required autocomplete="current-password" />
            </div>
            <div class="profile-field">
                <label for="newPassword">New Password</label>
                <input id="newPassword" type="password" name="newPassword" minlength="5" required autocomplete="new-password" />
            </div>
            <div class="profile-field">
                <label for="confirmNewPassword">Confirm New Password</label>
                <input id="confirmNewPassword" type="password" name="confirmNewPassword" minlength="5" required autocomplete="new-password" />
            </div>
            <div class="profile-actions">
                <button class="btn primary" type="submit">Change Password</button>
            </div>
        </form>

        <hr style="margin: 1.5rem 0;">
        <a href="${pageContext.request.contextPath}/main/logout"
           class="btn btn-danger"
           data-confirm="Are you sure you want to logout?">
            Logout
        </a>
    </section>
</div>

<script src="${pageContext.request.contextPath}/assets/js/profile.js"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
