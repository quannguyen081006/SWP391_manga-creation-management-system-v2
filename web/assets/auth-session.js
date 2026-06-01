(function () {
    'use strict';

    var ctx = window.MANGA_CTX || '';
    var loginPath = (ctx || '') + '/login';

    function redirectToLogin() {
        if (window.location.pathname.indexOf('/login') === -1) {
            window.location.replace(loginPath);
        }
    }

    function verifySession() {
        return fetch((ctx || '') + '/api/v1/auth/me', {
            credentials: 'same-origin',
            headers: { Accept: 'application/json' }
        }).then(function (res) {
            if (!res.ok) {
                redirectToLogin();
            }
        }).catch(function () {
            redirectToLogin();
        });
    }

    window.addEventListener('pageshow', function (event) {
        if (event.persisted) {
            verifySession();
        }
    });
})();
