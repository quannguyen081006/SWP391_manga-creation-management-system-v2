<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Ranking Results</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ranking.css" />
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
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
                                <span class="bottom-twenty-badge">⚠️ Decision Review Candidate</span>
                            </c:if>
                            <button class="btn-view-team" onclick="viewTeam(${r.seriesId}, '${r.seriesTitle}')" style="margin-left: 10px; padding: 4px 12px; font-size: 12px; background: #667eea; color: white; border: none; border-radius: 4px; cursor: pointer;">View Team</button>
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

<!-- Team Modal -->
<div id="team-modal" style="display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0, 0, 0, 0.5); justify-content: center; align-items: center; z-index: 1000;">
    <div style="background: white; padding: 30px; border-radius: 12px; max-width: 600px; max-height: 80vh; overflow-y: auto; position: relative;">
        <button onclick="closeTeamModal()" style="position: absolute; top: 15px; right: 15px; background: #e74c3c; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: 600;">✕ Close</button>
        <h3 id="team-modal-title" style="margin: 0 0 20px 0; color: #2c3e50; font-size: 24px;">Series Team</h3>
        <div id="team-modal-content"></div>
    </div>
</div>

<script>
function viewTeam(seriesId, seriesTitle) {
    document.getElementById('team-modal-title').textContent = 'Team: ' + seriesTitle;
    document.getElementById('team-modal-content').innerHTML = '<p style="color: #7f8c8d;">Loading team information...</p>';
    document.getElementById('team-modal').style.display = 'flex';
    
    fetch('${pageContext.request.contextPath}/api/v1/ranking/series/' + seriesId + '/team')
        .then(response => response.json())
        .then(data => {
            if (data.success && data.data) {
                renderTeam(data.data);
            } else {
                document.getElementById('team-modal-content').innerHTML = '<p style="color: #e74c3c;">Failed to load team information.</p>';
            }
        })
        .catch(error => {
            document.getElementById('team-modal-content').innerHTML = '<p style="color: #e74c3c;">Error loading team: ' + error.message + '</p>';
        });
}

function renderTeam(team) {
    let html = '';
    
    // Lead Mangaka
    if (team.leadMangaka) {
        html += '<div style="margin-bottom: 20px; padding: 15px; background: linear-gradient(to right, #fff9e6, #fff); border-left: 4px solid #ffd700; border-radius: 4px;">';
        html += '<h4 style="margin: 0 0 10px 0; color: #d4af00; font-size: 16px;">⭐ Lead Mangaka</h4>';
        html += '<div style="color: #2c3e50; font-weight: 600;">' + escapeHtml(team.leadMangaka.fullName) + '</div>';
        html += '<div style="color: #7f8c8d; font-size: 13px;">ID: #' + team.leadMangaka.id + ' | ' + escapeHtml(team.leadMangaka.username) + '</div>';
        html += '</div>';
    }
    
    // Tantou Editor
    if (team.tantouEditor) {
        html += '<div style="margin-bottom: 20px; padding: 15px; background: #f8f9fa; border-left: 4px solid #667eea; border-radius: 4px;">';
        html += '<h4 style="margin: 0 0 10px 0; color: #667eea; font-size: 16px;">📝 Tantou Editor</h4>';
        html += '<div style="color: #2c3e50; font-weight: 600;">' + escapeHtml(team.tantouEditor.fullName) + '</div>';
        html += '<div style="color: #7f8c8d; font-size: 13px;">ID: #' + team.tantouEditor.id + ' | ' + escapeHtml(team.tantouEditor.username) + '</div>';
        html += '</div>';
    }
    
    // Assistants
    if (team.assistants && team.assistants.length > 0) {
        html += '<div style="margin-bottom: 20px; padding: 15px; background: #f8f9fa; border-left: 4px solid #27ae60; border-radius: 4px;">';
        html += '<h4 style="margin: 0 0 10px 0; color: #27ae60; font-size: 16px;">🎨 Assistants (' + team.assistants.length + ')</h4>';
        team.assistants.forEach(function(assistant) {
            html += '<div style="padding: 8px 0; border-bottom: 1px solid #e9ecef; color: #2c3e50;">';
            html += '<span style="font-weight: 600;">' + escapeHtml(assistant.fullName) + '</span>';
            html += '<span style="color: #7f8c8d; font-size: 13px; margin-left: 10px;">ID: #' + assistant.id + ' | ' + escapeHtml(assistant.username) + '</span>';
            html += '</div>';
        });
        html += '</div>';
    }
    
    // Editorial Board
    if (team.editorialBoard && team.editorialBoard.length > 0) {
        html += '<div style="padding: 15px; background: #f8f9fa; border-left: 4px solid #9b59b6; border-radius: 4px;">';
        html += '<h4 style="margin: 0 0 10px 0; color: #9b59b6; font-size: 16px;">⚖️ Editorial Board (' + team.editorialBoard.length + ')</h4>';
        team.editorialBoard.forEach(function(member) {
            html += '<div style="padding: 8px 0; border-bottom: 1px solid #e9ecef; color: #2c3e50;">';
            html += '<span style="font-weight: 600;">' + escapeHtml(member.fullName) + '</span>';
            html += '<span style="color: #7f8c8d; font-size: 13px; margin-left: 10px;">ID: #' + member.id + ' | ' + escapeHtml(member.username) + '</span>';
            html += '</div>';
        });
        html += '</div>';
    }
    
    document.getElementById('team-modal-content').innerHTML = html;
}

function closeTeamModal() {
    document.getElementById('team-modal').style.display = 'none';
}

function escapeHtml(text) {
    if (!text) return '';
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// Close modal on outside click
document.getElementById('team-modal').addEventListener('click', function(e) {
    if (e.target === this) {
        closeTeamModal();
    }
});
</script>

</body>
</html>
