<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>My Salary</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/salary/salary.css?v=20260620-3" />
</head>
<body>
<jsp:include page="../common/header.jsp" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<div class="section-card">
    <h3 class="section-title">My settled salary periods</h3>
    <p class="section-desc">Read-only salary and KPI history.</p>

    <c:choose>
        <c:when test="${not empty records}">
            <div class="salary-table-wrap">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th class="salary-toggle-column"></th>
                            <th>Period</th>
                            <th>KPI score</th>
                            <th>Gross salary</th>
                            <th>Bonus</th>
                            <th>Net salary</th>
                        </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${records}" var="r">
                        <tr class="salary-row">
                            <td>
                                <button type="button" class="btn-toggle-tasks"
                                        data-salary-toggle="${r.periodId}"
                                        data-salary-target="period-tasks-${r.periodId}"
                                        aria-expanded="false"
                                        aria-controls="period-tasks-${r.periodId}">+</button>
                            </td>
                            <td><c:out value="${r.periodName}" /></td>
                            <td><fmt:formatNumber value="${r.kpiScore}" minFractionDigits="2" maxFractionDigits="2" /></td>
                            <td><fmt:formatNumber value="${r.grossSalary}" maxFractionDigits="0" /> VND</td>
                            <td><fmt:formatNumber value="${r.bonus}" maxFractionDigits="0" /> VND</td>
                            <td class="money-strong"><fmt:formatNumber value="${r.netSalary}" maxFractionDigits="0" /> VND</td>
                        </tr>
                        <tr class="task-detail-row" id="period-tasks-${r.periodId}">
                            <td colspan="6">
                                <c:choose>
                                    <c:when test="${not empty r.tasks}">
                                        <div class="salary-task-list">
                                        <c:forEach items="${r.tasks}" var="t">
                                            <div class="salary-task-card">
                                                <button type="button" class="salary-task-trigger"
                                                        data-salary-toggle="${t.id}"
                                                        data-salary-target="my-task-pages-${r.periodId}-${t.id}"
                                                        aria-expanded="false"
                                                        aria-controls="my-task-pages-${r.periodId}-${t.id}">
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
                                                     id="my-task-pages-${r.periodId}-${t.id}">
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
                                        <div class="empty-state"><div class="subtitle">No task breakdown is available.</div></div>
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
                <div class="title">No settled salary periods</div>
                <div class="subtitle">Your settled salary history will appear here.</div>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="../common/footer.jsp" />
<script src="${ctx}/assets/js/salary/salary.js?v=20260620-3"></script>
</body>
</html>
