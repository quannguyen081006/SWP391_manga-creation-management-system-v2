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

<c:if test="${not empty error}"><div class="alert error"><c:out value="${error}" /></div></c:if>

<div class="section-card">
    <form class="form-grid" method="post" action="${pageContext.request.contextPath}/main/proposals/${proposal.id}/edit" enctype="multipart/form-data">
        <label>Title</label>
        <input type="text" name="title" value="<c:out value='${proposal.title}' />" required ${lockIdentityFields ? 'readonly' : ''} />

        <label>Genre</label>
        <c:if test="${lockIdentityFields}">
            <input type="hidden" name="genre" value="<c:out value='${proposal.genre}' />" />
        </c:if>
        <select name="genre" required ${lockIdentityFields ? 'disabled' : ''}>
            <c:forEach items="${genres}" var="g">
                <option value="<c:out value='${g}' />" ${g == proposal.genre ? 'selected' : ''}><c:out value="${g}" /></option>
            </c:forEach>
        </select>

        <label>Synopsis</label>
        <textarea name="synopsis" rows="8" required><c:out value="${proposal.synopsis}" /></textarea>

        <label>Sample File</label>
        <input type="file" name="sampleFile" id="sampleFile" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" />
        <p class="form-note">Leave empty to keep the current file. New file must be PDF (.pdf) or Word (.docx), up to 20 MB, and must not be identical to a file already submitted on another proposal.</p>
        <c:if test="${not empty proposal.originalFileName}">
            <p class="form-note">Current file: <a href="${pageContext.request.contextPath}/main/proposals/${proposal.id}/file"><c:out value="${proposal.originalFileName}" /></a></p>
        </c:if>

        <label>Approximate Chapter</label>
        <input type="number" name="approximateChapter" min="1" value="${proposal.approximateChapter}" required />

        <div class="proposal-form-actions">
            <button class="btn primary" type="submit">Save Draft</button>
            <a class="btn" href="${pageContext.request.contextPath}/main/proposals/${proposal.id}">Cancel</a>
        </div>
    </form>
</div>

<%-- Same toast for both sample-file rejections: identical content, or wrong format. --%>
<c:set var="fileToastMessage" value="${not empty duplicateFileError ? duplicateFileError : fileTypeError}" />
<c:if test="${not empty fileToastMessage}">
    <div id="dupFileToast" class="dup-file-toast" role="alert" aria-live="assertive">
        <span class="dup-file-toast__icon">&#9888;</span>
        <div class="dup-file-toast__body">
            <span class="dup-file-toast__title">${not empty duplicateFileError ? 'Duplicate file' : 'Invalid file type'}</span>
            <p class="dup-file-toast__message"><c:out value="${fileToastMessage}" /></p>
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

<script src="${pageContext.request.contextPath}/assets/js/proposal-sample-file.js"></script>

<jsp:include page="../common/footer.jsp" />
</body>
</html>
