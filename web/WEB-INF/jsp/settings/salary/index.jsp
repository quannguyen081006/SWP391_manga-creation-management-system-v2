<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Salary &amp; KPI Settings</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/salary/salary-settings.css" />
    <style>
        .sal-section {
            background: var(--surface);
            border: 1px solid var(--line);
            border-radius: 14px;
            padding: 28px;
            margin-bottom: 20px;
        }

        .sal-section-label {
            margin: 0 0 12px;
            color: var(--muted);
            font-size: 11px;
            font-weight: 700;
            letter-spacing: .07em;
            text-transform: uppercase;
        }

        .sal-formula {
            margin-bottom: 20px;
            padding: 14px 18px;
            background: var(--bg);
            border-radius: 8px;
            color: var(--ink);
            font-family: "Courier New", monospace;
            font-size: 13px;
            line-height: 2;
        }

        .sal-comment {
            color: var(--muted);
            font-size: 12px;
            font-style: italic;
        }

        .sal-var-row {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 10px 14px;
            border: 1px solid var(--line);
            border-radius: 10px;
            margin-bottom: 8px;
        }

        .sal-dot {
            width: 8px;
            height: 8px;
            flex: 0 0 8px;
            border-radius: 50%;
        }

        .sal-dot-kpi { background: #185FA5; }
        .sal-dot-bonus { background: #3B6D11; }
        .sal-dot-penalty { background: #A32D2D; }
        .sal-dot-threshold { background: #854F0B; }

        .sal-var-copy {
            flex: 1;
            min-width: 0;
        }

        .sal-var-name {
            margin: 0 0 2px;
            font-size: 14px;
            font-weight: 600;
        }

        .sal-var-desc {
            margin: 0;
            color: var(--muted);
            font-size: 13px;
        }

        .sal-var-control {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-shrink: 0;
        }

        .sal-var-input {
            width: 90px;
            padding: 6px 10px;
            text-align: right;
        }

        .sal-var-unit {
            color: var(--muted);
            font-size: 13px;
            white-space: nowrap;
        }

        .sal-hint {
            margin-top: 8px;
            padding: 8px 14px;
            background: var(--bg);
            border-radius: 8px;
            color: var(--muted);
            font-size: 13px;
        }

        .sal-note {
            margin: 0;
            color: var(--muted);
            font-size: 13px;
        }

        .settings-actions {
            display: flex;
            justify-content: flex-end;
            gap: 10px;
            margin-top: 24px;
        }

        @media (max-width: 720px) {
            .sal-section {
                padding: 20px 16px;
            }

            .sal-var-row {
                align-items: flex-start;
                flex-wrap: wrap;
            }

            .sal-var-copy {
                min-width: calc(100% - 20px);
            }

            .sal-var-control {
                width: 100%;
                padding-left: 20px;
            }

            .settings-actions {
                flex-direction: column-reverse;
            }
        }
    </style>
</head>
<body>
<jsp:include page="../../common/header.jsp" />

<main class="container settings-page">
    <div class="settings-page-head">
        <div>
            <h2>Salary &amp; KPI Settings</h2>
            <p>Configure salary rates and the formulas used to calculate KPI, bonus, and deductions.</p>
        </div>
    </div>

    <c:if test="${not empty success}"><div class="alert success"><c:out value="${success}" /></div></c:if>
    <c:if test="${not empty error}"><div class="alert error"><c:out value="${error}" /></div></c:if>

    <div class="section-card settings-panel">
        <h3 class="section-title">Task Type Rates</h3>
        <p class="section-desc">Base salary per completed page, by task type. Saved individually.</p>
        <div class="task-rate-table-wrap">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Code</th>
                        <th>Name</th>
                        <th>Rate per page</th>
                    </tr>
                </thead>
                <tbody>
                <c:forEach items="${taskTypes}" var="item">
                    <tr>
                        <td><strong><c:out value="${item.code}" /></strong></td>
                        <td><c:out value="${item.displayName}" /></td>
                        <td>
                            <form class="task-rate-form" method="post"
                                  action="${pageContext.request.contextPath}/main/settings/salary/task-types/${item.code}/update">
                                <input aria-label="Rate per page" name="ratePerPage" type="number"
                                       min="1000" step="1000" value="${item.ratePerPage}" required />
                                <span>VND / page</span>
                                <button class="btn small" type="submit">Save</button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
    </div>

    <form class="settings-form" method="post"
          action="${pageContext.request.contextPath}/main/settings/salary">

        <div class="sal-section">
            <p class="sal-section-label">01 — KPI score</p>
            <div class="sal-formula">
                <div>KPI = onTimeRate</div>
                <div class="sal-comment">// onTimeRate = tasks on-time / total approved × 100</div>
            </div>
        </div>

        <div class="sal-section">
            <p class="sal-section-label">02 — Gross salary</p>
            <div class="sal-formula">
                <div>Gross = Σ (ratePerPage × pages)</div>
                <div class="sal-comment">// Summed by task type across approved tasks not yet paid</div>
            </div>
            <p class="sal-note">Adjust the unit rates in the Task Type Rates table above.</p>
        </div>

        <div class="sal-section">
            <p class="sal-section-label">03 — Bonus</p>
            <div class="sal-formula">
                <div>Bonus = KPI ≥ T ? Gross × B / 100 : 0</div>
                <div class="sal-comment">// Bonus-only policy: no bonus is granted when KPI is below the threshold, and there are no other deductions.</div>
            </div>

            <div class="sal-var-row">
                <span class="sal-dot sal-dot-bonus" aria-hidden="true"></span>
                <div class="sal-var-copy">
                    <p class="sal-var-name">KPI threshold (T)</p>
                    <p class="sal-var-desc">Minimum KPI score required to earn a bonus</p>
                </div>
                <div class="sal-var-control">
                    <input class="sal-var-input" id="kpiBonusThreshold" type="number"
                           name="kpiBonusThreshold" min="0" max="100"
                           value="${settings.kpiBonusThreshold}" required />
                    <span class="sal-var-unit">pts</span>
                </div>
            </div>

            <div class="sal-var-row">
                <span class="sal-dot sal-dot-bonus" aria-hidden="true"></span>
                <div class="sal-var-copy">
                    <p class="sal-var-name">Bonus rate (B)</p>
                    <p class="sal-var-desc">Bonus percentage applied to gross salary</p>
                </div>
                <div class="sal-var-control">
                    <input class="sal-var-input" id="bonusPercent" type="number"
                           name="bonusPercent" min="0" max="100" step="0.1"
                           value="${settings.bonusPercent}" required />
                    <span class="sal-var-unit">%</span>
                </div>
            </div>
        </div>

        <div class="sal-section">
            <p class="sal-section-label">04 — Net salary</p>
            <div class="sal-formula">
                <div>Net = Gross + Bonus</div>
            </div>
        </div>

        <div class="settings-actions">
            <a class="btn" href="${pageContext.request.contextPath}/main/settings">&larr; Back to Settings</a>
            <button class="btn primary" type="submit">Save Settings</button>
        </div>
    </form>
</main>

<jsp:include page="../../common/footer.jsp" />
</body>
</html>
