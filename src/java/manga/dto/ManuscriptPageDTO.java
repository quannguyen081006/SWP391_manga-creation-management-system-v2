package manga.dto;

import java.time.LocalDateTime;

/**
 * DTO for ManuscriptPage API responses.
 */
public class ManuscriptPageDTO {
    private Long id;
    private Long manuscriptVersionId;
    private Integer displayOrder;
    private String snapshotFileUrl;
    private String originalFileUrl;
    private Long sourceChapterImageId;
    private Long sourcePageTaskId;
    private Integer pageNumber;
    private LocalDateTime snapshotCreatedAt;
    private String snapshotChecksum;

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

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getSnapshotFileUrl() {
        return snapshotFileUrl;
    }

    public void setSnapshotFileUrl(String snapshotFileUrl) {
        this.snapshotFileUrl = snapshotFileUrl;
    }

    public String getOriginalFileUrl() {
        return originalFileUrl;
    }

    public void setOriginalFileUrl(String originalFileUrl) {
        this.originalFileUrl = originalFileUrl;
    }

    public Long getSourceChapterImageId() {
        return sourceChapterImageId;
    }

    public void setSourceChapterImageId(Long sourceChapterImageId) {
        this.sourceChapterImageId = sourceChapterImageId;
    }

    public Long getSourcePageTaskId() {
        return sourcePageTaskId;
    }

    public void setSourcePageTaskId(Long sourcePageTaskId) {
        this.sourcePageTaskId = sourcePageTaskId;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public LocalDateTime getSnapshotCreatedAt() {
        return snapshotCreatedAt;
    }

    public void setSnapshotCreatedAt(LocalDateTime snapshotCreatedAt) {
        this.snapshotCreatedAt = snapshotCreatedAt;
    }

    public String getSnapshotChecksum() {
        return snapshotChecksum;
    }

    public void setSnapshotChecksum(String snapshotChecksum) {
        this.snapshotChecksum = snapshotChecksum;
    }
}
