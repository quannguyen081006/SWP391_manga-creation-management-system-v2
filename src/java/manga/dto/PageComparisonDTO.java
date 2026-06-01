package manga.dto;

/**
 * DTO for page comparison result.
 * Contains details about a page change between versions.
 */
public class PageComparisonDTO {
    private Long pageId;
    private Integer displayOrder;
    private Integer pageNumber;
    private String snapshotFileUrl;
    private Long sourceChapterImageId;
    private String changeType; // ADDED, REMOVED, CHANGED, REORDERED
    private Integer previousOrder; // For REORDERED changes
    private Integer newOrder; // For REORDERED changes

    public Long getPageId() {
        return pageId;
    }

    public void setPageId(Long pageId) {
        this.pageId = pageId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getSnapshotFileUrl() {
        return snapshotFileUrl;
    }

    public void setSnapshotFileUrl(String snapshotFileUrl) {
        this.snapshotFileUrl = snapshotFileUrl;
    }

    public Long getSourceChapterImageId() {
        return sourceChapterImageId;
    }

    public void setSourceChapterImageId(Long sourceChapterImageId) {
        this.sourceChapterImageId = sourceChapterImageId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public Integer getPreviousOrder() {
        return previousOrder;
    }

    public void setPreviousOrder(Integer previousOrder) {
        this.previousOrder = previousOrder;
    }

    public Integer getNewOrder() {
        return newOrder;
    }

    public void setNewOrder(Integer newOrder) {
        this.newOrder = newOrder;
    }
}
