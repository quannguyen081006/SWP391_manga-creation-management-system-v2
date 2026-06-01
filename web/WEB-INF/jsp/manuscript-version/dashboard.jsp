<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <title>Review Dashboard</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">
    <style>
        .dashboard-container {
            max-width: 1200px;
            margin: 40px auto;
            padding: 30px;
        }
        .dashboard-header {
            margin-bottom: 30px;
        }
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .stat-card {
            background: #fff;
            padding: 25px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            text-align: center;
        }
        .stat-value {
            font-size: 48px;
            font-weight: bold;
            color: #228be6;
            margin-bottom: 10px;
        }
        .stat-value.warning { color: #fa5252; }
        .stat-value.success { color: #40c057; }
        .stat-label {
            font-size: 14px;
            color: #666;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        .progress-section {
            background: #fff;
            padding: 25px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            margin-bottom: 30px;
        }
        .progress-bar {
            height: 30px;
            background: #e9ecef;
            border-radius: 15px;
            overflow: hidden;
            margin: 15px 0;
        }
        .progress-fill {
            height: 100%;
            background: linear-gradient(90deg, #228be6, #4dabf7);
            display: flex;
            align-items: center;
            justify-content: center;
            color: #fff;
            font-weight: bold;
            transition: width 0.3s;
        }
        .progress-fill.high { background: linear-gradient(90deg, #40c057, #69db7c); }
        .progress-fill.low { background: linear-gradient(90deg, #fa5252, #ff8787); }
        .annotations-section {
            background: #fff;
            padding: 25px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        .annotation-summary {
            display: flex;
            justify-content: space-around;
            margin-bottom: 20px;
        }
        .annotation-stat {
            text-align: center;
        }
        .annotation-stat-value {
            font-size: 32px;
            font-weight: bold;
        }
        .annotation-stat-label {
            font-size: 12px;
            color: #666;
            text-transform: uppercase;
        }
        .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
            text-decoration: none;
            display: inline-block;
        }
        .btn-primary { background: #228be6; color: #fff; }
        .btn-secondary { background: #868e96; color: #fff; }
    </style>
</head>
<body>
    <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>
    
    <div class="dashboard-container">
        <div class="dashboard-header">
            <h1>Review Dashboard</h1>
            <p style="color: #666;">Manuscript version review progress and statistics</p>
        </div>

        <c:if test="${dashboard == null || dashboard.totalPages == null || dashboard.totalPages == 0}">
            <div class="no-changes" style="text-align: center; padding: 60px; color: #666; background: #fff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                <h3>No review data available</h3>
                <p>The manuscript version has no pages or review data yet.</p>
            </div>
        </c:if>

        <c:if test="${dashboard != null && dashboard.totalPages != null && dashboard.totalPages > 0}">
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-value">${dashboard.totalPages}</div>
                <div class="stat-label">Total Pages</div>
            </div>
            <div class="stat-card">
                <div class="stat-value ${dashboard.openAnnotations > 0 ? 'warning' : 'success'}">${dashboard.openAnnotations}</div>
                <div class="stat-label">Open Annotations</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${dashboard.resolvedAnnotations}</div>
                <div class="stat-label">Resolved</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${dashboard.totalAnnotations}</div>
                <div class="stat-label">Total Annotations</div>
            </div>
        </div>

        <div class="progress-section">
            <h3>Review Progress</h3>
            <div class="progress-bar">
                <c:choose>
                    <c:when test="${dashboard.reviewProgress >= 80}">
                        <div class="progress-fill high" style="width: ${dashboard.reviewProgress}%">
                            ${dashboard.reviewProgress}%
                        </div>
                    </c:when>
                    <c:when test="${dashboard.reviewProgress < 50}">
                        <div class="progress-fill low" style="width: ${dashboard.reviewProgress}%">
                            ${dashboard.reviewProgress}%
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="progress-fill" style="width: ${dashboard.reviewProgress}%">
                            ${dashboard.reviewProgress}%
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
            <p style="color: #666; font-size: 14px;">
                <c:choose>
                    <c:when test="${dashboard.reviewProgress == 100}">
                        ✅ Review complete - all annotations resolved
                    </c:when>
                    <c:when test="${dashboard.reviewProgress >= 80}">
                        🟢 Nearly complete - ${dashboard.openAnnotations} annotations remaining
                    </c:when>
                    <c:when test="${dashboard.reviewProgress >= 50}">
                        🟡 In progress - ${dashboard.openAnnotations} annotations remaining
                    </c:when>
                    <c:otherwise>
                        🔴 Early stage - ${dashboard.openAnnotations} annotations remaining
                    </c:otherwise>
                </c:choose>
            </p>
        </div>

        <div class="annotations-section">
            <h3>Annotation Summary</h3>
            <div class="annotation-summary">
                <div class="annotation-stat">
                    <div class="annotation-stat-value" style="color: #ff6b6b;">${dashboard.openAnnotations}</div>
                    <div class="annotation-stat-label">Open</div>
                </div>
                <div class="annotation-stat">
                    <div class="annotation-stat-value" style="color: #51cf66;">${dashboard.resolvedAnnotations}</div>
                    <div class="annotation-stat-label">Resolved</div>
                </div>
                <div class="annotation-stat">
                    <div class="annotation-stat-value" style="color: #228be6;">${dashboard.totalAnnotations}</div>
                    <div class="annotation-stat-label">Total</div>
                </div>
            </div>
        </div>

        <div style="margin-top: 30px; text-align: center;">
            <a href="javascript:history.back()" class="btn btn-secondary">Back</a>
        </div>
        </c:if>
    </div>
</body>
</html>
