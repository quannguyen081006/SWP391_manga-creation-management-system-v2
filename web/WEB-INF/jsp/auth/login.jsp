<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Manga Editorial System - Login</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
</head>
<body class="login-page">
    <main class="login-wrap" role="main">
        <section class="login-art" aria-hidden="true">
            <div class="manga-page">
                <div class="manga-panel manga-panel-wide">
                    <span class="speed-line speed-line-a"></span>
                    <span class="speed-line speed-line-b"></span>
                    <span class="speed-line speed-line-c"></span>
                    <span class="ink-burst"></span>
                </div>
                <div class="manga-panel manga-panel-tall">
                    <span class="panel-face"></span>
                    <span class="panel-shadow"></span>
                </div>
                <div class="manga-panel manga-panel-small">
                    <span class="speech-bubble"></span>
                </div>
                <div class="manga-panel manga-panel-small accent">
                    <span class="tone-dot-grid"></span>
                </div>
            </div>
        </section>
        <section class="login-panel" aria-label="Sign in form">
            <div class="login-brand">
                <div class="login-brand-mark">M</div>
                <div>
                    <h1>MangaFlow</h1>
                    <p>Editorial Publishing Hub</p>
                </div>
            </div>

            <h2 class="login-title">Welcome back</h2>
            <p class="login-subtitle">Sign in to continue managing proposals, chapters, and manuscripts.</p>

            <c:if test="${not empty error}">
                <div class="alert error">${error}</div>
            </c:if>

            <form method="post" action="${pageContext.request.contextPath}/main/login" class="login-form" novalidate>
                <label class="login-label" for="username">Username</label>
                <input id="username" class="login-input" type="text" name="username" value="${username}" required autocomplete="username" />

                <label class="login-label" for="password">Password</label>
                <input id="password" class="login-input" type="password" name="password" required autocomplete="current-password" />

                <button type="submit" class="login-submit">Sign In</button>
            </form>

            <div class="login-meta">Role-based access is enforced at API level.</div>
        </section>
    </main>
</body>
</html>


