(function () {
    'use strict';

    function applyDynamicStyles(root) {
        var scope = root || document;
        var progressItems = scope.querySelectorAll('[data-progress-width]');
        for (var i = 0; i < progressItems.length; i++) {
            var width = Number(progressItems[i].getAttribute('data-progress-width'));
            if (!isNaN(width)) {
                progressItems[i].style.width = Math.max(0, Math.min(100, width)) + '%';
            }
        }

        var positionedItems = scope.querySelectorAll('[data-position-left]');
        for (var j = 0; j < positionedItems.length; j++) {
            var item = positionedItems[j];
            item.style.left = item.getAttribute('data-position-left') + '%';
            item.style.top = item.getAttribute('data-position-top') + '%';
            item.style.width = item.getAttribute('data-position-width') + '%';
            item.style.height = item.getAttribute('data-position-height') + '%';
        }
    }

    function bindConfirmations() {
        document.addEventListener('submit', function (event) {
            var form = event.target;
            if (form && form.hasAttribute && form.hasAttribute('data-prevent-submit')) {
                event.preventDefault();
            }
            var message = form && form.getAttribute ? form.getAttribute('data-confirm') : null;
            if (message && !window.confirm(message)) {
                event.preventDefault();
            }
        });

        document.addEventListener('click', function (event) {
            var target = event.target.closest ? event.target.closest('[data-confirm]') : null;
            if (target && !window.confirm(target.getAttribute('data-confirm'))) {
                event.preventDefault();
            }
        });
    }

    function initialize() {
        applyDynamicStyles(document);
        bindConfirmations();
    }

    window.MangaUi = window.MangaUi || {};
    window.MangaUi.applyDynamicStyles = applyDynamicStyles;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    } else {
        initialize();
    }
}());
