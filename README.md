# Manga Creation & Publishing Management System

A web-based platform that manages the complete production lifecycle of a manga series — from initial proposal submission and editorial board voting, through chapter task coordination and manuscript review, to reader ranking and final publication decisions.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Problem Statement](#problem-statement)
3. [Project Objectives](#project-objectives)
4. [Team Members](#team-members)
5. [System Roles](#system-roles)
6. [Technology Stack](#technology-stack)
7. [Core Features](#core-features)
8. [Business Rules](#business-rules)
9. [System Workflow](#system-workflow)
10. [Repository Structure](#repository-structure)
11. [Prototype](#prototype)

## Project Overview

The **Manga Creation & Publishing Management System** is a role-based web application designed to digitalize and centralize the manga production pipeline. The system covers every operational phase from series proposal creation to editorial board decision-making, providing structured workflows, automated notifications, immutable audit trails, and a vote-based ranking engine.

## Problem Statement

The current manga production process is fragmented across multiple unconnected tools, resulting in operational inefficiencies and accountability gaps.

| Current Practice | Resulting Problem |
|-----------------|-------------------|
| Drafts shared via Google Drive or WeTransfer | No unified versioning; confusion over latest files |
| Task assignments communicated via Zalo or Line | No page-level progress tracking |
| Editorial feedback delivered via email | No structured annotation history or revision trail |
| Series rankings calculated manually in spreadsheets | Calculation errors and delayed announcements |
| Board decisions made without a formal record | Disputed outcomes; lack of accountability |

## Project Objectives

- Provide a centralized platform for tracking manga production progress at the individual page level.
- Digitalize the Editorial Board's series proposal voting process with quorum enforcement and conflict-of-interest controls.
- Replace email-based manuscript feedback with an inline annotation workflow and manuscript version history.
- Automate series ranking calculation from reader vote data, eliminating manual spreadsheet errors.
- Maintain an immutable audit trail for all votes, manuscript versions, and board decisions.
- Issue automated deadline alerts to prevent late submissions and missed publication dates.
- Enforce role-based access control (RBAC) across all system functions and sensitive data.

## Team Members


| Name | ID | Role | Responsibilities |
|------|----|------|-----------------|
| Nguyễn Hồng Quân | SE203653 | Project Leader | Sprint planning, team coordination, milestone tracking, final delivery |
| Nguyễn Đình Cao Thắng| SE203709 | Frontend Developer | JSP/Bootstrap UI, role-based view rendering, form validation, notification display |
| Nguyễn Thành Lộc | SE203692 | Backend Developer | Spring Boot API, business logic implementation, database schema design, BR enforcement |
| Vũ Nguyễn Trung Nguyên  | 	SE204969 | Backend Developer | Spring Boot API, business logic implementation, database schema design, BR enforcement |
| Phan Đức Thịnh | SE204966 | Backend Developer |  Spring Boot API, business logic implementation, database schema design, BR enforcement |

## System Roles

| Actor | Description |
|-------|-------------|
| Mangaka | Series author. Creates and submits proposals, plans chapters, assigns page tasks to Assistants, approves completed pages, and submits manuscripts for editorial review. |
| Assistant | Production collaborator. Receives page task assignments from the Mangaka, performs the assigned work, and submits completed tasks for approval. |
| Tantou Editor | Assigned series editor. Reviews submitted manuscripts, adds inline page-level annotations, and approves or rejects manuscript versions with mandatory written feedback. |
| Editorial Board Member | Votes on new series proposals, enters reader vote data each ranking period, and participates in Decision Sessions for low-ranking series. Subject to conflict-of-interest restrictions. |
| System (Automated) | Executes scheduled jobs: closes voting windows, calculates rankings on period close, flags At-Risk chapters, sends reminder notifications, and archives audit data. |

## Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Frontend | JSP + Bootstrap | Responsive, role-aware web interface |
| Backend | Spring Boot | RESTful API, business logic, BR enforcement |
| Authentication | Spring Security | Role-based access control (RBAC) |
| Database | MySQL | Relational data storage |
| File Storage | Local / MinIO | Manuscript and sample chapter file uploads |
| Notifications | JavaMailSender | Deadline alerts and voting reminders |
| Ranking Engine | Backend Service Layer | Vote-based ranking calculation |
| Prototype | v0 / Lovable | UI/UX design (prototype phase) |

**Application Architecture:** `Controller -> Service -> Repository -> Database`

## Core Features

### Mangaka

- Submit a series proposal containing title, genre, synopsis, and at least one sample chapter.
- Limit of one active proposal (Draft, Submitted, or Voting) at any given time.
- Create chapters with submission deadlines enforced at a minimum of 14 days before the publication date.
- Assign, reassign, and revoke page tasks for enrolled Assistants; self-assignment is prohibited.
- Approve or reject submitted page tasks, with a maximum of three rejections before automatic escalation to the Tantou Editor.
- Submit a manuscript once a chapter reaches 100% page task approval.
- Monitor real-time chapter completion percentage and At-Risk flags.

### Assistant

- View only page tasks assigned directly to the logged-in user.
- Transition task status through `Pending -> In-Progress -> Submitted`.
- Revise and resubmit tasks rejected by the Mangaka.
- Receive automated reminder notifications 24 hours before a task due date.
- View earnings information in read-only mode.

### Tantou Editor

- Receive manuscript submissions for assigned series.
- Add inline annotations referencing specific page numbers within a manuscript version.
- Approve or reject manuscripts with mandatory written feedback within a 48-hour SLA (reminder sent at 36 hours).
- Monitor chapter progress and studio production status.
- Receive escalated page tasks after three Mangaka rejections.
- Restricted from voting on proposals for series they personally manage.

### Editorial Board Member

- Vote on new series proposals: Approve, Reject (with mandatory written reason), or Abstain.
- Enter reader vote data during active ranking periods.
- Participate in Decision Sessions for bottom-20% ranked series: Continue, Cancel (with mandatory written justification), or Change Publication Type.
- Receive automated reminder notifications 24 hours before a voting window closes.
- Subject to conflict-of-interest enforcement preventing participation in sessions where a disqualifying relationship exists.

### Admin / System

- Manage user accounts and role assignments.
- Monitor active proposals, chapters, and decision sessions.
- Access full audit logs for votes, manuscript versions, and board decisions.
- Initialize and manage ranking vote periods.
- View system dashboards and export operational reports.

## Business Rules

### Phase 1–2: Proposal Drafting and Submission

| ID | Rule |
|----|------|
| BR-PRO-01 | A Mangaka may have at most one active Proposal (Draft, Submitted, or Voting) at any given time. |
| BR-PRO-02 | A valid Proposal must include: title, genre, synopsis, and at least one uploaded sample chapter. |
| BR-PRO-03 | A Proposal in Draft state is visible and editable only by its Mangaka. |
| BR-SUB-01 | A Mangaka may not access the voting function for their own Proposal. |
| BR-SUB-02 | A Tantou Editor may not vote on a Proposal they are assigned to manage. |
| BR-SUB-03 | State transitions must follow the sequence: `Draft -> Submitted -> Voting -> Approved / Rejected / Deferred`. |
| BR-SUB-04 | All Proposal content becomes read-only once the Proposal enters Voting state. |
| BR-SUB-05 | The voting window is exactly 7 days from the moment of submission; the system opens and closes it automatically. |
| BR-SUB-06 | The system sends a reminder to Board Members who have not yet voted, 24 hours before the voting window closes. |

### Phase 3–4: Voting and Resolution

| ID | Rule |
|----|------|
| BR-VOT-01 | Each Board Member may cast exactly one vote per Proposal; votes are immutable once submitted. |
| BR-VOT-02 | A Board Member selecting Reject must provide a written reason; an empty reason field triggers a validation error. |
| BR-VOT-03 | A minimum of 3 valid votes (quorum) is required to reach a decision; failure to reach quorum results in Deferred. |
| BR-VOT-04 | The system may close voting early if all eligible members have voted before the 7-day window expires. |
| BR-VOT-06 | All voting history is immutable and stored in an append-only audit log. |
| BR-VOT-07 | If a Board Member is removed during an active voting session, their previously cast vote remains valid. |
| BR-RES-01 | On Approval, the system atomically creates a Series record with Active status and assigns the designated Tantou Editor. |
| BR-RES-02 | A Rejected Proposal must wait a minimum of 30 days before resubmission. |
| BR-RES-03 | A Deferred Proposal is automatically queued for the next available voting cycle. |

### Phase 5–6: Chapter Planning and Task Execution

| ID | Rule |
|----|------|
| BR-CHP-01 | Only the Mangaka of a Series may create new chapters for that Series. |
| BR-CHP-02 | A chapter's submission deadline must be at least 14 days before the chapter's publication date. |
| BR-CHP-03 | Only the Mangaka may assign, reassign, or revoke page tasks. |
| BR-CHP-04 | An Assistant may only be assigned page tasks within chapters of series they are enrolled in. |
| BR-CHP-05 | A Mangaka may not assign page tasks to themselves. |
| BR-CHP-06 | A valid page task must include: pageRange, taskType, dueDate, and assistantID. |
| BR-CHP-07 | Page task page ranges within the same chapter must not overlap. |
| BR-CHP-08 | A page task's dueDate must not exceed the deadline of its parent chapter. |
| BR-TSK-01 | An Assistant may only submit a page task when its status is In-Progress. |
| BR-TSK-03 | Reassigning a page task to a different Assistant resets its status to Pending and clears the previous submission. |
| BR-TSK-05 | A Mangaka may reject a page task a maximum of 3 times; after the third rejection, the task is escalated to the Tantou Editor. |
| BR-TSK-06 | An Approved page task may not be rolled back; a new task must be created to replace it. |
| BR-TSK-08 | If a page task is not updated within 3 days of assignment, the system flags it as Delayed and notifies the Mangaka. |
| BR-TSK-09 | A reminder is sent 24 hours before a page task's dueDate. |
| BR-TSK-10 | If a page task's dueDate has passed and the task is not Approved, the system automatically marks it Overdue. |
| BR-TSK-11 | Chapter Completion % = (Approved Page Tasks / Total Page Tasks) × 100%; recalculated on every task status change. |
| BR-TSK-12 | A chapter with less than 50% progress after consuming 70% of its deadline window is automatically flagged At Risk, and the Tantou Editor is notified. |

### Phase 7: Manuscript and Editorial Review

| ID | Rule |
|----|------|
| BR-MAN-01 | A chapter is considered Complete only when 100% of its page tasks are in Approved status. |
| BR-MAN-02 | A chapter may only be submitted for Editorial Review when its status is Complete. |
| BR-MAN-03 | A chapter has exactly one active Manuscript at any given time; resubmission creates a new version and archives the previous one. |
| BR-MAN-04 | The system records a timestamp for every Manuscript version submitted. |
| BR-MAN-05 | The Tantou Editor must submit review feedback within 48 hours of receiving a Manuscript; a reminder is sent at 36 hours. |
| BR-MAN-06 | The Tantou Editor must provide written feedback when rejecting a Manuscript. |
| BR-MAN-07 | Each inline annotation must reference a specific page number within the Manuscript version it belongs to. |
| BR-MAN-08 | Inline annotations belong exclusively to one Manuscript version and do not carry over to subsequent versions. |
| BR-MAN-09 | The system blocks new Manuscript submissions while a review cycle for that chapter is still active. |
| BR-MAN-10 | After a Manuscript is rejected, the Mangaka has a maximum of 3 days to revise and resubmit. |
| BR-MAN-11 | An Approved Manuscript becomes read-only and may not be edited. |

### Phase 8: Ranking

| ID | Rule |
|----|------|
| BR-RNK-01 | Vote data is only accepted during an active vote period. |
| BR-RNK-02 | Only Editorial Board Members may enter ranking vote data. |
| BR-RNK-03 | The total vote count for a series in a given period must not exceed the total reader count. |
| BR-RNK-04 | Vote count values must not be negative. |
| BR-RNK-05 | Reader count must be greater than zero before ranking calculation is triggered, to prevent division by zero. |
| BR-RNK-06 | A Board Member may not submit duplicate vote entries for the same series in the same vote period. |
| BR-RNK-09 | Ranking Score = (voteCount / readerCount) × 100%; ties broken first by total vote count, then by earlier publication date. |
| BR-RNK-10 | Historical ranking data for every completed vote period must be archived by the system. |
| BR-RNK-11 | A closed vote period may not be reopened once ranking calculation has started. |

### Phase 9–10: Decision and Archive

| ID | Rule |
|----|------|
| BR-DEC-01 | Only eligible Board Members (active status, no conflict of interest) may participate in a Decision Session. |
| BR-DEC-02 | A Board Member with a conflict of interest (the Tantou Editor of the series under review) may not vote. |
| BR-DEC-03 | Each Board Member may cast exactly one vote per Decision Session; votes are immutable once submitted. |
| BR-DEC-04 | A Decision result may only be finalized once quorum of at least 3 valid votes is reached. |
| BR-DEC-05 | A Cancellation decision must include a written justification; an empty field triggers a validation error. |
| BR-DEC-06 | All decision-related actions must be recorded with the actor's userID and a timestamp in the audit log. |
| BR-DEC-07 | Confirmed Decision history may not be edited or deleted. |
| BR-ARC-01 | A Cancelled Series may not accept new chapter submissions. |
| BR-ARC-02 | A Cancelled Series may not participate in any further Decision Review sessions. |

## System Workflow

The system operates across ten sequential phases.

**Phase 1 — Proposal Drafting**

Mangaka creates a Proposal in Draft state. The proposal is private to the Mangaka. A maximum of one active Proposal is permitted per Mangaka at any time.

**Phase 2 — Proposal Submission and Voting Setup**

Mangaka submits the Proposal: `Draft -> Submitted -> Voting`. All proposal content is locked read-only. A 7-day voting window starts automatically. Eligible Board Members are notified. Conflict-of-interest checks are applied; the Mangaka and the assigned Tantou Editor are blocked from voting.

**Phase 3 — Editorial Board Voting**

Board Members vote: Approve, Reject (written reason required), or Abstain. A minimum of 3 valid votes (quorum) is required. The system may close voting early if all eligible members have voted. A reminder notification is sent 24 hours before the window closes to members who have not yet voted.

**Phase 4 — Proposal Resolution**

- Approved: System atomically creates a Series with Active status and assigns the Tantou Editor.
- Rejected: A 30-day cooldown period is enforced before resubmission is permitted.
- Deferred: The Proposal is automatically queued for the next voting cycle.

**Phase 5 — Chapter Planning and Task Assignment**

Mangaka creates a Chapter with a deadline at least 14 days before the publication date. Page tasks are assigned to enrolled Assistants. The system validates that page ranges do not overlap and that each task dueDate does not exceed the chapter deadline.

**Phase 6 — Task Execution and Collaboration**

Assistant transitions task status: `Pending -> In-Progress -> Submitted`. Mangaka approves or rejects (maximum 3 rejections before escalation to the Tantou Editor). Chapter Completion % is recalculated on every task status change. An At-Risk flag is raised if progress falls below 50% after 70% of the deadline has been consumed.

**Phase 7 — Manuscript Submission and Editorial Review**

Mangaka submits the Manuscript only when the Chapter status is Complete (100% tasks Approved). One active Manuscript version is maintained per chapter at any time. The Tantou Editor reviews within 48 hours and annotates by page number. If rejected, the Mangaka has 3 days to revise and resubmit. An Approved Manuscript is locked as read-only.

**Phase 8 — Ranking and Continuation Review**

Board Members enter reader vote data during the active period. The system validates all entries. On period close, the Ranking Score is calculated and historical data is archived. Series in the bottom 20% are flagged, and a Decision Session is triggered.

**Phase 9 — Editorial Board Decision Session**

Only eligible Board Members participate; conflict-of-interest members are blocked. Members vote: Continue, Cancel (written justification required), or Change Publication Type. A quorum of at least 3 valid votes is required to finalize the result. All actions are recorded in the immutable audit log.

**Phase 10 — Closure and Archive**

A Cancel decision transitions the Series to Cancelled status; no new chapter submissions are accepted. All manuscripts, votes, and decisions are archived. The immutable audit trail is maintained across all phases.

**End-to-end state summary:**

```
Draft -> Submitted -> Voting -> Approved | Rejected | Deferred
-> Series Active -> Chapter Planning -> Task Assignment
-> Task Execution -> Chapter Complete (100%)
-> Manuscript Review -> Approved Manuscript
-> Ranking Period -> Bottom-20% Decision
-> Continue | Cancel | Change Type -> Archive
```

## Repository Structure

```
manga-publishing-system/
|
|-- docs/
|   |-- business-rules/
|   |   `-- BR_by_Phase_v2.docx
|   |-- research/
|   |   `-- Manga_project_research.docx
|   |-- diagrams/
|   |   |-- system-flow.png
|   |   |-- er-diagram.png
|   |   `-- use-case-diagram.png
|   |-- use-cases/
|   `-- meeting-notes/
|
|-- prototype/
|   |-- wireframes/
|   `-- prototype-link.md
|
|-- src/
|   |-- main/
|   |   |-- java/
|   |   |   `-- com/manga/system/
|   |   |       |-- controller/
|   |   |       |-- service/
|   |   |       |-- repository/
|   |   |       |-- model/
|   |   |       |-- dto/
|   |   |       `-- config/
|   |   |-- resources/
|   |   |   |-- application.properties
|   |   |   `-- templates/
|   |   `-- webapp/
|   |       |-- WEB-INF/views/
|   |       `-- static/
|   |           |-- css/
|   |           `-- js/
|   `-- test/
|       `-- java/com/manga/system/
|
`-- README.md
```

## Prototype

The UI/UX prototype is built using v0 / Lovable and covers the primary screens for each system role.

| Item | Link |
|------|------|
| Interactive Prototype (v0 / Lovable) | *(Insert prototype URL)* |
| Wireframes (Figma) | *(Insert Figma URL)* |

> Replace the placeholder links above with actual URLs once the prototype is published.

---

*This project is developed as an academic software engineering capstone. All rights reserved by the project team.*
