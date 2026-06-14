<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Series</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/series.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<div id="seriesMessage" class="alert series-message is-hidden-initial"></div>

<div class="list-cards">
    <c:forEach items="${seriesList}" var="s">
        <article class="tile">
            <div class="section-head series-card-head">
                <h3>${s.title}</h3>
                <div class="score ${s.progressPct >= 70 ? 'metric-ok' : (s.progressPct >= 45 ? 'metric-amber' : 'metric-danger')}"><fmt:formatNumber value="${s.progressPct}" maxFractionDigits="0" />%</div>
            </div>
            <div class="genre">${s.genre}</div>
            <div class="inline-meta">
                <span>${s.chapterCount} chapters</span>
                <span>${s.inProgressChapters} in progress</span>
            </div>

            <div class="metric-label series-progress-label">Current chapter progress</div>
            <div class="progress ${s.progressPct < 40 ? 'red' : ''}"><span data-progress-width="${s.progressPct}"></span></div>

            <c:if test="${sessionScope.AUTH_USER != null && sessionScope.AUTH_USER.hasRole('TANTOU_EDITOR') && sessionScope.AUTH_USER.id == s.tantouEditorId}">
                <form class="series-deadline-form" data-series-id="${s.id}">
                    <label for="deadline-${s.id}">Series deadline</label>
                    <input id="deadline-${s.id}" type="date" name="publicationDate" value="${s.publicationDate}" required />
                    <button class="btn small" type="submit">Update</button>
                </form>
            </c:if>
            <c:if test="${sessionScope.AUTH_USER == null || !sessionScope.AUTH_USER.hasRole('TANTOU_EDITOR') || sessionScope.AUTH_USER.id != s.tantouEditorId}">
                <div class="series-deadline-readonly">
                    <span>Series deadline</span>
                    <strong>${empty s.publicationDate ? 'Not set' : s.publicationDate}</strong>
                </div>
            </c:if>

            <div class="series-card-actions">
                <span class="status-chip ${s.status == 'CANCELLED' ? 'status-rejected' : 'status-approved'}">${s.status}</span>
                <a class="btn small" href="${pageContext.request.contextPath}/main/chapters?seriesId=${s.id}">View</a>
            </div>
        </article>
    </c:forEach>
    <c:if test="${empty seriesList}"><div>No series found.</div></c:if>
</div>

<script src="${pageContext.request.contextPath}/assets/js/series.js"
        data-context-path="${pageContext.request.contextPath}"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>


