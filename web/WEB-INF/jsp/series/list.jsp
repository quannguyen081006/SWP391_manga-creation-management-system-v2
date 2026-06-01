<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Series</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<h2 class="page-title">Series</h2>
<p class="page-sub">Manage your manga series and chapters</p>
<div id="seriesMessage" class="alert" style="display:none;"></div>

<div class="list-cards">
    <c:forEach items="${seriesList}" var="s">
        <article class="tile">
            <div class="section-head" style="margin-bottom:8px">
                <h3>${s.title}</h3>
                <div class="score ${s.progressPct >= 70 ? 'metric-ok' : (s.progressPct >= 45 ? 'metric-amber' : 'metric-danger')}"><fmt:formatNumber value="${s.progressPct}" maxFractionDigits="0" />%</div>
            </div>
            <div class="genre">${s.genre}</div>
            <div class="inline-meta">
                <span>${s.chapterCount} chapters</span>
                <span>${s.inProgressChapters} in progress</span>
            </div>

            <div class="metric-label series-progress-label">Current chapter progress</div>
            <div class="progress ${s.progressPct < 40 ? 'red' : ''}"><span style="width:${s.progressPct}%"></span></div>

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

<script>
    (function () {
        var ctx = '${pageContext.request.contextPath}';
        var message = document.getElementById('seriesMessage');

        function showMessage(text, isError) {
            if (!message) { return; }
            message.textContent = text;
            message.style.display = 'block';
            message.className = 'alert ' + (isError ? 'error' : 'success');
        }

        function todayIso() {
            var date = new Date();
            var month = String(date.getMonth() + 1);
            var day = String(date.getDate());
            return date.getFullYear() + '-' + (month.length < 2 ? '0' + month : month) + '-' + (day.length < 2 ? '0' + day : day);
        }

        var deadlineInputs = document.querySelectorAll('.series-deadline-form input[type="date"]');
        for (var i = 0; i < deadlineInputs.length; i++) {
            deadlineInputs[i].min = todayIso();
        }

        document.addEventListener('submit', async function (e) {
            if (!e.target.classList || !e.target.classList.contains('series-deadline-form')) {
                return;
            }
            e.preventDefault();

            var form = e.target;
            var seriesId = form.getAttribute('data-series-id');
            var publicationDate = form.querySelector('[name="publicationDate"]').value;
            try {
                var res = await fetch(ctx + '/api/v1/series/' + seriesId + '/deadline?publicationDate=' + encodeURIComponent(publicationDate), {
                    method: 'PUT',
                    headers: {
                        'Accept': 'application/json'
                    }
                });
                var body = await res.json();
                if (!res.ok || body.success === false) {
                    throw new Error(body.message || 'Cannot update deadline');
                }
                showMessage('Series deadline updated.', false);
                window.location.reload();
            } catch (err) {
                showMessage(err.message, true);
            }
        });
    })();
</script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>


