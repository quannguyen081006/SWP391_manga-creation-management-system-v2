(function () {
    'use strict';

    function formatTime(seconds) {
        if (seconds <= 0) return '0h 0m 0s';
        var value = Math.max(0, Math.floor(seconds));
        var hours = Math.floor(value / 3600);
        var minutes = Math.floor((value % 3600) / 60);
        var remainingSeconds = value % 60;
        return hours + 'h ' + minutes + 'm ' + remainingSeconds + 's';
    }

    var elements = Array.prototype.slice.call(document.querySelectorAll('.countdown'));
    var state = {};
    elements.forEach(function (element) {
        var versionId = element.getAttribute('data-version-id');
        var remaining = parseInt(element.getAttribute('data-remaining'), 10);
        state[versionId] = isNaN(remaining) ? null : remaining;
        element.textContent = state[versionId] === null ? '-' : (remaining <= 0 ? 'OVERDUE' : formatTime(remaining));
    });

    window.setInterval(function () {
        Object.keys(state).forEach(function (versionId) {
            var remaining = state[versionId];
            var element = document.querySelector('.countdown[data-version-id="' + versionId + '"]');
            if (!element || remaining === null) return;
            remaining -= 1;
            state[versionId] = remaining;
            if (remaining <= 0) {
                element.textContent = 'OVERDUE';
                var row = element.closest('tr');
                var dot = row ? row.querySelector('.urgency-dot') : null;
                if (dot) {
                    dot.classList.remove('urgency-GREEN', 'urgency-YELLOW', 'urgency-RED');
                    dot.classList.add('urgency-OVERDUE');
                }
            } else {
                element.textContent = formatTime(remaining);
            }
        });
    }, 1000);
}());
