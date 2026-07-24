// Manuscript Workspace JavaScript
// Handles annotation creation, resolution, dismissal, and display
console.log('=== NEW MANUSCRIPT JS VERSION ===');

const workspaceScript = document.currentScript;
window.isMangaka = workspaceScript && workspaceScript.getAttribute('data-is-mangaka') === 'true';
window.contextPath = workspaceScript ? workspaceScript.getAttribute('data-context-path') || '' : '';

let currentManuscriptVersionId = null;
let currentManuscriptPageId = null;

// Initialize workspace
document.addEventListener('DOMContentLoaded', function () {

    // Get manuscript version ID from URL
    const pathParts = window.location.pathname.split('/');

    const versionIdIndex =
            pathParts.indexOf('manuscript-workspace') + 1;

    if (
            versionIdIndex > 0 &&
            versionIdIndex < pathParts.length
            ) {

        currentManuscriptVersionId =
                Number(pathParts[versionIdIndex]);
    }

    console.log(
            'Current manuscript version ID:',
            currentManuscriptVersionId
            );

    // Add click handlers for page images
    if (!window.isMangaka) {
        document.querySelectorAll('.page-image').forEach(img => {

            img.addEventListener('click', function (event) {

                handlePageImageClick(event, img);

            });

        });

    }

    initializeWorkspacePage();
});

//tao link image
function imageUrl(fileUrl) {
    const url = String(fileUrl || '');
    if (url.indexOf('http://') === 0 || url.indexOf('https://') === 0 || url.indexOf(window.contextPath + '/') === 0) {
        return url;
    }
    return window.contextPath + url;
}

//can giua man hinh
function scrollToPage(pageId) {
    const pageElement = document.getElementById('page-' + pageId);
    if (pageElement) {
        pageElement.scrollIntoView({behavior: 'smooth', block: 'center'});
    }
}

function showRejectModal() {
    const modal = document.getElementById('rejectModal');
    if (modal)
        modal.classList.remove('is-hidden');
}

function hideRejectModal() {
    const modal = document.getElementById('rejectModal');
    if (modal)
        modal.classList.add('is-hidden');
}

function initializeWorkspacePage() {
    document.querySelectorAll('img.page-image[data-original-url]').forEach(function (image) {
        const originalUrl = image.getAttribute('data-original-url');
        if (originalUrl)
            image.src = imageUrl(originalUrl);
    });

    if (window.MangaUi)
        window.MangaUi.applyDynamicStyles(document);

    document.addEventListener('click', function (event) {
        const rejectOpen = event.target.closest ? event.target.closest('[data-open-reject-modal]') : null;
        if (rejectOpen) {
            showRejectModal();
            return;
        }
        const rejectClose = event.target.closest ? event.target.closest('[data-close-reject-modal]') : null;
        if (rejectClose) {
            hideRejectModal();
            return;
        }
        const annotationClose = event.target.closest ? event.target.closest('[data-close-annotation-modal]') : null;
        if (annotationClose) {
            hideAnnotationModal();
            return;
        }
        const deleteButton = event.target.closest ? event.target.closest('[data-delete-annotation]') : null;
        if (deleteButton) {
            event.stopPropagation();
            deleteAnnotation(deleteButton.getAttribute('data-delete-annotation'));
            return;
        }
        const focusTarget = event.target.closest ? event.target.closest('[data-annotation-focus]') : null;
        if (focusTarget) {
            focusAnnotation(
                    focusTarget.getAttribute('data-annotation-id'),
                    focusTarget.getAttribute('data-page-id'),
                    focusTarget.getAttribute('data-category'),
                    focusTarget.getAttribute('data-content'),
                    focusTarget.getAttribute('data-severity')
                    );
        }
    });
    // Intercept replace-page form submissions
    document.querySelectorAll('form[action*="/replace"]').forEach(function (form) {
        form.addEventListener('submit', async function (event) {
            event.preventDefault();

            const pageCard = form.closest('.page-card');
            if (!pageCard)
                return;

            const pageId = pageCard.id.replace('page-', '');
            const img = document.getElementById('img-' + pageId);

            const formData = new FormData(form);
            const url = form.getAttribute('action');

            const submitBtn = form.querySelector('button[type="submit"]');
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.textContent = 'Replacing...';
            }

            try {
                const response = await fetch(url, {
                    method: 'POST',
                    credentials: 'same-origin',
                    body: formData   // multipart/form-data sent automatically
                });

                const result = await response.json();

                if (result.success && result.data) {
                    const newUrl = imageUrl(result.data.snapshotFileUrl);
                    // Bust the browser cache so the new image is actually fetched
                    img.src = newUrl + '?t=' + Date.now();
                    img.setAttribute('data-original-url', result.data.snapshotFileUrl);

                    // Optionally update the checksum/display-order meta text
                    const meta = pageCard.querySelector('.page-info-meta');
                    if (meta && result.data.snapshotChecksum) {
                        meta.textContent =
                                'Display Order: ' + result.data.displayOrder +
                                ' | Checksum: ' + result.data.snapshotChecksum;
                    }

                    // Reset the file input so the user can replace again
                    form.reset();
                } else {
                    alert('Replace failed: ' + (result.message || 'Unknown error'));
                }
            } catch (err) {
                console.error('Replace page error:', err);
                alert('Error replacing page. Please try again.');
            } finally {
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Replace Page';
                }
            }
        });
    });
}

// Handle page image click
function handlePageImageClick(event, img) {

    if (window.isMangaka) {
        return;
    }
    const pageCard = img.closest('.page-card');

    if (!pageCard) {

        console.error('Page card not found');

        return;
    }

    const pageId =
            pageCard.id.replace('page-', '');

    currentManuscriptPageId = Number(pageId);

    console.log(
            'Current manuscript page ID:',
            currentManuscriptPageId
            );

    // Show annotation modal
    showAnnotationModal(event, img);
}

// Show annotation creation modal
function showAnnotationModal(event, img) {

    if (window.isMangaka) {
        return;
    }
    const rect = img.getBoundingClientRect();

    // Calculate click position as percentage
    const xPercent = ((event.clientX - rect.left) / rect.width) * 100;
    const yPercent = ((event.clientY - rect.top) / rect.height) * 100;


    // Validate coordinates
    if (isNaN(xPercent) || isNaN(yPercent)) {

        console.error('INVALID COORDINATES', {
            xPercent,
            yPercent,
            rect
        });

        alert('Failed to calculate annotation coordinates');

        return;
    }

    // Create modal if it doesn't exist
    let modal = document.getElementById('annotationModal');

    if (!modal) {

        modal = document.createElement('div');

        modal.id = 'annotationModal';

        modal.style.cssText =
                'display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 1000;';

        document.body.appendChild(modal);
    }

    modal.innerHTML = `
        <div style="
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            background: #fff;
            padding: 30px;
            border-radius: 8px;
            width: 500px;
            max-height: 80vh;
            overflow-y: auto;
        ">

            <h3>Add Annotation</h3>

            <form id="annotationForm">

                <div style="margin-bottom: 15px;">
                    <label style="
                        display: block;
                        margin-bottom: 5px;
                        font-weight: bold;
                    ">
                        Category:
                    </label>

                    <select
                        name="category"
                        style="
                            width: 100%;
                            padding: 8px;
                            border: 1px solid #ddd;
                            border-radius: 4px;
                        "
                        required
                    >
                        <option value="ART">Art</option>
                        <option value="STORY">Story</option>
                        <option value="PACING">Pacing</option>
                        <option value="DIALOGUE">Dialogue</option>
                        <option value="PANELING">Paneling</option>
                        <option value="TYPOGRAPHY">Typography</option>
                        <option value="OTHER">Other</option>
                    </select>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="
                        display: block;
                        margin-bottom: 5px;
                        font-weight: bold;
                    ">
                        Severity:
                    </label>

                    <select
                        name="severity"
                        style="
                            width: 100%;
                            padding: 8px;
                            border: 1px solid #ddd;
                            border-radius: 4px;
                        "
                    >
                        <option value="CRITICAL">Critical</option>
                        <option value="HIGH">High</option>
                        <option value="MEDIUM" selected>Medium</option>
                        <option value="LOW">Low</option>
                        <option value="SUGGESTION">Suggestion</option>
                    </select>
                </div>

                <div style="margin-bottom: 15px;">
                    <label style="
                        display: block;
                        margin-bottom: 5px;
                        font-weight: bold;
                    ">
                        Content:
                    </label>

                    <textarea
                        name="content"
                        rows="4"
                        style="
                            width: 100%;
                            padding: 8px;
                            border: 1px solid #ddd;
                            border-radius: 4px;
                        "
                        required
                        placeholder="Describe the issue or suggestion..."
                    ></textarea>
                </div>

                <div style="margin-bottom: 15px;">

                    <label style="
                        display: block;
                        margin-bottom: 5px;
                        font-weight: bold;
                    ">
                        Selection Size:
                    </label>

                    <div style="display: flex; gap: 10px;">

                        <div style="flex: 1;">
                            <label style="font-size: 12px;">
                                Width %
                            </label>

                            <input
                                type="number"
                                name="widthPercent"
                                value="10"
                                min="1"
                                max="100"
                                style="
                                    width: 100%;
                                    padding: 8px;
                                    border: 1px solid #ddd;
                                    border-radius: 4px;
                                "
                            >
                        </div>

                        <div style="flex: 1;">
                            <label style="font-size: 12px;">
                                Height %
                            </label>

                            <input
                                type="number"
                                name="heightPercent"
                                value="10"
                                min="1"
                                max="100"
                                style="
                                    width: 100%;
                                    padding: 8px;
                                    border: 1px solid #ddd;
                                    border-radius: 4px;
                                "
                            >
                        </div>

                    </div>
                </div>

                <div style="text-align: right;">

                    <button
                        type="button"
                        class="btn btn-secondary"
                        onclick="hideAnnotationModal()"
                    >
                        Cancel
                    </button>

                    <button
                        type="submit"
                        class="btn btn-primary"
                    >
                        Add Annotation
                    </button>

                </div>

            </form>
        </div>
    `;

    // Add form submit handler
    const form = modal.querySelector('#annotationForm');

    form.setAttribute('data-x-percent', xPercent.toFixed(2));
    form.setAttribute('data-y-percent', yPercent.toFixed(2));

    console.log('DATASET DEBUG');
    console.log(form.dataset);
    console.log(form.dataset.xPercent);
    console.log(form.dataset.yPercent);

    form.addEventListener('submit', handleAnnotationSubmit);

    modal.style.display = 'block';
}

// Hide annotation modal
function hideAnnotationModal() {
    const modal = document.getElementById('annotationModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

// Handle annotation form submission
async function handleAnnotationSubmit(event) {
    event.preventDefault();

    const form = event.target;
    const formData = new FormData(form);

    console.log('FORM DATASET');
    console.log(form.dataset);
    const request = {
        manuscriptVersionId: Number(currentManuscriptVersionId),
        manuscriptPageId: Number(currentManuscriptPageId),

        category: formData.get('category'),
        severity: formData.get('severity'),
        content: formData.get('content'),

        xPercent: Number(form.dataset.xPercent),
        yPercent: Number(form.dataset.yPercent),

        widthPercent: Number(formData.get('widthPercent') || 10),
        heightPercent: Number(formData.get('heightPercent') || 10),

        parentAnnotationId: null
    };

    console.log('Annotation Request JSON:');
    console.log(JSON.stringify(request, null, 2));

    try {
        const url = `${window.contextPath}/api/v1/annotations`;

        console.log('Annotation URL:', url);
        console.log('Annotation Request JSON:');
        console.log(JSON.stringify(request, null, 2));
        if (
                isNaN(request.xPercent) ||
                isNaN(request.yPercent) ||
                isNaN(request.widthPercent) ||
                isNaN(request.heightPercent)
                ) {

            console.error('INVALID COORDINATES', request);

            alert('Coordinate calculation failed');

            return;
        }
        console.log('FINAL REQUEST');
        console.log(request);
        console.log(JSON.stringify(request));

        const response = await fetch(url, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            const errorText = await response.text();
            console.error('Add annotation failed:', errorText);
            throw new Error(errorText);
        }

        const result = await response.json();

        if (result.success) {
            hideAnnotationModal();
            window.location.reload();
        } else {
            alert('Failed to add annotation: ' + result.message);
        }

    } catch (error) {
        console.error('Error adding annotation:', error);
        alert('Error adding annotation. Please try again.');
    }
}

// Delete annotation
async function deleteAnnotation(annotationId) {
    if (!confirm('Are you sure you want to delete this annotation? This action cannot be undone.')) {
        return;
    }

    try {
        const response = await fetch(`${window.contextPath}/api/v1/annotations/${annotationId}`, {
            method: 'DELETE',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const result = await response.json();

        if (result.success) {
            // Reload page to show updated annotation list
            window.location.reload();
        } else {
            alert('Failed to delete annotation: ' + result.message);
        }
    } catch (error) {
        console.error('Error deleting annotation:', error);
        alert('Error deleting annotation. Please try again.');
    }
}

function focusAnnotation(
        annotationId,
        pageId,
        category,
        content,
        severity
        ) {
    console.log("FOCUS FUNCTION LOADED");
    scrollToPage(pageId);

    setTimeout(function () {

        document
                .querySelectorAll('.annotation-marker')
                .forEach(marker => {
                    marker.classList.remove('active');
                });

        const marker =
                document.querySelector(
                        '[data-annotation-id="' +
                        annotationId +
                        '"]'
                        );

        console.log("ANNOTATION ID =", annotationId);
        console.log("MARKER =", marker);
        if (!marker) {

            console.error(
                    'Marker not found:',
                    annotationId
                    );

            return;
        }

        marker.classList.add('active');

        marker.scrollIntoView({
            behavior: 'smooth',
            block: 'center'
        });

        showAnnotationPopup(
                marker,
                category,
                content,
                severity
                );

    }, 300);
}
function showAnnotationPopup(
        marker,
        category,
        content,
        severity
        ) {

    const popup =
            document.getElementById(
                    'annotationPopup'
                    );

    const popupContent =
            document.getElementById(
                    'annotationPopupContent'
                    );

    popupContent.innerHTML = `
        <div style="
            font-weight:600;
            margin-bottom:8px;
        ">
            ${category}
        </div>

        <div>
            ${content}
        </div>

        <div style="
            margin-top:10px;
            color:#6b7280;
            font-size:12px;
        ">
            Severity:
            ${severity}
        </div>
    `;

    const rect =
            marker.getBoundingClientRect();

    popup.style.left =
            (rect.right + 20) + 'px';

    popup.style.top =
            rect.top + 'px';

    popup.classList.add('show');
}

document.addEventListener(
        'click',
        function (event) {
            const popup =
                    document.getElementById(
                            'annotationPopup'
                            );

            if (!popup) {
                return;
            }

            if (
                    !popup.contains(event.target) &&
                    !event.target.closest('.annotation-item')
                    ) {

                popup.classList.remove('show');
            }
        }
);

