<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Change Password</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/profile.css" />
    <style>
        .input-with-toggle {
            position: relative;
            display: flex;
            align-items: center;
        }
        .input-with-toggle input {
            width: 100%;
            padding-right: 44px;
        }
        .input-with-toggle .toggle-password {
            position: absolute;
            right: 6px;
            top: 50%;
            transform: translateY(-50%);
            width: 32px;
            height: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
            border: none;
            background: transparent;
            color: #6b7280;
            cursor: pointer;
            padding: 0;
        }
        .input-with-toggle .toggle-password:hover {
            color: #374151;
        }
    </style>
</head>
<body>
<jsp:include page="../common/header.jsp" />

<c:if test="${not empty flashSuccess}"><div class="alert success profile-flash"><c:out value="${flashSuccess}" /></div></c:if>
<c:if test="${not empty flashError}"><div class="alert error profile-flash"><c:out value="${flashError}" /></div></c:if>

<div class="profile-layout">
    <section class="section-card">
        <h1 class="profile-card-title">Change Password</h1>
        <p class="profile-card-desc">Use at least 5 characters for your new password.</p>

        <form id="changePasswordForm" class="profile-form" method="post" action="${pageContext.request.contextPath}/main/profile/change-password">
            <div class="profile-field">
                <label for="currentPassword">Current Password</label>
                <div class="input-with-toggle">
                    <input type="password" id="currentPassword" name="currentPassword" class="form-control" required autocomplete="current-password" />
                    <button type="button" class="toggle-password" aria-label="Toggle password visibility" data-target="currentPassword">
                        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24"
                             fill="none" stroke="currentColor" stroke-width="2"
                             stroke-linecap="round" stroke-linejoin="round">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                        </svg>
                    </button>
                </div>
            </div>
            <div class="profile-field">
                <label for="newPassword">New Password</label>
                <div class="input-with-toggle">
                    <input type="password" id="newPassword" name="newPassword" class="form-control" minlength="5" required autocomplete="new-password" />
                    <button type="button" class="toggle-password" aria-label="Toggle password visibility" data-target="newPassword">
                        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24"
                             fill="none" stroke="currentColor" stroke-width="2"
                             stroke-linecap="round" stroke-linejoin="round">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                        </svg>
                    </button>
                </div>
            </div>
            <div class="profile-field">
                <label for="confirmNewPassword">Confirm New Password</label>
                <div class="input-with-toggle">
                    <input type="password" id="confirmNewPassword" name="confirmNewPassword" class="form-control" minlength="5" required autocomplete="new-password" />
                    <button type="button" class="toggle-password" aria-label="Toggle password visibility" data-target="confirmNewPassword">
                        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24"
                             fill="none" stroke="currentColor" stroke-width="2"
                             stroke-linecap="round" stroke-linejoin="round">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                        </svg>
                    </button>
                </div>
            </div>
        </form>

        <div class="profile-actions">
            <a href="${pageContext.request.contextPath}/main/profile" class="btn btn-secondary">&larr; Back to Profile</a>
            <button class="btn primary" type="submit" form="changePasswordForm">Change Password</button>
        </div>
    </section>
</div>

<script>
    document.addEventListener('DOMContentLoaded', function () {
        var toggles = document.querySelectorAll('.toggle-password');
        toggles.forEach(function (button) {
            button.addEventListener('click', function () {
                var targetId = button.getAttribute('data-target');
                var input = document.getElementById(targetId);
                if (!input) {
                    return;
                }
                var isHidden = input.type === 'password';
                input.type = isHidden ? 'text' : 'password';
                button.setAttribute('aria-pressed', isHidden ? 'true' : 'false');
            });
        });
    });
</script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
