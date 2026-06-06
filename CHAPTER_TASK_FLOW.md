# Chapter & Task Flow Notes

Tai lieu nay giai thich nhanh cac phan code lien quan den chapter, page, task va anh import sang manuscript.

## 1. Cac file chinh

### Controller API

- `src/java/manga/controller/api/chaptertask/ChapterApiController.java`
  - Tao chapter.
  - Submit chapter sang editorial review qua endpoint `/api/v1/chapters/{id}/submit-review`.

- `src/java/manga/controller/api/chaptertask/PageApiController.java`
  - Lay danh sach page slot cua chapter.
  - Tao page slot.
  - Mangaka upload anh truc tiep cho page qua `/api/v1/pages/{pageId}/upload`.
  - Khi Mangaka upload page dat stage cuoi `LETTERING`, code sync anh final vao `ChapterImage`.

- `src/java/manga/controller/api/chaptertask/PageTaskApiController.java`
  - Mangaka giao task cho assistant.
  - Assistant submit task.
  - Mangaka approve/reject task.
  - Reassign/delete/extend task.

- `src/java/manga/controller/api/chaptertask/ChapterImageApiController.java`
  - Assistant upload anh task vao `ChapterImage`.
  - List anh theo chapter/task.
  - Delete/deactivate anh.

### Repository

- `src/java/manga/repository/chaptertask/ChapterRepository.java`
  - SQL doc/ghi bang `Chapter`.
  - `submitForReview(...)` chi cho submit khi chapter 100%.
  - Khi submit review, backfill cac page final tu bang `Page` sang `ChapterImage` neu con thieu.

- `src/java/manga/repository/chaptertask/PageRepository.java`
  - SQL doc/ghi bang `[Page]`.
  - Quan ly page slot: page number, imageUrl, uploadedBy, status, completedStage.
  - `markUploaded(...)`: Mangaka upload truc tiep.
  - `promoteTaskImage(...)`: anh task duoc approve thi day vao page final/current.

- `src/java/manga/repository/chaptertask/PageTaskRepository.java`
  - SQL doc/ghi bang `PageTask`.
  - Tao task, update status, approve/reject, reassign/delete.
  - `refreshChapterProgress(...)`: tinh lai completion percent cua chapter dua tren `Page.completedStage`.
  - `updateStatusByAssistant(...)`: assistant submit task; hien tai bat buoc task phai co du anh trong `ChapterImage`.
  - `approveByMangaka(...)`: Mangaka approve task, sau do promote anh task sang bang `[Page]`.

- `src/java/manga/repository/chaptertask/ChapterImageRepository.java`
  - SQL doc/ghi bang `ChapterImage`.
  - Assistant upload anh task vao day.
  - Mangaka upload final page truc tiep thi cung sync vao day.
  - Moi `chapterId + pageNumber + imageType = PAGE` chi nen co 1 anh active moi nhat.

### JSP / JS

- `web/WEB-INF/jsp/chapter/detail.jsp`
  - Trang detail cua chapter.
  - Hien page grid, task table, modal giao task, upload page.
  - Shift-click dung de chon page da upload/da co anh de giao task tiep stage sau.

- `web/assets/page-submission.js`
  - Trang assistant lam task.
  - Upload/replace/delete anh task.
  - Submit task khi du anh.

## 2. Bang du lieu quan trong

### `Chapter`

Luu metadata cua chapter:

- `id`
- `seriesId`
- `chapterNumber`
- `title`
- `status`
- `submissionDeadline`
- `completionPct`
- `atRisk`

`completionPct` khong phai tinh bang so task approved. No duoc tinh dua tren progress tung page trong bang `[Page]`.

### `[Page]`

Luu trang hien tai cua chapter:

- `chapterId`
- `pageNumber`
- `imageUrl`
- `uploadedBy`
- `status`
- `completedStage`

`completedStage` gom:

- `SKETCHING`
- `INKING`
- `COLORING`
- `SCREENTONE`
- `LETTERING`

Khi page dat `LETTERING`, page duoc xem la hoan thanh.

### `PageTask`

Luu task giao cho assistant:

- `chapterId`
- `assistantId`
- `pageRangeStart`
- `pageRangeEnd`
- `taskType`
- `dueDate`
- `status`

Status hay gap:

- `IN_PROGRESS`: assistant dang lam.
- `SUBMITTED`: assistant da nop, cho Mangaka approve/reject.
- `APPROVED`: Mangaka da approve.
- `REJECTED`: bi reject, can sua lai.
- `OVERDUE`: qua han.
- `REASSIGNED`, `DELETED`, `CANCELLED`: task da dong.

### `ChapterImage`

Luu anh san xuat da upload:

- `chapterId`
- `pageTaskId`
- `uploadedBy`
- `imageType`
- `pageNumber`
- `fileUrl`
- `isActive`
- `note`

Manuscript import anh tu bang nay. Vi vay neu chapter 100% nhung `ChapterImage` thieu page, manuscript se import thieu.

## 3. Flow Mangaka tu upload page

1. Mangaka vao chapter detail.
2. Tao page slot neu chua co.
3. Upload anh cho page va chon stage da hoan thanh.
4. API vao `PageApiController.uploadImage(...)`.
5. `PageRepository.markUploaded(...)` update bang `[Page]`.
6. Neu page sau upload dat `LETTERING`, `ChapterImageRepository.syncFinalPageUpload(...)` se sync anh final vao `ChapterImage`.
7. `PageTaskRepository.refreshChapterProgress(...)` tinh lai `Chapter.completionPct`.

Ket qua:

- `[Page]` co anh current/final de hien thi chapter.
- `ChapterImage` co anh final de manuscript import.

## 4. Flow giao task cho assistant

1. Mangaka chon page trong chapter detail.
2. Bấm `Gan task`.
3. Frontend goi `POST /api/v1/chapters/{chapterId}/tasks`.
4. `PageTaskApiController.create(...)` goi `PageTaskRepository.create(...)`.
5. Repository validate:
   - User phai la owner Mangaka cua chapter.
   - Assistant phai hop le.
   - Page range khong overlap active task.
   - Page chua full `LETTERING`.
   - Due date hop le.
6. Tao row `PageTask` voi status `IN_PROGRESS`.
7. Gui notification cho assistant.

## 5. Flow assistant upload va submit task

1. Assistant mo task detail.
2. Upload anh tung page cua task.
3. JS `page-submission.js` goi:

```text
POST /api/v1/chapters/{chapterId}/images
```

Kem:

- `pageTaskId`
- `pageNumber`
- `imageType = PAGE`
- file anh

4. `ChapterImageApiController.upload(...)` luu file va goi `ChapterImageRepository.upload(...)`.
5. Anh duoc insert vao `ChapterImage`.
6. Khi assistant bam submit, API goi `PageTaskRepository.updateStatusByAssistant(...)`.
7. Backend kiem tra task co du anh active trong `ChapterImage` cho range page chua.
8. Neu du, task status thanh `SUBMITTED`.

Luu y: Backend check `ChapterImage`, khong chi tin frontend. Neu thieu anh trong DB thi khong cho submit.

## 6. Flow Mangaka approve task

1. Mangaka mo task submitted.
2. Bam approve.
3. `PageTaskRepository.approveByMangaka(...)` validate:
   - User la owner Mangaka.
   - Task dang `SUBMITTED`.
4. Task status thanh `APPROVED`.
5. `promoteTaskImagesToChapter(...)` lay anh tu `ChapterImage`.
6. `PageRepository.promoteTaskImage(...)` day anh approve sang bang `[Page]`.
7. `refreshChapterProgress(...)` tinh lai progress.

Ket qua:

- Task da approved.
- Page trong chapter detail hien anh approved.
- Stage cua page tien them theo `taskType`.

## 7. Flow chapter 100% va submit review

1. `refreshChapterProgress(...)` tinh completion dua tren `[Page].completedStage`.
2. Khi tat ca page dat `LETTERING`, chapter len 100% va status co the thanh `COMPLETE`.
3. Mangaka submit chapter review.
4. `ChapterRepository.submitForReview(...)` chi update sang `EDITORIAL_REVIEW` neu:
   - Chapter thuoc Mangaka.
   - `completionPct >= 100`.
   - Status dang `IN_PROGRESS` hoac `COMPLETE`.
5. Sau khi submit review, `ChapterImageRepository.backfillFinalPageUploads(...)` bo sung cac page final trong `[Page]` ma `ChapterImage` con thieu.

Backfill nay quan trong cho case:

- Chapter co 5 page.
- Mangaka tu lam 4 page dau.
- Assistant lam page cuoi.
- Neu chi assistant upload moi tao `ChapterImage`, manuscript se chi thay page cuoi.
- Backfill dam bao 4 page Mangaka tu lam cung co record trong `ChapterImage`.

## 8. Manuscript lien quan the nao

Manuscript workspace import tu `ChapterImage`, khong import truc tiep tu `[Page]`.

Ly do:

- `ChapterImage` la nguon anh san xuat/import.
- Giu duoc uploadedBy, pageTaskId, uploadedAt, active/inactive.
- De truy vet anh nao da duoc upload tu task hay tu Mangaka final upload.

Vi vay chapter/task/page flow phai dam bao final page nao cung co record active trong `ChapterImage`.

## 9. Nguyen tac tranh bug duplicate/import sai

- Moi page final chi nen co 1 active `ChapterImage` voi:

```text
chapterId + pageNumber + imageType = PAGE + isActive = 1
```

- Khi upload/sync anh moi cho cung page, code deactivate anh active cu truoc.
- Khi manuscript query candidate pages, nen lay latest active theo tung `pageNumber` de chong du lieu cu da bi duplicate.

## 10. Cac bug da gap gan day

### Bug: chapter 100% nhung manuscript bao `No chapter pages found to import`

Nguyen nhan:

- `[Page]` co final image.
- `ChapterImage` khong co final image.
- Manuscript chi import tu `ChapterImage`.

Huong fix dung:

- Sync/backfill final page tu chapter/page flow sang `ChapterImage`.

