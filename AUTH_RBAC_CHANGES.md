# Auth and RBAC Cleanup Summary

## Files touched

- `src/java/manga/controller/web/AuthController.java`: Added method comments explaining the login page, login submit, logout, and why this app uses `AUTH_USER` in `HttpSession`.
- `src/java/manga/service/AuthService.java`: Added a public-method comment explaining active-account authentication and why the existing plain `passwordHash` comparison was left unchanged.
- `src/java/manga/web/interceptor/AuthInterceptor.java`: Added comments around centralized auth/RBAC, session lookup, cache prevention, API errors, and route checks; also extracted session user lookup into a small private helper for readability.
- `src/java/manga/common/util/SessionUserUtil.java`: Added comments explaining when to use `requireUser` and `requireRole`, and fixed indentation.
- `src/java/manga/common/util/RoleCombinationValidator.java`: Added comments explaining the enforced role rules: ADMIN single-role, MANGAKA/ASSISTANT single-role, and TANTOU_EDITOR plus EDITORIAL_BOARD as the only valid pair.
- `web/WEB-INF/jsp/auth/login.jsp`: Clarified that the login form is plain HTML and the controller stores the authenticated user in session.
- `web/assets/auth-session.js`: Simplified variable names and added short comments explaining the back/forward-cache session check.
- `web/assets/role-assignment.js`: Added the required top-level comment that this is client-side convenience only and the server-side validator is the security authority.
- `AUTH_RBAC_CHANGES.md`: Added this summary so the cleanup is easy to explain and review.

## How to explain this to a professor

Login flow: the browser submits the login form to the existing Spring MVC login route. `AuthController` receives the username and password, calls `AuthService.login`, and `AuthService` checks the user record, account status, and existing password comparison. When login succeeds, the controller stores the returned `AuthenticatedUser` in `HttpSession` under `AUTH_USER`, then redirects the user to the dashboard.

RBAC flow: every protected request passes through `AuthInterceptor.preHandle` before it reaches a controller. The interceptor lets public routes such as `/login`, `/logout`, `/assets/**`, and `/redirect.jsp` pass, then reads `AUTH_USER` from session. If there is no logged-in user it redirects browser pages to login or returns 401 for API calls. If the user is logged in, `isAllowed` checks the request path against the role rules before the controller is allowed to run.

No business rule, URL mapping, or method signature was changed. No new library/framework was introduced.
