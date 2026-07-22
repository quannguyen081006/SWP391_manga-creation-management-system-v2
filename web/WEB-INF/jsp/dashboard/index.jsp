<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Dashboard</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/dashboard.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<section class="manga-dashboard-strip" aria-hidden="true">
    <div class="strip-panel strip-panel-focus photo-panel">
        <img class="panel-photo" src="${pageContext.request.contextPath}/assets/images/one-piece-dai-dien-696x391.jpg" alt="One Piece" loading="lazy" />
    </div>
    <div class="strip-panel strip-panel-page photo-panel">
        <img class="panel-photo" src="${pageContext.request.contextPath}/assets/images/Naruto_Volume_1_manga_cover.jpg" alt="Naruto Volume 1" loading="lazy" />
    </div>
    <div class="strip-panel strip-panel-tone photo-panel">
        <img class="panel-photo" src="${pageContext.request.contextPath}/assets/images/dragonball.jpg" alt="Dragon Ball" loading="lazy" />
    </div>
</section>

<section class="metric-grid">
    <article class="metric-card">
        <div class="metric-label">Proposals</div>
        <div class="metric-value">${proposalCount}</div>
        <div class="metric-note">${activeProposalCount} active proposal</div>
    </article>
    <article class="metric-card">
        <div class="metric-label">Active Series</div>
        <div class="metric-value">${seriesCount}</div>
        <div class="metric-note">${approvedCount} approved total</div>
    </article>
    <article class="metric-card">
        <div class="metric-label">Overdue Tasks</div>
        <div class="metric-value metric-danger">${overdueTasks}</div>
        <div class="metric-note">Needs immediate attention</div>
    </article>
    <article class="metric-card">
        <div class="metric-label">Pending Manuscripts</div>
        <div class="metric-value metric-violet">${pendingManuscripts}</div>
        <div class="metric-note">Awaiting editorial review</div>
    </article>
</section>

<div class="section-card">
    <div class="section-head">
        <div>
            <h3 class="section-title">Active Proposal</h3>
            <p class="section-desc">Current status of your proposal submission</p>
        </div>
        <c:if test="${not empty activeProposal}">
            <span class="status-chip status-review">${activeProposal.status}</span>
        </c:if>
    </div>

    <c:choose>
        <c:when test="${not empty activeProposal}">
            <h3>${activeProposal.title}</h3>
            <p class="page-sub dashboard-proposal-genre">${activeProposal.genre}</p>
            <div class="inline-meta">
                <span>Status: ${activeProposal.status}</span>
                <span>Submit attempt ${activeProposal.submitAttemptCount}/${maxSubmitAttempts}</span>
            </div>
            <div class="dashboard-card-action"><a class="btn" href="${pageContext.request.contextPath}/main/proposals/${activeProposal.id}">View Details</a></div>
        </c:when>
        <c:otherwise>
            <p class="page-sub">No active proposal right now.</p>
        </c:otherwise>
    </c:choose>
</div>

<div class="section-card">
    <div class="section-head">
        <div>
            <h3 class="section-title">Chapters in Progress</h3>
            <p class="section-desc">Track your ongoing chapter work</p>
        </div>
    </div>
    <table class="data-table">
        <thead>
            <tr>
                <th>Chapter</th>
                <th>Status</th>
                <th>Completion</th>
                <th>Due</th>
            </tr>
        </thead>
        <tbody>
            <c:forEach items="${inProgressChapters}" var="c">
                <tr>
                    <td>${c.title} - Ch. ${c.chapterNumber}</td>
                    <td>${c.status}</td>
                    <td>${c.completionPct}%</td>
                    <td>${c.submissionDeadline}</td>
                </tr>
            </c:forEach>
            <c:if test="${empty inProgressChapters}">
                <tr><td colspan="4">No in-progress chapters.</td></tr>
            </c:if>
        </tbody>
    </table>
</div>

<jsp:include page="../common/footer.jsp" />
</body>
</html>



