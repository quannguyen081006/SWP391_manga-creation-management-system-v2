<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Progress Display Settings</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<main class="container settings-page">
    <div class="page-header">
        <div class="page-header-icon settings-page-icon" aria-hidden="true"></div>
        <div>
            <h2>Progress Display Settings</h2>
            <p>Control the percentages where chapter/series progress bars and badges switch from red to amber, and from amber to green.</p>
        </div>
    </div>

    <c:if test="${not empty success}">
        <div class="alert success">${success}</div>
    </c:if>
    <c:if test="${not empty error}">
        <div class="alert error">${error}</div>
    </c:if>

    <div class="section-card settings-panel">
        <form class="settings-form" method="post" action="${pageContext.request.contextPath}/main/settings/progress">
            <div class="settings-grid">
                <label class="setting-control-card" for="lowThresholdPercent">
                    <span class="setting-control-copy">
                        <span class="setting-control-title">Red / Amber Threshold</span>
                        <span class="setting-control-desc">Progress below this percentage shows red; at/above it shows amber (until the green threshold).</span>
                    </span>
                    <span class="setting-number-wrap">
                        <input id="lowThresholdPercent" type="number" name="lowThresholdPercent" min="1" max="99" value="${settings.lowThresholdPercent}" required />
                        <span class="setting-number-unit">%</span>
                    </span>
                </label>

                <label class="setting-control-card" for="highThresholdPercent">
                    <span class="setting-control-copy">
                        <span class="setting-control-title">Amber / Green Threshold</span>
                        <span class="setting-control-desc">Progress at/above this percentage shows green. Must be higher than the red/amber threshold.</span>
                    </span>
                    <span class="setting-number-wrap">
                        <input id="highThresholdPercent" type="number" name="highThresholdPercent" min="1" max="100" value="${settings.highThresholdPercent}" required />
                        <span class="setting-number-unit">%</span>
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
