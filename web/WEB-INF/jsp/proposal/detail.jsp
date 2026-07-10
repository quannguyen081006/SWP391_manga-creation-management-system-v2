<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Proposal Detail</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=board-vote-ui-2" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/proposal.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<main class="container">
    <c:if test="${not empty error}">
        <div class="alert error">${error}</div>
    </c:if>

    <div class="panel">
        <p><strong>Genre:</strong> ${proposal.genre}</p>
        <p><strong>Status:</strong> <span class="chip">${proposal.status}</span></p>
        <p><strong>Approximate Chapter:</strong> ${proposal.approximateChapter}</p>
        <p><strong>Submit Attempts:</strong> ${proposal.submitAttemptCount}/${maxSubmitAttempts}</p>
        <p><strong>Assigned Tantou Editor:</strong> <c:out value="${proposal.assignedEditorId}" default="Not assigned" /></p>
        <div class="proposal-vote-summary ${proposal.boardRoundStatus == 'OPEN' ? 'is-open' : ''}">
            <div class="proposal-vote-summary-head">
                <div>
                    <strong>Editorial Board Voting</strong>
                    <c:if test="${not empty proposal.boardRoundId}">
                        <span class="vote-round-subtitle">Round #${proposal.boardRoundNumber}</span>
                    </c:if>
                </div>
                <c:if test="${not empty proposal.boardRoundStatus}">
                    <span class="status-chip ${proposal.boardRoundStatus == 'OPEN' ? 'status-review' : 'status-draft'}">${proposal.boardRoundStatus}</span>
                </c:if>
            </div>
            <c:choose>
                <c:when test="${not empty proposal.boardRoundId}">
                    <div class="proposal-vote-grid">
                        <div>
                            <span class="vote-label">Round Status</span>
                            <strong>${proposal.boardRoundStatus}</strong>
                            <small>
                                Opened
                                <fmt:formatDate value="${proposal.boardVotingOpenedAt}" pattern="yyyy-MM-dd HH:mm" />
                            </small>
                        </div>
                        <div>
                            <span class="vote-label">Votes Cast</span>
                            <strong>${proposal.boardTotalVotes}/${proposal.boardEligibleVoterCount}</strong>
                            <small>Minimum quorum: ${minimumVoteQuorum}</small>
                        </div>
                        <div>
                            <span class="vote-label">Window</span>
                            <strong>3 days</strong>
                            <small>
                                Closes
                                <fmt:formatDate value="${proposal.boardVotingClosesAt}" pattern="yyyy-MM-dd HH:mm" />
                            </small>
                        </div>
                    </div>

                    <div class="proposal-quorum-track" aria-label="Voting progress">
                        <span class="proposal-quorum-fill"
                              data-progress-width="${proposal.boardEligibleVoterCount > 0 ? (proposal.boardTotalVotes * 100 / proposal.boardEligibleVoterCount) : 0}"></span>
                    </div>
                    <div class="proposal-quorum-meta">
                        <span>${proposal.boardTotalVotes} vote(s) submitted</span>
                        <span>Decision waits for all voters or window close</span>
                    </div>

                    <div class="proposal-vote-breakdown board-breakdown">
                        <span class="vote-breakdown-approve">Approve <strong>${proposal.boardApproveVotes}</strong></span>
                        <span class="vote-breakdown-revise">Request Revision <strong>${proposal.boardReviseVotes}</strong></span>
                        <span class="vote-breakdown-reject">Reject <strong>${proposal.boardRejectVotes}</strong></span>
                    </div>
                    <p class="proposal-vote-note">
                        ${minimumVoteQuorum} votes is only the minimum quorum. The result is decided when the 3-day window closes or when every eligible board member in this round has voted.
                    </p>
                    <c:if test="${not empty boardVoters}">
                        <div class="board-voter-list">
                            <h4 class="board-voter-title">Board Members in This Round</h4>
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Member</th>
                                        <th>Status</th>
                                        <th>Decision</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach items="${boardVoters}" var="v">
                                        <tr>
                                            <td>
                                                <c:out value="${v.fullName}" />
                                                <c:if test="${v.isConflict}">
                                                    <span class="conflict-badge">conflict</span>
                                                </c:if>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${v.isConflict}">
                                                        <span class="status-chip status-draft">Excluded</span>
                                                    </c:when>
                                                    <c:when test="${v.hasVoted}">
                                                        <span class="status-chip status-approved">Voted</span>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="status-chip status-review">Pending</span>
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${v.isConflict}">-</c:when>
                                                    <c:when test="${v.voteDecision == 'APPROVED'}">
                                                        <span class="proposal-vote-label approve">Approve</span>
                                                    </c:when>
                                                    <c:when test="${v.voteDecision == 'REVISE_REQUESTED'}">
                                                        <span class="proposal-vote-label revise">Revise</span>
                                                    </c:when>
                                                    <c:when test="${v.voteDecision == 'REJECTED'}">
                                                        <span class="proposal-vote-label reject">Reject</span>
                                                        <c:if test="${not empty v.voteNote}">
                                                            <button type="button"
                                                                    class="btn small reject-reason-button"
                                                                    data-reject-reason="${fn:escapeXml(v.voteNote)}">Lý do</button>
                                                        </c:if>
                                                    </c:when>
                                                    <c:otherwise>-</c:otherwise>
                                                </c:choose>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </div>
                    </c:if>
                </c:when>
                <c:otherwise>
                    <span class="proposal-voting-pending">Board voting has not started.</span>
                </c:otherwise>
            </c:choose>
        </div>
        <p><strong>File Upload:</strong>
            <c:choose>
                <c:when test="${not empty proposal.originalFileName}">
                    <a class="btn small" href="${pageContext.request.contextPath}/main/proposals/${proposal.id}/file">${proposal.originalFileName}</a>
                </c:when>
                <c:otherwise>No file uploaded</c:otherwise>
            </c:choose>
        </p>
        <p><strong>Synopsis:</strong></p>
        <p>${proposal.synopsis}</p>
    </div>

    <c:if test="${canEdit || canSubmit}">
        <div class="action-row detail-actions">
            <c:if test="${canEdit}">
                <a class="btn" href="${pageContext.request.contextPath}/main/proposals/${proposal.id}/edit">Edit Draft</a>
            </c:if>
            <c:if test="${canSubmit}">
                <form method="post" action="${pageContext.request.contextPath}/main/proposals/${proposal.id}/submit">
                    <button class="btn" type="submit">Submit To Tantou Review</button>
                </form>
            </c:if>
        </div>
    </c:if>

    <c:if test="${canReview}">
        <div class="panel">
            <h2>Tantou Editor Review</h2>
            <form method="post" action="${pageContext.request.contextPath}/main/proposals/${proposal.id}/review" class="form-grid">
                <label>Decision</label>
                <select name="decision">
                    <option value="APPROVE">APPROVE</option>
                    <option value="REJECT">REJECT</option>
                    <option value="REVISE">REVISE</option>
                </select>

                <label>Reason / Revision Notes</label>
                <textarea id="reviewNote" name="note" rows="4" placeholder="Required for REJECT or REVISE"></textarea>

                <button type="submit" class="btn primary">Submit Review</button>
            </form>
        </div>
    </c:if>

    <c:if test="${isTantou && isBoard && proposal.assignedEditorId == sessionScope.AUTH_USER.id && proposal.status == 'BOARD_REVIEW'}">
        <div class="alert warning">
            Conflict of interest: you are the managing Tantou Editor for this proposal, so you cannot vote on it.
        </div>
    </c:if>

    <c:if test="${canBoardVote}">
        <div class="panel board-vote-panel">
            <div class="board-vote-panel-head">
                <div>
                    <h2>Cast Editorial Board Vote</h2>
                    <p>Choose one decision for this proposal round. Votes are final after submission.</p>
                </div>
                <span class="status-chip status-review">Eligible</span>
            </div>
            <form method="post" action="${pageContext.request.contextPath}/main/proposals/${proposal.id}/board-vote" class="board-vote-form">
                <div class="board-decision-options">
                    <label class="board-decision-card is-approve">
                        <input type="radio" name="decision" value="APPROVE" checked />
                        <span class="decision-title">Approve</span>
                        <span class="decision-copy">Move the proposal toward publication if approval wins.</span>
                    </label>
                    <label class="board-decision-card is-revise">
                        <input type="radio" name="decision" value="REVISE" />
                        <span class="decision-title">Request Revision</span>
                        <span class="decision-copy">Send it back with notes before publication can proceed.</span>
                    </label>
                    <label class="board-decision-card is-reject">
                        <input type="radio" name="decision" value="REJECT" />
                        <span class="decision-title">Reject</span>
                        <span class="decision-copy">Reject the proposal for this submission attempt.</span>
                    </label>
                </div>

                <div class="board-vote-note-field">
                    <label for="boardVoteNote">Reason / Revision Notes</label>
                    <textarea id="boardVoteNote" name="note" rows="5" placeholder="Required when requesting revision or rejecting"></textarea>
                    <small id="boardVoteHint">Optional for approval. Required for revision or rejection.</small>
                </div>

                <div class="board-vote-submit-row">
                    <span>Round closes <fmt:formatDate value="${proposal.boardVotingClosesAt}" pattern="yyyy-MM-dd HH:mm" /></span>
                    <button type="submit" class="btn primary">Submit Vote</button>
                </div>
            </form>
        </div>
    </c:if>
    <div class="panel">
        <h2>Revision History</h2>
        <%-- ProposalHistory is both the state-transition timeline and board vote record. --%>
        <table class="data-table">
            <thead>
                <tr>
                    <th>Time</th>
                    <th>Actor</th>
                    <th>Role</th>
                    <th>Action</th>
                    <th>Attempt</th>
                    <th>Note</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach items="${history}" var="h">
                    <tr>
                        <td><fmt:formatDate value="${h.createdAt}" pattern="yyyy-MM-dd HH:mm" /></td>
                        <td><c:out value="${empty h.actorName ? 'System' : h.actorName}" /></td>
                        <td>${h.actorRole}</td>
                        <td>${h.actionType}</td>
                        <td>${h.submitAttemptNumber}</td>
                        <td>${h.note}</td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>

    <p><a href="${pageContext.request.contextPath}/main/proposals">Back to list</a></p>
</main>

<script src="${pageContext.request.contextPath}/assets/js/proposal.js"></script>
<jsp:include page="../common/footer.jsp" />
</body>
</html>
