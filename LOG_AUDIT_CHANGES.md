# Revision History and Audit Log Cleanup Summary

## Files touched

- `src/java/manga/model/ProposalHistory.java`: Added class-level comments explaining that ProposalHistory is both the proposal state-transition timeline and the Editorial Board vote record.
- `src/java/manga/model/ReviewDecision.java`: Clarified that ReviewDecision is a purpose-built manuscript review audit row, separate from general AuditLog.
- `src/java/manga/repository/AuditLogRepository.java`: Added comments explaining AuditLog as a general-purpose append-only table and noting that no JSP currently displays raw AuditLog rows.
- `src/java/manga/repository/ReviewDecisionRepository.java`: Added comments explaining append-only create/read behavior for manuscript review decisions.
- `src/java/manga/repository/ProposalRepository.java`: Added comments only around ProposalHistory insert/read/delete points, including the narrow board-vote undo exception.
- `src/java/manga/service/ProposalService.java`: Added comments on history-related repository delegation and board-vote undo history lookup.
- `src/java/manga/repository/DecisionRepository.java`: Added comments only at AuditLog insert call sites in session open, vote submit, quorum resolution, and series cancellation.
- `src/java/manga/repository/ManuscriptVersionRepository.java`: Added comments only around version-history reads and mapping fields such as `previousVersionId`, `revisionNotes`, and status timestamps.
- `src/java/manga/service/ManuscriptVersionService.java`: Added comments around ReviewDecision creation and read methods.
- `src/java/manga/controller/api/ManuscriptVersionApiController.java`: Clarified that the decisions endpoint returns ReviewDecision audit rows.
- `src/java/manga/controller/web/MainController.java`: Added one comment where ProposalHistory is wired into the proposal detail model.
- `src/java/manga/controller/api/DecisionApiController.java`: Added comments that opening/voting decision sessions triggers AuditLog writes through the repository.
- `web/WEB-INF/jsp/manuscript-version/history.jsp`: Added comments explaining the server-rendered version timeline, revision notes, and previous-version links.
- `web/WEB-INF/jsp/proposal/detail.jsp`: Added a comment above the Revision History table explaining the dual-purpose ProposalHistory source.
- `web/WEB-INF/jsp/manuscript-version/workspace.jsp`: Added a comment above the Version History sidebar loop.
- `LOG_AUDIT_CHANGES.md`: Added this summary and manual checklist.

## How to explain this to the professor

`ProposalHistory` is the proposal-specific history table. It records normal proposal transitions such as created, submitted, approved, rejected, and revision requested. It also stores Editorial Board votes, so the proposal detail page can show one chronological Revision History table instead of joining separate timeline and vote tables.

`ReviewDecision` is the manuscript-review history table. It records who approved or rejected a manuscript version, the decision type, the comment, and the decision time. It is purpose-built for manuscript review screens.

`AuditLog` is the general-purpose audit table. `DecisionRepository` writes to it when decision sessions are opened, votes are submitted, sessions are resolved, and a series is cancelled by a decision result. Unlike ProposalHistory and ReviewDecision, AuditLog is not currently displayed by any JSP; verifying it today means querying the database directly.

These tables are treated as append-only because history should explain what happened at the time, not be rewritten later. The narrow exceptions are intentional: board-vote undo deletes a just-cast ProposalHistory vote only inside the short undo window, and annotation delete is a workflow exception outside this pass. The rest of the history/audit design uses insert plus read.

No business rule, URL mapping, method signature, SQL behavior, JavaScript, library, framework, or build tool was changed.

## Skipped or not touched

- `src/java/manga/controller/web/ModuleWebController.java`: Skipped `manuscriptWorkspaceHistory`, `decisionDetail`, and `decisionVote` because this file was already modified before this pass began. I left it untouched as requested.
- I did not change Proposal, Manuscript, Decision validation/state-machine logic. Comments were added only at history/audit persistence or display points.

## Manual test checklist

- [ ] Proposal detail page still shows Revision History table with correct time/actor/role/action/attempt/note.
- [ ] Board vote submit/undo still updates history correctly.
- [ ] Manuscript version history page (`history.jsp`) still lists all versions with feedback/revision notes.
- [ ] Manuscript workspace sidebar "Version History" links still work.
- [ ] Decision session vote still writes an audit log entry (verify via DB query on `AuditLog`, since there's no UI page for it).
