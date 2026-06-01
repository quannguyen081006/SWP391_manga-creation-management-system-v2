<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Ranking Periods</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <style>
        .dashboard-hero {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px;
            border-radius: 12px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(102, 126, 234, 0.3);
        }

        .dashboard-hero h1 {
            margin: 0 0 10px 0;
            font-size: 36px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 2px;
        }

        .dashboard-hero .subtitle {
            font-size: 18px;
            opacity: 0.9;
            margin-bottom: 20px;
        }

        .status-badge {
            display: inline-block;
            padding: 8px 20px;
            background: rgba(255, 255, 255, 0.2);
            border-radius: 20px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-right: 10px;
        }

        .status-badge.OPEN {
            background: rgba(46, 204, 113, 0.3);
            border: 2px solid #27ae60;
        }

        .status-badge.CLOSED {
            background: rgba(231, 76, 60, 0.3);
            border: 2px solid #c0392b;
        }

        .status-badge.CALCULATED {
            background: rgba(52, 152, 219, 0.3);
            border: 2px solid #2980b9;
        }

        .status-badge.CALCULATING {
            background: rgba(241, 196, 15, 0.3);
            border: 2px solid #f39c12;
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.7; }
        }

        .countdown-display {
            font-size: 28px;
            font-weight: 700;
            margin-top: 15px;
        }

        .countdown-display.urgent {
            color: #e74c3c;
            animation: urgent-pulse 1s infinite;
        }

        @keyframes urgent-pulse {
            0%, 100% { transform: scale(1); }
            50% { transform: scale(1.05); }
        }

        .period-card {
            background: white;
            border-radius: 12px;
            padding: 24px;
            margin-bottom: 20px;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
            transition: transform 0.2s, box-shadow 0.2s;
            border-left: 4px solid #667eea;
        }

        .period-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
        }

        .period-card.status-OPEN {
            border-left-color: #27ae60;
        }

        .period-card.status-CLOSED {
            border-left-color: #c0392b;
        }

        .period-card.status-CALCULATED {
            border-left-color: #2980b9;
        }

        .period-card.status-CALCULATING {
            border-left-color: #f39c12;
        }

        .period-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
        }

        .period-title {
            font-size: 20px;
            font-weight: 600;
            color: #2c3e50;
        }

        .period-meta {
            display: flex;
            gap: 20px;
            color: #7f8c8d;
            font-size: 14px;
            margin-bottom: 16px;
        }

        .period-actions {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }

        .upload-zone {
            border: 2px dashed #bdc3c7;
            border-radius: 8px;
            padding: 20px;
            text-align: center;
            margin-top: 16px;
            transition: border-color 0.2s, background-color 0.2s;
        }

        .upload-zone:hover {
            border-color: #667eea;
            background-color: #f8f9fa;
        }

        .upload-zone.dragover {
            border-color: #667eea;
            background-color: #e8f4fd;
        }

        .upload-zone input[type="file"] {
            display: none;
        }

        .upload-label {
            cursor: pointer;
            color: #667eea;
            font-weight: 600;
        }

        .timeline {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-top: 12px;
            font-size: 12px;
        }

        .timeline-step {
            padding: 4px 12px;
            border-radius: 12px;
            background: #ecf0f1;
            color: #7f8c8d;
            font-weight: 600;
        }

        .timeline-step.active {
            background: #667eea;
            color: white;
        }

        .timeline-step.completed {
            background: #27ae60;
            color: white;
        }

        .timeline-arrow {
            color: #bdc3c7;
        }
    </style>
    <script>
        function updateCountdowns() {
            var countdownElements = document.querySelectorAll('[data-end-date]');
            countdownElements.forEach(function(el) {
                var endDate = new Date(el.getAttribute('data-end-date'));
                var now = new Date();
                var diff = endDate - now;

                if (diff > 0) {
                    var days = Math.floor(diff / (1000 * 60 * 60 * 24));
                    var hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
                    var minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
                    var seconds = Math.floor((diff % (1000 * 60)) / 1000);

                    var timeStr = days + 'd ' + hours + 'h ' + minutes + 'm ' + seconds + 's';
                    el.textContent = timeStr;

                    // Warning for < 24 hours
                    if (days < 1) {
                        el.classList.add('urgent');
                    } else {
                        el.classList.remove('urgent');
                    }
                } else {
                    el.textContent = 'Expired';
                    el.style.color = '#e74c3c';
                }
            });
        }

        // Update countdowns every second
        setInterval(updateCountdowns, 1000);
        updateCountdowns();
    </script>
</head>
<body>
<jsp:include page="../common/header.jsp" />

<div class="dashboard-hero">
    <h1>📊 Monthly Ranking Dashboard</h1>
    <div class="subtitle">Manage ranking cycles, monitor performance, and drive editorial decisions</div>
</div>

<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>
<c:if test="${not empty success}"><div class="alert success">${success}</div></c:if>

<c:if test="${sessionScope.AUTH_USER.hasRole('ADMIN')}">
<div class="section-card">
    <h3 class="section-title">➕ Create New Period (Admin)</h3>
    <form method="post" action="${pageContext.request.contextPath}/main/ranking/periods/create" style="display:grid;grid-template-columns:2fr 1fr auto;gap:10px;align-items:end;">
        <div><label>Period Name</label><input type="text" name="name" required placeholder="e.g., June 2026" /></div>
        <div><label>End Date</label><input type="date" name="endDate" required /></div>
        <div><button class="btn primary" type="submit">Create Period</button></div>
    </form>
</div>
</c:if>

<div class="section-card">
    <h3 class="section-title" style="font-size: 24px; margin-bottom: 24px;">📅 Ranking Periods</h3>
    
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
                        <div style="font-size: 14px; color: #7f8c8d; margin-top: 8px;">
                            ⏰ Time remaining until deadline
                        </div>
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
            
            <div class="period-actions" style="margin-top: 20px;">
                <a class="btn small" href="${pageContext.request.contextPath}/main/ranking/periods/${p.id}/results">📊 Series Ranking</a>
                <a class="btn small" href="${pageContext.request.contextPath}/main/ranking/periods/${p.id}/mangaka">👑 Mangaka Ranking</a>
                
                <c:if test="${p.status == 'OPEN'}">
                    <c:if test="${sessionScope.AUTH_USER.hasRole('EDITORIAL_BOARD')}">
                        <form method="post" action="${pageContext.request.contextPath}/main/ranking/periods/${p.id}/upload" enctype="multipart/form-data" style="display:inline-block;">
                            <div class="upload-zone">
                                <label class="upload-label">
                                    📤 Upload CSV
                                    <input type="file" name="csvFile" accept=".csv" required />
                                </label>
                            </div>
                            <button class="btn small" type="submit" style="margin-left: 10px;">Submit</button>
                        </form>
                    </c:if>
                    <c:if test="${sessionScope.AUTH_USER.hasRole('ADMIN')}">
                        <form method="post" action="${pageContext.request.contextPath}/main/ranking/periods/${p.id}/close" style="display:inline-block;">
                            <button class="btn small" type="submit" style="background: #e74c3c; color: white;">🔒 Close Period</button>
                        </form>
                    </c:if>
                </c:if>
            </div>
            
        </div>
    </c:forEach>
    
    <c:if test="${empty periods}">
        <div style="text-align: center; padding: 60px; color: #95a5a6;">
            <div style="font-size: 48px; margin-bottom: 16px;">📊</div>
            <div style="font-size: 18px;">No ranking periods yet</div>
            <div style="font-size: 14px;">Create a period to start the monthly ranking cycle</div>
        </div>
    </c:if>
</div>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
