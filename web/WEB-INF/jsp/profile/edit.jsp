<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
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
<c:set var="trimmedFullName" value="${fn:trim(user.fullName)}" />
<c:choose>
    <c:when test="${not empty trimmedFullName}">
        <c:set var="nameParts" value="${fn:split(trimmedFullName, ' ')}" />
        <c:set var="firstNamePart" value="${nameParts[0]}" />
        <c:set var="lastNamePart" value="${nameParts[fn:length(nameParts)-1]}" />
        <c:set var="avatarInitials" value="${fn:toUpperCase(fn:substring(firstNamePart, 0, 1))}${fn:toUpperCase(fn:substring(lastNamePart, 0, 1))}" />
    </c:when>
    <c:otherwise>
        <c:set var="avatarInitials" value="U" />
    </c:otherwise>
</c:choose>

<div class="profile-layout">
    <section class="section-card">
        <h1 class="profile-card-title">Profile Information</h1>
        <p class="profile-card-desc">Update your name, email address, and profile picture.</p>

        <form id="profileForm" class="profile-form" method="post" action="${pageContext.request.contextPath}/main/profile/update"
              enctype="multipart/form-data">
            <div class="avatar-editor">
                <c:choose>
                    <c:when test="${not empty currentAvatarUrl}">
                        <img id="avatarPreview" class="profile-avatar-preview" src="${currentAvatarUrl}" alt="Profile avatar" />
                        <div id="avatarEmpty" class="profile-avatar-empty is-hidden"><c:out value="${avatarInitials}" /></div>
                    </c:when>
                    <c:otherwise>
                        <img id="avatarPreview" class="profile-avatar-preview is-hidden" src="" alt="Profile avatar" />
                        <div id="avatarEmpty" class="profile-avatar-empty"><c:out value="${avatarInitials}" /></div>
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
        </form>

        <div class="profile-actions">
            <a href="${pageContext.request.contextPath}/main/profile/change-password"
               class="btn btn-secondary">
                Change Password
            </a>
            <button class="btn btn-primary" type="submit" form="profileForm">Save Profile</button>
        </div>
    </section>
</div>

<script src="${pageContext.request.contextPath}/assets/js/profile.js"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
