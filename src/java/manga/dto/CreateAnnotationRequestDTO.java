package manga.dto;

/**
 * DTO for creating annotation requests.
 */
public class CreateAnnotationRequestDTO {

    private Long manuscriptVersionId;
    private Long manuscriptPageId;
    private Integer pageNumber;
    private String category;
    private String severity;
    private String content;
    private Double xCoordinatePercent;
    private Double yCoordinatePercent;
    private Double widthPercent;
    private Double heightPercent;
    private Long parentAnnotationId;

    // Getters and Setters
    public Long getManuscriptVersionId() {
        return manuscriptVersionId;
    }

    public void setManuscriptVersionId(Long manuscriptVersionId) {
        this.manuscriptVersionId = manuscriptVersionId;
    }

    public Long getManuscriptPageId() {
        return manuscriptPageId;
    }

    public void setManuscriptPageId(Long manuscriptPageId) {
        this.manuscriptPageId = manuscriptPageId;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Double getxCoordinatePercent() {
        return xCoordinatePercent;
    }

    public void setxCoordinatePercent(Double xCoordinatePercent) {
        this.xCoordinatePercent = xCoordinatePercent;
    }

    public Double getyCoordinatePercent() {
        return yCoordinatePercent;
    }

    public void setyCoordinatePercent(Double yCoordinatePercent) {
        this.yCoordinatePercent = yCoordinatePercent;
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

    public Long getParentAnnotationId() {
        return parentAnnotationId;
    }

    public void setParentAnnotationId(Long parentAnnotationId) {
        this.parentAnnotationId = parentAnnotationId;
    }

    @Override
    public String toString() {
        return "CreateAnnotationRequestDTO{"
                + "manuscriptVersionId=" + manuscriptVersionId
                + ", manuscriptPageId=" + manuscriptPageId
                + ", category='" + category + '\''
                + ", severity='" + severity + '\''
                + ", content='" + content + '\''
                + ", xCoordinatePercent=" + xCoordinatePercent
                + ", yCoordinatePercent=" + yCoordinatePercent
                + ", widthPercent=" + widthPercent
                + ", heightPercent=" + heightPercent
                + ", parentAnnotationId=" + parentAnnotationId
                + '}';
    }
}
