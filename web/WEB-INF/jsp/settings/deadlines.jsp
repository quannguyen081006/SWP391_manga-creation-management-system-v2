<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Deadline Settings</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<main class="container settings-page">
    <div class="settings-page-head">
        <div class="settings-page-icon" aria-hidden="true"></div>
        <div>
            <h2>Deadline Settings</h2>
            <p>Control the minimum buffer between a task's due date and its chapter deadline, and between a chapter deadline and its series deadline.</p>
        </div>
    </div>

    <c:if test="${not empty success}">
        <div class="alert success">${success}</div>
    </c:if>
    <c:if test="${not empty error}">
        <div class="alert error">${error}</div>
    </c:if>

    <div class="section-card settings-panel">
        <form class="settings-form" method="post" action="${pageContext.request.contextPath}/main/settings/deadlines">
            <div class="settings-grid">
                <label class="setting-control-card" for="taskChapterBufferDays">
                    <span class="setting-control-copy">
                        <span class="setting-control-title">Task → Chapter Buffer</span>
                        <span class="setting-control-desc">Minimum days a task's due date must be before its chapter's submission deadline (BR-34). Also caps how far a rejected task's due date can be extended.</span>
                    </span>
                    <span class="setting-number-wrap">
                        <input id="taskChapterBufferDays" type="number" name="taskChapterBufferDays" min="0" max="60" value="${settings.taskChapterBufferDays}" required />
                        <span class="setting-number-unit">days</span>
                    </span>
                </label>

                <label class="setting-control-card" for="chapterSeriesBufferDays">
                    <span class="setting-control-copy">
                        <span class="setting-control-title">Chapter → Series Buffer</span>
                        <span class="setting-control-desc">Minimum days a chapter's submission deadline must be before its series' deadline.</span>
                    </span>
                    <span class="setting-number-wrap">
                        <input id="chapterSeriesBufferDays" type="number" name="chapterSeriesBufferDays" min="0" max="60" value="${settings.chapterSeriesBufferDays}" required />
                        <span class="setting-number-unit">days</span>
                    </span>
                </label>
            </div>

            <div class="settings-actions">
                <a class="btn" href="${pageContext.request.contextPath}/main/settings">← Back to Settings</a>
                <button class="btn primary" type="submit">Save Settings</button>
            </div>
        </form>
    </div>
</main>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
