# CLAUDE.md

This file is a quick orientation guide for Claude or any other coding agent working in this repository.

## Project Identity

Manga Creation & Publishing Management System is a Java 8 web application for managing manga production workflows: proposal submission, editorial review, board voting, series production, page tasks, manuscripts, ranking, decision sessions, notifications, audit logs, and user/role administration.

The project is a traditional NetBeans/Ant Java web app, not Maven/Gradle and not Spring Boot.

Core stack:
- Java 8
- Spring Framework / Spring MVC 4.x
- JSP + JSTL
- JDBC repositories
- SQL Server
- Tomcat
- Ant / NetBeans project files

## Important Directories

- `src/java/manga/controller/web`: Spring MVC page controllers returning JSP views.
- `src/java/manga/controller/api`: REST-style API controllers returning `ApiResponse`.
- `src/java/manga/service`: business logic and workflow services.
- `src/java/manga/repository`: JDBC data access. Most SQL lives here.
- `src/java/manga/model`: view/domain models.
- `src/java/manga/dto`: request/response DTOs.
- `src/java/manga/common`: shared response, exceptions, utilities.
- `src/java/manga/web`: MVC advice, interceptors, JSON helpers.
- `src/java/manga/scheduler`: scheduled jobs.
- `web/WEB-INF/jsp`: JSP views.
- `web/assets`: global CSS and browser JS.
- `web/WEB-INF/applicationContext.xml`: Spring application context.
- `web/WEB-INF/dispatcher-servlet.xml`: Spring MVC config.
- `web/WEB-INF/web.xml`: web app entry/config.
- `database/schema.sql`: full SQL Server schema.
- `database/seed_v5.sql`: seed data.
- `src/conf/jdbc.properties`: DB connection config copied into build output.
- `nbproject`: NetBeans/Ant metadata.

## Architecture Pattern

Most features follow:

`JSP view -> Web Controller -> Service -> Repository -> SQL Server`

API features follow:

`API Controller -> Service/Repository -> SQL Server -> ApiResponse`

Repositories use plain JDBC with `DataSource`; there is no ORM.

## Main Feature Areas

Proposal flow:
- Controllers:
  - `src/java/manga/controller/web/ProposalController.java`
  - `src/java/manga/controller/web/MainController.java`
  - `src/java/manga/controller/api/ProposalApiController.java`
- Service:
  - `src/java/manga/service/ProposalService.java`
- Repository:
  - `src/java/manga/repository/ProposalRepository.java`
- JSP:
  - `web/WEB-INF/jsp/proposal/list.jsp`
  - `web/WEB-INF/jsp/proposal/detail.jsp`
  - `web/WEB-INF/jsp/proposal/create.jsp`
  - `web/WEB-INF/jsp/proposal/edit.jsp`

User and role management:
- Controller:
  - `src/java/manga/controller/web/ModuleWebController.java`
  - `src/java/manga/controller/api/UserApiController.java`
- Repository:
  - `src/java/manga/repository/UserAdminRepository.java`
  - `src/java/manga/repository/UserRepository.java`
- Utility:
  - `src/java/manga/common/util/RoleCombinationValidator.java`
- JSP/JS:
  - `web/WEB-INF/jsp/user/list.jsp`
  - `web/WEB-INF/jsp/user/form.jsp`
  - `web/assets/role-assignment.js`

Authentication and dev switch account:
- Controller:
  - `src/java/manga/controller/web/AuthController.java`
  - delegated by `src/java/manga/controller/web/MainController.java`
- Service:
  - `src/java/manga/service/AuthService.java`
- Header/switcher UI:
  - `web/WEB-INF/jsp/common/header.jsp`
- Interceptor:
  - `src/java/manga/web/interceptor/AuthInterceptor.java`

Dashboard and navigation:
- Controller:
  - `src/java/manga/controller/web/DashboardController.java`
- Shared header/sidebar:
  - `web/WEB-INF/jsp/common/header.jsp`
- Header model advice:
  - `src/java/manga/web/NotificationViewAdvice.java`

Series, chapters, tasks, manuscripts:
- `src/java/manga/controller/web/ModuleWebController.java` contains many page endpoints.
- Repositories include:
  - `ProductionRepository`
  - `ChapterRepository`
  - `PageTaskRepository`
  - `ManuscriptRepository`
  - `ChapterImageRepository`
- JSPs under:
  - `web/WEB-INF/jsp/series`
  - `web/WEB-INF/jsp/chapter`
  - `web/WEB-INF/jsp/task`
  - `web/WEB-INF/jsp/manuscript`

Ranking, analytics, decisions:
- Controllers:
  - `ModuleWebController`
  - `MangakaPerformanceController`
  - API controllers under `controller/api`
- Repositories:
  - `RankingRepository`
  - `DecisionRepository`
  - performance repositories
- JSPs:
  - `web/WEB-INF/jsp/ranking`
  - `web/WEB-INF/jsp/analytics`
  - `web/WEB-INF/jsp/decision`

## Role Rules

Valid roles:
- `ADMIN`
- `MANGAKA`
- `ASSISTANT`
- `TANTOU_EDITOR`
- `EDITORIAL_BOARD`

Important business rules:
- `MANGAKA` must be single-role only.
- `ASSISTANT` must be single-role only.
- `ADMIN` should not be combined with other roles; DB also enforces only one admin role assignment.
- The only valid multi-role combination is `TANTOU_EDITOR + EDITORIAL_BOARD`.
- Switch account/role UI must not mutate or validate role combinations. It should only choose an existing active user/account.
- Role assignment validation belongs in user creation / admin role assignment flows.

Relevant files:
- `RoleCombinationValidator.java`
- `UserAdminRepository.addRole`
- `ModuleWebController.validateCreateUser`
- `ModuleWebController.validateAssignableRoles`
- `web/assets/role-assignment.js`
- `web/WEB-INF/jsp/common/header.jsp`
- `web/WEB-INF/jsp/user/list.jsp`

## Build And Verification

This is an Ant/NetBeans project. The normal build is through NetBeans or Ant.

Known local issue: `ant` and `javac` may not be in PATH, even when a JDK exists.

Manual compile command used successfully in this workspace:

```powershell
$jars = New-Object System.Collections.Generic.List[string]
Get-ChildItem build\web\WEB-INF\lib -Filter *.jar | ForEach-Object { $jars.Add($_.FullName) }
Get-ChildItem 'D:\FPTU\4-SP26\PRJ\apache-tomcat-9.0.113-windows-x64\apache-tomcat-9.0.113\lib' -Filter *.jar | ForEach-Object { $jars.Add($_.FullName) }
$cp = [string]::Join(';', $jars)
$src = Get-ChildItem src\java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force -Path build\codex-compile | Out-Null
& 'C:\Program Files\Java\jdk1.8.0_172\bin\javac.exe' -source 1.8 -target 1.8 -encoding UTF-8 -cp $cp -d build\codex-compile $src
```

If using Tomcat/NetBeans, restart or redeploy after changing Java, JSP, CSS, or JS files.

## Database Notes

SQL Server connection config is in:
- `src/conf/jdbc.properties`
- `web/WEB-INF/jdbc.properties`
- `src/java/jdbc.properties`

Current local JDBC config commonly uses:

```properties
jdbc.url=jdbc:sqlserver://localhost:1433;databaseName=MangaEditorialDB;encrypt=true;trustServerCertificate=true
jdbc.username=SA
jdbc.password=12345
```

Use `database/schema.sql` for full schema and `database/seed_v5.sql` for seed data.

Some recent features assume proposal board voting tables exist:
- `ProposalBoardRound`
- `ProposalBoardRoundVoter`
- `ProposalHistory.boardRoundId`

If local DB is old, proposal queries can fail with missing board round tables.

## Common Pitfalls

- Do not assume this is a React app. Most UI is server-rendered JSP, with small JS helpers in `web/assets`.
- Do not introduce Maven/Gradle unless explicitly asked.
- Do not rewrite repositories to ORM; existing pattern is JDBC.
- Be careful with `ModuleWebController.java`; it owns many unrelated routes.
- Check both `/main/...` web controllers and `/api/v1/...` API controllers for the same feature.
- Header/sidebar behavior lives in `web/WEB-INF/jsp/common/header.jsp`, with extra model attributes supplied by `NotificationViewAdvice`.
- Many pages use `sessionScope.AUTH_USER.hasRole(...)` directly in JSP.
- When fixing role or access bugs, update both backend validation and JSP visibility if needed.
- When changing switch account behavior, keep it read-only: no role creation, no assignment validation.
- After JSP/CSS/JS changes, Tomcat may still serve old deployed output until redeploy.

## Git And Generated Output

Do not edit generated build output unless specifically debugging deployment output:
- `build/`
- `dist/`
- nested copied project folders

Prefer editing source files under:
- `src/`
- `web/`
- `database/`

The working tree may contain user changes. Inspect `git status --short` before editing and avoid reverting unrelated work.
