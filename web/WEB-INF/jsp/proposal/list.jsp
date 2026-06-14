<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Proposals</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=board-vote-ui-2" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/proposal.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<c:if test="${isMangaka}">
    <div class="page-actions">
        <a class="btn primary" href="${pageContext.request.contextPath}/main/proposals/create">+ New Proposal</a>
    </div>
</c:if>

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
                    <td><strong>${p.title}</strong></td>
                    <td>${p.genre}</td>
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
                                        <span>Min quorum 3</span>
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
