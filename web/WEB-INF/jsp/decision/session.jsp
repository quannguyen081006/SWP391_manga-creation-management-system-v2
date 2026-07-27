<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>Decision Sessions</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
        <style>
            /* Page-local styles for the decision list + detail.
               Palette is the project one only: navy #0f172a, red #b91c1c,
               amber #b45309, green #16794b, slate #64748b / #e2e8f0 / #f8fafc.
               The page head comes from the shared .page-header in common.css,
               so there is no hero block defined here any more. */

            /* Content column. A one-per-row list has nothing to fill the middle of a
               1400px shell, so it is capped here and centred. */
            .decision-page {
                width: min(1040px, 100%);
                margin-inline: auto;
            }

            .decision-back {
                margin-top: 30px;
            }

            /* One card per series, sitting straight on the page background — there is no
               wrapping panel, so a card is never a white box inside another white box. */
            .decision-card {
                padding: 26px 28px;
                margin-bottom: 18px;
                background: #fff;
                border: 1px solid #e2e8f0;
                border-left: 6px solid #64748b;
                border-radius: 10px;
            }

            /* Coloured edge lets you scan the column at a glance; the chip next to each
               title spells the same status out in words. */
            .decision-card.status-OPEN { border-left-color: #b91c1c; }
            .decision-card.status-UNDER_REVIEW { border-left-color: #b45309; }
            .decision-card.status-PENDING { border-left-color: #b45309; }
            .decision-card.status-CLOSED,
            .decision-card.status-FINALIZED { border-left-color: #16794b; }

            /* Identity left, actions right, both vertically centred. */
            .decision-card-head {
                display: flex;
                align-items: center;
                justify-content: space-between;
                flex-wrap: wrap;
                gap: 12px 24px;
            }

            .decision-card-main {
                flex: 1 1 320px;
                min-width: 0;
            }

            .decision-title-row {
                display: flex;
                align-items: center;
                flex-wrap: wrap;
                gap: 10px;
            }

            .decision-series-title {
                margin: 0;
                color: #0f172a;
                font-size: 22px;
                font-weight: 800;
                line-height: 1.25;
            }

            .decision-series-id {
                color: #94a3b8;
                font-size: 16px;
                font-weight: 400;
            }

            /* Sits next to the title it describes, not across the card. Text, fill and
               border are all written by the status script so a CLOSED session can be
               green for CONTINUE and red for CANCEL; these are the fallbacks. */
            .decision-status-chip {
                padding: 5px 14px;
                border: 1px solid #e2e8f0;
                border-radius: 999px;
                color: #64748b;
                background: #f8fafc;
                font-size: 12px;
                font-weight: 800;
                letter-spacing: .04em;
                text-transform: uppercase;
                white-space: nowrap;
            }

            .decision-meta-row {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                gap: 8px 14px;
                margin-top: 14px;
            }

            .decision-count {
                padding: 5px 14px;
                color: #b45309;
                background: #fffbeb;
                border: 1px solid #fde68a;
                border-radius: 999px;
                font-size: 13px;
                font-weight: 700;
            }

            .decision-timestamp {
                color: #64748b;
                font-size: 14px;
            }

            .decision-card-actions {
                display: flex;
                flex-shrink: 0;
                flex-wrap: wrap;
                gap: 10px;
            }

            /* Slightly taller than the global .btn.small so the buttons carry the
               weight of the bigger card instead of floating in it. */
            .decision-card-actions .btn.small {
                min-height: 42px;
                padding: 10px 18px;
                font-size: 14px;
                font-weight: 700;
            }

            .decision-btn-outline {
                color: #0f172a;
                background: #fff;
                border: 1px solid #cbd5e1;
                cursor: pointer;
            }

            .decision-btn-solid {
                color: #fff;
                background: #0f172a;
                border: 1px solid #0f172a;
            }

            .decision-history {
                margin-top: 20px;
                padding: 16px;
                background: #f8fafc;
                border-radius: 8px;
            }

            .decision-history-title {
                margin: 0 0 12px;
                color: #0f172a;
                font-size: 14px;
            }

            .decision-history-item {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                justify-content: space-between;
                gap: 8px;
                padding: 10px 0;
            }

            .decision-history-item:not(:last-child) {
                border-bottom: 1px solid #e2e8f0;
            }

            .decision-history-when {
                color: #0f172a;
                font-weight: 600;
            }

            .decision-history-status {
                margin-left: 12px;
                color: #64748b;
                font-size: 13px;
            }

            .decision-history-link {
                color: #0f172a;
                font-size: 13px;
                font-weight: 600;
                text-decoration: none;
            }

            .decision-detail-title {
                margin: 0 0 20px;
                color: #0f172a;
                font-size: 24px;
            }

            .decision-detail-grid {
                display: grid;
                grid-template-columns: repeat(4, auto);
                gap: 20px;
                margin-bottom: 20px;
            }

            .decision-field-label {
                color: #64748b;
                font-size: 12px;
                letter-spacing: 0.05em;
                text-transform: uppercase;
            }

            .decision-field-value {
                color: #0f172a;
                font-weight: 600;
            }

            .system-suggestion {
                padding: 16px;
                margin: 16px 0;
                background: #f8fafc;
                border-left: 4px solid #0f172a;
                border-radius: 4px;
            }

            .system-suggestion strong {
                color: #0f172a;
            }

            .decision-vote-panel {
                margin-top: 24px;
                padding-top: 24px;
                border-top: 1px solid #e2e8f0;
            }

            .decision-vote-panel h4 {
                margin: 0 0 16px;
                color: #0f172a;
                font-size: 18px;
            }

            .decision-vote-field {
                margin-bottom: 16px;
            }

            .decision-vote-field label {
                display: block;
                margin-bottom: 8px;
                color: #0f172a;
                font-weight: 600;
            }

            .decision-vote-field select,
            .decision-vote-field input {
                width: 100%;
                padding: 12px;
                border: 1px solid #cbd5e1;
                border-radius: 8px;
                font-size: 14px;
            }

            .vote-buttons {
                display: flex;
                gap: 12px;
                margin-top: 20px;
            }

            /* Flat fills, no gradients and no lift on hover. */
            .vote-btn {
                padding: 12px 24px;
                border: none;
                border-radius: 8px;
                font-size: 14px;
                font-weight: 600;
                letter-spacing: 0.05em;
                text-transform: uppercase;
                cursor: pointer;
            }

            .vote-btn-continue {
                color: #fff;
                background: #16794b;
            }

            .vote-btn-change {
                color: #fff;
                background: #0f172a;
            }

            .vote-btn-cancel {
                color: #fff;
                background: #b91c1c;
            }

            .vote-table {
                width: 100%;
                border-collapse: collapse;
            }

            .vote-table th {
                padding: 12px;
                color: #0f172a;
                background: #f8fafc;
                border-bottom: 1px solid #e2e8f0;
                font-weight: 600;
                text-align: left;
            }

            .vote-table td {
                padding: 12px;
                border-bottom: 1px solid #e2e8f0;
            }

            .vote-table tr:hover {
                background: #f8fafc;
            }

            .decision-continue {
                color: #16794b;
                font-weight: 600;
            }

            .decision-cancel {
                color: #b91c1c;
                font-weight: 600;
            }

            .decision-change {
                color: #0f172a;
                font-weight: 600;
            }

            /* .empty-state itself comes from common.css; only the SVG sizing is
               specific to this page. */
            .empty-state-icon svg {
                width: 44px;
                height: 44px;
            }

            @media (max-width: 900px) {
                .decision-detail-grid {
                    grid-template-columns: repeat(2, minmax(0, 1fr));
                }
            }
        </style>
    </head>
    <body>
        <jsp:include page="../common/header.jsp" />

        <%-- Single-column list, so the content column is capped and centred instead of
             stretching the full shell width; the head is inside it so both line up. --%>
        <div class="decision-page">

        <c:if test="${not empty error}"><div class="alert error"><c:out value="${error}" /></div></c:if>

        <%-- Shared page head block (common.css), same structure as series/list.jsp. --%>
        <div class="page-header">
            <div class="page-header-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 4.5v15"/>
                    <path d="M7.5 19.5h9"/>
                    <path d="M4.5 7.5h15"/>
                    <path d="M4.5 7.5 1.8 13.2h5.4L4.5 7.5Z"/>
                    <path d="M19.5 7.5l-2.7 5.7h5.4l-2.7-5.7Z"/>
                </svg>
            </div>
            <div>
                <h2>Decision Sessions</h2>
                <p>Editorial Board voting on series continuation, cancellation, or type change.</p>
            </div>
        </div>

        <c:if test="${not empty groupedSessions}">
            <c:forEach items="${groupedSessions}" var="group">
                <div class="decision-card status-${group.latestStatus}">
                    <%-- One row: identity on the left, actions on the right, so a full-width
                         card is filled on both edges instead of stacking everything left. --%>
                    <div class="decision-card-head">
                        <div class="decision-card-main">
                            <div class="decision-title-row">
                                <h3 class="decision-series-title"><c:out value="${group.seriesTitle}" /> <span class="decision-series-id">#<c:out value="${group.seriesId}" /></span></h3>
                                <span id="status-label-${group.seriesId}" class="decision-status-chip"></span>
                            </div>
                            <div class="decision-meta-row">
                                <span class="decision-count">Entered Bottom 20 &middot; ${group.sessionCount} <c:choose><c:when test="${group.sessionCount == 1}">time</c:when><c:otherwise>times</c:otherwise></c:choose></span>
                                <span class="decision-timestamp">Latest <fmt:formatDate value="${group.latestOpenedAt}" pattern="dd/MM/yyyy HH:mm" /></span>
                            </div>
                        </div>

                        <div class="decision-card-actions">
                            <button class="btn small decision-btn-outline" onclick="toggleHistory('${group.seriesId}')">View History</button>
                            <c:if test="${not empty group.sessions}">
                                <a class="btn small decision-btn-solid" href="${pageContext.request.contextPath}/main/decisions/${group.sessions[0].id}">Review Latest &rarr;</a>
                            </c:if>
                        </div>
                    </div>

                    <div id="history-${group.seriesId}" class="decision-history" style="display: none;">
                        <h4 class="decision-history-title">Decision History</h4>
                        <c:forEach items="${group.sessions}" var="s">
                            <div class="decision-history-item">
                                <div>
                                    <span class="decision-history-when"><fmt:formatDate value="${s.openedAt}" pattern="dd/MM/yyyy HH:mm" /></span>
                                    <span class="decision-history-status">Status: ${s.status}</span>
                                </div>
                                <span class="decision-${s.result.toLowerCase()}">${s.result}</span>
                                <a href="${pageContext.request.contextPath}/main/decisions/${s.id}" class="decision-history-link">View Details &rarr;</a>
                            </div>
                        </c:forEach>
                    </div>
                </div>
            </c:forEach>
        </c:if>

        <c:if test="${not empty sessionDetail}">
            <div class="decision-card status-${sessionDetail.status}">
                <h3 class="decision-detail-title">Decision Session #${sessionDetail.id}</h3>

                <div class="decision-detail-grid">
                    <div>
                        <div class="decision-field-label">Series</div>
                        <div class="decision-field-value"><c:out value="${sessionDetail.seriesTitle}" /> <span class="decision-series-id">(#<c:out value="${sessionDetail.seriesId}" />)</span></div>
                    </div>
                    <div>
                        <div class="decision-field-label">Status</div>
                        <div class="decision-detail-status decision-field-value"><c:out value="${sessionDetail.status}" /></div>
                    </div>
                    <div>
                        <div class="decision-field-label">Result</div>
                        <div class="decision-field-value">${sessionDetail.result}</div>
                    </div>
                    <div>
                        <div class="decision-field-label">Opened</div>
                        <div class="decision-field-value"><fmt:formatDate value="${sessionDetail.openedAt}" pattern="dd/MM/yyyy HH:mm" /></div>
                    </div>
                </div>

                <c:if test="${not empty sessionDetail.systemSuggestion}">
                    <div class="system-suggestion">
                        <strong>System Recommendation:</strong> ${sessionDetail.systemSuggestion}
                    </div>
                </c:if>

                <c:if test="${not empty revenueHistory}">
                    <div style="margin-top:24px;">
                        <h4>Revenue Trend Analysis</h4>
                        <canvas id="revenueChart"></canvas>
                    </div>
                </c:if>

                <c:if test="${sessionScope.AUTH_USER.hasRole('EDITORIAL_BOARD') && sessionDetail.status == 'OPEN'}">
                    <div class="decision-vote-panel">
                        <h4>Cast Your Vote</h4>
                        <form method="post" action="${pageContext.request.contextPath}/main/decisions/${sessionDetail.id}/votes">
                            <div class="decision-vote-field">
                                <label>Select Decision</label>
                                <select name="decision">
                                    <option value="CONTINUE">CONTINUE - Series shows growth potential</option>
                                    <option value="CHANGE_TYPE">CHANGE_TYPE - Transform series direction</option>
                                    <option value="CANCEL">CANCEL - Terminate underperforming series</option>
                                </select>
                            </div>
                            <div class="decision-vote-field">
                                <label>Justification (Required for CANCEL)</label>
                                <input type="text" name="justification" placeholder="Explain your reasoning..." />
                            </div>
                            <div class="vote-buttons">
                                <button type="submit" class="vote-btn vote-btn-continue">Submit</button>
                            </div>
                        </form>
                    </div>
                </c:if>
            </div>

            <div class="section-card" style="margin-top: 24px;">
                <h3 class="section-title section-title-sm">Board Votes</h3>
                <c:if test="${not empty sessionDetail.votes}">
                    <table class="vote-table">
                        <thead>
                            <tr>
                                <th>Voter ID</th>
                                <th>Decision</th>
                                <th>Justification</th>
                                <th>Voted At</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach items="${sessionDetail.votes}" var="v">
                                <tr>
                                    <td>#${v.voterId}</td>
                                    <td class="decision-${v.decision.toLowerCase()}"><c:out value="${v.decision}" /></td>
                                    <td><c:out value="${v.justification}" /></td>
                                    <td><fmt:formatDate value="${v.votedAt}" pattern="dd/MM/yyyy HH:mm" /></td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </c:if>
                <c:if test="${empty sessionDetail.votes}">
                    <div class="empty-state">
                        <div class="empty-state-title">No votes cast yet</div>
                    </div>
                </c:if>
            </div>
        </c:if>

        <%-- The list is built from groupedSessions, so the empty state has to test that
             same attribute. It used to test "sessions", which the controller never sets,
             so this block rendered underneath a full list on every visit. --%>
        <c:if test="${empty groupedSessions && empty sessionDetail}">
            <div class="empty-state">
                <div class="empty-state-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M12 4.5v15"/>
                        <path d="M7.5 19.5h9"/>
                        <path d="M4.5 7.5h15"/>
                        <path d="M4.5 7.5 1.8 13.2h5.4L4.5 7.5Z"/>
                        <path d="M19.5 7.5l-2.7 5.7h5.4l-2.7-5.7Z"/>
                    </svg>
                </div>
                <div class="empty-state-title">No active decision sessions</div>
                <div class="empty-state-copy">Decision sessions are created by Administrator from ranking results</div>
            </div>
        </c:if>

        <div class="decision-back">
            <a class="btn" href="${pageContext.request.contextPath}/main/dashboard">&larr; Back to Dashboard</a>
        </div>

        </div><%-- /.decision-page --%>

        <jsp:include page="../common/footer.jsp" />
        <script>
            window.revenueHistory = '${revenueHistory}';

            function toggleHistory(seriesId) {
                var historyDiv = document.getElementById('history-' + seriesId);
                if (historyDiv) {
                    historyDiv.style.display = historyDiv.style.display === 'none' ? 'block' : 'none';
                }
            }

            function getDecisionStatusLabel(status, result) {
                if (!status) return '';

                if (status === 'OPEN') {
                    return 'OPEN - Voting In Progress';
                }

                if (status === 'UNDER_REVIEW') {
                    return 'Under Review';
                }

                if (status === 'CLOSED' || status === 'FINALIZED') {
                    if (result === 'CONTINUE') {
                        return 'RESOLVED - Continue Publication';
                    }
                    if (result === 'CHANGE_TYPE') {
                        return 'RESOLVED - Series Type Changed';
                    }
                    if (result === 'CANCEL') {
                        return 'RESOLVED - Series Cancelled';
                    }
                }

                return status;
            }

            // Same branches as before; only the colour literals moved onto the
            // project palette (slate / red / amber / green / navy).
            function getStatusBadgeColor(status, result) {
                if (!status) return '#64748b';

                if (status === 'OPEN') {
                    return '#b91c1c';
                }

                if (status === 'UNDER_REVIEW') {
                    return '#b45309';
                }

                if (status === 'CLOSED' || status === 'FINALIZED') {
                    if (result === 'CONTINUE') {
                        return '#16794b';
                    }
                    if (result === 'CHANGE_TYPE') {
                        return '#0f172a';
                    }
                    if (result === 'CANCEL') {
                        return '#b91c1c';
                    }
                }

                return '#64748b';
            }

            // Soft fill + border that go with each text colour above, so the chip reads
            // as a filled badge instead of bare coloured text.
            var STATUS_CHIP_TINTS = {
                '#b91c1c': { background: '#fef2f2', border: '#fecaca' },
                '#b45309': { background: '#fffbeb', border: '#fde68a' },
                '#16794b': { background: '#f0fdf4', border: '#bbf7d0' },
                '#0f172a': { background: '#f1f5f9', border: '#cbd5e1' },
                '#64748b': { background: '#f8fafc', border: '#e2e8f0' }
            };

            function paintStatusChip(el, status, result) {
                var color = getStatusBadgeColor(status, result);
                var tint = STATUS_CHIP_TINTS[color] || STATUS_CHIP_TINTS['#64748b'];
                el.textContent = getDecisionStatusLabel(status, result);
                el.style.color = color;
                el.style.background = tint.background;
                el.style.borderColor = tint.border;
            }

            document.addEventListener('DOMContentLoaded', function() {
                <c:forEach items="${groupedSessions}" var="group">
                    var statusLabel = document.getElementById('status-label-${group.seriesId}');
                    if (statusLabel) {
                        paintStatusChip(statusLabel, '${group.latestStatus}', '${group.latestResult}');
                    }
                </c:forEach>

                <c:if test="${not empty sessionDetail}">
                    var detailStatus = document.querySelector('.decision-detail-status');
                    if (detailStatus) {
                        var label = getDecisionStatusLabel('${sessionDetail.status}', '${sessionDetail.result}');
                        var color = getStatusBadgeColor('${sessionDetail.status}', '${sessionDetail.result}');
                        detailStatus.textContent = label;
                        detailStatus.style.color = color;
                    }
                </c:if>
            });
        </script>

        <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
        <script src="${pageContext.request.contextPath}/assets/decision-chart.js"></script>
    </body>
</html>
