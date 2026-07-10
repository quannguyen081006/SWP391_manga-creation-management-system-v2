package manga.model;

import java.time.LocalDateTime;
import manga.enums.ReviewDecisionType;

/**
 * Purpose-built audit row for manuscript review decisions.
 * ReviewDecision is separate from the general AuditLog table because manuscript
 * review screens need reviewer, decision type, comment, and decision time in a
 * domain-specific shape.
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
