package manga.model;

import java.sql.Timestamp;

public class AnnotationSummary {
    private long id;
    private long manuscriptId;
    private long editorId;
    private String editorName;
    private int pageNumber;
    private String category;
    private String status;
    private String content;
    private Timestamp createdAt;
    
    // New fields for coordinate-based annotations
    private Double xPercent;
    private Double yPercent;
    private Double widthPercent;
    private Double heightPercent;
    private String severity;
    private Long parentAnnotationId;
    private Timestamp resolvedAt;
    private Long resolvedBy;
    private Long manuscriptVersionId;
    private Long manuscriptPageId;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getManuscriptId() { return manuscriptId; }
    public void setManuscriptId(long manuscriptId) { this.manuscriptId = manuscriptId; }
    public long getEditorId() { return editorId; }
    public void setEditorId(long editorId) { this.editorId = editorId; }
    public String getEditorName() { return editorName; }
    public void setEditorName(String editorName) { this.editorName = editorName; }
    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getComment() { return content; }
    public void setComment(String comment) { this.content = comment; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    // New getters and setters
    public Double getXPercent() { return xPercent; }
    
    public void setXPercent(Double xPercent) { this.xPercent = xPercent; }
    public Double getYPercent() { return yPercent; }
    public void setYPercent(Double yPercent) { this.yPercent = yPercent; }
    public Double getWidthPercent() { return widthPercent; }
    public void setWidthPercent(Double widthPercent) { this.widthPercent = widthPercent; }
    public Double getHeightPercent() { return heightPercent; }
    public void setHeightPercent(Double heightPercent) { this.heightPercent = heightPercent; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Long getParentAnnotationId() { return parentAnnotationId; }
    public void setParentAnnotationId(Long parentAnnotationId) { this.parentAnnotationId = parentAnnotationId; }
    public Timestamp getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Timestamp resolvedAt) { this.resolvedAt = resolvedAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public Long getManuscriptVersionId() { return manuscriptVersionId; }
    public void setManuscriptVersionId(Long manuscriptVersionId) { this.manuscriptVersionId = manuscriptVersionId; }
    public Long getManuscriptPageId() { return manuscriptPageId; }
    public void setManuscriptPageId(Long manuscriptPageId) { this.manuscriptPageId = manuscriptPageId; }
    
    public String debugVersion() {
    return "ANNOTATION_SUMMARY_V2";
}
}

