<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Edit Proposal</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/proposal.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<c:if test="${not empty error}"><div class="alert error">${error}</div></c:if>

<div class="section-card">
    <form class="form-grid" method="post" action="${pageContext.request.contextPath}/main/proposals/${proposal.id}/edit" enctype="multipart/form-data">
        <label>Title</label>
        <input type="text" name="title" value="${proposal.title}" required />

        <label>Genre</label>
        <select name="genre" required>
            <c:forEach items="${genres}" var="g">
                <option value="${g}" ${g == proposal.genre ? 'selected' : ''}>${g}</option>
            </c:forEach>
        </select>

        <label>Synopsis</label>
        <textarea name="synopsis" rows="8" required>${proposal.synopsis}</textarea>

        <label>Sample File</label>
        <input type="file" name="sampleFile" />
        <c:if test="${not empty proposal.originalFileName}">
            <p class="form-note">Current file: <a href="${pageContext.request.contextPath}/main/proposals/${proposal.id}/file">${proposal.originalFileName}</a></p>
        </c:if>

        <label>Approximate Chapter</label>
        <input type="number" name="approximateChapter" min="1" value="${proposal.approximateChapter}" required />

        <div class="proposal-form-actions">
            <button class="btn primary" type="submit">Save Draft</button>
            <a class="btn" href="${pageContext.request.contextPath}/main/proposals/${proposal.id}">Cancel</a>
        </div>
    </form>
</div>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
