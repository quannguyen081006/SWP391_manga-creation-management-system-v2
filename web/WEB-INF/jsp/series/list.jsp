<%--
  PURPOSE: Series overview cards for every role that can reach /main/series.
  TYPOGRAPHY: follows the Settings pages (28px page head, 14px sub, 800-weight card
  titles, #64748b supporting text, #e2e8f0 hairlines) so the admin screens and this
  screen read as one system.
  LAYOUT PER CARD (top to bottom):
    [1] CHIPS     — status chip (ACTIVE / CANCELLED) + genre chip
    [2] TITLE     — series title
    [3] STATS     — one flat line: "N chapters · M in progress"
    [4] PROGRESS  — label + percentage on one row, then the bar
    [5] DEADLINE  — editable form for the owning Tantou Editor, read-only row for everyone else
    [6] FOOT      — outline link to the chapter list
  Progress level (is-low / is-mid / is-done) follows the admin-configurable thresholds
  in Settings > Progress Display and is shown by the bar colour only.
  series.js hooks that must stay: #seriesMessage, .series-deadline-form[data-series-id],
  input[name=publicationDate], and the [data-progress-width] span filled in by common.js.
--%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Series</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=20260727series3" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/series.css?v=20260727series3" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<main class="series-page">

    <div id="seriesMessage" class="alert series-message is-hidden-initial"></div>

    <%-- Head uses the shared .page-header block, title, one line of context. --%>
    <div class="page-header">
        <div class="page-header-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 6.7S9.6 4.8 4.2 4.8v12.6c5.4 0 7.8 1.9 7.8 1.9s2.4-1.9 7.8-1.9V4.8c-5.4 0-7.8 1.9-7.8 1.9Z"/>
                <path d="M12 6.7v12.6"/>
            </svg>
        </div>
        <div>
            <h2>Series</h2>
            <p>${fn:length(seriesList)} series you can access.</p>
        </div>
    </div>

    <div class="list-cards">
        <c:forEach items="${seriesList}" var="s">
            <%-- One state name drives the progress bar colour. --%>
            <c:set var="progressState" value="${s.progressPct >= progressHighThreshold ? 'done' : (s.progressPct >= progressLowThreshold ? 'mid' : 'low')}" />
            <c:set var="isCancelled" value="${s.status == 'CANCELLED'}" />
            <article class="tile series-tile ${isCancelled ? 'series-tile-cancelled' : ''}">
                <div class="series-tile-top">
                    <div class="series-chips">
                        <span class="status-chip series-status ${isCancelled ? 'status-rejected' : 'status-approved'}">${s.status}</span>
                        <span class="series-genre-chip">${s.genre}</span>
                    </div>
                    <h3 class="series-name">${s.title}</h3>
                </div>

                <div class="series-stats">${s.chapterCount} chapters &middot; ${s.inProgressChapters} in progress</div>

                <div class="series-progress-block">
                    <div class="series-progress-row">
                        <span class="series-progress-key">Current chapter progress</span>
                        <span class="series-progress-value series-progress-value-${progressState}"><fmt:formatNumber value="${s.progressPct}" maxFractionDigits="0" />%</span>
                    </div>
                    <div class="progress is-${progressState}"><span data-progress-width="${s.progressPct}"></span></div>
                </div>

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
                        <strong class="${empty s.publicationDate ? 'is-unset' : ''}">${empty s.publicationDate ? 'Not set' : s.publicationDate}</strong>
                    </div>
                </c:if>

                <div class="series-tile-foot">
                    <a class="btn primary series-view-btn" href="${pageContext.request.contextPath}/main/chapters?seriesId=${s.id}">
                        View chapters
                        <svg class="series-view-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
                    </a>
                </div>
            </article>
        </c:forEach>
        <c:if test="${empty seriesList}">
            <div class="series-empty">
                <p class="series-empty-title">No series found</p>
                <p class="series-empty-note">Series show up here once a proposal is approved and production starts.</p>
            </div>
        </c:if>
    </div>

</main>

<script src="${pageContext.request.contextPath}/assets/js/series.js"
        data-context-path="${pageContext.request.contextPath}"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
