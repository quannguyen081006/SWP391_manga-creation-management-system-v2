package manga.service;

import manga.common.exception.BusinessRuleException;
import manga.enums.ManuscriptStatus;
import manga.model.AuthenticatedUser;
import manga.model.ManuscriptPage;
import manga.model.ManuscriptProductionLock;
import manga.model.ManuscriptVersion;
import manga.repository.chaptertask.ChapterRepository;
import manga.repository.ManuscriptPageRepository;
import manga.repository.ManuscriptProductionLockRepository;
import manga.repository.ManuscriptVersionRepository;
import manga.repository.chaptertask.PageTaskRepository;
import manga.repository.ReviewDecisionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for ManuscriptVersion entity.
 *
 * Implements the new visual workspace workflow for manuscript management.
 * Enforces business rules BR-1 through BR-9.
 */
@Service
@Transactional
public class ManuscriptVersionService {

    @Autowired
    private ManuscriptVersionRepository manuscriptVersionRepository;

    @Autowired
    private ManuscriptPageRepository manuscriptPageRepository;

    @Autowired
    private ManuscriptProductionLockRepository lockRepository;

    @Autowired
    private PageTaskRepository pageTaskRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ReviewDecisionRepository reviewDecisionRepository;

    @Autowired
    private AnnotationServiceV2 annotationServiceV2;

    @Autowired
    private ReviewTaskService reviewTaskService;

    @Autowired
    private DataSource dataSource;

    /**
     * Validate that manuscript version can be edited. Centralized immutability
     * enforcement. Enforces review locking rules: DRAFT and IN_PROGRESS are
     * editable, others are not.
     */
    private void validateEditable(Long manuscriptVersionId) {
        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }
        version.validateEditable();
    }

    /**
     * Validate that manuscript version can be modified (add pages, reorder,
     * etc.). Enforces review locking rules: only DRAFT and IN_PROGRESS are
     * mutable. SUBMITTED_FOR_REVIEW and UNDER_REVIEW are read-only. APPROVED,
     * PUBLISHED, REJECTED, ARCHIVED are immutable.
     */
    private void validateMutable(Long manuscriptVersionId) {
        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }
        if (version.getStatus() != ManuscriptStatus.DRAFT) {
            throw new BusinessRuleException("Only DRAFT manuscript can be modified");
        }
        version.validateMutable();
    }

    /**
     * Create new manuscript workspace (idempotent). BR-1: Only chapters in
     * EDITORIAL_REVIEW can create manuscripts BR-2: Only one active workspace
     * per chapter (DRAFT, IN_PROGRESS, SUBMITTED_FOR_REVIEW, UNDER_REVIEW)
     *
     * If an active workspace already exists, returns the existing one. Only
     * creates a new workspace if no active workspace exists.
     *
     * Transaction Safety: Uses SELECT FOR UPDATE locking to prevent race
     * conditions.
     */
    @Transactional
    public ManuscriptVersion createWorkspace(Long chapterId, AuthenticatedUser user) {
        // Validate chapterId before any database operations
        if (chapterId == null || chapterId <= 0) {
            throw new BusinessRuleException("Invalid chapterId: " + chapterId + ". Must be greater than 0.");
        }

        // Validate chapter status (BR-1)
        String chapterStatus = chapterRepository.getChapterStatus(chapterId);
        if (!"EDITORIAL_REVIEW".equals(chapterStatus)) {
            throw new BusinessRuleException("Chapter must be in EDITORIAL_REVIEW to create manuscript (BR-1)");
        }

        // Check for existing active workspace (idempotency)
        ManuscriptVersion existingActive = manuscriptVersionRepository.findActiveWorkspace(chapterId);
        if (existingActive != null) {
            // Return existing active workspace instead of creating duplicate
            return existingActive;
        }

        // Double-check with count for race condition protection
        long activeCount = manuscriptVersionRepository.countActiveWorkspaces(chapterId);
        if (activeCount > 0) {
            // If another transaction created a workspace, find and return it
            existingActive = manuscriptVersionRepository.findActiveWorkspace(chapterId);
            if (existingActive != null) {
                return existingActive;
            }
            throw new BusinessRuleException("Active workspace already exists for this chapter");
        }

        // Get next version number and previous version ID
        List<ManuscriptVersion> existingVersions = manuscriptVersionRepository.findByChapterIdOrderByVersionDesc(chapterId);
        Long previousVersionId = existingVersions.isEmpty() ? null : existingVersions.get(0).getId();
        Integer nextVersion = existingVersions.isEmpty() ? 1 : existingVersions.get(0).getVersion() + 1;

        // Create manuscript version
        ManuscriptVersion version = new ManuscriptVersion();
        version.setChapterId(chapterId);
        version.setVersion(nextVersion);
        version.setPreviousVersionId(previousVersionId);
        version.setStatus(ManuscriptStatus.DRAFT);
        version.setCreatedAt(LocalDateTime.now());
        version.setCreatedBy(user.getId());
        version.setTotalPageCount(0);

        long versionId = manuscriptVersionRepository.create(version);
        version.setId(versionId);

        return version;
    }

    /**
     * Get active workspace for a chapter. Returns the latest manuscript version
     * with status in: DRAFT, IN_PROGRESS, SUBMITTED_FOR_REVIEW, UNDER_REVIEW
     * Returns null if no active workspace exists.
     */
    public ManuscriptVersion getActiveWorkspace(Long chapterId) {
        return manuscriptVersionRepository.findActiveWorkspace(chapterId);
    }

    public ManuscriptVersion getLatestVersion(long chapterId) {
        return manuscriptVersionRepository.findLatestByChapterId(chapterId);
    }

    /**
     * Bulk import all chapter pages into manuscript workspace. Imports final
     * chapter pages directly without re-uploading. Preserves page ordering from
     * chapter.
     */
    public List<ManuscriptPage> importChapterPages(Long manuscriptVersionId, Long chapterId, AuthenticatedUser user) {
        validateMutable(manuscriptVersionId);

        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }

        if (!version.getStatus().isEditable()) {
            throw new BusinessRuleException("Can only import pages into DRAFT or IN_PROGRESS manuscripts");
        }

        // Get all chapter images ordered by displayOrder
        List<manga.dto.chaptertask.ChapterImageDTO> chapterImages = getCandidatePages(chapterId);
        if (chapterImages.isEmpty()) {
            throw new BusinessRuleException("No chapter pages found to import");
        }

        List<ManuscriptPage> importedPages = new ArrayList<>();
        int displayOrder = 1;

        for (manga.dto.chaptertask.ChapterImageDTO chapterImage : chapterImages) {
            // Create immutable snapshot for each page
            String imageUrl = chapterImage.getImageUrl();
            String snapshotUrl = createImmutableSnapshot(imageUrl);
            String checksum = calculateChecksum(snapshotUrl);

            ManuscriptPage page = new ManuscriptPage();
            page.setManuscriptVersionId(manuscriptVersionId);
            page.setDisplayOrder(displayOrder);
            page.setSnapshotFileUrl(snapshotUrl);
            page.setOriginalFileUrl(imageUrl);
            page.setSourceChapterImageId(chapterImage.getId());
            page.setPageNumber(displayOrder);
            page.setSnapshotCreatedAt(LocalDateTime.now());
            page.setSnapshotChecksum(checksum);

            long pageId = manuscriptPageRepository.create(page);
            page.setId(pageId);
            importedPages.add(page);

            displayOrder++;
        }

        // Update total page count
        int totalPages = importedPages.size();

        version.setTotalPageCount(totalPages);

        manuscriptVersionRepository.updateTotalPageCount(
                manuscriptVersionId,
                totalPages
        );

        return importedPages;
    }

    /**
     * Add page snapshot to manuscript. Creates immutable copy of production
     * asset (BR-7).
     */
    public ManuscriptPage addPageSnapshot(Long manuscriptVersionId, Long chapterImageId,
            Integer displayOrder, AuthenticatedUser user) {
        validateMutable(manuscriptVersionId);

        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }

        if (!version.getStatus().isEditable()) {
            throw new BusinessRuleException("Can only add pages to DRAFT or IN_PROGRESS manuscripts");
        }

        // Get chapter image details - for now use a placeholder approach
        // In production, this would query ChapterImage table
        String imageUrl = "/assets/images/chapter/" + chapterImageId + ".jpg"; // Placeholder

        // Create immutable snapshot (BR-7)
        String snapshotUrl = createImmutableSnapshot(imageUrl);
        String checksum = calculateChecksum(snapshotUrl);

        ManuscriptPage page = new ManuscriptPage();
        page.setManuscriptVersionId(manuscriptVersionId);
        page.setDisplayOrder(displayOrder);
        page.setSnapshotFileUrl(snapshotUrl);
        page.setOriginalFileUrl(imageUrl);
        page.setSourceChapterImageId(chapterImageId);
        page.setPageNumber(displayOrder);
        page.setSnapshotCreatedAt(LocalDateTime.now());
        page.setSnapshotChecksum(checksum);

        long pageId = manuscriptPageRepository.create(page);
        page.setId(pageId);

        // Update total page count
        version.setTotalPageCount((int) manuscriptPageRepository.countByManuscriptVersionId(manuscriptVersionId));
        manuscriptVersionRepository.updateStatus(manuscriptVersionId, version.getStatus());

        return page;
    }

    /**
     * Reorder pages in manuscript.
     */
    public void reorderPages(Long manuscriptVersionId, List<Integer> pageOrders, AuthenticatedUser user) {
        validateMutable(manuscriptVersionId);

        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }

        if (!version.getStatus().isEditable()) {
            throw new BusinessRuleException("Can only reorder pages in DRAFT or IN_PROGRESS manuscripts");
        }

        // Get current pages
        List<ManuscriptPage> pages = manuscriptPageRepository.findByManuscriptVersionIdOrderByDisplayOrder(manuscriptVersionId);

        if (pages.size() != pageOrders.size()) {
            throw new BusinessRuleException("Page order count mismatch");
        }

        // Update displayOrder for each page
        for (int i = 0; i < pages.size(); i++) {
            ManuscriptPage page = pages.get(i);
            Integer newDisplayOrder = pageOrders.get(i);
            updatePageDisplayOrder(page.getId(), newDisplayOrder);
        }
    }

    private void updatePageDisplayOrder(Long pageId, Integer newDisplayOrder) {
        String sql = "UPDATE ManuscriptPage SET displayOrder = ? WHERE id = ?";
        try ( java.sql.Connection conn = dataSource.getConnection();  java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newDisplayOrder);
            ps.setLong(2, pageId);
            ps.executeUpdate();
        } catch (java.sql.SQLException ex) {
            throw new RuntimeException("Cannot update page display order", ex);
        }
    }

    /**
     * Validate that manuscript version is the latest before review actions.
     * BR-4: Only the latest version can be reviewed
     */
    private void validateLatestVersion(Long manuscriptVersionId) {
        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }

        ManuscriptVersion latest = manuscriptVersionRepository.findLatestByChapterId(version.getChapterId());
        if (latest == null || !latest.getId().equals(manuscriptVersionId)) {
            throw new BusinessRuleException("Only the latest version can be reviewed (BR-4)");
        }
    }

    /**
     * Submit manuscript for review. Locks production assets (BR-9). Validates
     * status transition: DRAFT/IN_PROGRESS → SUBMITTED_FOR_REVIEW →
     * UNDER_REVIEW
     *
     * ATOMIC TRANSACTION: All operations must succeed or all must rollback.
     * Uses manual transaction handling to ensure atomicity across repository
     * calls.
     */
    public void submitForReview(Long manuscriptVersionId, AuthenticatedUser user) {
        validateLatestVersion(manuscriptVersionId);

        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }

        // Validate chapterId is not zero (prevents orphaned locks)
        if (version.getChapterId() == null || version.getChapterId() == 0) {
            throw new BusinessRuleException("Manuscript version has invalid chapterId: " + version.getChapterId());
        }

        // Validate status transition using state machine
        version.validateTransition(ManuscriptStatus.SUBMITTED_FOR_REVIEW);

        long pageCount = manuscriptPageRepository.countByManuscriptVersionId(manuscriptVersionId);
        if (pageCount == 0) {
            throw new BusinessRuleException("Manuscript must have at least one page");
        }

        // Validate no other UNDER_REVIEW exists (BR-2)
        ManuscriptVersion underReview = manuscriptVersionRepository.findByChapterIdAndStatus(version.getChapterId(), ManuscriptStatus.UNDER_REVIEW);
        if (underReview != null && !underReview.getId().equals(manuscriptVersionId)) {
            throw new BusinessRuleException("Only one manuscript can be UNDER_REVIEW per chapter (BR-2)");
        }

        // Manual transaction handling to ensure atomicity
        // All operations must succeed or all must rollback
        java.sql.Connection conn = null;
        boolean oldAutoCommit = false;
        try {
            conn = dataSource.getConnection();
            oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Lock production (BR-9)
            lockProduction(version.getChapterId(), manuscriptVersionId, user.getId());

            // Update status to SUBMITTED_FOR_REVIEW first, then UNDER_REVIEW
            manuscriptVersionRepository.updateStatus(manuscriptVersionId, ManuscriptStatus.SUBMITTED_FOR_REVIEW);

            // Immediately transition to UNDER_REVIEW for reviewer assignment
            manuscriptVersionRepository.updateSubmit(manuscriptVersionId, user.getId());

            // Create ReviewTask for SLA tracking (BR-51, BR-52)
            // This is the critical operation that may fail if table doesn't exist
            reviewTaskService.createReviewTask(manuscriptVersionId, user);

            // Commit transaction - all operations succeeded
            conn.commit();

        } catch (BusinessRuleException ex) {
            // Business rule violations should not rollback - they're validation errors
            throw ex;
        } catch (Exception ex) {
            // Rollback transaction on any error
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException rollbackEx) {
                System.err.println("Error rolling back transaction: " + rollbackEx.getMessage());
            }

            // Re-throw with context
            throw new BusinessRuleException("SUBMIT_FOR_REVIEW_FAILED: " + ex.getMessage());
        } finally {
            // Restore auto-commit and close connection
            try {
                if (conn != null) {
                    conn.setAutoCommit(oldAutoCommit);
                    conn.close();
                }
            } catch (SQLException ex) {
                System.err.println("Error closing connection: " + ex.getMessage());
            }
        }

        // Notify Tantou (outside transaction - notification failure should not affect submission)
        Long tantouId = chapterRepository.getChapterTantou(version.getChapterId());
        if (tantouId != null) {
            try {
                notificationService.notifyUser(
                        tantouId,
                        "MANUSCRIPT_SUBMITTED",
                        "Manuscript v" + version.getVersion() + " submitted for review",
                        manuscriptVersionId,
                        "MANUSCRIPT"
                );
            } catch (Exception ex) {
                // Notification failure should not fail the entire operation
                System.err.println("Warning: Failed to send manuscript submission notification to user " + tantouId + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Approve manuscript. BR-4: APPROVED manuscripts cannot be mutated BR-6:
     * Publishing requires APPROVED status Approval Gate: Cannot approve while
     * OPEN annotations exist Validates status transition: UNDER_REVIEW →
     * APPROVED
     */
    public void approve(Long manuscriptVersionId, AuthenticatedUser user) {
        validateLatestVersion(manuscriptVersionId);

        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }

        // Validate chapterId is not zero
        if (version.getChapterId() == null || version.getChapterId() == 0) {
            throw new BusinessRuleException("Manuscript version has invalid chapterId: " + version.getChapterId());
        }

        // Validate status transition using state machine
        version.validateTransition(ManuscriptStatus.APPROVED);

        // Approval Gate: Check for OPEN annotations
        long openAnnotationCount = annotationServiceV2.countOpenAnnotations(manuscriptVersionId);
        if (openAnnotationCount > 0) {
            throw new BusinessRuleException(
                    "Cannot approve manuscript with " + openAnnotationCount + " open annotation(s). "
                    + "All annotations must be resolved or dismissed before approval."
            );
        }

        // Record decision in audit trail
        manga.model.ReviewDecision decision = new manga.model.ReviewDecision();
        decision.setManuscriptVersionId(manuscriptVersionId);
        decision.setReviewerId(user.getId());
        decision.setDecisionType(manga.enums.ReviewDecisionType.APPROVE);
        decision.setComment(null);
        reviewDecisionRepository.create(decision);

        // Update status
        manuscriptVersionRepository.updateApproval(manuscriptVersionId, ManuscriptStatus.APPROVED, null, user.getId());

        // Complete ReviewTask
        reviewTaskService.completeReviewTask(manuscriptVersionId, user);

        // Phase 11: Approval Finalization - Mark chapter as APPROVED
        // When manuscript is approved, chapter automatically becomes APPROVED
        chapterRepository.updateChapterStatus(version.getChapterId(), "APPROVED");

        // Unlock production
        boolean unlocked = lockRepository.unlock(version.getChapterId(), manuscriptVersionId);
        if (!unlocked) {
            System.err.println("Warning: No production lock found for chapterId " + version.getChapterId() + ", manuscriptVersionId " + manuscriptVersionId + " during approve");
        }

        // Notify Mangaka
        Long mangakaId = chapterRepository.getChapterMangaka(version.getChapterId());
        if (mangakaId != null) {
            notificationService.notifyUser(
                    mangakaId,
                    "MANUSCRIPT_APPROVED",
                    "Manuscript approved for chapter #" + version.getChapterId(),
                    manuscriptVersionId,
                    "MANUSCRIPT"
            );
        }
    }

    /**
     * Publish manuscript. BR-6: Publishing requires APPROVED status
     */
    public void publish(Long manuscriptVersionId, AuthenticatedUser user) {
        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }

        if (!version.canPublish()) {
            throw new BusinessRuleException("Only APPROVED manuscripts can be published");
        }

        // Update status to PUBLISHED
        manuscriptVersionRepository.updatePublish(manuscriptVersionId);

        // Notify Mangaka
        Long mangakaId = chapterRepository.getChapterMangaka(version.getChapterId());
        if (mangakaId != null) {
            notificationService.notifyUser(
                    mangakaId,
                    "MANUSCRIPT_PUBLISHED",
                    "Manuscript published for chapter #" + version.getChapterId(),
                    manuscriptVersionId,
                    "MANUSCRIPT"
            );
        }
    }

    /**
     * Reject manuscript. BR-3: REJECTED manuscripts cannot be mutated Validates
     * status transition: UNDER_REVIEW → REJECTED
     */
    public void reject(Long manuscriptVersionId, String feedback, AuthenticatedUser user) {
        validateLatestVersion(manuscriptVersionId);

        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }

        // Validate chapterId is not zero
        if (version.getChapterId() == null || version.getChapterId() == 0) {
            throw new BusinessRuleException("Manuscript version has invalid chapterId: " + version.getChapterId());
        }

        // Validate status transition using state machine
        version.validateTransition(ManuscriptStatus.REJECTED);

        if (feedback == null || feedback.trim().isEmpty()) {
            throw new BusinessRuleException("Feedback is required when rejecting manuscript");
        }

        // Record decision in audit trail
        manga.model.ReviewDecision decision = new manga.model.ReviewDecision();
        decision.setManuscriptVersionId(manuscriptVersionId);
        decision.setReviewerId(user.getId());
        decision.setDecisionType(manga.enums.ReviewDecisionType.REJECT);
        decision.setComment(feedback);
        reviewDecisionRepository.create(decision);

        // Update status
        manuscriptVersionRepository.updateApproval(manuscriptVersionId, ManuscriptStatus.REJECTED, feedback, user.getId());

        // Complete ReviewTask
        reviewTaskService.completeReviewTask(manuscriptVersionId, user);

        // Unlock production
        boolean unlocked = lockRepository.unlock(version.getChapterId(), manuscriptVersionId);
        if (!unlocked) {
            System.err.println("Warning: No production lock found for chapterId " + version.getChapterId() + ", manuscriptVersionId " + manuscriptVersionId + " during reject");
        }

        // Notify Mangaka
        Long mangakaId = chapterRepository.getChapterMangaka(version.getChapterId());
        if (mangakaId != null) {
            notificationService.notifyUser(
                    mangakaId,
                    "MANUSCRIPT_REJECTED",
                    "Manuscript rejected. Feedback: " + feedback,
                    manuscriptVersionId,
                    "MANUSCRIPT"
            );
        }
    }

    /**
     * Create new version after rejection. BR-3: Previous REJECTED version
     * remains immutable
     */
    public ManuscriptVersion createNewVersion(Long chapterId, AuthenticatedUser user) {
        // Validate chapterId before any database operations
        if (chapterId == null || chapterId <= 0) {
            throw new BusinessRuleException("Invalid chapterId: " + chapterId + ". Must be greater than 0.");
        }

        // Authorization: Only MANGAKA role can create new version
        if (!user.hasRole("MANGAKA")) {
            throw new BusinessRuleException("Only MANGAKA role can create a new manuscript version");
        }

        // Authorization: Only the chapter owner (author) can create new version
        long mangakaId = chapterRepository.findOwnerMangakaByChapter(chapterId);
        if (user.getId() != mangakaId) {
            throw new BusinessRuleException("Only the chapter owner can create a new manuscript version");
        }

        // Validate latest version is REJECTED
        List<ManuscriptVersion> versions = manuscriptVersionRepository.findByChapterIdOrderByVersionDesc(chapterId);
        if (versions.isEmpty()) {
            throw new BusinessRuleException("No previous manuscript version found");
        }

        ManuscriptVersion latest = versions.get(0);
        if (latest.getStatus() != ManuscriptStatus.REJECTED) {
            throw new BusinessRuleException("New version can only be created after REJECTED status");
        }

        // Get next version number
        Integer nextVersion = latest.getVersion() + 1;

        // Create manuscript version with previousVersionId set
        ManuscriptVersion version = new ManuscriptVersion();
        version.setChapterId(chapterId);
        version.setVersion(nextVersion);
        version.setPreviousVersionId(latest.getId());
        version.setStatus(ManuscriptStatus.DRAFT);
        version.setCreatedAt(LocalDateTime.now());
        version.setCreatedBy(user.getId());
        version.setTotalPageCount(0);

        long versionId = manuscriptVersionRepository.create(version);
        version.setId(versionId);

        // Copy pages from rejected version
        List<ManuscriptPage> previousPages = manuscriptPageRepository.findByManuscriptVersionIdOrderByDisplayOrder(latest.getId());
        for (ManuscriptPage prevPage : previousPages) {
            ManuscriptPage newPage = new ManuscriptPage();
            newPage.setManuscriptVersionId(versionId);
            newPage.setDisplayOrder(prevPage.getDisplayOrder());
            newPage.setPageNumber(prevPage.getPageNumber());
            newPage.setSnapshotFileUrl(prevPage.getSnapshotFileUrl());
            newPage.setOriginalFileUrl(prevPage.getOriginalFileUrl());
            newPage.setSnapshotChecksum(prevPage.getSnapshotChecksum());
            newPage.setSnapshotCreatedAt(LocalDateTime.now());
            newPage.setSourceChapterImageId(prevPage.getSourceChapterImageId());
            newPage.setSourcePageTaskId(prevPage.getSourcePageTaskId());
            manuscriptPageRepository.create(newPage);
        }

        int totalPages = previousPages.size();

        version.setTotalPageCount(totalPages);

        manuscriptVersionRepository.updateTotalPageCount(
                versionId,
                totalPages
        );

        return version;
    }

    public ManuscriptPage replacePage(
            Long pageId,
            String fileUrl,
            String checksum
    ) {

        ManuscriptPage page
                = manuscriptPageRepository
                        .findById(pageId);

        if (page == null) {

            throw new BusinessRuleException(
                    "Page not found"
            );
        }

        validateMutable(
                page.getManuscriptVersionId()
        );

        page.setSnapshotFileUrl(fileUrl);

        page.setOriginalFileUrl(fileUrl);

        page.setSnapshotChecksum(checksum);

        manuscriptPageRepository.update(page);

        return page;
    }

    /**
     * Get manuscript version by ID.
     */
    public ManuscriptVersion getVersion(Long manuscriptVersionId) {
        return manuscriptVersionRepository.findById(manuscriptVersionId);
    }

    /**
     * List versions for chapter.
     */
    public List<ManuscriptVersion> listVersions(Long chapterId) {
        return manuscriptVersionRepository.findByChapterIdOrderByVersionDesc(chapterId);
    }

    /**
     * Get pages for manuscript version.
     */
    public List<ManuscriptPage> getPages(Long manuscriptVersionId) {
        return manuscriptPageRepository.findByManuscriptVersionIdOrderByDisplayOrder(manuscriptVersionId);
    }

    /**
     * Get review decision history for manuscript version.
     */
    public List<manga.model.ReviewDecision> getReviewDecisions(Long manuscriptVersionId) {
        return reviewDecisionRepository.findByManuscriptVersionId(manuscriptVersionId);
    }

    /**
     * Phase 9: Version Comparison - Compare two manuscript versions. Identifies
     * added pages, removed pages, changed pages, and reordered pages. Returns a
     * comparison result with detailed differences.
     */
    public manga.dto.VersionComparisonDTO compareVersions(Long versionId1, Long versionId2) {
        ManuscriptVersion v1 = manuscriptVersionRepository.findById(versionId1);
        ManuscriptVersion v2 = manuscriptVersionRepository.findById(versionId2);

        if (v1 == null || v2 == null) {
            throw new BusinessRuleException("One or both manuscript versions not found");
        }

        if (!v1.getChapterId().equals(v2.getChapterId())) {
            throw new BusinessRuleException("Cannot compare versions from different chapters");
        }

        List<ManuscriptPage> pages1 = manuscriptPageRepository.findByManuscriptVersionIdOrderByDisplayOrder(versionId1);
        List<ManuscriptPage> pages2 = manuscriptPageRepository.findByManuscriptVersionIdOrderByDisplayOrder(versionId2);

        manga.dto.VersionComparisonDTO comparison = new manga.dto.VersionComparisonDTO();
        comparison.setVersion1Id(versionId1);
        comparison.setVersion2Id(versionId2);
        comparison.setVersion1Number(v1.getVersion());
        comparison.setVersion2Number(v2.getVersion());
        comparison.setAddedPages(new ArrayList<>());
        comparison.setRemovedPages(new ArrayList<>());
        comparison.setChangedPages(new ArrayList<>());
        comparison.setReorderedPages(new ArrayList<>());

        // Track pages by source chapter image ID for comparison
        java.util.Map<Long, ManuscriptPage> pages1BySource = new java.util.HashMap<>();
        for (ManuscriptPage p : pages1) {
            if (p.getSourceChapterImageId() != null) {
                pages1BySource.put(p.getSourceChapterImageId(), p);
            }
        }

        java.util.Map<Long, ManuscriptPage> pages2BySource = new java.util.HashMap<>();
        for (ManuscriptPage p : pages2) {
            if (p.getSourceChapterImageId() != null) {
                pages2BySource.put(p.getSourceChapterImageId(), p);
            }
        }

        // Identify added pages (in v2 but not in v1)
        for (ManuscriptPage p2 : pages2) {
            if (p2.getSourceChapterImageId() != null && !pages1BySource.containsKey(p2.getSourceChapterImageId())) {
                comparison.getAddedPages().add(mapToPageComparisonDTO(p2, "ADDED"));
            }
        }

        // Identify removed pages (in v1 but not in v2)
        for (ManuscriptPage p1 : pages1) {
            if (p1.getSourceChapterImageId() != null && !pages2BySource.containsKey(p1.getSourceChapterImageId())) {
                comparison.getRemovedPages().add(mapToPageComparisonDTO(p1, "REMOVED"));
            }
        }

        // Identify changed and reordered pages
        for (ManuscriptPage p1 : pages1) {
            if (p1.getSourceChapterImageId() == null) {
                continue;
            }

            ManuscriptPage p2 = pages2BySource.get(p1.getSourceChapterImageId());
            if (p2 != null) {
                // Check if page changed (different snapshot)
                if (!p1.getSnapshotFileUrl().equals(p2.getSnapshotFileUrl())
                        || !p1.getSnapshotChecksum().equals(p2.getSnapshotChecksum())) {
                    comparison.getChangedPages().add(mapToPageComparisonDTO(p2, "CHANGED"));
                }

                // Check if page reordered (different display order)
                if (!p1.getDisplayOrder().equals(p2.getDisplayOrder())) {
                    manga.dto.PageComparisonDTO reorderDTO = mapToPageComparisonDTO(p2, "REORDERED");
                    reorderDTO.setPreviousOrder(p1.getDisplayOrder());
                    reorderDTO.setNewOrder(p2.getDisplayOrder());
                    comparison.getReorderedPages().add(reorderDTO);
                }
            }
        }

        return comparison;
    }

    private manga.dto.PageComparisonDTO mapToPageComparisonDTO(ManuscriptPage page, String changeType) {
        manga.dto.PageComparisonDTO dto = new manga.dto.PageComparisonDTO();
        dto.setPageId(page.getId());
        dto.setDisplayOrder(page.getDisplayOrder());
        dto.setPageNumber(page.getPageNumber());
        dto.setSnapshotFileUrl(page.getSnapshotFileUrl());
        dto.setSourceChapterImageId(page.getSourceChapterImageId());
        dto.setChangeType(changeType);
        return dto;
    }

    /**
     * Get annotations for a specific manuscript page.
     */
    public List<manga.model.AnnotationSummary> getPageAnnotations(Long manuscriptVersionId, Long pageId) {
        // This would delegate to AnnotationServiceV2
        // For now, return empty list as placeholder
        return new java.util.ArrayList<>();
    }

    /**
     * Phase 10: Review Dashboard - Get review progress summary for manuscript
     * version. Returns comprehensive review status including version info, page
     * counts, annotation counts, and progress.
     */
    public manga.dto.ReviewDashboardDTO getReviewDashboard(Long manuscriptVersionId) {
        ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }

        List<ManuscriptPage> pages = manuscriptPageRepository.findByManuscriptVersionIdOrderByDisplayOrder(manuscriptVersionId);
        long totalAnnotations = annotationServiceV2.countOpenAnnotations(manuscriptVersionId);
        long openAnnotations = totalAnnotations; // Currently only counting open

        // Get total annotation count (all statuses)
        long totalAnnotationCount = getTotalAnnotationCount(manuscriptVersionId);
        long resolvedAnnotations = totalAnnotationCount - openAnnotations;

        manga.dto.ReviewDashboardDTO dashboard = new manga.dto.ReviewDashboardDTO();
        dashboard.setManuscriptVersionId(manuscriptVersionId);
        dashboard.setVersionNumber(version.getVersion());
        dashboard.setStatus(version.getStatus().name());
        dashboard.setChapterId(version.getChapterId());
        dashboard.setTotalPages(pages.size());
        dashboard.setOpenAnnotations((int) openAnnotations);
        dashboard.setResolvedAnnotations((int) resolvedAnnotations);
        dashboard.setTotalAnnotations((int) totalAnnotationCount);

        // Calculate review progress
        if (totalAnnotationCount == 0) {
            dashboard.setReviewProgress(100.0); // No annotations = ready
        } else {
            dashboard.setReviewProgress((resolvedAnnotations * 100.0) / totalAnnotationCount);
        }

        // Set timestamps
        dashboard.setCreatedAt(version.getCreatedAt());
        dashboard.setSubmittedAt(version.getSubmittedAt());
        dashboard.setApprovedAt(version.getApprovedAt());
        dashboard.setRejectedAt(version.getRejectedAt());

        // Get latest activity
        List<manga.model.ReviewDecision> decisions = reviewDecisionRepository.findByManuscriptVersionId(manuscriptVersionId);
        if (!decisions.isEmpty()) {
            dashboard.setLatestActivity(decisions.get(0).getDecisionAt());
        } else {
            dashboard.setLatestActivity(version.getCreatedAt());
        }

        // Get review history
        dashboard.setReviewHistory(decisions);

        // Check if ready for approval
        dashboard.setReadyForApproval(openAnnotations == 0 && version.getStatus() == ManuscriptStatus.UNDER_REVIEW);

        return dashboard;
    }

    private long getTotalAnnotationCount(Long manuscriptVersionId) {
        String sql = "SELECT COUNT(*) FROM Annotation WHERE manuscriptVersionId = ?";
        try ( java.sql.Connection conn = dataSource.getConnection();  java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, manuscriptVersionId);
            try ( java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (java.sql.SQLException ex) {
            throw new RuntimeException("Cannot count total annotations", ex);
        }
        return 0;
    }

    /**
     * Get candidate chapter images for manuscript builder. Returns approved
     * chapter images that can be used to build manuscript pages.
     */
    public List<manga.dto.chaptertask.ChapterImageDTO> getCandidatePages(Long chapterId) {
        // Validate chapter is in EDITORIAL_REVIEW
        String chapterStatus = chapterRepository.getChapterStatus(chapterId);
        if (!"EDITORIAL_REVIEW".equals(chapterStatus)) {
            throw new BusinessRuleException("Chapter must be in EDITORIAL_REVIEW to get candidate pages");
        }

        // Query one latest approved chapter image per page number.
        String sql = "SELECT id, chapterId, pageNumber, fileUrl, uploadedAt "
                + "FROM ("
                + "  SELECT id, chapterId, pageNumber, fileUrl, uploadedAt, "
                + "         ROW_NUMBER() OVER (PARTITION BY pageNumber ORDER BY uploadedAt DESC, id DESC) AS rn "
                + "  FROM ChapterImage "
                + "  WHERE chapterId = ? AND isActive = 1 AND imageType = 'PAGE' AND pageNumber IS NOT NULL"
                + ") x "
                + "WHERE rn = 1 ORDER BY pageNumber ASC";
        List<manga.dto.chaptertask.ChapterImageDTO> results = new java.util.ArrayList<>();
        try ( java.sql.Connection conn = dataSource.getConnection();  java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chapterId);
            try ( java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    manga.dto.chaptertask.ChapterImageDTO dto = new manga.dto.chaptertask.ChapterImageDTO();
                    dto.setId(rs.getLong("id"));
                    dto.setChapterId(rs.getLong("chapterId"));
                    dto.setDisplayOrder(rs.getInt("pageNumber"));
                    dto.setImageUrl(rs.getString("fileUrl"));
                    dto.setPageNumber(rs.getInt("pageNumber"));
                    dto.setCreatedAt(rs.getTimestamp("uploadedAt") != null ? rs.getTimestamp("uploadedAt").toLocalDateTime() : null);
                    results.add(dto);
                }
            }
        } catch (java.sql.SQLException ex) {
            throw new RuntimeException("Cannot get candidate pages", ex);
        }
        return results;
    }

    /**
     * Check if chapter has active production lock.
     */
    public boolean isProductionLocked(Long chapterId) {
        return lockRepository.findByChapterId(chapterId) != null;
    }

    /**
     * Find all manuscript versions with UNDER_REVIEW status, optionally
     * filtered by series assignments. Used for Tantou review inbox.
     *
     * @param tantouUserId the Tantou user ID (null for admin to see all)
     * @param isAdmin whether the user is an admin
     * @return list of under-review manuscript versions
     */
    public List<ManuscriptVersion> findUnderReviewForTantou(Long tantouUserId, boolean isAdmin) {
        return manuscriptVersionRepository.findUnderReviewForTantou(tantouUserId, isAdmin);
    }

    private void lockProduction(Long chapterId, Long manuscriptVersionId, Long lockedBy) {
        // Validate chapterId before creating lock to prevent orphaned locks
        if (chapterId == null || chapterId == 0) {
            throw new BusinessRuleException("Cannot create production lock with invalid chapterId: " + chapterId);
        }

        ManuscriptProductionLock lock = new ManuscriptProductionLock();
        lock.setChapterId(chapterId);
        lock.setManuscriptVersionId(manuscriptVersionId);
        lock.setLockedAt(LocalDateTime.now());
        lock.setLockedBy(lockedBy);
        lockRepository.create(lock);
    }

    private String createImmutableSnapshot(String originalUrl) {
        // Implementation: Copy file to immutable storage
        // Generate new URL with checksum
        // Return immutable URL
        // For now, return the original URL as placeholder
        return originalUrl;
    }

    private String calculateChecksum(String fileUrl) {
        // Implementation: Calculate SHA-256 checksum
        // For now, return placeholder
        return "placeholder-checksum-" + System.currentTimeMillis();
    }
}
