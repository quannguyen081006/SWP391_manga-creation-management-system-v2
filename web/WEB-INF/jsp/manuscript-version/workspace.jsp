<%@ page contentType="text/html;charset=UTF-8" language="java" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<!DOCTYPE html>

<html>

    <head>

        <title>Manuscript Workspace - Chapter ${chapter.chapterNumber}</title>

        <!-- Bootstrap 5 CDN -->
        <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">

        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css">

        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/manuscript-version.css" />

        <!-- Toolbar & Sidebar Modern Styling -->
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/workspace-ui.css" />

        <script src="${pageContext.request.contextPath}/assets/manuscript-workspace.js"

                data-is-mangaka="${isMangakaOwner}"

        data-context-path="${pageContext.request.contextPath}"></script>

    </head>

    <body>

        <jsp:include page="/WEB-INF/jsp/common/header.jsp"/>



        <div class="workspace-container">

            <!-- Left Sidebar: Manuscript Info & Version History -->

            <div class="workspace-sidebar">

                <div class="sidebar-section">

                    <div class="sidebar-title">Manuscript Info</div>

                    <div class="dashboard-stat">

                        <span class="stat-label">Chapter</span>

                        <span class="stat-value">${chapter.chapterNumber}</span>

                    </div>

                    <div class="dashboard-stat">

                        <span class="stat-label">Version</span>

                        <span class="stat-value">v${version.version}</span>

                    </div>

                    <div class="dashboard-stat">

                        <span class="stat-label">Status</span>

                        <span class="status-badge status-${fn:toLowerCase(version.status)}">${version.status}</span>

                    </div>

                    <div class="dashboard-stat">

                        <span class="stat-label">Pages</span>

                        <span class="stat-value">${version.totalPageCount}</span>

                    </div>

                    <div class="dashboard-stat">

                        <span class="stat-label">Created</span>

                        <span class="stat-value stat-value-compact">${createdAtFormatted}</span>

                    </div>

                    <c:if test="${not empty submittedAtFormatted}">

                        <div class="dashboard-stat">

                            <span class="stat-label">Submitted</span>

                            <span class="stat-value stat-value-compact">${submittedAtFormatted}</span>

                        </div>

                    </c:if>

                </div>



                <div class="sidebar-section">

                    <div class="sidebar-title">Version History</div>

                    <%-- Server-rendered version chain; links use stored version history rows. --%>
                    <c:forEach var="v" items="${versionHistory}">

                        <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${v.id}" class="version-item ${v.id == version.id ? 'current' : ''}">

                            <div><strong>v${v.version}</strong> - ${v.status}</div>

                            <div class="version-date">

                                ${versionHistoryDates[v.id]}

                            </div>

                        </a>

                    </c:forEach>

                </div>

            </div>



            <!-- Main Content -->

            <div class="workspace-main">

                <div class="workspace-toolbar">

                    <div>

                        <strong>Chapter <c:out value="${chapter.chapterNumber}" />: <c:out value="${chapter.title}" /></strong>

                        <span class="workspace-version-meta">

                            v${version.version} - <span class="status-badge status-${version.status}">${version.status}</span>

                        </span>

                        <c:if test="${isReadonly}">

                            <span class="workspace-lock">

                                🔒 Readonly

                            </span>

                        </c:if>

                        <c:if test="${productionLocked}">

                            <span class="workspace-lock is-production">

                                🔒 Production Locked

                            </span>

                        </c:if>

                    </div>

                    <div class="workspace-summary">

                        Pages: ${version.totalPageCount} | 

                        Open Annotations: <span class="${dashboard.openAnnotations > 0 ? 'text-danger' : 'text-success'}">${dashboard.openAnnotations}</span> |

                        Progress: ${dashboard.reviewProgress}%

                    </div>

                    <div>

                        <c:if test="${!isReadonly && version.status == 'DRAFT' && version.version == 1 && empty pages}">

                            <form class="inline-form" method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/import-pages">

                                <button type="submit" class="btn btn-primary">Import Initial Pages Chapter</button>

                            </form>

                        </c:if>

                        <c:if test="${!isReadonly && version.status == 'DRAFT' && not empty pages && isMangakaOwner }">

                            <form class="inline-form" method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/submit">

                                <button type="submit" class="btn btn-primary">Submit for Review</button>

                            </form>

                        </c:if>

                        <c:if test="${!isReadonly && version.status == 'UNDER_REVIEW' && (isAssignedTantou || isAdmin)}">

                            <form class="inline-form" method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/approve">

                                <button type="submit" class="btn btn-success" ${dashboard.openAnnotations > 0 ? 'disabled' : ''}>Approve</button>

                            </form>

                            <button type="button" class="btn btn-danger" data-open-reject-modal>Reject</button>

                        </c:if>

                        <c:if test="${!isReadonly && version.status == 'REJECTED' && isMangakaOwner}">

                            <form class="inline-form" method="post" action="${pageContext.request.contextPath}/main/chapters/${chapter.id}/manuscript-workspace/new-version">

                                <button type="submit" class="btn btn-primary">Create New Version</button>

                            </form>

                        </c:if>

                        <c:if test="${!isReadonly && version.status == 'APPROVED'}">

                            <form class="inline-form" method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/publish">

                                <button type="submit" class="btn btn-success">Publish</button>

                            </form>

                        </c:if>

                        <a href="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/dashboard" class="btn btn-secondary">Dashboard</a>

                        <c:choose>
                            <c:when test="${isMangakaOwner}">
                                <a href="${pageContext.request.contextPath}/main/chapters/${chapter.id}" class="btn btn-secondary">Back to Chapter</a>
                            </c:when>
                            <c:otherwise>
                                <a href="${pageContext.request.contextPath}/main/manuscript-review" class="btn btn-secondary">Back to Manuscript Review</a>
                            </c:otherwise>
                        </c:choose>
                    </div>

                </div>



                <c:if test="${error != null}">

                    <div class="error-message"><c:out value="${error}" /></div>

                </c:if>



                <div class="workspace-pages">

                    <c:if test="${empty pages}">

                        <div class="empty-state">

                            <h3>No pages imported yet</h3>

                            <p>Import chapter pages to begin the manuscript workspace.</p>

                        </div>

                    </c:if>

                    <c:forEach var="page" items="${pages}">

                        <div class="page-card" id="page-${page.id}">

                            <div class="page-image-container">

                                <div class="image-wrapper">

                                    <img data-original-url="${page.snapshotFileUrl}" alt="Page ${page.pageNumber}" class="page-image" id="img-${page.id}">

                                    <c:forEach var="ann" items="${annotations}">

                                        <c:if test="${ann.manuscriptPageId == page.id}">

                                            <button type="button"

                                                    class="annotation-marker ${ann.status == 'RESOLVED' ? 'resolved' : ann.status == 'DISMISSED' ? 'dismissed' : ''}"

                                                    data-position-left="${ann.getXPercent()}"

                                                    data-position-top="${ann.getYPercent()}"

                                                    data-position-width="${ann.getWidthPercent()}"

                                                    data-position-height="${ann.getHeightPercent()}"

                                                    data-annotation-focus

                                                    data-annotation-id="${ann.id}"

                                                    data-page-id="${ann.manuscriptPageId}"

                                                    data-category="${ann.category}"

                                                    data-content="<c:out value='${ann.content}' />"

                                                    data-severity="${ann.severity}"

                                                    title="${ann.category}: <c:out value='${ann.content}' />"></button>

                                        </c:if>

                                    </c:forEach>

                                </div>

                            </div>

                            <div class="page-info">

                                <div class="page-info-title">Page ${page.pageNumber}</div>

                                <div class="page-info-meta">

                                    Display Order: ${page.displayOrder} | Checksum: ${page.snapshotChecksum}

                                </div>

                                <c:if test="${!isReadonly && isMangakaOwner && version.status == 'DRAFT'}">

                                    <form

                                        method="post"

                                        enctype="multipart/form-data"

                                        action="${pageContext.request.contextPath}/api/v1/manuscript-versions/pages/${page.id}/replace">



                                        <input

                                            type="file"

                                            name="image"

                                            accept="image/*"

                                            required>



                                        <button

                                            type="submit"

                                            class="btn btn-secondary">



                                            Replace Page

                                        </button>



                                    </form>

                                </c:if>

                            </div>

                        </div>

                    </c:forEach>

                </div>

            </div>



            <!-- Right Sidebar: Feedback & Annotations -->

            <div class="workspace-right-sidebar">

                <c:if test="${not empty version.feedback}">

                    <div class="sidebar-section">

                        <div class="sidebar-title">Version Feedback</div>

                        <div class="feedback-panel">

                            <div class="feedback-title">Feedback for v${version.version}</div>

                            <div class="feedback-content"><c:out value="${version.feedback}" /></div>

                        </div>

                    </div>

                </c:if>



                <div class="sidebar-section">

                    <div class="sidebar-title">Annotations</div>

                    <div class="annotation-list">

                        <c:if test="${empty annotations}">

                            <div class="feedback-empty">

                                No annotations yet

                            </div>

                        </c:if>

                        <c:forEach var="annotation" items="${annotations}">

                            <div

                                class="annotation-item ${annotation.status == 'RESOLVED' ? 'resolved' : annotation.status == 'DISMISSED' ? 'dismissed' : ''}"

                                data-annotation-focus

                                data-annotation-id="${annotation.id}"

                                data-page-id="${annotation.manuscriptPageId}"

                                data-category="<c:out value='${annotation.category}' />"

                                data-content="<c:out value='${annotation.content}' />"

                                data-severity="${annotation.severity}"

                                >



                                <div><c:out value="${annotation.category}" /></div>

                                <div><c:out value="${annotation.content}" /></div>



                                <div>

                                    Page <c:out value="${annotation.pageNumber}" />

                                    -

                                    <c:out value="${annotation.status}" />

                                </div>



                                <c:if test="${version.status == 'UNDER_REVIEW' && (isAssignedTantou || isAdmin) && !isMangakaRole}">

                                    <div class="annotation-item-actions">

                                        <button

                                            type="button"

                                            class="btn btn-danger annotation-delete-btn"

                                            data-delete-annotation="${annotation.id}"

                                            >

                                            Delete

                                        </button>

                                    </div>

                                </c:if>



                            </div>

                        </c:forEach>

                    </div>

                </div>

            </div>

        </div>



        <!-- Reject Modal -->

        <div id="rejectModal" class="modal-backdrop is-hidden">

            <div class="modal-card">

                <h3 class="modal-title">Reject Manuscript</h3>

                <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/reject">

                    <div class="modal-field">

                        <label>Feedback (required):</label>

                        <textarea name="feedback" rows="5" required placeholder="Please provide feedback for rejection..."></textarea>

                    </div>

                    <div class="modal-actions">

                        <button type="button" class="btn btn-secondary" data-close-reject-modal>Cancel</button>

                        <button type="submit" class="btn btn-danger">Reject Manuscript</button>

                    </div>

                </form>

            </div>

        </div>

        <div id="annotationPopup" class="annotation-popup">



            <div id="annotationPopupContent"></div>



        </div>

    </body>

</html>

