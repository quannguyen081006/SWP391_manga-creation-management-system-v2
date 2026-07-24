# Project Progress — Manga Creation & Publishing Management System

Last analyzed: 2026-07-24, based on the `main` branch (125 commits, 2026-06-01 → 2026-07-24) and a direct read of the source tree.

## 1. Overview

This is an academic capstone-style project (SWP391) — a Java 8 / Spring MVC / JSP web application for managing an end-to-end manga editorial pipeline: proposal intake, board voting, series/chapter production, page-level task assignment, manuscript review, ranking/analytics, decisions, notifications, and payroll ("salary"). No ORM — JDBC repositories talk directly to SQL Server.

Overall assessment: **the application is feature-complete for a first working release and is in an active hardening/polish phase**, not early-stage. Every major module described in `AGENTS.md` has controllers, services, repositories, and JSPs wired end-to-end. Recent commit history is dominated by bug fixes, refactors, seed-data correctness, and deployment setup rather than net-new features — a sign the team has moved from building to stabilizing.

## 2. Evidence Used

- `git log` (125 commits) — commit message trends by date.
- Full read of `AGENTS.md` and `CHAPTER_TASK_FLOW.md` (existing internal docs).
- Directory census of `src/java/manga/{controller,service,repository}` and `web/WEB-INF/jsp`.
- Grep for `TODO|FIXME|placeholder|not implemented` across Java/JSP/JS.
- Spot-read of `ManuscriptVersionService.java`, enum definitions, scheduler jobs, `Dockerfile`/`docker-entrypoint.sh`.

## 3. Module-by-Module Status

### Proposal & Board Voting — Mature
- `ProposalController`, `ProposalApiController` → `ProposalService` → `ProposalRepository`.
- Full status lifecycle: `DRAFT → UNDER_REVIEW → BOARD_REVIEW → REVISION_REQUESTED → APPROVED/REJECTED`.
- Board voting is a real, non-trivial feature: `ProposalBoardRound` / `ProposalBoardRoundVoter` tables, quorum rules, round history — confirmed by extensive SQL in `ProposalRepository.java` (round open/close, vote counting, resubmission round tracking) and a dedicated `ProposalBoardVotingScheduler` (auto-closes rounds every 15 min, hourly/5-min housekeeping jobs).
- Recent commits (`2026-07-17 fix business rule in decision vote quorum`, `2026-06-16 Update proposal settings UI and notifications`) show this module is still being refined for correctness, not built from scratch.

### User & Role Management — Mature, recently simplified
- `ModuleWebController` + `UserApiController` → `UserAdminRepository`/`UserRepository`; enforced by `RoleCombinationValidator`.
- Role rules are settled and documented (single-role `MANGAKA`/`ASSISTANT`, single-role `ADMIN`, only `TANTOU_EDITOR + EDITORIAL_BOARD` combo allowed).
- `2026-06-16 Remove switch role code and update user form` — the team deliberately **removed** switch-role mutation logic from the UI to keep the dev/test "switch account" feature strictly read-only. This is a completed simplification, not an in-progress item.

### Authentication — Mature
- `AuthController`/`AuthApiController` → `AuthService`, enforced by `AuthInterceptor`.
- `2026-06-07 Simplify public auth route checks` and `2026-07-10 Fix: remove wrong data-confirm popup on profile/change-password forms` indicate this is stable, in cleanup mode.
- **Single active session per account (2026-07-24, uncommitted at time of writing)** — chặn hai người dùng chung một tài khoản đăng nhập đồng thời; người đăng nhập sau đá người trước ra.
  - New `ActiveSessionRegistry` (`src/java/manga/web/`) — in-memory map `userId → sessionId hợp lệ duy nhất`. Mỗi lần login thành công ghi đè sessionId mới; phiên cũ lập tức mất hiệu lực.
  - `AuthController.login` gọi `register()` (kèm invalidate session cũ để chống session fixation); `AuthController.logout` gọi `unregister()` (chỉ gỡ đúng phiên của mình).
  - `AuthApiController.login`/`logout` (`/api/v1/auth/*`) được vá để cũng `register()`/`unregister()` — trước đó đường API đặt `AUTH_USER` mà không ghi registry, tạo lỗ hổng lách luật một-phiên.
  - `AuthInterceptor` (BƯỚC 1b) kiểm `isCurrent()` mỗi request: phiên không còn hợp lệ → invalidate + redirect `/login?reason=session_replaced` (web) hoặc JSON 401 "signed in elsewhere" (API).
  - `login.jsp` hiện thông báo tiếng Việt khi `reason=session_replaced`.
  - Giới hạn đã biết: registry nằm trong bộ nhớ 1 tiến trình (đủ cho 1 instance Render); nếu scale nhiều instance sau load balancer cần chuyển sang DB/Redis. Sau khi restart Tomcat, phiên đang sống đầu tiên gặp lại được nhận làm "chủ".
  - Chưa build/test local được trong môi trường phân tích (không có JDK thật, chỉ JRE) — cần verify thủ công qua NetBeans/Tomcat với 2 trình duyệt.

### Series / Chapter / Page / Task Production — Mature, actively refactored
- This is the most actively maintained module in the whole repo (see `CHAPTER_TASK_FLOW.md` for the full data-flow spec).
- As of **2026-07-15**, the chapter/page/chapter-image API controllers were routed through a proper service layer (`service/chaptertask/*`) — commits `refactor(chaptertask): route page/chapter-image controllers through a service layer`, `clean mvc v1`/`v2`, `fix architecture`. Before that, logic lived more directly in repositories/controllers.
- Dead-code cleanup has been happening in parallel: `2026-07-15 chore(chaptertask): drop dead URL-upload path and unused COVER/REFERENCE handling`, `2026-07-15 chore(chaptertask): remove dead legacy-schema fallback in task lifecycle`, `2026-07-14 chore(chapter): drop dead schema-readiness fallback in ChapterRepository` — these read as removal of transitional/legacy-schema compatibility code once the new schema was fully rolled out, i.e. a completed migration.
- `2026-07-03 feat(chaptertask): image dedup, submission history, and page revision rollback` and `2026-07-13 fix(chapter): sync ChapterImage on rollback` / `fix(chapter): block page rollback after submitted for editorial review` show the page-rollback and image-dedup feature set was added and is now being edge-case-hardened.
- Known invariant enforced by design (see `progress` in `CHAPTER_TASK_FLOW.md`): manuscript import reads only from `ChapterImage`, and a backfill step guards against the "chapter 100% but manuscript has no pages" failure mode. This bug class has a documented, applied fix.

### Manuscript Review Workspace — Mostly mature, with 3 confirmed stub methods
- `ManuscriptVersionApiController` → `ManuscriptVersionService` (956 lines, the largest service in the codebase) → `ManuscriptVersionRepository`/`ManuscriptPageRepository`/`ManuscriptProductionLockRepository`.
- Full status lifecycle implemented: `DRAFT → IN_PROGRESS → SUBMITTED_FOR_REVIEW → UNDER_REVIEW → APPROVED/PUBLISHED/REJECTED/ARCHIVED`, with `isEditable()/isInReview()/isImmutable()` helpers actually used for gating (production lock, BR-2 through BR-7 business rules referenced in code comments).
- Annotation system (`AnnotationServiceV2`, 513 lines) is a full thread-based review workflow: `AnnotationStatus` (OPEN/IN_PROGRESS/RESOLVED/DISMISSED), `AnnotationSeverity` (CRITICAL→SUGGESTION), `AnnotationCategory` (ART/STORY/PACING/DIALOGUE/PANELING/TYPOGRAPHY/OTHER). Recent fixes: `2026-06-09 add delete annotation function for tantou`, `2026-06-09 fix bug mangaka cannot use function scroll to page and focus marker`.
- **Confirmed incomplete / stubbed code** in `ManuscriptVersionService.java`:
  - `addPageSnapshot()` (line ~250-256): builds the snapshot image URL from a hardcoded `"/assets/images/chapter/" + chapterImageId + ".jpg"` pattern instead of querying the actual `ChapterImage` record — comment explicitly says *"for now use a placeholder approach... In production, this would query ChapterImage table"*. This is wired to a live endpoint (`POST /api/v1/manuscript-versions/{id}/pages`), so it's a real gap, not dead code.
  - `calculateChecksum()` (line ~953): returns `"placeholder-checksum-" + System.currentTimeMillis()` instead of a real file hash — snapshot integrity/checksum verification described in `ManuscriptPage.java`/`ManuscriptVersion.java` comments is not actually implemented.
  - `getPageAnnotations()` (line ~789-792): always returns an empty list ("This would delegate to AnnotationServiceV2... for now, return empty list as placeholder"). This is wired to `GET /api/v1/manuscript-versions/{id}/pages/{pageId}/annotations`, but no frontend caller was found in `manuscript-workspace.js` — likely a superseded/legacy endpoint kept for compatibility rather than a currently-relied-upon broken feature, but it should not be assumed to work if invoked.
- Comment markers `Phase 9 (Version Comparison)`, `Phase 10 (Review Dashboard)`, `Phase 11 (Approval Finalization)` are all present and implemented (not just planned) — the phase numbering is a leftover from the original design doc, not an indicator of missing phases.

### Ranking / Analytics — Mature, currently being refactored into a service layer
- `RankingApiController`, `ModuleWebController`, `MangakaPerformanceController` → repositories (`RankingRepository`, `MangakaRankingRepository`, `RankingCsvUploadRepository`).
- CSV import pipeline exists (`RankingCsvImportService`, sample data under `/csv`) with dedicated test fixtures (`fasleInputData.csv`, `missingEntry.csv`, `negativeVoteEntry.csv`, `readerCountEqual0.csv`) — suggests validation edge cases were deliberately tested.
- **In-flight refactor as of 2026-07-23**: `2026-07-23 introduce service layer for ranking repository access` — controllers were likely calling the repository directly before; this is a recent, possibly not fully finished architectural cleanup. Immediately followed by `2026-07-24 refactor: gom style ranking module ve ranking.css, bo style trung lap` (CSS consolidation) and `2026-07-23 Decision + Ranking improve UX` — this module saw a concentrated push in the last 2 days of history, i.e. it's the most recently touched area at time of writing.
- `RankingPeriodScheduler` automates monthly period open/close (cron `0 0 0 1 * ?`) plus a 5-minute housekeeping tick.

### Decision Sessions — Mature
- `DecisionApiController` → `DecisionService` → `DecisionRepository`.
- `DecisionSessionStatus` (OPEN/CLOSED/DEFERRED), `DecisionResult` (CONTINUE/CANCEL/CHANGE_TYPE/DEFERRED) enums are complete and specific to a real business decision (continue/cancel/change series type).
- `2026-07-23 Decision + Ranking improve UX`, `2026-06-12 update chart.js for decision revenue snapshot` — decision revenue charting (`decision-chart.js`) exists and is maintained.

### Salary / Payroll — Mature, feature-complete as of early July
- `SalaryWebController` → `SalaryService`/`SalarySettingsService`/`TaskTypeRateService` → `salary/*Repository`.
- `2026-06-20 fully upgraded salary system` followed by iterative KPI fixes: `2026-07-07 feat(salary): bonus-only payroll with automatic monthly period rotation`, `2026-07-13 feat(salary): KPI based purely on on-time rate, drop rejection weighting`, `2026-07-13 fix(seed): May salary period should settle on the 5th, not the 1st`. Business-rule tuning (what counts toward KPI) continued into mid-July — the mechanics are done, the exact scoring formula was iterated on more than once.
- `SalaryScheduler` handles automatic period rotation.

### Notifications — Mature
- `NotificationWebController`/`NotificationApiController` → `NotificationService` → `NotificationRepository`.
- Latest commit in the repo (`2026-07-24 fix: notify`) touches this area, alongside `2026-07-11 Fix notification viewUrl redirect for manuscript-workspace and series` — this is a small, frequently-poked module (68-line service) rather than a large undertaking, consistent with "notification routing/deep-linking" being the recurring pain point rather than the notification mechanism itself.

### Audit Log — Present, minimally scoped
- `AuditLogRepository` exists; `2026-06-11 fix bug cannot insert audit-log` shows it's wired into real write paths, but there's no dedicated `AuditLogService` or controller — it's likely called inline from other services rather than being a first-class feature with its own UI.

## 4. Cross-Cutting / Infrastructure Status

- **Build & deploy**: originally NetBeans/Ant-only. As of `2026-07-16 build: make project buildable via plain javac/Docker for Render deployment`, the project now also builds via a plain `javac` + `Dockerfile` path (`tomcat:9-jdk8-temurin`), with `docker-entrypoint.sh` injecting `JDBC_URL`/`JDBC_USERNAME`/`JDBC_PASSWORD` from environment variables at container start. **This means the project has moved from "runs only on a dev machine via NetBeans" to "deployable to a cloud host (Render)"** — a meaningful infra milestone.
- **Database**: `database/schema.sql` is the authoritative full schema; `database/seed_v5.sql` is the working seed script, rewritten multiple times in mid-July for correctness (`fix QUOTED_IDENTIFIER errors`, `add cleanup block so seed_v5.sql can be re-run without redoing schema.sql`, several `pHash`/demo-data realism fixes). `schema_azure_DONT_TOUCH.sql` / `seed_azure_DONT_TOUCH.sql` were added `2026-07-18` as the frozen, deployed-environment copies — implying a live/shared Azure SQL instance now exists that must not be touched casually.
- **Testing**: `test/` directory is empty. There is no automated test suite (unit or integration) anywhere in the repo. All verification described in commit history is manual (seed data + UI walkthroughs) or ad hoc debugging (`2026-07-17 debug: log stack trace for unhandled exceptions`).
- **Documentation**: `AGENTS.md` and `CHAPTER_TASK_FLOW.md` are well-maintained, detailed internal docs (the latter in Vietnamese) — a good sign of team discipline given no formal test suite exists to encode this knowledge instead.
- **Academic artifact generation**: `tools/build_proposal_voting_study_doc.py` generates a Vietnamese-language study/exam-prep document (`docs/ON_TAP_MODULE_PROPOSAL_VOTING.md/.docx`) explaining the Proposal/Voting module for oral defense — confirms this is coursework with a defense/demo requirement, not just a portfolio project. The `docs/` output directory is not currently committed.

## 5. Recent Trajectory (last ~2 weeks of history)

The most recent commits cluster around three themes, suggesting the team is in a pre-submission/demo-prep stabilization pass:
1. **UX/visual polish** — `refactor JS`, `view Team`, ranking CSS consolidation, "Decision + Ranking improve UX" (2026-07-23/24).
2. **Architecture cleanup** — introducing a ranking service layer, removing dead legacy-schema fallbacks across chapter/task code (throughout July).
3. **Data/deployment correctness** — repeated seed-data fixes for demo realism, Azure-specific schema/seed files, Docker/Render deployability (2026-07-16 to 2026-07-18).

## 6. Known Gaps (actionable)

1. `ManuscriptVersionService.addPageSnapshot()` fabricates the image URL instead of reading it from `ChapterImage` — likely fine only if `chapterImageId` happens to coincide with the actual stored filename convention; worth verifying against real data before relying on it.
2. `ManuscriptVersionService.calculateChecksum()` is not a real checksum — any feature that depends on snapshot integrity verification is currently a no-op.
3. `ManuscriptVersionService.getPageAnnotations()` always returns empty — if any UI path still calls `GET /api/v1/manuscript-versions/{id}/pages/{pageId}/annotations`, it will silently show no annotations even when they exist. No caller was found in `manuscript-workspace.js` at time of analysis, but this should be re-checked before removing or fixing it.
4. No automated tests exist; regressions currently rely entirely on manual QA and seed-data walkthroughs.
5. The ranking service-layer refactor (2026-07-23) is very recent relative to this analysis — worth a closer look to confirm all ranking read paths were migrated consistently and none were missed.
6. Single-session enforcement (`ActiveSessionRegistry`) is in-memory per-process — correct for the current single-instance Render deploy, but silently stops enforcing "one session per account" across the cluster if the app is ever scaled horizontally. Move to a shared store (DB/Redis) before multi-instance deployment.
