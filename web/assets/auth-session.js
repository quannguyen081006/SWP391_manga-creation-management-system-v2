(function () {
  'use strict';

  var script = document.currentScript;
  var contextPath = window.MANGA_CTX || (script ? script.getAttribute('data-context-path') : '') || '';
  var loginPath = contextPath + '/login';

  window.MANGA_CTX = contextPath;

  function redirectToLogin() {
    if (window.location.pathname.indexOf('/login') === -1) {
      window.location.replace(loginPath);
    }
  }

  function verifySession() {
    // pageshow also runs when the browser restores a page from back/forward cache.
    // This request confirms the server session still exists before showing the page.
    return fetch(contextPath + '/api/v1/auth/me', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    }).then(function (res) {
      if (!res.ok) {
        redirectToLogin();
      } else {
        document.documentElement.style.visibility = '';
      }
    }).catch(function (error) {
      console.error('Session verification failed', error);
      redirectToLogin();
    });
  }

  window.addEventListener('pageshow', function () {
    // Hide protected content until the session check finishes.
    document.documentElement.style.visibility = 'hidden';
    verifySession();
  });
})();
