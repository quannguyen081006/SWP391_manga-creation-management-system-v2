<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>Ranking Periods</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ranking.css" />
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
        <%--
            All shared look-and-feel (period card, status badge, countdown,
            timeline, upload zone, CSV preview modal...) lives in ranking.css
            so this page stays in sync with results.jsp and mangaka-ranking.jsp
            instead of drifting with its own copy. The countdown ticking logic
            itself is shared too, in ranking.js.
        --%>
        <script src="${pageContext.request.contextPath}/assets/js/ranking.js"></script>
        <script>
            // CSV Preview functionality: page-specific, so it stays inline here
            // rather than in the shared ranking.js.
            function handleFileSelect(input) {
                var file = input.files[0];
                if (file) {
                    var filenameDisplay = document.getElementById('csv-filename-' + input.dataset.periodId);
                    if (filenameDisplay) {
                        filenameDisplay.innerHTML = '📄 ' + file.name;
                        filenameDisplay.style.display = 'inline-block';
                        filenameDisplay.style.cursor = 'pointer';
                        filenameDisplay.onclick = function() {
                            previewCsv(file);
                        };
                    }
                }
            }

            function previewCsv(file) {
                var reader = new FileReader();
                reader.onload = function(e) {
                    var content = e.target.result;
                    var modal = document.getElementById('csv-preview-modal');
                    var modalContent = document.getElementById('csv-preview-content');
                    modalContent.textContent = content;
                    modal.style.display = 'flex';
                };
                reader.readAsText(file);
            }

            function closeCsvPreview() {
                var modal = document.getElementById('csv-preview-modal');
                modal.style.display = 'none';
            }
        </script>
    </head>
    <body>
        <jsp:include page="../common/header.jsp" />

        <c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>
        <c:if test="${not empty success}"><div class="alert success">${success}</div></c:if>

        <c:if test="${sessionScope.AUTH_USER.hasRole('ADMIN')}">
            <div class="section-card">
                <h3 class="section-title section-title-sm">➕ Create New Period (Admin)</h3>
                <form method="post" action="${pageContext.request.contextPath}/main/ranking/periods/create" class="period-create-form">
                    <div><label>Period Name</label><input type="text" name="name" required placeholder="e.g., June 2026" /></div>
                    <div><label>End Date</label><input type="date" name="endDate" required /></div>
                    <div><button class="btn primary" type="submit">Create Period</button></div>
                </form>
            </div>
        </c:if>

        <div class="section-card">
            <h3 class="section-title section-title-sm">📅 Ranking Periods</h3>

            <c:forEach items="${periods}" var="p">
                <div class="period-card status-${p.status}">
                    <div class="period-header">
                        <div>
                            <div class="period-title">${p.name}</div>
                            <div class="period-meta">
                                <span>📅 ${p.startDate} → ${p.endDate}</span>
                                <span class="status-badge ${p.status}">${p.status}</span>
                            </div>
                            <c:if test="${p.status == 'OPEN'}">
                                <div class="countdown-display" data-end-date="${p.endDate}T23:59:59">Loading...</div>
                                <div class="period-note">⏰ Time remaining until deadline</div>
                            </c:if>
                        </div>
                    </div>

                    <div class="timeline">
                        <span class="timeline-step ${p.status == 'UPCOMING' || p.status == 'OPEN' || p.status == 'CLOSED' || p.status == 'CALCULATING' || p.status == 'CALCULATED' ? 'completed' : ''}">UPCOMING</span>
                        <span class="timeline-arrow">→</span>
                        <span class="timeline-step ${p.status == 'OPEN' || p.status == 'CLOSED' || p.status == 'CALCULATING' || p.status == 'CALCULATED' ? 'completed' : ''} ${p.status == 'OPEN' ? 'active' : ''}">OPEN</span>
                        <span class="timeline-arrow">→</span>
                        <span class="timeline-step ${p.status == 'CLOSED' || p.status == 'CALCULATING' || p.status == 'CALCULATED' ? 'completed' : ''} ${p.status == 'CLOSED' ? 'active' : ''}">CLOSED</span>
                        <span class="timeline-arrow">→</span>
                        <span class="timeline-step ${p.status == 'CALCULATING' || p.status == 'CALCULATED' ? 'completed' : ''} ${p.status == 'CALCULATING' ? 'active' : ''}">CALCULATING</span>
                        <span class="timeline-arrow">→</span>
                        <span class="timeline-step ${p.status == 'CALCULATED' ? 'completed' : ''} ${p.status == 'CALCULATED' ? 'active' : ''}">CALCULATED</span>
                    </div>

                    <div class="period-actions">
                        <a class="btn small" href="${pageContext.request.contextPath}/main/ranking/periods/${p.id}/results"><i class="bi bi-bar-chart-fill"></i> Series Ranking</a>
                        <a class="btn small" href="${pageContext.request.contextPath}/main/ranking/periods/${p.id}/mangaka"><i class="bi bi-trophy-fill"></i> Mangaka Ranking</a>

                        <c:if test="${p.status == 'OPEN'}">
                            <c:if test="${sessionScope.AUTH_USER.hasRole('EDITORIAL_BOARD')}">
                                <c:choose>
                                    <c:when test="${submittedRankingPeriodIds.contains(p.id)}">
                                        <span class="vote-submitted-badge">✓ Vote entry submitted</span>
                                    </c:when>
                                    <c:otherwise>
                                        <form method="post" action="${pageContext.request.contextPath}/main/ranking/periods/${p.id}/upload" enctype="multipart/form-data" class="period-upload-form">
                                            <div class="upload-zone">
                                                <label class="upload-label">
                                                    📤 Upload CSV
                                                    <input type="file" name="csvFile" accept=".csv" required data-period-id="${p.id}" onchange="handleFileSelect(this)" />
                                                </label>
                                                <span id="csv-filename-${p.id}" class="csv-filename"></span>
                                            </div>
                                            <button class="btn small" type="submit">Submit</button>
                                        </form>
                                    </c:otherwise>
                                </c:choose>
                            </c:if>
                            <c:if test="${sessionScope.AUTH_USER.hasRole('ADMIN')}">
                                <form method="post" action="${pageContext.request.contextPath}/main/ranking/periods/${p.id}/close" class="inline-form">
                                    <button class="btn small danger-soft" type="submit">🔒 Close Period</button>
                                </form>
                                <a class="btn small" href="${pageContext.request.contextPath}/main/ranking/periods/${p.id}/csv-uploads">📄 View CSV Uploads</a>
                            </c:if>
                        </c:if>
                    </div>

                </div>
            </c:forEach>

            <c:if test="${empty periods}">
                <div class="empty-state">
                    <div class="empty-state-icon">📊</div>
                    <div class="empty-state-title">No ranking periods yet</div>
                    <div class="empty-state-copy">Create a period to start the monthly ranking cycle</div>
                </div>
            </c:if>
        </div>

        <!-- CSV Preview Modal -->
        <div id="csv-preview-modal" class="csv-preview-modal">
            <button class="csv-preview-close" onclick="closeCsvPreview()">Close</button>
            <div class="csv-preview-content" id="csv-preview-content"></div>
        </div>

        <jsp:include page="../common/footer.jsp" />
    </body>
</html>
