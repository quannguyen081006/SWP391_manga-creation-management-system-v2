// Manuscript Workspace JavaScript
// Handles annotation creation, resolution, dismissal, and display

let currentManuscriptVersionId = null;
let currentManuscriptPageId = null;

// Initialize workspace
document.addEventListener('DOMContentLoaded', function() {
    // Get manuscript version ID from URL
    const pathParts = window.location.pathname.split('/');
    const versionIdIndex = pathParts.indexOf('manuscript-workspace') + 1;
    if (versionIdIndex > 0 && versionIdIndex < pathParts.length) {
        currentManuscriptVersionId = parseInt(pathParts[versionIdIndex]);
    }
    
    // Add click handlers for page images to enable annotation creation
    document.querySelectorAll('.page-image').forEach(img => {
        img.addEventListener('click', handlePageImageClick);
    });
});

// Handle page image click for annotation creation
function handlePageImageClick(event) {
    const pageCard = event.target.closest('.page-card');
    if (!pageCard) return;
    
    const pageId = pageCard.id.replace('page-', '');
    currentManuscriptPageId = parseInt(pageId);
    
    // Show annotation creation modal
    showAnnotationModal(event);
}

// Show annotation creation modal
function showAnnotationModal(event) {
    const img = event.target;
    const rect = img.getBoundingClientRect();
    
    // Calculate click position as percentage
    const xPercent = ((event.clientX - rect.left) / rect.width) * 100;
    const yPercent = ((event.clientY - rect.top) / rect.height) * 100;
    
    // Create modal if it doesn't exist
    let modal = document.getElementById('annotationModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'annotationModal';
        modal.style.cssText = 'display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 1000;';
        document.body.appendChild(modal);
    }
    
    modal.innerHTML = `
        <div style="position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); background: #fff; padding: 30px; border-radius: 8px; width: 500px; max-height: 80vh; overflow-y: auto;">
            <h3>Add Annotation</h3>
            <form id="annotationForm">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">Category:</label>
                    <select name="category" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" required>
                        <option value="ARTWORK">Artwork</option>
                        <option value="TEXT">Text</option>
                        <option value="LAYOUT">Layout</option>
                        <option value="CONSISTENCY">Consistency</option>
                        <option value="OTHER">Other</option>
                    </select>
                </div>
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">Severity:</label>
                    <select name="severity" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                        <option value="CRITICAL">Critical</option>
                        <option value="HIGH">High</option>
                        <option value="MEDIUM" selected>Medium</option>
                        <option value="LOW">Low</option>
                        <option value="SUGGESTION">Suggestion</option>
                    </select>
                </div>
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">Content:</label>
                    <textarea name="content" rows="4" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" required placeholder="Describe the issue or suggestion..."></textarea>
                </div>
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">Selection Size:</label>
                    <div style="display: flex; gap: 10px;">
                        <div style="flex: 1;">
                            <label style="font-size: 12px;">Width %:</label>
                            <input type="number" name="widthPercent" value="10" min="1" max="100" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                        </div>
                        <div style="flex: 1;">
                            <label style="font-size: 12px;">Height %:</label>
                            <input type="number" name="heightPercent" value="10" min="1" max="100" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                        </div>
                    </div>
                </div>
                <input type="hidden" name="xPercent" value="${xPercent.toFixed(2)}">
                <input type="hidden" name="yPercent" value="${yPercent.toFixed(2)}">
                <div style="text-align: right;">
                    <button type="button" class="btn btn-secondary" onclick="hideAnnotationModal()">Cancel</button>
                    <button type="submit" class="btn btn-primary">Add Annotation</button>
                </div>
            </form>
        </div>
    `;
    
    // Set hidden coordinates
    const xInput = modal.querySelector('input[name="xPercent"]');
    const yInput = modal.querySelector('input[name="yPercent"]');
    if (xInput) xInput.value = xPercent.toFixed(2);
    if (yInput) yInput.value = yPercent.toFixed(2);
    
    // Add form submit handler
    const form = modal.querySelector('#annotationForm');
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
    
    const request = {
        manuscriptVersionId: currentManuscriptVersionId,
        manuscriptPageId: currentManuscriptPageId,
        category: formData.get('category'),
        severity: formData.get('severity'),
        content: formData.get('content'),
        xPercent: parseFloat(formData.get('xPercent')),
        yPercent: parseFloat(formData.get('yPercent')),
        widthPercent: parseFloat(formData.get('widthPercent')),
        heightPercent: parseFloat(formData.get('heightPercent')),
        parentAnnotationId: null
    };
    
    try {
        const response = await fetch('/api/v1/annotations', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(request)
        });
        
        const result = await response.json();
        
        if (result.success) {
            hideAnnotationModal();
            // Reload page to show new annotation
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
        const response = await fetch(`/api/v1/annotations/${annotationId}/resolve`, {
            method: 'POST',
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
        const response = await fetch(`/api/v1/annotations/${annotationId}/dismiss`, {
            method: 'POST',
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
        const response = await fetch(`/api/v1/annotations/${annotationId}/replies`, {
            method: 'POST',
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
}
