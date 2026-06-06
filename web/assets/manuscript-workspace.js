// Manuscript Workspace JavaScript
// Handles annotation creation, resolution, dismissal, and display
console.log('=== NEW MANUSCRIPT JS VERSION ===');

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
    document.querySelectorAll('.page-image').forEach(img => {

        img.addEventListener('click', function (event) {

            handlePageImageClick(event, img);

        });

    });

});

// Handle page image click
function handlePageImageClick(event, img) {

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

    const rect = img.getBoundingClientRect();

    // Calculate click position as percentage
    const xPercent = ((event.clientX - rect.left) / rect.width) * 100;
    const yPercent = ((event.clientY - rect.top) / rect.height) * 100;

    console.log('Coordinate Debug', {
        rectWidth: rect.width,
        rectHeight: rect.height,
        clientX: event.clientX,
        clientY: event.clientY,
        xPercent,
        yPercent
    });

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

// Resolve annotation
async function resolveAnnotation(annotationId) {
    if (!confirm('Are you sure you want to resolve this annotation?')) {
        return;
    }

    try {
        const response = await fetch(`${window.contextPath}/api/v1/annotations/${annotationId}/resolve`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const result = await response.json();

        if (result.success) {
            // Reload page to show updated annotation
            window.location.reload();
        } else {
            alert('Failed to resolve annotation: ' + result.message);
        }
    } catch (error) {
        console.error('Error resolving annotation:', error);
        alert('Error resolving annotation. Please try again.');
    }
}

// Dismiss annotation
async function dismissAnnotation(annotationId) {
    if (!confirm('Are you sure you want to dismiss this annotation?')) {
        return;
    }

    try {
        const response = await fetch(`${window.contextPath}/api/v1/annotations/${annotationId}/dismiss`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const result = await response.json();

        if (result.success) {
            // Reload page to show updated annotation
            window.location.reload();
        } else {
            alert('Failed to dismiss annotation: ' + result.message);
        }
    } catch (error) {
        console.error('Error dismissing annotation:', error);
        alert('Error dismissing annotation. Please try again.');
    }
}

// Reopen annotation (for resolved/dismissed annotations)
async function reopenAnnotation(annotationId) {
    if (!confirm('Are you sure you want to reopen this annotation?')) {
        return;
    }

    // This would require a reopen endpoint in the API
    // For now, alert the user
    alert('Reopen annotation feature not yet implemented');
}

// Add reply to annotation
async function addReply(annotationId) {
    const replyContent = prompt('Enter your reply:');
    if (!replyContent || replyContent.trim() === '') {
        return;
    }

    try {
        const response = await fetch(`${window.contextPath}/api/v1/annotations/${annotationId}/replies`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                content: replyContent
            })
        });

        const result = await response.json();

        if (result.success) {
            // Reload page to show new reply
            window.location.reload();
        } else {
            alert('Failed to add reply: ' + result.message);
        }
    } catch (error) {
        console.error('Error adding reply:', error);
        alert('Error adding reply. Please try again.');
    }
    function focusAnnotation(
            annotationId,
            content,
            category,
            severity
            ) {

        document
                .querySelectorAll('.annotation-marker')
                .forEach(marker => {
                    marker.classList.remove('active');
                });

        const marker = document.querySelector(
                '[data-annotation-id="' +
                annotationId +
                '"]'
                );

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

        const popup =
                document.getElementById(
                        'annotationPopup'
                        );

        const popupContent =
                document.getElementById(
                        'annotationPopupContent'
                        );

        popupContent.innerHTML = `
        <div style="font-weight:bold">
            ${category}
        </div>

        <div style="margin-top:8px">
            ${content}
        </div>

        <div style="
            margin-top:8px;
            color:#6b7280;
            font-size:12px;
        ">
            Severity: ${severity}
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
}
