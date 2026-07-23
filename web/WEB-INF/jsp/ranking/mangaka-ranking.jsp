<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Mangaka Ranking</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ranking.css" />
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
</head>
<body>
<jsp:include page="../common/header.jsp" />

<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

<div class="section-card">
    <h3 class="section-title section-title-sm">🌟 Top Creators Leaderboard</h3>
    
    <c:if test="${not empty mangakaRanking}">
        <div class="mangaka-leaderboard">
            <c:forEach items="${mangakaRanking}" var="m">
                <div class="mangaka-card rank-${m.rankPosition}">
                    <div class="mangaka-rank">
                        <c:if test="${m.rankPosition == 1}"><i class="bi bi-trophy-fill rank-icon gold"></i></c:if>
                        <c:if test="${m.rankPosition == 2}"><i class="bi bi-award-fill rank-icon silver"></i></c:if>
                        <c:if test="${m.rankPosition == 3}"><i class="bi bi-award-fill rank-icon bronze"></i></c:if>
                        ${m.rankPosition}
                    </div>
                    <div class="mangaka-info">
                        <h3>${m.mangakaName}</h3>
                        <div class="mangaka-meta">
                            <span>ID: #${m.mangakaId}</span>
                            <c:if test="${m.rankPosition <= 3}">
                                <span class="elite-badge">⭐ Elite Creator</span>
                            </c:if>
                        </div>
                    </div>
                    <div class="mangaka-stats">
                        <div class="mangaka-stat-item">
                            <span class="mangaka-stat-value">${m.totalReads}</span>
                            <span class="mangaka-stat-label">Total Reads</span>
                        </div>
                        <div class="mangaka-stat-item">
                            <span class="mangaka-stat-value">${m.totalLikes}</span>
                            <span class="mangaka-stat-label">Total Likes</span>
                        </div>
                        <div class="mangaka-stat-item">
                            <span class="mangaka-stat-value">${m.totalRevenue}</span>
                            <span class="mangaka-stat-label">Revenue</span>
                        </div>
                    </div>
                </div>
            </c:forEach>
        </div>
    </c:if>
    
    <c:if test="${empty mangakaRanking}">
        <div class="empty-state">
            <div class="icon"><i class="bi bi-trophy-fill" style="font-size: 48px; color: #ffd700;"></i></div>
            <div class="title">No mangaka ranking data yet</div>
            <div class="subtitle">Close a period to generate the prestige snapshot</div>
        </div>
    </c:if>
</div>

<div class="stack-actions content-section-large">
    <a class="btn" href="${pageContext.request.contextPath}/main/ranking/periods/${period.id}/results">← Back to Series Ranking</a>
    <a class="btn" href="${pageContext.request.contextPath}/main/ranking/periods">Back to Periods</a>
</div>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
