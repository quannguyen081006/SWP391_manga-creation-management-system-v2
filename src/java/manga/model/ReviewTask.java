package manga.model;

import java.time.LocalDateTime;

/**
 * ReviewTask entity - Track review assignment and SLA compliance.
 * 
 * Business Rules:
 * - BR-51: ReviewTask must contain versionId, reviewerId, assignedAt, dueAt, reviewStatus
 * - BR-52: SLA: 48h review deadline, 36h warning threshold, overdue state
 */
public class ReviewTask {
    private Long id;
    private Long versionId;
    private Long reviewerId;
    private LocalDateTime assignedAt;
    private LocalDateTime dueAt;
    private String reviewStatus; // ASSIGNED, IN_PROGRESS, COMPLETED, OVERDUE
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getVersionId() {
        return versionId;
    }
    
    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }
    
    public Long getReviewerId() {
        return reviewerId;
    }
    
    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }
    
    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }
    
    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }
    
    public LocalDateTime getDueAt() {
        return dueAt;
    }
    
    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }
    
    public String getReviewStatus() {
        return reviewStatus;
    }
    
    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }
    
    // Business rule validation methods
    
    /**
     * Check if this review task is overdue.
     * BR-52: Overdue state after 48h
     */
    public boolean isOverdue() {
        return LocalDateTime.now().isAfter(dueAt);
    }
    
    /**
     * Check if this review task is in warning threshold.
     * BR-52: 36h warning threshold
     */
    public boolean isWarningThreshold() {
        LocalDateTime warningThreshold = dueAt.minusHours(36);
        return LocalDateTime.now().isAfter(warningThreshold) && !isOverdue();
    }
    
    /**
     * Calculate remaining hours until due.
     */
    public long getRemainingHours() {
        return java.time.Duration.between(LocalDateTime.now(), dueAt).toHours();
    }
}
