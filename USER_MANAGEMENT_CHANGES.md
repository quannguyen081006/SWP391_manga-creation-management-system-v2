# User Management Cleanup Summary

## Files touched

- `src/java/manga/controller/web/ModuleWebController.java`: Added comments to the user-management section explaining admin guards, create/edit behavior, role-option parsing, early validation, and why repository checks remain authoritative.
- `src/java/manga/repository/UserAdminRepository.java`: Added comments explaining database-side enforcement for unique username/email, singleton ADMIN, status protection, and role-combination validation.
- `src/java/manga/controller/api/UserApiController.java`: Added comments for API endpoints and clarified that the repository is still the authority for user and role rules.
- `web/WEB-INF/jsp/user/list.jsp`: Added JSP comments for the role-add panel, status action visibility, and why some UI is hidden.
- `web/WEB-INF/jsp/user/form.jsp`: Added JSP comments explaining edit-only fields, the `roleOption` radio group, and the singleton ADMIN note.
- `web/assets/css/user.css`: Added one short CSS comment for the shared role-choice layout; no class names or visual rules were changed.
- `USER_MANAGEMENT_CHANGES.md`: Added this review summary and manual test checklist.

## How to explain this to the professor

`ModuleWebController.java`: The controller is the first gate for the user-management pages. It checks that the session user is an ADMIN, prepares JSP data, and performs early validation so the user gets a clear error quickly. It is not the final authority for database rules; the repository still checks important rules when saving.

`UserAdminRepository.java`: This is the authoritative layer because it is closest to the database write. It enforces unique username/email, blocks creating or assigning a second ADMIN, blocks deactivating the only active ADMIN, blocks removing the only ADMIN role, and calls `RoleCombinationValidator` before role changes are saved.

`UserApiController.java`: The API keeps the same `ApiResponse` shapes and uses the same repository rules as the web pages. Each endpoint checks that the caller is an ADMIN, then delegates real user-management work to `UserAdminRepository`.

`list.jsp`: The list page is a plain JSP/JSTL admin screen. It shows current roles and forms for status/role changes, but hidden UI is only a convenience; server-side validation still decides what is allowed.

`form.jsp`: Create mode uses one `roleOption` radio group because the allowed role combinations are limited and easy to present as fixed choices. Edit mode only changes `fullName` and `email`; username is immutable because it identifies the account, and roles are managed from the list page.

`user.css`: The stylesheet only describes the existing role-choice layout. It does not enforce security or business rules.

Singleton ADMIN is enforced in more than one place deliberately: the controller/JSP can guide the admin early, but `UserAdminRepository` is the real gate that protects the database. Role-combination validation happens before saving through `UserAdminRepository` and `RoleCombinationValidator`; JSP and shared role-assignment JavaScript are UX-only.

No business rule, URL mapping, method signature, public API contract, library, framework, or build tool was changed.

## Manual test checklist

- [ ] Admin creates a new user with each of the 5 role options -> correct role(s) assigned.
- [ ] Admin tries to create a second Admin-equivalent (if UI allowed it before, still blocked) -> singleton rule holds.
- [ ] Admin edits a user's fullName/email -> saves correctly; username field stays read-only.
- [ ] Admin deactivates the only Admin account -> blocked with correct error.
- [ ] Admin adds/removes a role via the list page -> notification (`ROLE_ASSIGNED`/`ROLE_REMOVED`) still fires.
- [ ] Duplicate username or email on create -> validation error shown, no account created.
