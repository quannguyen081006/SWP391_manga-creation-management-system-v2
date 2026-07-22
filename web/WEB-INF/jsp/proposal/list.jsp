<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Proposals</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=board-vote-ui-2" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/proposal.css?v=proposal-settings-2" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<c:if test="${isMangaka}">
    <div class="page-actions">
        <a class="btn primary" href="${pageContext.request.contextPath}/main/proposals/create">+ New Proposal</a>
    </div>
</c:if>

<c:if test="${isAdmin}">
    <section id="proposal-settings" class="section-card settings-panel proposal-settings-panel">
        <div class="proposal-settings-head">
            <div class="settings-page-icon" aria-hidden="true"></div>
            <div>
                <h2>Proposal Settings</h2>
                <p>Submission and voting limits.</p>
            </div>
        </div>
        <form class="settings-form" method="post" action="${pageContext.request.contextPath}/main/settings/proposals">
            <input type="hidden" name="returnTo" value="proposals" />
            <div class="settings-grid">
                <label class="setting-control-card" for="maxSubmitAttempts">
                    <span class="setting-control-icon setting-control-icon-resubmit" aria-hidden="true"></span>
                    <span class="setting-control-copy">
                        <span class="setting-control-title">Submit Attempts</span>
                        <span class="setting-control-desc">Max times a proposal can be submitted.</span>
                    </span>
                    <span class="setting-number-wrap">
                        <input id="maxSubmitAttempts" type="number" name="maxSubmitAttempts" min="1" max="10" value="${maxSubmitAttempts}" required />
                        <span class="setting-number-unit">attempts</span>
                    </span>
                </label>

                <label class="setting-control-card" for="minimumVoteQuorum">
                    <span class="setting-control-icon setting-control-icon-quorum" aria-hidden="true"></span>
                    <span class="setting-control-copy">
                        <span class="setting-control-title">Vote Quorum</span>
                        <span class="setting-control-desc">Min Board votes to resolve.</span>
                    </span>
                    <span class="setting-number-wrap">
                        <input id="minimumVoteQuorum" type="number" name="minimumVoteQuorum" min="1" max="20" value="${minimumVoteQuorum}" required />
                        <span class="setting-number-unit">votes</span>
                    </span>
                </label>
            </div>

            <div class="settings-actions">
                <button class="btn primary" type="submit">Save</button>
            </div>
        </form>
    </section>
</c:if>

<c:if test="${not empty success}"><div class="alert success">${success}</div></c:if>
<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

<div class="section-card">
    <table class="data-table">
        <thead>
            <tr>
                <th>Title</th>
                <th>Genre</th>
                <th>Approx. Chapter</th>
                <th>Status</th>
                <th>Board Voting</th>
                <th>Action</th>
            </tr>
        </thead>
        <tbody>
            <c:forEach items="${proposals}" var="p">
                <tr>
                    <td><strong><c:out value="${p.title}" /></strong></td>
                    <td><c:out value="${p.genre}" /></td>
                    <td>${p.approximateChapter}</td>
                    <td>
                        <span class="status-chip ${p.status=='UNDER_REVIEW' || p.status=='BOARD_REVIEW' ? 'status-review' : (p.status=='DRAFT' || p.status=='REVISION_REQUESTED' ? 'status-draft' : (p.status=='APPROVED' ? 'status-approved' : 'status-rejected'))}">${p.status}</span>
                    </td>
                    <td>
                        <c:choose>
                            <c:when test="${not empty p.boardRoundId}">
                                <c:if test="${isTantou && p.assignedEditorId == sessionScope.AUTH_USER.id}">
                                    <div class="conflict-badge">Conflict: you manage this proposal - cannot vote</div>
                                </c:if>
                                <div class="proposal-vote-compact ${p.boardRoundStatus == 'OPEN' ? 'is-open' : 'is-closed'}">
                                    <div class="compact-vote-head">
                                        <strong>${p.boardTotalVotes}/${p.boardEligibleVoterCount} cast</strong>
                                        <span class="compact-round ${p.boardRoundStatus == 'OPEN' ? 'is-open' : ''}">${p.boardRoundStatus}</span>
                                    </div>
                                    <div class="compact-vote-track">
                                        <span data-progress-width="${p.boardEligibleVoterCount > 0 ? (p.boardTotalVotes * 100 / p.boardEligibleVoterCount) : 0}"></span>
                                    </div>
                                    <div class="compact-vote-meta">
                                        <span>Round #${p.boardRoundNumber}</span>
                                        <span>Min quorum ${minimumVoteQuorum}</span>
                                    </div>
                                    <c:if test="${p.boardRoundStatus == 'OPEN'}">
                                        <div class="compact-vote-deadline">
                                            Closes <fmt:formatDate value="${p.boardVotingClosesAt}" pattern="yyyy-MM-dd HH:mm" />
                                        </div>
                                    </c:if>
                                    <div class="compact-vote-counts">
                                        <span class="vote-breakdown-approve">A ${p.boardApproveVotes}</span>
                                        <span class="vote-breakdown-revise">R ${p.boardReviseVotes}</span>
                                        <span class="vote-breakdown-reject">X ${p.boardRejectVotes}</span>
                                    </div>
                                </div>
                            </c:when>
                            <c:when test="${p.boardTotalVotes > 0}">
                                <strong>${p.boardTotalVotes} cast</strong>
                                <span class="proposal-vote-counts">(${p.boardApproveVotes} approve, ${p.boardReviseVotes} revise, ${p.boardRejectVotes} reject)</span>
                            </c:when>
                            <c:otherwise>
                                <span class="proposal-vote-not-started">Not started</span>
                            </c:otherwise>
                        </c:choose>
                    </td>
                    <td><a class="btn small" href="${pageContext.request.contextPath}/main/proposals/${p.id}">View</a></td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
</div>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
