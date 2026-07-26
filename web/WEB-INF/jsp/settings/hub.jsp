<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Settings</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/settings/settings-hub.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<main class="container settings-page">
    <div class="settings-page-head">
        <div class="settings-page-icon" aria-hidden="true"></div>
        <div>
            <h2>Settings</h2>
            <p>System-wide configuration. Admin only.</p>
        </div>
    </div>

    <div class="settings-hub-grid">
        <a class="settings-hub-card section-card" href="${ctx}/main/settings/proposals">
            <div class="settings-hub-card-title">Proposal Settings</div>
            <div class="settings-hub-card-desc">Resubmission limits and Editorial Board voting quorum.</div>
        </a>

        <a class="settings-hub-card section-card settings-hub-card-with-icon" href="${ctx}/main/settings/salary">
            <span class="settings-hub-card-icon settings-hub-card-icon-salary" aria-hidden="true">$</span>
            <div>
            <div class="settings-hub-card-title">Salary &amp; KPI Settings</div>
            <div class="settings-hub-card-desc">Task rates, bonus threshold, bonus percentage, and late-task penalty.</div>
            </div>
        </a>

        <a class="settings-hub-card section-card" href="${ctx}/main/settings/deadlines">
            <div class="settings-hub-card-title">Deadline Settings</div>
            <div class="settings-hub-card-desc">Buffer between task/chapter deadlines and chapter/series deadlines.</div>
        </a>

        <a class="settings-hub-card section-card" href="${ctx}/main/settings/progress">
            <div class="settings-hub-card-title">Progress Display Settings</div>
            <div class="settings-hub-card-desc">Red/amber threshold for chapter and series progress bars.</div>
        </a>
    </div>
</main>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
