(function () {
    'use strict';

    var script = document.currentScript;
    var contextPath = script ? script.getAttribute('data-context-path') || '' : '';
    var message = document.getElementById('seriesMessage');

    function showMessage(text, isError) {
        if (!message) return;
        message.textContent = text;
        message.className = 'alert series-message ' + (isError ? 'error' : 'success');
        message.classList.remove('is-hidden-initial');
    }

    function todayIso() {
        var date = new Date();
        var month = String(date.getMonth() + 1).padStart(2, '0');
        var day = String(date.getDate()).padStart(2, '0');
        return date.getFullYear() + '-' + month + '-' + day;
    }

    var deadlineInputs = document.querySelectorAll('.series-deadline-form input[type="date"]');
    for (var i = 0; i < deadlineInputs.length; i++) deadlineInputs[i].min = todayIso();

    document.addEventListener('submit', function (event) {
        if (!event.target.classList || !event.target.classList.contains('series-deadline-form')) return;
        event.preventDefault();
        var form = event.target;
        var seriesId = form.getAttribute('data-series-id');
        var publicationDate = form.querySelector('[name="publicationDate"]').value;
        fetch(contextPath + '/api/v1/series/' + seriesId + '/deadline?publicationDate=' + encodeURIComponent(publicationDate), {
            method: 'PUT',
            headers: {Accept: 'application/json'}
        }).then(function (response) {
            return response.json().then(function (body) {
                if (!response.ok || body.success === false) throw new Error(body.message || 'Cannot update deadline');
                showMessage('Series deadline updated.', false);
                window.location.reload();
            });
        }).catch(function (error) {
            showMessage(error.message, true);
        });
    });
}());
