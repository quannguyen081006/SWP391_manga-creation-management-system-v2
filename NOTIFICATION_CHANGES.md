# Notification Module Cleanup Summary

## Files touched

- `src/java/manga/repository/NotificationRepository.java`: Added class-level Javadoc on the two write paths (NotificationService vs PageTaskRepository scheduler bypass), dedup patterns (`exists` once-ever vs daily dedup in task schedulers), and comments on `defaultTitle` / `defaultViewUrl` as the canonical type-to-title/URL mapping for the service path.
- `src/java/manga/service/NotificationService.java`: Added class and method comments explaining `notifyUser()` as the primary workflow entry point and `existsNotification()` as the once-ever dedup guard before create.
- `src/java/manga/web/NotificationViewAdvice.java`: Added class-level comment explaining `@ControllerAdvice(annotations = Controller.class)` auto-injects header badge count and dropdown list into every MVC page without per-controller fetches.
- `src/java/manga/controller/web/NotificationWebController.java`: Added class comment and expanded `isSupportedViewUrl` Javadoc as an open-redirect allowlist guard for `/click` redirects.
- `src/java/manga/controller/api/NotificationApiController.java`: Added class-level comment explaining manual JSON strings instead of `ApiResponse<T>` for legacy frontend compatibility.
- `web/WEB-INF/jsp/notification/list.jsp`: Added page-level and inline JSP comments for server-rendered vs JS-driven actions (mark-all POST, delete/toggle fetch, timeAgo, click-through allowlist).
- `web/assets/css/notification.css`: Added file header comment clarifying page-specific overrides atop `styles.css`; no rule changes.
- `NOTIFICATION_CHANGES.md`: Added this summary and manual test checklist.

## Skipped or not touched

- `web/assets/js/header.js`: Already modified before this pass (confirm-popup / password-toggle fix). Contains notification dropdown, fetch delete/toggle, and `timeAgo` — left unchanged.
- `web/WEB-INF/jsp/common/header.jsp`: Already modified before this pass; left unchanged.
- `web/assets/css/header.css`: Already modified before this pass; left unchanged.
- `src/java/manga/repository/PageTaskRepository.java`: Out of scope. Task scheduler dedup helpers (`createNotificationIfAbsent`, `createNotificationIfAbsentToday`) and matching title/URL behavior are documented from the notification side only.

## How to explain this to the professor

**Two write paths, one mapping design.** Most workflows call `NotificationService.notifyUser()`, which validates input and delegates to `NotificationRepository.create()`. The repository resolves display title and click URL from notification type and reference metadata via `defaultTitle()` and `defaultViewUrl()`. Task scheduler jobs in `PageTaskRepository` use a parallel bypass: they insert notifications in the same JDBC batch as task status updates, using local `createNotificationIfAbsent` / `createNotificationIfAbsentToday` helpers with matching title/URL rules. Both paths must produce the same user-visible title and redirect target; the repository comments are the place to point for the canonical mapping rules.

**Once-ever vs daily dedup.** `NotificationRepository.exists()` (via `NotificationService.existsNotification()`) supports once-ever dedup: e.g. `ReviewTaskService` checks before sending `REVIEW_WARNING` so a reviewer gets only one warning per manuscript version. Task schedulers use two patterns in `PageTaskRepository`: `createNotificationIfAbsent` (once-ever — e.g. one `TASK_OVERDUE` per task when it first goes overdue) and `createNotificationIfAbsentToday` (daily — e.g. `TASK_DUE_SOON` due-within-24h reminders and `TASK_DELAYED` no-progress alerts can fire again on a new calendar day).

**Manual JSON in the API controller.** `NotificationApiController` builds JSON with `StringBuilder` instead of returning `ApiResponse<T>` like newer API controllers. Existing `header.js` fetch handlers expect the legacy shape (`success`, `message`, `data`) and field aliases (`body`, `recipientId`, `relatedEntityId`). Changing to `ApiResponse` would break in-place delete and read/unread toggle without a frontend rewrite — out of scope for this cleanup pass.

**Why header files were skipped.** `header.js`, `header.jsp`, and `header.css` were already modified in an earlier unrelated fix before this notification pass began. This pass documents their role (dropdown, fetch actions, timestamps) but does not edit them, same approach as the Revision History / Audit Log pass with `ModuleWebController`.

**Why this module keeps JavaScript.** Unlike Auth/RBAC or User Management, where optional JS (role-checkbox convenience, client-side validation hints) had pure server-rendered equivalents, notification delete and read/unread toggle have no web form routes — only API endpoints (`DELETE`, `PATCH .../read`, `PATCH .../unread`). Removing JS would require new web routes and full-page reloads. The dropdown menu and relative timestamps also need client-side behavior. This pass makes that design easier to explain; it does not remove necessary JS.

No business rule, URL mapping, method signature, dedup behavior, JSON response shape, or visual output was changed.

## Manual test checklist

- [ ] Notification bell dropdown still opens/closes and shows unread count correctly.
- [ ] Full notification list page still loads up to 100 notifications.
- [ ] Mark-as-read (single) still works from both the web route and the dropdown/API path.
- [ ] Mark-all-read still works.
- [ ] Delete notification (API/JS path) still removes the row from the list without reload.
- [ ] Clicking a notification still redirects to its `viewUrl` only if it's an allowlisted internal path (test with a known notification type).
