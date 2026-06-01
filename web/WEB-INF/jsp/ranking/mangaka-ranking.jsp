<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Mangaka Ranking</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <style>
        .prestige-hero {
            background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
            color: white;
            padding: 40px;
            border-radius: 12px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(245, 87, 108, 0.3);
        }

        .prestige-hero h1 {
            margin: 0 0 10px 0;
            font-size: 32px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 2px;
        }

        .prestige-hero .period-info {
            font-size: 18px;
            opacity: 0.9;
            margin-bottom: 20px;
        }

        .prestige-hero .status-badge {
            display: inline-block;
            padding: 8px 20px;
            background: rgba(255, 255, 255, 0.2);
            border-radius: 20px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 1px;
        }

        .mangaka-leaderboard {
            display: grid;
            gap: 16px;
        }

        .mangaka-card {
            background: white;
            border-radius: 12px;
            padding: 24px;
            display: grid;
            grid-template-columns: 80px 1fr auto;
            gap: 24px;
            align-items: center;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
            transition: transform 0.2s, box-shadow 0.2s;
            border-left: 4px solid transparent;
        }

        .mangaka-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
        }

        .mangaka-card.rank-1 {
            border-left-color: #ffd700;
            background: linear-gradient(to right, #fff9e6, white);
            box-shadow: 0 4px 16px rgba(255, 215, 0, 0.2);
        }

        .mangaka-card.rank-2 {
            border-left-color: #c0c0c0;
            background: linear-gradient(to right, #f5f5f5, white);
        }

        .mangaka-card.rank-3 {
            border-left-color: #cd7f32;
            background: linear-gradient(to right, #fff5e6, white);
        }

        .mangaka-rank {
            font-size: 36px;
            font-weight: 800;
            color: #f5576c;
            text-align: center;
        }

        .mangaka-card.rank-1 .mangaka-rank {
            color: #ffd700;
            font-size: 44px;
        }

        .mangaka-card.rank-2 .mangaka-rank {
            color: #c0c0c0;
            font-size: 40px;
        }

        .mangaka-card.rank-3 .mangaka-rank {
            color: #cd7f32;
            font-size: 38px;
        }

        .mangaka-info h3 {
            margin: 0 0 8px 0;
            font-size: 20px;
            font-weight: 600;
            color: #2c3e50;
        }

        .mangaka-meta {
            display: flex;
            gap: 16px;
            font-size: 14px;
            color: #7f8c8d;
        }

        .mangaka-stats {
            display: grid;
            grid-template-columns: repeat(3, auto);
            gap: 20px;
            text-align: right;
        }

        .mangaka-stat-item {
            display: flex;
            flex-direction: column;
            align-items: flex-end;
        }

        .mangaka-stat-value {
            font-size: 22px;
            font-weight: 700;
            color: #2c3e50;
        }

        .mangaka-stat-label {
            font-size: 12px;
            color: #95a5a6;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .elite-badge {
            display: inline-block;
            padding: 4px 12px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border-radius: 12px;
            font-size: 11px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .trophy-icon {
            font-size: 28px;
        }

        .empty-state {
            text-align: center;
            padding: 60px;
            color: #95a5a6;
        }

        .empty-state .icon {
            font-size: 48px;
            margin-bottom: 16px;
        }

        .empty-state .title {
            font-size: 18px;
            margin-bottom: 8px;
        }

        .empty-state .subtitle {
            font-size: 14px;
        }
    </style>
</head>
<body>
<jsp:include page="../common/header.jsp" />

<div class="prestige-hero">
    <h1>👑 Mangaka Prestige Ranking</h1>
    <div class="period-info">
        Period: ${period.name} • Status: <span class="status-badge">${period.status}</span>
    </div>
    <div style="font-size: 14px; opacity: 0.8;">
        Read-only snapshot • Calculated at: ${period.calculatedAt}
    </div>
</div>

<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

<div class="section-card">
    <h3 class="section-title" style="font-size: 24px; margin-bottom: 24px;">🌟 Top Creators Leaderboard</h3>
    
    <c:if test="${not empty mangakaRanking}">
        <div class="mangaka-leaderboard">
            <c:forEach items="${mangakaRanking}" var="m">
                <div class="mangaka-card rank-${m.rankPosition}">
                    <div class="mangaka-rank">
                        <c:if test="${m.rankPosition == 1}">👑</c:if>
                        <c:if test="${m.rankPosition == 2}">🥈</c:if>
                        <c:if test="${m.rankPosition == 3}">🥉</c:if>
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
            <div class="icon">👑</div>
            <div class="title">No mangaka ranking data yet</div>
            <div class="subtitle">Close a period to generate the prestige snapshot</div>
        </div>
    </c:if>
</div>

<div style="margin-top: 30px; display: flex; gap: 12px;">
    <a class="btn" href="${pageContext.request.contextPath}/main/ranking/periods/${period.id}/results">← Back to Series Ranking</a>
    <a class="btn" href="${pageContext.request.contextPath}/main/ranking/periods">Back to Periods</a>
</div>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
