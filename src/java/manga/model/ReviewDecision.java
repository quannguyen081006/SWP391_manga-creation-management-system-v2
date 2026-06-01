package manga.model;

import java.time.LocalDateTime;
import manga.enums.ReviewDecisionType;

/**
 * ReviewDecision entity - Audit trail for manuscript review decisions.
 * 
 * Tracks who approved or rejected a manuscript version, when, and why.
 * Preserves decision history forever for audit purposes.
 */
public class ReviewDecision {
    private Long id;
    private Long manuscriptVersionId;
    private Long reviewerId;
    private String reviewerName;
    private ReviewDecisionType decisionType;
    private String comment;
    private LocalDateTime decisionAt;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getManuscriptVersionId() {
        return manuscriptVersionId;
    }
    
    public void setManuscriptVersionId(Long manuscriptVersionId) {
        this.manuscriptVersionId = manuscriptVersionId;
    }
    
    public Long getReviewerId() {
        return reviewerId;
    }
    
    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }
    
    public String getReviewerName() {
        return reviewerName;
    }
    
    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }
    
    public ReviewDecisionType getDecisionType() {
        return decisionType;
    }
    
    public void setDecisionType(ReviewDecisionType decisionType) {
        this.decisionType = decisionType;
    }
    
    public String getComment() {
        return comment;
    }
    
    public void setComment(String comment) {
        this.comment = comment;
    }
    
    public LocalDateTime getDecisionAt() {
        return decisionAt;
    }
    
    public void setDecisionAt(LocalDateTime decisionAt) {
        this.decisionAt = decisionAt;
    }
}
