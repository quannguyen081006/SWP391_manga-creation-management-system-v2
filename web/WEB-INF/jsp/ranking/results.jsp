<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Ranking Results</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <style>
        .ranking-hero {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px;
            border-radius: 12px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(102, 126, 234, 0.3);
        }

        .ranking-hero h1 {
            margin: 0 0 10px 0;
            font-size: 32px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 2px;
        }

        .ranking-hero .period-info {
            font-size: 18px;
            opacity: 0.9;
            margin-bottom: 20px;
        }

        .ranking-hero .status-badge {
            display: inline-block;
            padding: 8px 20px;
            background: rgba(255, 255, 255, 0.2);
            border-radius: 20px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 1px;
        }

        .leaderboard {
            display: grid;
            gap: 16px;
        }

        .ranking-card {
            background: white;
            border-radius: 12px;
            padding: 20px;
            display: grid;
            grid-template-columns: 60px 1fr auto;
            gap: 20px;
            align-items: center;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
            transition: transform 0.2s, box-shadow 0.2s;
            border-left: 4px solid transparent;
        }

        .ranking-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
        }

        .ranking-card.rank-1 {
            border-left-color: #ffd700;
            background: linear-gradient(to right, #fff9e6, white);
        }

        .ranking-card.rank-2 {
            border-left-color: #c0c0c0;
            background: linear-gradient(to right, #f5f5f5, white);
        }

        .ranking-card.rank-3 {
            border-left-color: #cd7f32;
            background: linear-gradient(to right, #fff5e6, white);
        }

        .ranking-card.bottom-twenty {
            border-left-color: #e74c3c;
            background: linear-gradient(to right, #ffe6e6, white);
        }

        .rank-number {
            font-size: 32px;
            font-weight: 800;
            color: #667eea;
            text-align: center;
        }

        .ranking-card.rank-1 .rank-number {
            color: #ffd700;
            font-size: 40px;
        }

        .ranking-card.rank-2 .rank-number {
            color: #c0c0c0;
            font-size: 36px;
        }

        .ranking-card.rank-3 .rank-number {
            color: #cd7f32;
            font-size: 34px;
        }

        .ranking-card.bottom-twenty .rank-number {
            color: #e74c3c;
        }

        .series-info h3 {
            margin: 0 0 8px 0;
            font-size: 18px;
            font-weight: 600;
            color: #2c3e50;
        }

        .series-meta {
            display: flex;
            gap: 20px;
            font-size: 14px;
            color: #7f8c8d;
        }

        .series-meta span {
            display: flex;
            align-items: center;
            gap: 4px;
        }

        .series-stats {
            display: grid;
            grid-template-columns: repeat(4, auto);
            gap: 16px;
            text-align: right;
        }

        .stat-item {
            display: flex;
            flex-direction: column;
            align-items: flex-end;
        }

        .stat-value {
            font-size: 20px;
            font-weight: 700;
            color: #2c3e50;
        }

        .stat-label {
            font-size: 12px;
            color: #95a5a6;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .bottom-twenty-badge {
            display: inline-block;
            padding: 4px 12px;
            background: #e74c3c;
            color: white;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .podium-icon {
            font-size: 24px;
        }

        .entries-section {
            margin-top: 40px;
        }

        .entries-table {
            width: 100%;
            border-collapse: collapse;
        }

        .entries-table th {
            background: #f8f9fa;
            padding: 12px;
            text-align: left;
            font-weight: 600;
            color: #2c3e50;
            border-bottom: 2px solid #e9ecef;
        }

        .entries-table td {
            padding: 12px;
            border-bottom: 1px solid #e9ecef;
        }

        .entries-table tr:hover {
            background: #f8f9fa;
        }
    </style>
</head>
<body>
<jsp:include page="../common/header.jsp" />

<div class="ranking-hero">
    <h1>🏆 Monthly Ranking Results</h1>
    <div class="period-info">
        Period: ${period.name} • Status: <span class="status-badge">${period.status}</span>
    </div>
    <div style="font-size: 14px; opacity: 0.8;">
        Calculated at: ${period.calculatedAt}
    </div>
</div>

<div class="section-card">
    <h3 class="section-title" style="font-size: 24px; margin-bottom: 24px;">📊 Series Ranking Leaderboard</h3>
    
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
                            <span class="stat-value" style="font-size: 14px;">${r.calculatedAt}</span>
                            <span class="stat-label">Calculated</span>
                        </div>
                    </div>
                </div>
            </c:forEach>
        </div>
    </c:if>
    
    <c:if test="${empty results}">
        <div style="text-align: center; padding: 60px; color: #95a5a6;">
            <div style="font-size: 48px; margin-bottom: 16px;">📊</div>
            <div style="font-size: 18px;">No ranking results yet</div>
            <div style="font-size: 14px;">Close a period to generate the ranking snapshot</div>
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
        <div style="text-align: center; padding: 40px; color: #95a5a6;">
            No entries submitted yet.
        </div>
    </c:if>
</div>

<div style="margin-top: 30px;">
    <a class="btn" href="${pageContext.request.contextPath}/main/ranking/periods">← Back to Periods</a>
</div>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
