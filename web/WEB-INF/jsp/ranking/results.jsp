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
                            <button class="btn-view-team" onclick="viewTeam(${r.seriesId}, '${r.seriesTitle}')">View Team</button>
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

// First letter of the member's name, shown inside the round avatar badge.
function initialOf(fullName) {
    var trimmed = (fullName || '?').trim();
    return trimmed.length > 0 ? trimmed.charAt(0).toUpperCase() : '?';
}

// Builds one "role-block" section: colored left border, a heading (with an
// optional count badge), and one avatar + name row per person. modifierClass
// picks the color from ranking.css (.team-role-block.lead / .assistants /
// .board; tantou editor uses the block's default indigo, so it gets none).
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
