package manga.dto;

import java.time.LocalDateTime;
import java.util.List;
import manga.model.ReviewDecision;

/**
 * DTO for review dashboard summary.
 * Contains comprehensive review status and progress information.
 */
public class ReviewDashboardDTO {
    private Long manuscriptVersionId;
    private Integer versionNumber;
    private String status;
    private Long chapterId;
    private Integer totalPages;
    private Integer openAnnotations;
    private Integer resolvedAnnotations;
    private Integer totalAnnotations;
    private Double reviewProgress;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime latestActivity;
    private List<ReviewDecision> reviewHistory;
    private Boolean readyForApproval;

    public Long getManuscriptVersionId() {
        return manuscriptVersionId;
    }

    public void setManuscriptVersionId(Long manuscriptVersionId) {
        this.manuscriptVersionId = manuscriptVersionId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Integer getOpenAnnotations() {
        return openAnnotations;
    }

    public void setOpenAnnotations(Integer openAnnotations) {
        this.openAnnotations = openAnnotations;
    }

    public Integer getResolvedAnnotations() {
        return resolvedAnnotations;
    }

    public void setResolvedAnnotations(Integer resolvedAnnotations) {
        this.resolvedAnnotations = resolvedAnnotations;
    }

    public Integer getTotalAnnotations() {
        return totalAnnotations;
    }

    public void setTotalAnnotations(Integer totalAnnotations) {
        this.totalAnnotations = totalAnnotations;
    }

    public Double getReviewProgress() {
        return reviewProgress;
    }

    public void setReviewProgress(Double reviewProgress) {
        this.reviewProgress = reviewProgress;
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

    public LocalDateTime getLatestActivity() {
        return latestActivity;
    }

    public void setLatestActivity(LocalDateTime latestActivity) {
        this.latestActivity = latestActivity;
    }

    public List<ReviewDecision> getReviewHistory() {
        return reviewHistory;
    }

    public void setReviewHistory(List<ReviewDecision> reviewHistory) {
        this.reviewHistory = reviewHistory;
    }

    public Boolean getReadyForApproval() {
        return readyForApproval;
    }

    public void setReadyForApproval(Boolean readyForApproval) {
        this.readyForApproval = readyForApproval;
    }
}
