<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Proposal Settings</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<main class="container settings-page">
    <div class="page-header">
        <div class="page-header-icon settings-page-icon" aria-hidden="true"></div>
        <div>
            <h2>Proposal Settings</h2>
            <p>Control proposal resubmission limits and Editorial Board voting quorum.</p>
        </div>
    </div>

    <c:if test="${not empty success}">
        <div class="alert success">${success}</div>
    </c:if>
    <c:if test="${not empty error}">
        <div class="alert error">${error}</div>
    </c:if>

    <div class="section-card settings-panel">
        <form class="settings-form" method="post" action="${pageContext.request.contextPath}/main/settings/proposals">
            <div class="settings-grid">
                <label class="setting-control-card" for="maxSubmitAttempts">
                    <span class="setting-control-icon setting-control-icon-resubmit" aria-hidden="true"></span>
                    <span class="setting-control-copy">
                        <span class="setting-control-title">Resubmit Count</span>
                        <span class="setting-control-desc">Maximum proposal submission attempts per Mangaka.</span>
                    </span>
                    <span class="setting-number-wrap">
                        <input id="maxSubmitAttempts" type="number" name="maxSubmitAttempts" min="1" max="10" value="${settings.maxSubmitAttempts}" required />
                        <span class="setting-number-unit">attempts</span>
                    </span>
                </label>

                <label class="setting-control-card" for="minimumVoteQuorum">
                    <span class="setting-control-icon setting-control-icon-quorum" aria-hidden="true"></span>
                    <span class="setting-control-copy">
                        <span class="setting-control-title">Minimum Vote Quorum</span>
                        <span class="setting-control-desc">Minimum valid Board votes required before a result can be resolved.</span>
                    </span>
                    <span class="setting-number-wrap">
                        <input id="minimumVoteQuorum" type="number" name="minimumVoteQuorum" min="1" max="20" value="${settings.minimumVoteQuorum}" required />
                        <span class="setting-number-unit">votes</span>
                    </span>
                </label>
            </div>

            <div class="settings-actions">
                <a class="btn" href="${pageContext.request.contextPath}/main/proposals">Back to Proposals</a>
                <a class="btn" href="${pageContext.request.contextPath}/main/settings">← Back to Settings</a>
                <button class="btn primary" type="submit">Save Settings</button>
            </div>
        </form>
    </div>
</main>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
