package manga.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import manga.enums.ManuscriptStatus;

/**
 * ManuscriptVersion entity - Aggregate root for the manuscript workspace.
 * 
 * Represents a versioned manuscript workspace with immutable page snapshots.
 * Each version stores its own pages and annotations, supporting v1 → v2 → v3 chains.
 * 
 * Business Rules:
 * - BR-1: Only chapters in EDITORIAL_REVIEW can create manuscripts
 * - BR-2: Only one UNDER_REVIEW per chapter
 * - BR-3: REJECTED manuscripts cannot be mutated
 * - BR-4: APPROVED manuscripts cannot be mutated
 */
public class ManuscriptVersion {
    private Long id;
    private Long chapterId;
    private Integer version;
    private Long previousVersionId;
    private ManuscriptStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime publishedAt;
    private Long createdBy;
    private Long submittedBy;
    private Long approvedBy;
    private Long rejectedBy;
    private String feedback;
    private String revisionNotes;
    private Integer totalPageCount;
    
    // Child entities
    private List<ManuscriptPage> pages = new ArrayList<>();
    private List<Annotation> annotations = new ArrayList<>();
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getChapterId() {
        return chapterId;
    }
    
    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }
    
    public Integer getVersion() {
        return version;
    }
    
    public void setVersion(Integer version) {
        this.version = version;
    }
    
    public Long getPreviousVersionId() {
        return previousVersionId;
    }
    
    public void setPreviousVersionId(Long previousVersionId) {
        this.previousVersionId = previousVersionId;
    }
    
    public ManuscriptStatus getStatus() {
        return status;
    }
    
    public void setStatus(ManuscriptStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
    
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
    
    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }
    
    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }
    
    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }
    
    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }
    
    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
    
    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
    
    public Long getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
    
    public Long getSubmittedBy() {
        return submittedBy;
    }
    
    public void setSubmittedBy(Long submittedBy) {
        this.submittedBy = submittedBy;
    }
    
    public Long getApprovedBy() {
        return approvedBy;
    }
    
    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }
    
    public Long getRejectedBy() {
        return rejectedBy;
    }
    
    public void setRejectedBy(Long rejectedBy) {
        this.rejectedBy = rejectedBy;
    }
    
    public String getFeedback() {
        return feedback;
    }
    
    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
    
    public String getRevisionNotes() {
        return revisionNotes;
    }
    
    public void setRevisionNotes(String revisionNotes) {
        this.revisionNotes = revisionNotes;
    }
    
    public Integer getTotalPageCount() {
        return totalPageCount;
    }
    
    public void setTotalPageCount(Integer totalPageCount) {
        this.totalPageCount = totalPageCount;
    }
    
    public List<ManuscriptPage> getPages() {
        return pages;
    }
    
    public void setPages(List<ManuscriptPage> pages) {
        this.pages = pages;
    }
    
    public List<Annotation> getAnnotations() {
        return annotations;
    }
    
    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }
    
    // Business rule validation methods
    
    /**
     * Check if this manuscript version can be edited.
     * Uses enum helper for consistency.
     */
    public boolean canEdit() {
        return status.isEditable();
    }
    
    /**
     * Check if this manuscript version can be submitted for review.
     * Valid transitions: DRAFT → SUBMITTED_FOR_REVIEW, IN_PROGRESS → SUBMITTED_FOR_REVIEW
     */
    public boolean canSubmit() {
        return (status == ManuscriptStatus.DRAFT || status == ManuscriptStatus.IN_PROGRESS) && !pages.isEmpty();
    }
    
    /**
     * Check if this manuscript version can be approved.
     * Valid transition: UNDER_REVIEW → APPROVED
     */
    public boolean canApprove() {
        return status == ManuscriptStatus.UNDER_REVIEW;
    }
    
    /**
     * Check if this manuscript version can be rejected.
     * Valid transition: UNDER_REVIEW → REJECTED
     */
    public boolean canReject() {
        return status == ManuscriptStatus.UNDER_REVIEW;
    }
    
    /**
     * Check if this is the latest version for the chapter.
     * This would be validated at the service/repository level.
     */
    public boolean isLatestVersion() {
        // This would be checked by querying the repository
        return true; // Placeholder
    }
    
    /**
     * Check if this version can generate the next version.
     * Only REJECTED versions can generate next version for revision.
     */
    public boolean canGenerateNextVersion() {
        return status == ManuscriptStatus.REJECTED;
    }
    
    /**
     * Check if this manuscript version is immutable.
     * Uses enum helper for consistency.
     */
    public boolean isImmutable() {
        return status.isImmutable();
    }
    
    /**
     * Validate that this manuscript can be edited.
     * Throws exception if immutable or in review.
     */
    public void validateEditable() {
        if (!status.isEditable()) {
            throw new IllegalStateException(
                "Manuscript version " + version + " is " + status + " and cannot be edited. " +
                "Only DRAFT and IN_PROGRESS statuses are editable."
            );
        }
    }
    
    /**
     * Validate that this manuscript can be modified (add pages, reorder, etc.).
     * Throws exception if not in editable status.
     */
    public void validateMutable() {
        if (status.isImmutable()) {
            throw new IllegalStateException(
                "Manuscript version " + version + " is " + status + " and cannot be modified. " +
                "Status is immutable."
            );
        }
        if (status.isInReview()) {
            throw new IllegalStateException(
                "Manuscript version " + version + " is " + status + " and cannot be modified. " +
                "Status is in review workflow."
            );
        }
    }
    
    /**
     * Check if this manuscript version can be published.
     * Valid transition: APPROVED → PUBLISHED
     */
    public boolean canPublish() {
        return status == ManuscriptStatus.APPROVED;
    }
    
    /**
     * Validate status transition.
     * Throws exception if transition is invalid.
     */
    public void validateTransition(ManuscriptStatus newStatus) {
        if (status == newStatus) {
            throw new IllegalStateException("Cannot transition to same status: " + newStatus);
        }
        
        // Define valid transitions
        switch (status) {
            case DRAFT:
                if (newStatus != ManuscriptStatus.IN_PROGRESS && 
                    newStatus != ManuscriptStatus.SUBMITTED_FOR_REVIEW &&
                    newStatus != ManuscriptStatus.ARCHIVED) {
                    throw new IllegalStateException(
                        "Invalid transition from DRAFT to " + newStatus + ". " +
                        "Valid transitions: DRAFT → IN_PROGRESS, DRAFT → SUBMITTED_FOR_REVIEW, DRAFT → ARCHIVED"
                    );
                }
                break;
            case IN_PROGRESS:
                if (newStatus != ManuscriptStatus.SUBMITTED_FOR_REVIEW &&
                    newStatus != ManuscriptStatus.DRAFT &&
                    newStatus != ManuscriptStatus.ARCHIVED) {
                    throw new IllegalStateException(
                        "Invalid transition from IN_PROGRESS to " + newStatus + ". " +
                        "Valid transitions: IN_PROGRESS → SUBMITTED_FOR_REVIEW, IN_PROGRESS → DRAFT, IN_PROGRESS → ARCHIVED"
                    );
                }
                break;
            case SUBMITTED_FOR_REVIEW:
                if (newStatus != ManuscriptStatus.UNDER_REVIEW &&
                    newStatus != ManuscriptStatus.DRAFT &&
                    newStatus != ManuscriptStatus.ARCHIVED) {
                    throw new IllegalStateException(
                        "Invalid transition from SUBMITTED_FOR_REVIEW to " + newStatus + ". " +
                        "Valid transitions: SUBMITTED_FOR_REVIEW → UNDER_REVIEW, SUBMITTED_FOR_REVIEW → DRAFT, SUBMITTED_FOR_REVIEW → ARCHIVED"
                    );
                }
                break;
            case UNDER_REVIEW:
                if (newStatus != ManuscriptStatus.APPROVED && 
                    newStatus != ManuscriptStatus.REJECTED &&
                    newStatus != ManuscriptStatus.ARCHIVED) {
                    throw new IllegalStateException(
                        "Invalid transition from UNDER_REVIEW to " + newStatus + ". " +
                        "Valid transitions: UNDER_REVIEW → APPROVED, UNDER_REVIEW → REJECTED, UNDER_REVIEW → ARCHIVED"
                    );
                }
                break;
            case APPROVED:
                if (newStatus != ManuscriptStatus.PUBLISHED &&
                    newStatus != ManuscriptStatus.ARCHIVED) {
                    throw new IllegalStateException(
                        "Invalid transition from APPROVED to " + newStatus + ". " +
                        "Valid transitions: APPROVED → PUBLISHED, APPROVED → ARCHIVED"
                    );
                }
                break;
            case PUBLISHED:
                if (newStatus != ManuscriptStatus.ARCHIVED) {
                    throw new IllegalStateException(
                        "Invalid transition from PUBLISHED to " + newStatus + ". " +
                        "Valid transitions: PUBLISHED → ARCHIVED"
                    );
                }
                break;
            case REJECTED:
                if (newStatus != ManuscriptStatus.ARCHIVED) {
                    throw new IllegalStateException(
                        "Invalid transition from REJECTED to " + newStatus + ". " +
                        "Valid transitions: REJECTED → ARCHIVED (use createNewVersion for revision)"
                    );
                }
                break;
            case ARCHIVED:
                throw new IllegalStateException(
                    "Cannot transition from ARCHIVED status. Status is final."
                );
        }
    }
}
