<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Ranking Results</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ranking.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<div class="section-card">
    <h3 class="section-title section-title-sm">📊 Series Ranking Leaderboard</h3>
    
    <c:if test="${not empty results}">
        <div class="leaderboard">
            <c:forEach items="${results}" var="r" varStatus="status">
                <div class="ranking-card rank-${r.rankPosition} ${r.isBottomTwenty ? 'bottom-twenty' : ''}">
                    <div class="rank-number">
                        <c:if test="${r.rankPosition == 1}">🥇</c:if>
                        <c:if test="${r.rankPosition == 2}">🥈</c:if>
                        <c:if test="${r.rankPosition == 3}">🥉</c:if>
                        ${r.rankPosition}
                    </div>
                    <div class="series-info">
                        <h3>${r.seriesTitle}</h3>
                        <div class="series-meta">
                            <span>ID: #${r.seriesId}</span>
                            <c:if test="${r.isBottomTwenty}">
                                <span class="bottom-twenty-badge">⚠️ Decision Review Candidate</span>
                            </c:if>
                        </div>
                    </div>
                    <div class="series-stats">
                        <div class="stat-item">
                            <span class="stat-value">${r.rankScore}%</span>
                            <span class="stat-label">Engagement</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-value">${r.totalLikes}</span>
                            <span class="stat-label">Likes</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-value">${r.totalReads}</span>
                            <span class="stat-label">Reads</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-value stat-value-sm">${r.calculatedAt}</span>
                            <span class="stat-label">Calculated</span>
                        </div>
                    </div>
                </div>
            </c:forEach>
        </div>
    </c:if>
    
    <c:if test="${empty results}">
        <div class="empty-state">
            <div class="empty-state-icon">📊</div>
            <div class="empty-state-title">No ranking results yet</div>
            <div class="empty-state-copy">Close a period to generate the ranking snapshot</div>
        </div>
    </c:if>
</div>

<div class="section-card entries-section">
    <h3 class="section-title">📝 Submitted Board Entries</h3>
    <c:if test="${not empty entries}">
        <table class="entries-table">
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Series</th>
                    <th>Board Member</th>
                    <th>Vote Count</th>
                    <th>Reader Count</th>
                    <th>Submitted At</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach items="${entries}" var="e">
                    <tr>
                        <td>${e.id}</td>
                        <td>#${e.seriesId}</td>
                        <td>#${e.boardMemberId}</td>
                        <td>${e.voteCount}</td>
                        <td>${e.readerCount}</td>
                        <td>${e.submittedAt}</td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </c:if>
    <c:if test="${empty entries}">
        <div class="empty-state">
            No entries submitted yet.
        </div>
    </c:if>
</div>

<div class="content-section-large">
    <a class="btn" href="${pageContext.request.contextPath}/main/ranking/periods">← Back to Periods</a>
</div>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
