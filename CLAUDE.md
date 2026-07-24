# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Identity

Manga Creation & Publishing Management System — a Java 8 web application for managing manga production workflows: proposal submission, editorial review, board voting, series production, chapter/page/task management, manuscripts, ranking, decision sessions, notifications, audit logs, and user/role administration.

This is a traditional NetBeans/Ant Java web app — **not** Maven/Gradle, **not** Spring Boot.

Core stack:
- Java 8
- Spring Framework / Spring MVC 4.x
- JSP + JSTL
- JDBC repositories (no ORM)
- SQL Server
- Tomcat
- Ant / NetBeans project files

## Build And Verification

The normal build is through NetBeans or Ant (`build.xml` imports `nbproject/build-impl.xml`).

Known local issue: `ant` and `javac` may not be on PATH even when a JDK exists. Manual compile command that has worked in this workspace (PowerShell):

```powershell
$jars = New-Object System.Collections.Generic.List[string]
Get-ChildItem build\web\WEB-INF\lib -Filter *.jar | ForEach-Object { $jars.Add($_.FullName) }
Get-ChildItem 'D:\FPTU\4-SP26\PRJ\apache-tomcat-9.0.113-windows-x64\apache-tomcat-9.0.113\lib' -Filter *.jar | ForEach-Object { $jars.Add($_.FullName) }
$cp = [string]::Join(';', $jars)
$src = Get-ChildItem src\java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force -Path build\codex-compile | Out-Null
& 'C:\Program Files\Java\jdk1.8.0_172\bin\javac.exe' -source 1.8 -target 1.8 -encoding UTF-8 -cp $cp -d build\codex-compile $src
```

There is no automated test suite (`test/` is empty) — verification is manual, through NetBeans/Tomcat run or the compile command above.

After changing Java, JSP, CSS, or JS files, Tomcat/NetBeans needs a restart or redeploy to serve the new output — don't assume hot reload.

## Architecture Pattern

Most features follow:

```
JSP view -> Web Controller -> Service -> Repository -> SQL Server
```

API features follow:

```
API Controller -> Service/Repository -> SQL Server -> ApiResponse
```

Repositories use plain JDBC with `DataSource`; there is no ORM. Most SQL lives in `src/java/manga/repository`.

## Important Directories

- `src/java/manga/controller/web`: Spring MVC page controllers returning JSP views.
- `src/java/manga/controller/api`: REST-style API controllers returning `ApiResponse` (includes `api/chaptertask` sub-package for chapter/page/task endpoints).
- `src/java/manga/service`: business logic and workflow services.
- `src/java/manga/repository`: JDBC data access (includes `repository/chaptertask` sub-package).
- `src/java/manga/model`, `src/java/manga/dto`, `src/java/manga/enums`: domain models, request/response DTOs, and enums.
- `src/java/manga/common`: shared response, exceptions, utilities (e.g. `RoleCombinationValidator`).
- `src/java/manga/web`: MVC advice, interceptors, JSON helpers (e.g. `NotificationViewAdvice`, `AuthInterceptor`).
- `src/java/manga/scheduler`: scheduled jobs.
- `web/WEB-INF/jsp`: JSP views, organized by feature (`proposal/`, `user/`, `series/`, `chapter/`, `task/`, `manuscript/`, `ranking/`, `analytics/`, `decision/`, `common/`).
- `web/assets`: global CSS (per-feature files, e.g. `ranking.css`, `salary/`) and browser JS (per-feature files, e.g. `chaptertask/`, `salary/`).
- `web/WEB-INF/applicationContext.xml`: Spring application context.
- `web/WEB-INF/dispatcher-servlet.xml`: Spring MVC config.
- `web/WEB-INF/web.xml`: web app entry/config.
- `database/schema.sql`: full SQL Server schema; `database/seed_v5.sql`: seed data. Files suffixed `_azure_DONT_TOUCH` are the deployed Azure schema/seed — treat as read-only reference, not editable local dev files.
- `src/conf/jdbc.properties`, `web/WEB-INF/jdbc.properties`, `src/java/jdbc.properties`: DB connection config (copied into build output by `build.xml`'s `-post-compile` target).
- `nbproject`: NetBeans/Ant metadata.

## Main Feature Areas

**Proposal flow**: `ProposalController`, `MainController`, `ProposalApiController` -> `ProposalService` -> `ProposalRepository` -> `web/WEB-INF/jsp/proposal/*`.

**User and role management**: `ModuleWebController`, `UserApiController` -> `UserAdminRepository`, `UserRepository`; validation in `RoleCombinationValidator`; JSP/JS in `web/WEB-INF/jsp/user/*` and `web/assets/role-assignment.js`.

**Authentication and dev switch account**: `AuthController` (delegated by `MainController`) -> `AuthService`; UI in `web/WEB-INF/jsp/common/header.jsp`; enforced by `AuthInterceptor`.

**Dashboard and navigation**: `DashboardController`; shared header/sidebar in `web/WEB-INF/jsp/common/header.jsp`, with extra model attributes supplied by `NotificationViewAdvice`.

**Series, chapters, tasks, manuscripts**: `ModuleWebController` holds many page endpoints; chapter/page/task API lives under `controller/api/chaptertask`. Repositories: `ProductionRepository`, `ChapterRepository`, `PageRepository`, `PageTaskRepository`, `ManuscriptRepository`, `ChapterImageRepository` (chaptertask sub-package). JSPs under `web/WEB-INF/jsp/series`, `chapter`, `task`, `manuscript`. See **CHAPTER_TASK_FLOW.md** for the detailed chapter/page/task/manuscript data flow (below).

**Ranking, analytics, decisions**: `ModuleWebController`, `MangakaPerformanceController`, plus API controllers under `controller/api`. Repositories: `RankingRepository`, `DecisionRepository`, performance repositories. JSPs under `web/WEB-INF/jsp/ranking`, `analytics`, `decision`.

**Salary**: `controller/web/salary`, with matching CSS/JS under `web/assets/css/salary` and `web/assets/js/salary`.

## Chapter/Task/Manuscript Data Flow (see CHAPTER_TASK_FLOW.md for full detail)

Key invariant: **manuscript import reads only from `ChapterImage`, never directly from `[Page]`.** `[Page]` holds the current/latest state of a page slot; `ChapterImage` is the production/import source of truth (tracks `uploadedBy`, `pageTaskId`, `isActive`).

- A page reaches completion at `completedStage = LETTERING`.
- Each `chapterId + pageNumber + imageType=PAGE` should have exactly one active `ChapterImage` row at a time; uploading/syncing a new image must deactivate the prior one first.
- `Chapter.completionPct` is derived from `[Page].completedStage`, not from approved-task counts (`PageTaskRepository.refreshChapterProgress`).
- `ChapterRepository.submitForReview` only allows moving to `EDITORIAL_REVIEW` when the chapter is 100% complete and owned by the requesting Mangaka; it then backfills any final pages missing from `ChapterImage` (`ChapterImageRepository.backfillFinalPageUploads`) — this guards against the "chapter 100% but manuscript import finds no pages" bug, which happens when Mangaka self-uploads pages directly (only creating `[Page]` rows) while assistants only populate `ChapterImage` via task uploads.
- Task submission (`PageTaskRepository.updateStatusByAssistant`) is validated against `ChapterImage` server-side — the frontend having "enough images" is not trusted.
- Task approval (`PageTaskRepository.approveByMangaka`) promotes images from `ChapterImage` into `[Page]` via `PageRepository.promoteTaskImage`, then refreshes chapter progress.

## Role Rules

Valid roles: `ADMIN`, `MANGAKA`, `ASSISTANT`, `TANTOU_EDITOR`, `EDITORIAL_BOARD`.

- `MANGAKA` and `ASSISTANT` must each be single-role only.
- `ADMIN` should not be combined with other roles; the DB also enforces only one admin role assignment.
- The only valid multi-role combination is `TANTOU_EDITOR + EDITORIAL_BOARD`.
- The switch-account/switch-role UI must stay read-only: it selects an existing active user/account and must never mutate or validate role combinations. Role assignment validation belongs only in user creation / admin role-assignment flows.

Relevant files: `RoleCombinationValidator.java`, `UserAdminRepository.addRole`, `ModuleWebController.validateCreateUser`, `ModuleWebController.validateAssignableRoles`, `web/assets/role-assignment.js`, `web/WEB-INF/jsp/common/header.jsp`, `web/WEB-INF/jsp/user/list.jsp`.

## Database Notes

Current local JDBC config commonly used:

```properties
jdbc.url=jdbc:sqlserver://localhost:1433;databaseName=MangaEditorialDB;encrypt=true;trustServerCertificate=true
jdbc.username=SA
jdbc.password=12345
```

Use `database/schema.sql` for full schema and `database/seed_v5.sql` for seed data. Some recent features assume proposal board voting tables exist (`ProposalBoardRound`, `ProposalBoardRoundVoter`, `ProposalHistory.boardRoundId`) — if the local DB is old, proposal queries can fail with missing table errors.

## Deployment

The app is deployed live on Render: https://swp391-manga-creation-management-system.onrender.com/MangaProject/main/login

- Hosting: Render, running the Tomcat/WAR build (context path `/MangaProject`).
- DB connection for the deployed instance is injected at container start by `docker-entrypoint.sh`, which overwrites the baked-in dev `jdbc.properties` (localhost/SA/12345) using the `JDBC_URL`, `JDBC_USERNAME`, `JDBC_PASSWORD` (and optional `JDBC_DRIVER_CLASS_NAME`) environment variables set in the Render service's Environment tab. It currently points at a SQL Server instance reachable from Render (e.g. an Oracle Cloud VM public IP), not the local dev DB.
- Because the deployed schema/seed may lag behind local changes, treat `database/schema.sql`/`seed_v5.sql` as the source of truth to reconcile against when the live site errors on missing tables/columns (see the proposal board voting table caveat above).
- No CI/CD pipeline described here — assume deploys are manual (Docker build + Render redeploy) unless told otherwise.

## Common Pitfalls

- Most UI is server-rendered JSP with small JS helpers in `web/assets` — this is not a React app.
- Do not introduce Maven/Gradle unless explicitly asked; do not rewrite repositories to an ORM.
- `ModuleWebController.java` owns many unrelated routes — be careful making changes there.
- Check both `/main/...` web controllers and `/api/v1/...` API controllers when touching a feature; the same feature is often split across both.
- Many JSPs use `sessionScope.AUTH_USER.hasRole(...)` directly — when fixing role/access bugs, update both backend validation and JSP visibility.
- Do not edit generated build output (`build/`, `dist/`, nested copied project folders) — edit sources under `src/`, `web/`, `database/` instead.
- The working tree may contain in-progress user changes; check `git status --short` before editing and avoid reverting unrelated work.
