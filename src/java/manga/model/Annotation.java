package manga.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import manga.enums.AnnotationCategory;
import manga.enums.AnnotationSeverity;
import manga.enums.AnnotationStatus;
import manga.enums.ManuscriptStatus;

/**
 * Annotation entity - Rich coordinate-based editorial feedback.
 * 
 * Represents editorial annotations on manuscript pages with coordinate anchoring,
 * severity levels, and thread-based resolution workflow.
 * 
 * Business Rules:
 * - BR-5: Annotations belong to specific manuscript version
 * - BR-8: Coordinates must scale responsively (0-100 percentage-based)
 */
public class Annotation {
    private Long id;
    private Long manuscriptVersionId;
    private Long editorId;
    private String editorName;
    private Integer pageNumber;
    private Long manuscriptPageId;
    
    // Coordinate anchoring (BR-8: responsive scaling)
    private Double xPercent;
    private Double yPercent;
    private Double widthPercent;
    private Double heightPercent;
    
    private AnnotationCategory category;
    private AnnotationSeverity severity;
    private AnnotationStatus status;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    
    // Thread support
    private Long parentAnnotationId;
    private List<Annotation> replies = new ArrayList<>();
    
    // Legacy field for migration compatibility
    private Long manuscriptId;
    
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
    
    public Long getEditorId() {
        return editorId;
    }
    
    public void setEditorId(Long editorId) {
        this.editorId = editorId;
    }
    
    public String getEditorName() {
        return editorName;
    }
    
    public void setEditorName(String editorName) {
        this.editorName = editorName;
    }
    
    public Integer getPageNumber() {
        return pageNumber;
    }
    
    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }
    
    public Long getManuscriptPageId() {
        return manuscriptPageId;
    }
    
    public void setManuscriptPageId(Long manuscriptPageId) {
        this.manuscriptPageId = manuscriptPageId;
    }
    
    public Double getXPercent() {
        return xPercent;
    }
    
    public void setXPercent(Double xPercent) {
        this.xPercent = xPercent;
    }
    
    public Double getYPercent() {
        return yPercent;
    }
    
    public void setYPercent(Double yPercent) {
        this.yPercent = yPercent;
    }
    
    public Double getWidthPercent() {
        return widthPercent;
    }
    
    public void setWidthPercent(Double widthPercent) {
        this.widthPercent = widthPercent;
    }
    
    public Double getHeightPercent() {
        return heightPercent;
    }
    
    public void setHeightPercent(Double heightPercent) {
        this.heightPercent = heightPercent;
    }
    
    public AnnotationCategory getCategory() {
        return category;
    }
    
    public void setCategory(AnnotationCategory category) {
        this.category = category;
    }
    
    public AnnotationSeverity getSeverity() {
        return severity;
    }
    
    public void setSeverity(AnnotationSeverity severity) {
        this.severity = severity;
    }
    
    public AnnotationStatus getStatus() {
        return status;
    }
    
    public void setStatus(AnnotationStatus status) {
        this.status = status;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
    
    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
    
    public Long getResolvedBy() {
        return resolvedBy;
    }
    
    public void setResolvedBy(Long resolvedBy) {
        this.resolvedBy = resolvedBy;
    }
    
    public Long getParentAnnotationId() {
        return parentAnnotationId;
    }
    
    public void setParentAnnotationId(Long parentAnnotationId) {
        this.parentAnnotationId = parentAnnotationId;
    }
    
    public List<Annotation> getReplies() {
        return replies;
    }
    
    public void setReplies(List<Annotation> replies) {
        this.replies = replies;
    }
    
    public Long getManuscriptId() {
        return manuscriptId;
    }
    
    public void setManuscriptId(Long manuscriptId) {
        this.manuscriptId = manuscriptId;
    }
    
    // Business rule validation methods
    
    /**
     * Check if this annotation has valid coordinates.
     * BR-8: Coordinates must be between 0-100
     * Coordinates require manuscriptPageId to be set for page stability.
     */
    public boolean hasValidCoordinates() {
        if (xPercent == null || yPercent == null || widthPercent == null || heightPercent == null) {
            return false;
        }
        // Coordinates require manuscriptPageId to be set
        if (manuscriptPageId == null) {
            return false;
        }
        return xPercent >= 0 && xPercent <= 100
               && yPercent >= 0 && yPercent <= 100
               && widthPercent > 0 && widthPercent <= 100
               && heightPercent > 0 && heightPercent <= 100;
    }
    
    /**
     * Check if this is a root annotation (not a reply).
     */
    public boolean isRoot() {
        return parentAnnotationId == null;
    }
    
    /**
     * Check if this is a reply annotation.
     */
    public boolean isReply() {
        return parentAnnotationId != null;
    }
    
    /**
     * Check if this annotation is resolved.
     */
    public boolean isResolved() {
        return status == AnnotationStatus.RESOLVED;
    }
    
    /**
     * Check if this annotation can be edited.
     */
    public boolean canEdit() {
        return status == AnnotationStatus.OPEN || status == AnnotationStatus.IN_PROGRESS;
    }
    
    /**
     * Check if this annotation can be resolved.
     */
    public boolean canResolve() {
        return status == AnnotationStatus.OPEN || status == AnnotationStatus.IN_PROGRESS;
    }
    
    /**
     * Validate that this annotation can be edited.
     * Throws exception if manuscript is immutable.
     */
    public void validateEditable(Long manuscriptVersionId, ManuscriptStatus manuscriptStatus) {
        if (manuscriptStatus == ManuscriptStatus.APPROVED || 
            manuscriptStatus == ManuscriptStatus.PUBLISHED ||
            manuscriptStatus == ManuscriptStatus.REJECTED ||
            manuscriptStatus == ManuscriptStatus.ARCHIVED) {
            throw new IllegalStateException(
                "Cannot edit annotation: manuscript version is " + manuscriptStatus
            );
        }
    }
}
