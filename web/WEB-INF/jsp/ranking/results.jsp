<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>Ranking Results - Administrator Review</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ranking.css" />
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
        <style>
            .admin-review-header {
                background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
                color: white;
                padding: 30px;
                border-radius: 12px;
                margin-bottom: 25px;
                box-shadow: 0 4px 15px rgba(44, 62, 80, 0.3);
            }

            .admin-review-header h2 {
                margin: 0 0 10px 0;
                font-size: 28px;
                font-weight: 700;
            }

            .admin-review-header p {
                margin: 0;
                opacity: 0.9;
                font-size: 14px;
            }

            .ranking-card {
                display: flex;
                align-items: center;
                padding: 20px;
                background: white;
                border-radius: 12px;
                margin-bottom: 15px;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
                transition: transform 0.2s, box-shadow 0.2s;
                border-left: 4px solid #2c3e50;
            }

            .ranking-card:hover {
                transform: translateY(-2px);
                box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
            }

            .ranking-card.rank-1 {
                border-left-color: #f1c40f;
                background: linear-gradient(to right, #fff9e6, white);
            }

            .ranking-card.rank-2 {
                border-left-color: #bdc3c7;
                background: linear-gradient(to right, #f8f9fa, white);
            }

            .ranking-card.rank-3 {
                border-left-color: #cd7f32;
                background: linear-gradient(to right, #fff4e6, white);
            }

            /* Medal icon + placing sit side by side, same as the Top Creators board.
               Wide enough that a 40px rank-1 trophy and its number stay on one line;
               the gold/silver/bronze colours come from .rank-icon in ranking.css. */
            .rank-number {
                font-size: 36px;
                font-weight: 700;
                color: #2c3e50;
                width: 110px;
                text-align: center;
                margin-right: 20px;
            }

            .series-info {
                flex: 1;
            }

            .series-info h3 {
                margin: 0 0 8px 0;
                font-size: 18px;
                color: #2c3e50;
                font-weight: 600;
            }

            .series-meta {
                display: flex;
                gap: 12px;
                align-items: center;
                flex-wrap: wrap;
            }

            .series-meta span {
                font-size: 13px;
                color: #7f8c8d;
            }

            .bottom-twenty-badge {
                background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%);
                color: white;
                padding: 4px 12px;
                border-radius: 12px;
                font-size: 11px;
                font-weight: 600;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }

            .series-stats {
                display: flex;
                gap: 25px;
                margin-right: 20px;
            }

            .stat-item {
                text-align: center;
            }

            .stat-value {
                font-size: 18px;
                font-weight: 700;
                color: #2c3e50;
            }

            .stat-label {
                font-size: 11px;
                color: #95a5a6;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }

            .stat-value-sm {
                font-size: 12px;
            }

            .ranking-actions {
                display: flex;
                flex-direction: column;
                gap: 8px;
                min-width: 180px;
            }

            .btn-create-decision {
                background: linear-gradient(135deg, #3498db 0%, #2980b9 100%);
                color: white;
                border: none;
                padding: 10px 16px;
                border-radius: 8px;
                font-size: 13px;
                font-weight: 600;
                cursor: pointer;
                transition: transform 0.2s, box-shadow 0.2s;
                text-align: center;
            }

            .btn-create-decision:hover {
                transform: translateY(-2px);
                box-shadow: 0 4px 12px rgba(52, 152, 219, 0.4);
            }

            .btn-create-decision:disabled {
                background: #95a5a6;
                cursor: not-allowed;
                transform: none;
                box-shadow: none;
            }

            .btn-view-team {
                background: #f8f9fa;
                color: #2c3e50;
                border: 1px solid #e9ecef;
                padding: 8px 16px;
                border-radius: 8px;
                font-size: 12px;
                font-weight: 600;
                cursor: pointer;
                transition: background 0.2s;
                text-align: center;
            }

            .btn-view-team:hover {
                background: #e9ecef;
            }

            .session-status-badge {
                padding: 4px 10px;
                border-radius: 12px;
                font-size: 11px;
                font-weight: 600;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }

            .session-status-badge.OPEN {
                background: #e74c3c;
                color: white;
            }

            .session-status-badge.CLOSED {
                background: #27ae60;
                color: white;
            }

            .session-status-badge.CONTINUE {
                background: #27ae60;
                color: white;
            }

            .session-status-badge.CANCEL {
                background: #e74c3c;
                color: white;
            }

            .session-status-badge.CHANGE_TYPE {
                background: #3498db;
                color: white;
            }

            .recommendation-badge {
                background: linear-gradient(135deg, #f39c12 0%, #e67e22 100%);
                color: white;
                padding: 6px 14px;
                border-radius: 12px;
                font-size: 12px;
                font-weight: 600;
                display: inline-flex;
                align-items: center;
                gap: 6px;
            }

            .recommendation-badge i {
                font-size: 14px;
            }
        </style>
    </head>
    <body>
        <jsp:include page="../common/header.jsp" />

        <c:if test="${not empty error}">
            <div class="alert error">${error}</div>
        </c:if>

        <div class="admin-review-header">
            <h2>📊 Ranking Results - Administrator Review</h2>
            <p>Review ranking performance and create Decision Sessions for series requiring editorial review</p>
        </div>

        <div class="section-card">
            <h3 class="section-title section-title-sm">Series Performance Leaderboard</h3>

            <c:if test="${not empty results}">
                <div class="leaderboard">
                    <c:forEach items="${results}" var="r" varStatus="status">
                        <div class="ranking-card rank-${r.rankPosition}">
                            <div class="rank-number">
                                <c:if test="${r.rankPosition == 1}"><i class="bi bi-trophy-fill rank-icon gold"></i></c:if>
                                <c:if test="${r.rankPosition == 2}"><i class="bi bi-award-fill rank-icon silver"></i></c:if>
                                <c:if test="${r.rankPosition == 3}"><i class="bi bi-award-fill rank-icon bronze"></i></c:if>
                                ${r.rankPosition}
                            </div>
                            <div class="series-info">
                                <h3>${r.seriesTitle}</h3>
                                <div class="series-meta">
                                    <span>ID: #${r.seriesId}</span>
                                    <c:if test="${r.isBottomTwenty}">
                                        <span class="recommendation-badge">
                                            <i class="bi bi-exclamation-triangle-fill"></i>
                                            Suggested - Bottom 20%
                                        </span>
                                    </c:if>
                                    <c:if test="${not empty r.decisionSession}">
                                        <span class="session-status-badge ${r.decisionSession.result != null ? r.decisionSession.result : r.decisionSession.status}">
                                            ${r.decisionSession.result != null ? r.decisionSession.result : r.decisionSession.status}
                                        </span>
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
                                    <span class="stat-label">Readers</span>
                                </div>
                                <div class="stat-item">
                                    <span class="stat-value">${r.totalRevenue}</span>
                                    <span class="stat-label">Revenue</span>
                                </div>
                            </div>
                            <c:if test="${sessionScope.AUTH_USER.hasRole('ADMIN')}">
                                <div class="ranking-actions">
                                    <c:choose>
                                        <c:when test="${empty r.decisionSession}">
                                            <form method="post" action="${pageContext.request.contextPath}/main/ranking/records/${r.id}/create-decision" style="margin: 0;">
                                                <input type="hidden" name="seriesId" value="${r.seriesId}" />
                                                <button type="submit" class="btn-create-decision">
                                                    <i class="bi bi-plus-circle"></i> Create Decision Session
                                                </button>
                                            </form>
                                        </c:when>
                                        <c:otherwise>
                                            <button class="btn-create-decision" disabled>
                                                <i class="bi bi-check-circle"></i> Decision Session Created
                                            </button>
                                            <a class="btn-view-team" href="${pageContext.request.contextPath}/main/decisions/${r.decisionSession.id}" style="text-decoration: none; display: block; text-align: center;">
                                                <i class="bi bi-eye"></i> View Session
                                            </a>
                                        </c:otherwise>
                                    </c:choose>
                                    <button class="btn-view-team" onclick="viewTeam(${r.seriesId}, & quot;${r.seriesTitle} & quot; )">
                                        <i class="bi bi-people"></i> View Team
                                    </button>
                                </div>
                            </c:if>

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

        <!-- Team Modal -->
        <div id="team-modal" class="team-modal">
            <div class="team-modal-panel">
                <button class="team-modal-close" onclick="closeTeamModal()">✕ Close</button>
                <h3 id="team-modal-title" class="team-modal-title">Series Team</h3>
                <p class="team-modal-subtitle">Everyone assigned to this series, grouped by role.</p>
                <div id="team-modal-content"></div>
            </div>
        </div>

        <script>
            function viewTeam(seriesId, seriesTitle) {
                document.getElementById('team-modal-title').textContent = 'Team: ' + seriesTitle;
                document.getElementById('team-modal-content').innerHTML = '<p class="text-muted">Loading team information...</p>';
                document.getElementById('team-modal').style.display = 'flex';

                fetch('${pageContext.request.contextPath}/api/v1/ranking/series/' + seriesId + '/team')
                        .then(response => response.json())
                        .then(data => {
                            if (data.success && data.data) {
                                renderTeam(data.data);
                            } else {
                                document.getElementById('team-modal-content').innerHTML = '<p class="text-danger">Failed to load team information.</p>';
                            }
                        })
                        .catch(error => {
                            document.getElementById('team-modal-content').innerHTML = '<p class="text-danger">Error loading team: ' + error.message + '</p>';
                        });
            }

            function initialOf(fullName) {
                var trimmed = (fullName || '?').trim();
                return trimmed.length > 0 ? trimmed.charAt(0).toUpperCase() : '?';
            }

            function renderTeamRoleBlock(icon, roleName, members, modifierClass) {
                var blockClass = 'team-role-block' + (modifierClass ? ' ' + modifierClass : '');
                var html = '<div class="' + blockClass + '">';
                html += '<h4 class="team-role-heading">' + icon + ' ' + roleName;
                if (members.length > 1) {
                    html += '<span class="team-role-count">' + members.length + '</span>';
                }
                html += '</h4>';
                members.forEach(function (member) {
                    html += '<div class="team-member-row">';
                    html += '<span class="team-avatar">' + initialOf(member.fullName) + '</span>';
                    html += '<span>';
                    html += '<span class="team-member-name">' + escapeHtml(member.fullName) + '</span>';
                    html += '<span class="team-member-meta">ID: #' + member.id + ' &middot; ' + escapeHtml(member.username) + '</span>';
                    html += '</span>';
                    html += '</div>';
                });
                html += '</div>';
                return html;
            }

            function renderTeam(team) {
                let html = '';

                if (team.leadMangaka) {
                    html += renderTeamRoleBlock('⭐', 'Lead Mangaka', [team.leadMangaka], 'lead');
                }
                if (team.tantouEditor) {
                    html += renderTeamRoleBlock('📝', 'Tantou Editor', [team.tantouEditor], '');
                }
                if (team.assistants && team.assistants.length > 0) {
                    html += renderTeamRoleBlock('🎨', 'Assistants', team.assistants, 'assistants');
                }
                if (team.editorialBoard && team.editorialBoard.length > 0) {
                    html += renderTeamRoleBlock('⚖️', 'Editorial Board', team.editorialBoard, 'board');
                }

                document.getElementById('team-modal-content').innerHTML = html;
            }

            function closeTeamModal() {
                document.getElementById('team-modal').style.display = 'none';
            }

            function escapeHtml(text) {
                if (!text)
                    return '';
                return text
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;')
                        .replace(/'/g, '&#039;');
            }

            document.getElementById('team-modal').addEventListener('click', function (e) {
                if (e.target === this) {
                    closeTeamModal();
                }
            });
        </script>

    </body>
</html>
