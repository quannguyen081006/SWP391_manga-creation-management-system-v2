<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Create Proposal</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/proposal.css" />
</head>
<body>
<jsp:include page="../common/header.jsp" />

<main class="container narrow">
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
        <textarea name="synopsis" rows="8" required><c:out value="${synopsis}" /></textarea>

        <label>Sample File</label>
        <input type="file" name="sampleFile" id="sampleFile" required />

        <label>Approximate Chapter</label>
        <input type="number" name="approximateChapter" min="1" value="${approximateChapter}" required />

        <button type="submit" class="btn primary">Save Draft</button>
    </form>
</main>

<c:if test="${not empty duplicateFileError}">
    <div id="dupFileToast" class="dup-file-toast" role="alert" aria-live="assertive">
        <span class="dup-file-toast__icon">&#9888;</span>
        <div class="dup-file-toast__body">
            <span class="dup-file-toast__title">Duplicate file</span>
            <p class="dup-file-toast__message"><c:out value="${duplicateFileError}" /></p>
        </div>
        <button type="button" class="dup-file-toast__ok" onclick="dismissDupFileToast()">OK</button>
    </div>
    <script>
        function dismissDupFileToast() {
            var t = document.getElementById('dupFileToast');
            if (t) { t.parentNode.removeChild(t); }
            var input = document.getElementById('sampleFile');
            if (input) { input.focus(); }
        }
    </script>
</c:if>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
