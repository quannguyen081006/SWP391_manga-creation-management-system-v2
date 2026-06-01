# Editorial Workspace Stability Bug Audit

**Date:** 2026-06-01  
**Auditor:** Cascade  
**Scope:** Manuscript workspace creation, UX flow, and import pages functionality

---

## Executive Summary

Three critical bugs identified in the editorial workspace feature:

1. **Duplicate Workspace Creation** - Service allows multiple DRAFT workspaces per chapter
2. **Missing "Continue Workspace" UX** - UI always shows "Create Workspace" even when active workspace exists
3. **Import Pages SQL Failure** - Query references non-existent columns in ChapterImage table

---

## A. Active Workspace Audit

### Issue: Duplicate manuscript workspaces can be created for same chapter

### Root Cause

**File:** `src/java/manga/service/ManuscriptVersionService.java`  
**Method:** `createWorkspace()` (lines 79-111)

**Current Validation:**
```java
// Validate no UNDER_REVIEW exists (BR-2)
ManuscriptVersion underReview = manuscriptVersionRepository.findByChapterIdAndStatus(chapterId, ManuscriptStatus.UNDER_REVIEW);
if (underReview != null) {
    throw new BusinessRuleException("Only one manuscript can be UNDER_REVIEW per chapter (BR-2)");
}
```

**Problem:** Only checks for `UNDER_REVIEW` status. Does NOT check for `DRAFT` or `APPROVED` status.

**Business Rule (BR):** Only one active workspace may exist per chapter.

**Active statuses:** DRAFT, UNDER_REVIEW, APPROVED

### Affected Code Paths

#### Repository Methods
- `ManuscriptVersionRepository.findByChapterIdAndStatus()` (line 52-68)
  - Finds by single status only
  - No method to find any active status

- `ManuscriptVersionRepository.create()` (line 114-143)
  - No uniqueness constraint at database level
  - No check before insert

#### Service Methods
- `ManuscriptVersionService.createWorkspace()` (line 79-111)
  - Missing validation for DRAFT and APPROVED

#### Controller Methods
- `ModuleWebController.manuscriptWorkspaceCreatePost()` (line 763-775)
  - Calls service without additional validation

- `ManuscriptVersionApiController.createWorkspace()` (line 34-41)
  - Calls service without additional validation

### Exact Fix Plan

**Option 1: Add comprehensive status check in service**

```java
// In ManuscriptVersionService.createWorkspace(), after line 90:

// Validate no active workspace exists (DRAFT, UNDER_REVIEW, APPROVED)
List<ManuscriptStatus> activeStatuses = Arrays.asList(
    ManuscriptStatus.DRAFT,
    ManuscriptStatus.UNDER_REVIEW,
    ManuscriptStatus.APPROVED
);
for (ManuscriptStatus status : activeStatuses) {
    ManuscriptVersion existing = manuscriptVersionRepository.findByChapterIdAndStatus(chapterId, status);
    if (existing != null) {
        throw new BusinessRuleException(
            "Only one active manuscript workspace allowed per chapter. Existing: " + status.name()
        );
    }
}
```

**Option 2: Add repository method for any active status**

```java
// In ManuscriptVersionRepository.java:

public ManuscriptVersion findAnyActiveByChapterId(Long chapterId) {
    String sql = "SELECT TOP 1 id, chapterId, version, previousVersionId, status, createdAt, submittedAt, approvedAt, rejectedAt, publishedAt, createdBy, submittedBy, approvedBy, rejectedBy, feedback, revisionNotes, totalPageCount " +
                "FROM ManuscriptVersion WHERE chapterId = ? AND status IN ('DRAFT', 'UNDER_REVIEW', 'APPROVED')";
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, chapterId);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return map(rs);
            }
        }
    } catch (SQLException ex) {
        throw new RuntimeException("Cannot find active manuscript version", ex);
    }
    return null;
}
```

Then use in service:
```java
ManuscriptVersion existing = manuscriptVersionRepository.findAnyActiveByChapterId(chapterId);
if (existing != null) {
    throw new BusinessRuleException(
        "Only one active manuscript workspace allowed per chapter. Existing: " + existing.getStatus().name()
    );
}
```

**Recommended:** Option 2 for cleaner code and better performance (single query).

---

## B. Continue Workspace UX

### Issue: Chapter page still shows "Create Workspace" after workspace exists

### Affected JSPs

#### 1. `web/WEB-INF/jsp/manuscript-version/create.jsp`
- **Line 139:** Button text "Create Workspace"
- **Context:** This is the create form page, button text is appropriate here
- **Action:** No change needed

#### 2. `web/WEB-INF/jsp/manuscript-version/history.jsp`
- **Line 141:** Link text "Create Workspace" in empty state
- **Context:** Only shown when no versions exist
- **Action:** No change needed

#### 3. `web/WEB-INF/jsp/chapter/detail.jsp`
- **Line 200:** Button element `<a id="btnManuscriptWorkspace" href="#" class="btn small" style="display:none;">📝 Manuscript Workspace</a>`
- **Lines 1051-1059:** JavaScript logic
  ```javascript
  // Show manuscript workspace button for EDITORIAL_REVIEW status
  var isEditorialReview = String(chapter.status || '').toUpperCase() === 'EDITORIAL_REVIEW';
  var btnManuscriptWorkspace = document.getElementById('btnManuscriptWorkspace');
  if (isEditorialReview) {
      btnManuscriptWorkspace.style.display = '';
      btnManuscriptWorkspace.href = '${pageContext.request.contextPath}/main/chapters/' + chapter.id + '/manuscript-workspace/create';
  } else {
      btnManuscriptWorkspace.style.display = 'none';
  }
  ```
- **Problem:** Always links to `/create` endpoint, never checks if active workspace exists
- **Action Required:** Change to conditional logic

### Exact Fix Plan

**File:** `web/WEB-INF/jsp/chapter/detail.jsp`

**Replace lines 1051-1059 with:**

```javascript
// Show manuscript workspace button for EDITORIAL_REVIEW status
var isEditorialReview = String(chapter.status || '').toUpperCase() === 'EDITORIAL_REVIEW';
var btnManuscriptWorkspace = document.getElementById('btnManuscriptWorkspace');
if (isEditorialReview) {
    btnManuscriptWorkspace.style.display = '';
    
    // Check if active workspace exists
    fetch('${pageContext.request.contextPath}/api/v1/manuscript-versions?chapterId=' + chapter.id)
        .then(res => res.json())
        .then(data => {
            if (data.success && data.data && data.data.length > 0) {
                // Find active workspace (DRAFT, UNDER_REVIEW, APPROVED)
                var activeWorkspace = data.data.find(v => 
                    ['DRAFT', 'UNDER_REVIEW', 'APPROVED'].includes(v.status)
                );
                if (activeWorkspace) {
                    btnManuscriptWorkspace.textContent = '📝 Continue Workspace';
                    btnManuscriptWorkspace.href = '${pageContext.request.contextPath}/main/manuscript-workspace/' + activeWorkspace.id;
                } else {
                    btnManuscriptWorkspace.textContent = '📝 Create Workspace';
                    btnManuscriptWorkspace.href = '${pageContext.request.contextPath}/main/chapters/' + chapter.id + '/manuscript-workspace/create';
                }
            } else {
                btnManuscriptWorkspace.textContent = '📝 Create Workspace';
                btnManuscriptWorkspace.href = '${pageContext.request.contextPath}/main/chapters/' + chapter.id + '/manuscript-workspace/create';
            }
        })
        .catch(err => {
            console.error('Failed to check workspace status:', err);
            btnManuscriptWorkspace.textContent = '📝 Create Workspace';
            btnManuscriptWorkspace.href = '${pageContext.request.contextPath}/main/chapters/' + chapter.id + '/manuscript-workspace/create';
        });
} else {
    btnManuscriptWorkspace.style.display = 'none';
}
```

**Alternative (server-side):** Add model attribute in controller

**File:** `src/java/manga/controller/web/ModuleWebController.java`

Add method to chapter detail endpoint to check for active workspace and set model attribute, then use JSP conditional instead of JavaScript.

---

## C. Import Page Failure Investigation

### Issue: Import Chapter Pages fails with RuntimeException("Cannot get candidate pages")

### Trace Flow

#### 1. Import Button
**File:** `web/WEB-INF/jsp/manuscript-version/workspace.jsp`  
**Line 259:** Form posts to `/main/manuscript-workspace/{id}/import-pages`

```jsp
<c:if test="${version.status == 'DRAFT' && empty pages}">
    <form method="post" action="${pageContext.request.contextPath}/main/manuscript-workspace/${version.id}/import-pages" style="display: inline;">
        <button type="submit" class="btn btn-primary">Import Chapter Pages</button>
    </form>
</c:if>
```

#### 2. Controller
**File:** `src/java/manga/controller/web/ModuleWebController.java`  
**Method:** `manuscriptWorkspaceImportPages()` (lines 865-876)

```java
@RequestMapping(value = "/manuscript-workspace/{id}/import-pages", method = RequestMethod.POST)
public String manuscriptWorkspaceImportPages(@PathVariable("id") long id, HttpSession session, Model model) {
    AuthenticatedUser user = requireUser(session);
    try {
        manga.model.ManuscriptVersion version = manuscriptVersionService.getVersion(id);
        manuscriptVersionService.importChapterPages(id, version.getChapterId(), user);
        return "redirect:/main/manuscript-workspace/" + id;
    } catch (RuntimeException ex) {
        model.addAttribute("error", ex.getMessage());
        return manuscriptWorkspaceView(id, session, model);
    }
}
```

#### 3. Service
**File:** `src/java/manga/service/ManuscriptVersionService.java`  
**Method:** `importChapterPages()` (lines 118-167)

```java
public List<ManuscriptPage> importChapterPages(Long manuscriptVersionId, Long chapterId, AuthenticatedUser user) {
    validateEditable(manuscriptVersionId);

    ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
    if (version == null) {
        throw new BusinessRuleException("Manuscript version not found");
    }

    if (version.getStatus() != ManuscriptStatus.DRAFT) {
        throw new BusinessRuleException("Can only import pages into DRAFT manuscripts");
    }

    // Get all chapter images ordered by displayOrder
    List<manga.dto.ChapterImageDTO> chapterImages = getCandidatePages(chapterId);
    if (chapterImages.isEmpty()) {
        throw new BusinessRuleException("No chapter pages found to import");
    }
    // ... rest of method
}
```

#### 4. Service Helper (Exception Source)
**File:** `src/java/manga/service/ManuscriptVersionService.java`  
**Method:** `getCandidatePages()` (lines 714-744)

**Exact throwing line:** Line 741

```java
public List<manga.dto.ChapterImageDTO> getCandidatePages(Long chapterId) {
    // Validate chapter is in EDITORIAL_REVIEW
    String chapterStatus = chapterRepository.getChapterStatus(chapterId);
    if (!"EDITORIAL_REVIEW".equals(chapterStatus)) {
        throw new BusinessRuleException("Chapter must be in EDITORIAL_REVIEW to get candidate pages");
    }

    // Query approved chapter images
    String sql = "SELECT id, chapterId, displayOrder, imageUrl, pageNumber, createdAt " +
                "FROM ChapterImage WHERE chapterId = ? ORDER BY displayOrder ASC";
    List<manga.dto.ChapterImageDTO> results = new java.util.ArrayList<>();
    try (java.sql.Connection conn = dataSource.getConnection();
         java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, chapterId);
        try (java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                manga.dto.ChapterImageDTO dto = new manga.dto.ChapterImageDTO();
                dto.setId(rs.getLong("id"));
                dto.setChapterId(rs.getLong("chapterId"));
                dto.setDisplayOrder(rs.getInt("displayOrder"));
                dto.setImageUrl(rs.getString("imageUrl"));
                dto.setPageNumber(rs.getInt("pageNumber"));
                dto.setCreatedAt(rs.getTimestamp("createdAt") != null ? rs.getTimestamp("createdAt").toLocalDateTime() : null);
                results.add(dto);
            }
        }
    } catch (java.sql.SQLException ex) {
        throw new RuntimeException("Cannot get candidate pages", ex);  // LINE 741
    }
    return results;
}
```

### Root Cause: SQL Schema Mismatch

**SQL Query (line 722-723):**
```sql
SELECT id, chapterId, displayOrder, imageUrl, pageNumber, createdAt 
FROM ChapterImage WHERE chapterId = ? ORDER BY displayOrder ASC
```

**Actual ChapterImage Table Schema** (from `database/schema_clean.sql` lines 150-168):
```sql
CREATE TABLE [dbo].[ChapterImage](
	[id] [bigint] IDENTITY(1,1) NOT NULL,
	[chapterId] [bigint] NOT NULL,
	[pageTaskId] [bigint] NULL,
	[pageId] [bigint] NULL,
	[uploadedBy] [bigint] NOT NULL,
	[imageType] [varchar](20) NOT NULL,
	[pageNumber] [int] NULL,
	[fileUrl] [varchar](512] NOT NULL,        -- NOT imageUrl
	[originalFileName] [nvarchar](255] NOT NULL,
	[fileSizeBytes] [bigint] NULL,
	[uploadedAt] [datetime] NOT NULL,        -- NOT createdAt
	[isActive] [bit] NOT NULL,
	[note] [nvarchar](500] NULL,
    -- NO displayOrder column
)
```

**Column Mismatches:**
1. `displayOrder` - **DOES NOT EXIST** in ChapterImage table
2. `imageUrl` - should be `fileUrl`
3. `createdAt` - should be `uploadedAt`

**SQLException:** Invalid column name 'displayOrder'

### Exact Fix Plan

**File:** `src/java/manga/service/ManuscriptVersionService.java`  
**Method:** `getCandidatePages()` (lines 714-744)

**Replace SQL query (lines 722-723) with:**

```java
String sql = "SELECT id, chapterId, pageNumber, fileUrl, uploadedAt " +
            "FROM ChapterImage WHERE chapterId = ? AND isActive = 1 " +
            "ORDER BY pageNumber ASC, uploadedAt ASC";
```

**Replace ResultSet mapping (lines 730-737) with:**

```java
manga.dto.ChapterImageDTO dto = new manga.dto.ChapterImageDTO();
dto.setId(rs.getLong("id"));
dto.setChapterId(rs.getLong("chapterId"));
dto.setDisplayOrder(rs.getInt("pageNumber"));  // Use pageNumber as displayOrder
dto.setImageUrl(rs.getString("fileUrl"));      // Map fileUrl to imageUrl
dto.setPageNumber(rs.getInt("pageNumber"));
dto.setCreatedAt(rs.getTimestamp("uploadedAt") != null ? rs.getTimestamp("uploadedAt").toLocalDateTime() : null);
results.add(dto);
```

**Note:** This uses `pageNumber` for ordering and display order, which matches the existing `ChapterImageRepository.listByChapter()` pattern (see line 62-66).

---

## Summary of Required Changes

### Priority 1: Critical (Import Failure)
1. **File:** `src/java/manga/service/ManuscriptVersionService.java`
   - **Method:** `getCandidatePages()` (lines 714-744)
   - **Change:** Fix SQL query to match actual ChapterImage schema
   - **Lines:** 722-723, 730-737

### Priority 2: High (Duplicate Prevention)
2. **File:** `src/java/manga/service/ManuscriptVersionService.java`
   - **Method:** `createWorkspace()` (line 79-111)
   - **Change:** Add validation for DRAFT and APPROVED status
   - **Lines:** After line 90

   **OR**

3. **File:** `src/java/manga/repository/ManuscriptVersionRepository.java`
   - **Add Method:** `findAnyActiveByChapterId()`
   - **Use in:** `ManuscriptVersionService.createWorkspace()`

### Priority 3: Medium (UX Improvement)
4. **File:** `web/WEB-INF/jsp/chapter/detail.jsp`
   - **Lines:** 1051-1059
   - **Change:** Add logic to check for active workspace and show "Continue Workspace" vs "Create Workspace"

---

## Testing Recommendations

### Test Case 1: Duplicate Workspace Prevention
1. Create a workspace for chapter in EDITORIAL_REVIEW status
2. Attempt to create another workspace
3. Expected: BusinessRuleException thrown
4. Verify exception message mentions existing workspace status

### Test Case 2: Continue Workspace UX
1. Create a workspace for chapter
2. Navigate to chapter detail page
3. Expected: Button shows "Continue Workspace" and links to existing workspace
4. Delete workspace
5. Navigate to chapter detail page
6. Expected: Button shows "Create Workspace" and links to create endpoint

### Test Case 3: Import Pages
1. Create workspace for chapter with existing chapter images
2. Click "Import Chapter Pages"
3. Expected: Pages imported successfully
4. Verify page count matches chapter image count
5. Verify displayOrder matches pageNumber

---

## Affected Files Summary

| File | Lines | Issue | Priority |
|------|-------|-------|----------|
| `src/java/manga/service/ManuscriptVersionService.java` | 714-744 | SQL schema mismatch | Critical |
| `src/java/manga/service/ManuscriptVersionService.java` | 79-111 | Missing DRAFT/APPROVED validation | High |
| `src/java/manga/repository/ManuscriptVersionRepository.java` | N/A | Missing active status query method | High |
| `web/WEB-INF/jsp/chapter/detail.jsp` | 1051-1059 | Always shows "Create Workspace" | Medium |
