(function () {
    'use strict';

    function updateCountdowns() {
        var elements = document.querySelectorAll('[data-end-date]');
        for (var i = 0; i < elements.length; i++) {
            var element = elements[i];
            var endDate = new Date(element.getAttribute('data-end-date'));
            var difference = endDate - new Date();
            if (difference <= 0) {
                element.textContent = 'Expired';
                element.classList.add('is-expired');
                continue;
            }

            var days = Math.floor(difference / 86400000);
            var hours = Math.floor((difference % 86400000) / 3600000);
            var minutes = Math.floor((difference % 3600000) / 60000);
            var seconds = Math.floor((difference % 60000) / 1000);
            element.textContent = days + 'd ' + hours + 'h ' + minutes + 'm ' + seconds + 's';
            element.classList.toggle('urgent', days < 1);
        }
    }

    updateCountdowns();
    window.setInterval(updateCountdowns, 1000);
}());
