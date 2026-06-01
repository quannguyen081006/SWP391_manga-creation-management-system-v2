# Manuscript Review Workflow UI - Manual Test Checklist

## Overview
This checklist verifies the complete end-to-end Editorial Review UI workflow for manuscript management.

## Test Prerequisites
- Database has at least one series with chapters in EDITORIAL_REVIEW status
- At least one TANTOU_EDITOR user assigned to a series
- At least one MANGAKA user
- At least one ADMIN user

---

## Mangaka Workflow Tests

### Test 1: Create Manuscript Workspace
**Preconditions:**
- Logged in as MANGAKA
- Chapter exists with status EDITORIAL_REVIEW

**Steps:**
1. Navigate to chapter detail page
2. Click "Create Workspace" button
3. Verify workspace.jsp loads with status DRAFT
4. Verify "Import Pages" button is visible
5. Verify "Submit for Review" button is NOT visible (no pages yet)

**Expected Result:**
- Workspace created successfully
- Status shows DRAFT badge
- Only "Import Pages" button visible

---

### Test 2: Import Pages
**Preconditions:**
- Logged in as MANGAKA
- Manuscript workspace in DRAFT status with no pages

**Steps:**
1. Click "Import Pages" button
2. Verify pages are imported
3. Verify "Import Pages" button is now hidden
4. Verify "Submit for Review" button is now visible

**Expected Result:**
- Pages imported successfully
- Action buttons update based on state

---

### Test 3: Submit for Review
**Preconditions:**
- Logged in as MANGAKA
- Manuscript workspace in DRAFT status with pages

**Steps:**
1. Click "Submit for Review" button
2. Verify status changes to UNDER_REVIEW
3. Verify "Submit for Review" button is hidden
4. Verify "Approve/Reject" buttons are NOT visible (not Tantou)
5. Verify "Version History" button is visible
6. Verify "Dashboard" button is visible

**Expected Result:**
- Manuscript submitted successfully
- Status shows UNDER_REVIEW badge
- Production locked indicator visible

---

### Test 4: View Version History
**Preconditions:**
- Logged in as MANGAKA
- Manuscript workspace exists

**Steps:**
1. Click "Version History" button
2. Verify history.jsp loads
3. Verify all versions are listed with status badges
4. Verify "Compare With Previous" button appears for versions with previousVersionId
5. Verify empty state message if no versions exist

**Expected Result:**
- Version history displays correctly
- Status badges are consistent
- Comparison links work

---

### Test 5: View Dashboard
**Preconditions:**
- Logged in as MANGAKA
- Manuscript workspace exists with pages

**Steps:**
1. Click "Dashboard" button
2. Verify dashboard.jsp loads
3. Verify stats display: Total Pages, Open Annotations, Resolved, Total
4. Verify review progress bar displays
5. Verify empty state if no data

**Expected Result:**
- Dashboard displays correctly
- Stats are accurate
- Progress bar shows correct percentage

---

### Test 6: Create New Version After Rejection
**Preconditions:**
- Logged in as MANGAKA
- Manuscript workspace in REJECTED status
- User is the mangaka owner

**Steps:**
1. Navigate to workspace
2. Verify "Create New Version" button is visible
3. Click "Create New Version" button
4. Verify new workspace created with DRAFT status
5. Verify pages copied from rejected version

**Expected Result:**
- New version created successfully
- Pages copied correctly
- Status is DRAFT

---

## Tantou Editor Workflow Tests

### Test 7: Access Review Inbox
**Preconditions:**
- Logged in as TANTOU_EDITOR
- At least one manuscript in UNDER_REVIEW status for assigned series

**Steps:**
1. Click "Manuscript Reviews" in sidebar navigation
2. Verify review-inbox.jsp loads
3. Verify only manuscripts from assigned series are shown
4. Verify table shows: Chapter, Version, Status, Mangaka, Submitted At, Action
5. Verify "Review Workspace" button for each entry
6. Verify empty state if no manuscripts

**Expected Result:**
- Review inbox displays correctly
- Only assigned series manuscripts shown
- Status badges display correctly

---

### Test 8: Review Manuscript Workspace
**Preconditions:**
- Logged in as TANTOU_EDITOR
- Manuscript in UNDER_REVIEW status for assigned series

**Steps:**
1. Click "Review Workspace" from inbox
2. Verify workspace.jsp loads
3. Verify status shows UNDER_REVIEW badge
4. Verify "Approve" button is visible
5. Verify "Reject" button is visible
6. Verify "Approve" button is disabled if open annotations > 0
7. Verify annotation markers display on pages

**Expected Result:**
- Workspace loads correctly
- Action buttons appropriate for Tantou role
- Approval gate enforced

---

### Test 9: Approve Manuscript
**Preconditions:**
- Logged in as TANTOU_EDITOR
- Manuscript in UNDER_REVIEW status
- No open annotations

**Steps:**
1. Click "Approve" button
2. Verify status changes to APPROVED
3. Verify "Approve/Reject" buttons hidden
4. Verify "Publish" button is visible
5. Verify chapter status changes to APPROVED

**Expected Result:**
- Manuscript approved successfully
- Chapter status updated
- Production unlocked

---

### Test 10: Reject Manuscript
**Preconditions:**
- Logged in as TANTOU_EDITOR
- Manuscript in UNDER_REVIEW status

**Steps:**
1. Click "Reject" button
2. Verify reject modal appears
3. Enter feedback text
4. Click "Reject" in modal
5. Verify status changes to REJECTED
6. Verify feedback is displayed
7. Verify "Create New Version" button visible for mangaka

**Expected Result:**
- Manuscript rejected successfully
- Feedback saved
- Mangaka can create new version

---

## Admin Workflow Tests

### Test 11: Access All Manuscripts in Review Inbox
**Preconditions:**
- Logged in as ADMIN
- Multiple manuscripts in UNDER_REVIEW status

**Steps:**
1. Click "Manuscript Reviews" in sidebar navigation
2. Verify review-inbox.jsp loads
3. Verify ALL manuscripts in UNDER_REVIEW are shown (not just assigned)
4. Verify table displays correctly

**Expected Result:**
- Admin sees all under-review manuscripts
- No series filtering applied

---

### Test 12: Admin Review Actions
**Preconditions:**
- Logged in as ADMIN
- Manuscript in UNDER_REVIEW status

**Steps:**
1. Navigate to workspace
2. Verify "Approve" button is visible
3. Verify "Reject" button is visible
4. Verify actions work same as Tantou

**Expected Result:**
- Admin has same review capabilities as Tantou
- Actions function correctly

---

## Version Comparison Tests

### Test 13: Compare Versions
**Preconditions:**
- Logged in as any role with access
- At least two manuscript versions exist

**Steps:**
1. Navigate to version history
2. Click "Compare With Previous" for a version
3. Verify compare.jsp loads
4. Verify both versions displayed with status badges
5. Verify changes section shows:
   - Added Pages
   - Removed Pages
   - Changed Pages
   - Reordered Pages
6. Verify empty state if no differences

**Expected Result:**
- Comparison displays correctly
- All change types shown
- Empty state handled

---

## Empty State Tests

### Test 14: Review Inbox Empty State
**Preconditions:**
- Logged in as TANTOU_EDITOR or ADMIN
- No manuscripts in UNDER_REVIEW status

**Steps:**
1. Navigate to review inbox
2. Verify empty state message displays
3. Verify message: "No manuscript submissions waiting for review"

**Expected Result:**
- Friendly empty state message
- No errors

---

### Test 15: Version History Empty State
**Preconditions:**
- Logged in as any user
- Chapter with no manuscript versions

**Steps:**
1. Navigate to version history
2. Verify empty state message displays
3. Verify message: "No manuscript versions found"
4. Verify "Create Workspace" link present

**Expected Result:**
- Friendly empty state message
- Call-to-action provided

---

### Test 16: Compare Empty State
**Preconditions:**
- Logged in as any user
- Comparing identical versions

**Steps:**
1. Navigate to compare page
2. Verify empty state message displays
3. Verify message: "No differences found between versions"

**Expected Result:**
- Friendly empty state message
- No errors

---

### Test 17: Dashboard Empty State
**Preconditions:**
- Logged in as any user
- Manuscript with no pages or data

**Steps:**
1. Navigate to dashboard
2. Verify empty state message displays
3. Verify message: "No review data available"

**Expected Result:**
- Friendly empty state message
- No errors

---

## Status Badge Consistency Tests

### Test 18: Status Badges Across Pages
**Preconditions:**
- Logged in as any user
- Manuscripts in various statuses

**Steps:**
1. Check workspace.jsp - verify status badges
2. Check history.jsp - verify status badges
3. Check compare.jsp - verify status badges
4. Verify badge colors:
   - DRAFT: yellow (#ffd43b)
   - UNDER_REVIEW: blue (#74c0fc)
   - APPROVED: green (#51cf66)
   - REJECTED: red (#ff6b6b)
   - PUBLISHED: purple (#845ef7)

**Expected Result:**
- Status badges consistent across all pages
- Colors match specification

---

## Navigation Tests

### Test 19: Sidebar Navigation
**Preconditions:**
- Logged in as TANTOU_EDITOR

**Steps:**
1. Verify "Manuscript Reviews" link visible in sidebar
2. Click link - verify navigates to /main/manuscript-review
3. Verify link is highlighted when active

**Expected Result:**
- Navigation link visible for Tantou
- Link works correctly
- Active state shown

---

### Test 20: Sidebar Navigation for Admin
**Preconditions:**
- Logged in as ADMIN

**Steps:**
1. Verify "Manuscript Reviews" link visible in sidebar
2. Click link - verify navigates to /main/manuscript-review
3. Verify link is highlighted when active

**Expected Result:**
- Navigation link visible for Admin
- Link works correctly
- Active state shown

---

### Test 21: Sidebar Navigation Hidden for Other Roles
**Preconditions:**
- Logged in as MANGAKA or ASSISTANT

**Steps:**
1. Verify "Manuscript Reviews" link is NOT visible in sidebar

**Expected Result:**
- Navigation link hidden for non-Tantou/Admin roles

---

## Workflow Integration Tests

### Test 22: Complete End-to-End Mangaka Flow
**Preconditions:**
- Logged in as MANGAKA
- Chapter in EDITORIAL_REVIEW

**Steps:**
1. Create workspace
2. Import pages
3. Submit for review
4. View version history
5. View dashboard
6. (After rejection) Create new version

**Expected Result:**
- Complete workflow functions smoothly
- All state transitions work
- All buttons appear/disappear correctly

---

### Test 23: Complete End-to-End Tantou Flow
**Preconditions:**
- Logged in as TANTOU_EDITOR
- Manuscript submitted for review

**Steps:**
1. Access review inbox
2. Review workspace
3. Approve or reject
4. View version history
5. Compare versions

**Expected Result:**
- Complete workflow functions smoothly
- All actions work correctly
- Series filtering works

---

## Files Created/Modified

### Repository
- `ManuscriptVersionRepository.java` - Added `findUnderReviewForTantou()` method

### Controller
- `ModuleWebController.java` - Added `manuscriptReviewInbox()` endpoint

### JSP Pages Created
- `manuscript-version/review-inbox.jsp` - New review inbox page

### JSP Pages Modified
- `common/header.jsp` - Added "Manuscript Reviews" navigation link
- `manuscript-version/workspace.jsp` - Added Version History and Dashboard buttons, improved state-aware actions
- `manuscript-version/history.jsp` - Added "Compare With Previous" link
- `manuscript-version/compare.jsp` - Updated to use VersionComparisonDTO fields, improved empty state
- `manuscript-version/dashboard.jsp` - Added empty state handling

---

## Summary
All 23 tests should pass to verify the complete Manuscript Review Workflow UI implementation.
