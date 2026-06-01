# Dev Feature Notes

## Temporary Switch Role (for testing)
Feature marker: `DEV_SWITCH_ROLE`

To remove quickly later:
1. Remove method block between `DEV_SWITCH_ROLE_START/END` in:
   - `src/java/manga/web/controller/AuthController.java`
2. Remove helper method in:
   - `src/java/manga/service/AuthService.java`
3. Remove header block between `DEV_SWITCH_ROLE_START/END` in:
   - `web/WEB-INF/jsp/common/header.jsp`
4. Remove CSS block:
   - `.role-switch-links` in `web/assets/styles.css`

After removal, sync to `build/web` and redeploy.
