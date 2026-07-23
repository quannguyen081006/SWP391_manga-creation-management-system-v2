<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>Decision Sessions</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
        <style>
            .editorial-hero {
                background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
                color: white;
                padding: 40px;
                border-radius: 12px;
                margin-bottom: 30px;
                box-shadow: 0 10px 30px rgba(44, 62, 80, 0.4);
            }

            .editorial-hero h1 {
                margin: 0 0 10px 0;
                font-size: 32px;
                font-weight: 700;
                text-transform: uppercase;
                letter-spacing: 2px;
            }

            .editorial-hero .subtitle {
                font-size: 16px;
                opacity: 0.8;
            }

            .decision-card {
                background: white;
                border-radius: 12px;
                padding: 24px;
                margin-bottom: 20px;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
                transition: transform 0.2s, box-shadow 0.2s;
                border-left: 4px solid #2c3e50;
            }

            .decision-card:hover {
                transform: translateY(-2px);
                box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
            }

            .decision-card.status-OPEN {
                border-left-color: #e74c3c;
                background: linear-gradient(to right, #ffe6e6, white);
            }

            .decision-card.status-CLOSED {
                border-left-color: #27ae60;
            }

            .decision-card.status-PENDING {
                border-left-color: #f39c12;
            }

            .risk-badge {
                display: inline-block;
                padding: 4px 12px;
                border-radius: 12px;
                font-size: 11px;
                font-weight: 600;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }

            .risk-badge.HIGH {
                background: #e74c3c;
                color: white;
            }

            .risk-badge.MEDIUM {
                background: #f39c12;
                color: white;
            }

            .risk-badge.LOW {
                background: #27ae60;
                color: white;
            }

            .pressure-banner {
                background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%);
                color: white;
                padding: 12px 20px;
                border-radius: 8px;
                margin-bottom: 16px;
                font-weight: 600;
                display: flex;
                align-items: center;
                gap: 10px;
            }

            .vote-btn {
                padding: 12px 24px;
                border: none;
                border-radius: 8px;
                font-weight: 600;
                font-size: 14px;
                cursor: pointer;
                transition: transform 0.2s, box-shadow 0.2s;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }

            .vote-btn:hover {
                transform: translateY(-2px);
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
            }

            .vote-btn-continue {
                background: linear-gradient(135deg, #27ae60 0%, #2ecc71 100%);
                color: white;
            }

            .vote-btn-change {
                background: linear-gradient(135deg, #3498db 0%, #5dade2 100%);
                color: white;
            }

            .vote-btn-cancel {
                background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%);
                color: white;
            }

            .vote-buttons {
                display: flex;
                gap: 12px;
                margin-top: 20px;
            }

            .system-suggestion {
                background: #f8f9fa;
                border-left: 4px solid #3498db;
                padding: 16px;
                margin: 16px 0;
                border-radius: 4px;
            }

            .system-suggestion strong {
                color: #3498db;
            }

            .vote-table {
                width: 100%;
                border-collapse: collapse;
            }

            .vote-table th {
                background: #f8f9fa;
                padding: 12px;
                text-align: left;
                font-weight: 600;
                color: #2c3e50;
                border-bottom: 2px solid #e9ecef;
            }

            .vote-table td {
                padding: 12px;
                border-bottom: 1px solid #e9ecef;
            }

            .vote-table tr:hover {
                background: #f8f9fa;
            }

            .decision-continue {
                color: #27ae60;
                font-weight: 600;
            }

            .decision-cancel {
                color: #e74c3c;
                font-weight: 600;
            }

            .decision-change {
                color: #3498db;
                font-weight: 600;
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
        </style>
    </head>
    <body>
        <jsp:include page="../common/header.jsp" />

        <c:if test="${not empty error}"><div class="alert error"><c:out value="${error}" /></div></c:if>

        <c:if test="${not empty groupedSessions}">
            <div class="section-card">
                <h3 class="section-title" style="font-size: 24px; margin-bottom: 24px;">📋 Decision Sessions by Series</h3>

                <c:forEach items="${groupedSessions}" var="group">
                    <div class="decision-card status-${group.latestStatus}">
                        <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 16px;">
                            <div>
                                <h3 style="margin: 0 0 8px 0; font-size: 18px; color: #2c3e50;"><c:out value="${group.seriesTitle}" /> <span style="font-size: 14px; color: #95a5a6;">(#<c:out value="${group.seriesId}" />)</span></h3>
                                <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;">
                                    <span style="background: #f39c12; color: white; padding: 4px 12px; border-radius: 12px; font-size: 11px; font-weight: 600; text-transform: uppercase;">Entered Bottom 20: ${group.sessionCount} times</span>
                                    <span style="color: #7f8c8d; font-size: 14px;">Latest: ${group.latestOpenedAt}</span>
                                </div>
                            </div>
                            <div style="text-align: right;">
                                <div style="font-size: 12px; color: #95a5a6; text-transform: uppercase; letter-spacing: 0.5px;">Latest Status</div>
                                <div id="status-label-${group.seriesId}" style="font-weight: 600; color: #2c3e50;">Loading...</div>
                            </div>
                        </div>

                        <div style="display: flex; gap: 12px; margin-top: 16px;">
                            <button class="btn small" onclick="toggleHistory('${group.seriesId}')" style="background: #667eea; color: white; border: none; cursor: pointer;">View History</button>
                            <c:if test="${not empty group.sessions}">
                                <a class="btn small" href="${pageContext.request.contextPath}/main/decisions/${group.sessions[0].id}" style="background: #2c3e50; color: white;">Review Latest →</a>
                            </c:if>
                        </div>

                        <div id="history-${group.seriesId}" style="display: none; margin-top: 20px; padding: 16px; background: #f8f9fa; border-radius: 8px;">
                            <h4 style="margin: 0 0 12px 0; font-size: 14px; color: #2c3e50;">Decision History</h4>
                            <c:forEach items="${group.sessions}" var="s">
                                <div style="padding: 10px; background: white; border-radius: 4px; margin-bottom: 8px; border-left: 3px solid #667eea;">
                                    <div style="display: flex; justify-content: space-between; align-items: center;">
                                        <div>
                                            <span style="font-weight: 600; color: #2c3e50;">${s.openedAt}</span>
                                            <span style="margin-left: 12px; color: #7f8c8d; font-size: 13px;">Status: ${s.status}</span>
                                        </div>
                                        <span class="decision-${s.result.toLowerCase()}" style="font-weight: 600;">${s.result}</span>
                                    </div>
                                    <a href="${pageContext.request.contextPath}/main/decisions/${s.id}" style="display: inline-block; margin-top: 8px; font-size: 13px; color: #667eea; text-decoration: none;">View Details →</a>
                                </div>
                            </c:forEach>
                        </div>
                    </div>
                </c:forEach>
            </div>
        </c:if>

        <c:if test="${not empty sessionDetail}">
            <div class="decision-card status-${sessionDetail.status}">
                <div class="pressure-banner">
                    <span style="font-size: 24px;">⚠️</span>
                    <span>Bottom 20% Performance Review Required</span>
                </div>

                <h3 style="margin: 0 0 20px 0; font-size: 24px; color: #2c3e50;">Decision Session #${sessionDetail.id}</h3>

                <div style="display: grid; grid-template-columns: repeat(4, auto); gap: 20px; margin-bottom: 20px;">
                    <div>
                        <div style="font-size: 12px; color: #95a5a6; text-transform: uppercase; letter-spacing: 0.5px;">Series</div>
                        <div style="font-weight: 600; color: #2c3e50;"><c:out value="${sessionDetail.seriesTitle}" /> <span style="font-size: 14px; color: #95a5a6;">(#<c:out value="${sessionDetail.seriesId}" />)</span></div>
                    </div>
                    <div>
                        <div style="font-size: 12px; color: #95a5a6; text-transform: uppercase; letter-spacing: 0.5px;">Status</div>
                        <div class="decision-detail-status" style="font-weight: 600; color: #2c3e50;"><c:out value="${sessionDetail.status}" /></div>
                    </div>
                    <div>
                        <div style="font-size: 12px; color: #95a5a6; text-transform: uppercase; letter-spacing: 0.5px;">Result</div>
                        <div style="font-weight: 600; color: #2c3e50;">${sessionDetail.result}</div>
                    </div>
                    <div>
                        <div style="font-size: 12px; color: #95a5a6; text-transform: uppercase; letter-spacing: 0.5px;">Opened</div>
                        <div style="font-weight: 600; color: #2c3e50;">${sessionDetail.openedAt}</div>
                    </div>
                </div>

                <c:if test="${not empty sessionDetail.systemSuggestion}">
                    <div class="system-suggestion">
                        <strong>🤖 System Recommendation:</strong> ${sessionDetail.systemSuggestion}
                    </div>
                </c:if>

                <c:if test="${not empty revenueHistory}">
                    <div style="margin-top:24px;">
                        <h4>📈 Revenue Trend Analysis</h4>

                            <canvas id="revenueChart"></canvas>
                    </div>
                </c:if>

                <c:if test="${sessionScope.AUTH_USER.hasRole('EDITORIAL_BOARD') && sessionDetail.status == 'OPEN'}">
                    <div style="margin-top: 24px; padding-top: 24px; border-top: 2px solid #e9ecef;">
                        <h4 style="margin: 0 0 16px 0; font-size: 18px; color: #2c3e50;">🗳️ Cast Your Vote</h4>
                        <form method="post" action="${pageContext.request.contextPath}/main/decisions/${sessionDetail.id}/votes">
                            <div style="margin-bottom: 16px;">
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #2c3e50;">Select Decision</label>
                                <select name="decision" style="width: 100%; padding: 12px; border: 2px solid #e9ecef; border-radius: 8px; font-size: 14px;">
                                    <option value="CONTINUE">✅ CONTINUE - Series shows growth potential</option>
                                    <option value="CHANGE_TYPE">🔄 CHANGE_TYPE - Transform series direction</option>
                                    <option value="CANCEL">❌ CANCEL - Terminate underperforming series</option>
                                </select>
                            </div>
                            <div style="margin-bottom: 16px;">
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #2c3e50;">Justification (Required for CANCEL)</label>
                                <input type="text" name="justification" placeholder="Explain your reasoning..." style="width: 100%; padding: 12px; border: 2px solid #e9ecef; border-radius: 8px; font-size: 14px;" />
                            </div>
                            <div class="vote-buttons">
                                <button type="submit" class="vote-btn vote-btn-continue">Submit</button>
                            </div>
                        </form>
                    </div>
                </c:if>
            </div>

            <div class="section-card" style="margin-top: 24px;">
                <h3 class="section-title" style="font-size: 20px; margin-bottom: 20px;">📊 Board Votes</h3>
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
                                    <td><c:out value="${v.votedAt}" /></td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </c:if>
                <c:if test="${empty sessionDetail.votes}">
                    <div class="empty-state">
                        <div class="icon">📊</div>
                        <div>No votes cast yet</div>
                    </div>
                </c:if>
            </div>
        </c:if>

        <c:if test="${empty sessions && empty sessionDetail}">
            <div class="empty-state">
                <div class="icon">⚖️</div>
                <div style="font-size: 18px;">No active decision sessions</div>
                <div style="font-size: 14px;">Decision sessions are triggered for series in the bottom 20% ranking</div>
            </div>
        </c:if>

        <div style="margin-top: 30px;">
            <a class="btn" href="${pageContext.request.contextPath}/main/dashboard">← Back to Dashboard</a>
        </div>

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
                    return 'High Risk';
                }
                
                if (status === 'UNDER_REVIEW') {
                    return 'Under Review';
                }
                
                if (status === 'CLOSED' || status === 'FINALIZED') {
                    if (result === 'CONTINUE') {
                        return 'Resolved';
                    }
                    if (result === 'CHANGE_TYPE') {
                        return 'Changed';
                    }
                    if (result === 'CANCEL') {
                        return 'Cancelled';
                    }
                }
                
                return status;
            }
            
            function getStatusBadgeColor(status, result) {
                if (!status) return '#7f8c8d';
                
                if (status === 'OPEN') {
                    return '#e74c3c';
                }
                
                if (status === 'UNDER_REVIEW') {
                    return '#f39c12';
                }
                
                if (status === 'CLOSED' || status === 'FINALIZED') {
                    if (result === 'CONTINUE') {
                        return '#27ae60';
                    }
                    if (result === 'CHANGE_TYPE') {
                        return '#3498db';
                    }
                    if (result === 'CANCEL') {
                        return '#e74c3c';
                    }
                }
                
                return '#7f8c8d';
            }
            
            // Initialize status labels on page load
            document.addEventListener('DOMContentLoaded', function() {
                <c:forEach items="${groupedSessions}" var="group">
                    var statusLabel = document.getElementById('status-label-${group.seriesId}');
                    if (statusLabel) {
                        var label = getDecisionStatusLabel('${group.latestStatus}', '${group.latestResult}');
                        var color = getStatusBadgeColor('${group.latestStatus}', '${group.latestResult}');
                        statusLabel.textContent = label;
                        statusLabel.style.color = color;
                    }
                </c:forEach>
                
                // Also update session detail status if present
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

