/*
 * Client-side guard for the proposal sample file input.
 *
 * Gives instant feedback when the picked file is not a .pdf / .docx so the Mangaka does not
 * have to submit the form to find out. The server (ProposalSampleFileUploader) enforces the
 * same rule — plus the duplicate-content check — and stays the source of truth.
 */
(function () {
    'use strict';

    var ALLOWED = /\.(pdf|docx)$/i;
    var MAX_SIZE_BYTES = 20 * 1024 * 1024;
    var MESSAGE = 'Sample file must be a PDF (.pdf) or Word (.docx) document. Please choose another file.';
    var SIZE_MESSAGE = 'Sample file must not exceed 20 MB.';

    function init() {
        var input = document.getElementById('sampleFile');
        if (!input) {
            return;
        }

        var error = document.createElement('p');
        error.className = 'alert error';
        error.id = 'sampleFileClientError';
        error.style.display = 'none';
        input.parentNode.insertBefore(error, input.nextSibling);

        function showError(message) {
            error.textContent = message;
            error.style.display = '';
            input.value = '';
        }

        input.addEventListener('change', function () {
            error.style.display = 'none';
            if (!input.files || input.files.length === 0) {
                return;
            }
            var file = input.files[0];
            if (!ALLOWED.test(file.name)) {
                showError(MESSAGE);
                return;
            }
            if (file.size > MAX_SIZE_BYTES) {
                showError(SIZE_MESSAGE);
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
}());
