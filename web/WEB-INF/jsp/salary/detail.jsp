<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Salary Period Details</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/salary/salary.css?v=20260620-3" />
</head>
<body>
<jsp:include page="../common/header.jsp" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<c:if test="${not empty error}"><div class="alert error"><c:out value="${error}" /></div></c:if>

<div class="section-card">
    <div class="salary-head">
        <div>
            <h3 class="section-title salary-title"><c:out value="${period.name}" /></h3>
            <div>
                <span class="status-badge salary-status-badge ${period.status}">${period.status}</span>
            </div>
        </div>
        <c:if test="${period.status == 'OPEN'}">
            <p class="section-desc">This period updates automatically as tasks are approved, and is settled
                automatically by the system on the 5th of each month.</p>
        </c:if>
    </div>

    <c:choose>
        <c:when test="${not empty records}">
            <div class="salary-table-wrap">
                <table class="data-table salary-detail-table">
                    <thead>
                        <tr>
                            <th class="salary-toggle-column"></th>
                            <th>Assistant</th>
                            <th>Approved tasks</th>
                            <th>Pages</th>
                            <th>On-time rate</th>
                            <th>KPI</th>
                            <th>Gross salary</th>
                            <th>Bonus</th>
                            <th>Net salary</th>
                        </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${records}" var="r">
                        <c:choose>
                            <c:when test="${r.onTimeRate >= 90}"><c:set var="onTimeClass" value="metric-good" /></c:when>
                            <c:when test="${r.onTimeRate >= 80}"><c:set var="onTimeClass" value="metric-warn" /></c:when>
                            <c:otherwise><c:set var="onTimeClass" value="metric-bad" /></c:otherwise>
                        </c:choose>
                        <c:choose>
                            <c:when test="${r.kpiScore >= 90}"><c:set var="kpiClass" value="metric-good" /></c:when>
                            <c:when test="${r.kpiScore >= 80}"><c:set var="kpiClass" value="metric-warn" /></c:when>
                            <c:otherwise><c:set var="kpiClass" value="metric-bad" /></c:otherwise>
                        </c:choose>
                        <tr class="salary-row" data-assistant="${r.assistantId}">
                            <td>
                                <button type="button" class="btn-toggle-tasks"
                                        data-salary-toggle="${r.assistantId}"
                                        data-salary-target="assistant-tasks-${r.assistantId}"
                                        aria-expanded="false"
                                        aria-controls="assistant-tasks-${r.assistantId}">+</button>
                            </td>
                            <td><c:out value="${r.assistantName}" /></td>
                            <td>${r.totalTasksApproved}</td>
                            <td>${r.totalPagesCompleted}</td>
                            <td class="${onTimeClass}"><fmt:formatNumber value="${r.onTimeRate}" minFractionDigits="2" maxFractionDigits="2" />%</td>
                            <td class="${kpiClass}"><fmt:formatNumber value="${r.kpiScore}" minFractionDigits="2" maxFractionDigits="2" /></td>
                            <td><fmt:formatNumber value="${r.grossSalary}" type="number" maxFractionDigits="0" /> VND</td>
                            <td class="salary-adjust-summary">
                                <span class="salary-bonus">+<fmt:formatNumber value="${r.bonus}" maxFractionDigits="0" /> VND</span>
                            </td>
                            <td class="money-strong"><fmt:formatNumber value="${r.netSalary}" maxFractionDigits="0" /> VND</td>
                        </tr>
                        <tr class="task-detail-row" id="assistant-tasks-${r.assistantId}">
                            <td colspan="9">
                                <c:choose>
                                    <c:when test="${not empty r.tasks}">
                                        <div class="salary-task-list">
                                        <c:forEach items="${r.tasks}" var="t">
                                            <div class="salary-task-card">
                                                <button type="button" class="salary-task-trigger"
                                                        data-salary-toggle="${t.id}"
                                                        data-salary-target="task-pages-${r.assistantId}-${t.id}"
                                                        aria-expanded="false"
                                                        aria-controls="task-pages-${r.assistantId}-${t.id}">
                                                    <span class="salary-task-chevron" aria-hidden="true">+</span>
                                                    <span class="salary-task-main">
                                                        <strong>Task #${t.id}</strong>
                                                        <span><c:out value="${t.seriesTitle}" /> · Chapter ${t.chapterNumber} · Pages ${t.pageRangeStart}-${t.pageRangeEnd}</span>
                                                    </span>
                                                    <span class="salary-task-status ${t.onTime ? 'is-good' : 'is-bad'}">
                                                        <c:choose>
                                                            <c:when test="${t.onTime}">On time</c:when>
                                                            <c:otherwise>Overdue ${t.daysLate} day(s)</c:otherwise>
                                                        </c:choose>
                                                    </span>
                                                    <span class="salary-task-money">
                                                        <fmt:formatNumber value="${t.amount}" maxFractionDigits="0" /> VND
                                                    </span>
                                                </button>

                                                <div class="salary-task-meta">
                                                    <span>Due <strong><fmt:formatDate value="${t.dueDate}" pattern="dd/MM/yyyy" /></strong></span>
                                                    <span>Approved <strong><fmt:formatDate value="${t.approvedAt}" pattern="dd/MM/yyyy" /></strong></span>
                                                    <span>Rejections <strong>${t.rejectionCount}</strong></span>
                                                </div>

                                                <div class="salary-page-panel"
                                                     id="task-pages-${r.assistantId}-${t.id}">
                                                    <div class="salary-page-grid salary-page-grid-head">
                                                        <span>Page</span>
                                                        <span>Work stage</span>
                                                        <span>Rate</span>
                                                        <span>Amount</span>
                                                    </div>
                                                    <c:forEach items="${t.pages}" var="p">
                                                        <div class="salary-page-grid">
                                                            <span>Page ${p.pageNumber}</span>
                                                            <span class="salary-stage-badge">${p.taskType}</span>
                                                            <span><fmt:formatNumber value="${p.ratePerPage}" maxFractionDigits="0" /> VND/page</span>
                                                            <strong><fmt:formatNumber value="${p.amount}" maxFractionDigits="0" /> VND</strong>
                                                        </div>
                                                    </c:forEach>
                                                </div>
                                            </div>
                                        </c:forEach>
                                        </div>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="empty-state">
                                            <div class="subtitle">No approved tasks in this period.</div>
                                        </div>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:when>
        <c:otherwise>
            <div class="empty-state">
                <div class="title">No salary data yet</div>
                <div class="subtitle">Open periods refresh automatically when viewed. You can also use “Refresh calculation”.</div>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<div class="stack-actions content-section-large">
    <a class="btn" href="${ctx}/main/salary/periods">&larr; Back to salary periods</a>
</div>

<jsp:include page="../common/footer.jsp" />
<script src="${ctx}/assets/js/salary/salary.js?v=20260620-3"></script>
</body>
</html>
