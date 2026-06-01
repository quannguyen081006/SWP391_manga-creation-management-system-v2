<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Create Proposal</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<main class="container narrow">
    <h1>Create Proposal</h1>

    <c:if test="${not empty error}">
        <div class="alert error">${error}</div>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/main/proposals/create" enctype="multipart/form-data" class="form-grid">
        <label>Title</label>
        <input type="text" name="title" value="${title}" required />

        <label>Genre</label>
        <select name="genre" required>
            <option value="">Select genre</option>
            <c:forEach items="${genres}" var="g">
                <option value="${g}" ${g == genre ? 'selected' : ''}>${g}</option>
            </c:forEach>
        </select>

        <label>Synopsis</label>
        <textarea name="synopsis" rows="8" required>${synopsis}</textarea>

        <label>Sample File</label>
        <input type="file" name="sampleFile" required />

        <label>Approximate Chapter</label>
        <input type="number" name="approximateChapter" min="1" value="${approximateChapter}" required />

        <button type="submit" class="btn primary">Save Draft</button>
    </form>
</main>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
