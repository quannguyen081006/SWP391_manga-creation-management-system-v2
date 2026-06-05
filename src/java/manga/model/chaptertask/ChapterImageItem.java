package manga.model.chaptertask;

import java.sql.Timestamp;

public class ChapterImageItem {
    private long id;
    private long chapterId;
    private Long pageTaskId;
    private long uploadedBy;
    private String imageType;
    private Integer pageNumber;
    private String fileUrl;
    private String originalFileName;
    private long fileSizeBytes;
    private Timestamp uploadedAt;
    private boolean isActive;
    private String note;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getChapterId() { return chapterId; }
    public void setChapterId(long chapterId) { this.chapterId = chapterId; }
    public Long getPageTaskId() { return pageTaskId; }
    public void setPageTaskId(Long pageTaskId) { this.pageTaskId = pageTaskId; }
    public long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(long uploadedBy) { this.uploadedBy = uploadedBy; }
    public String getImageType() { return imageType; }
    public void setImageType(String imageType) { this.imageType = imageType; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public Timestamp getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Timestamp uploadedAt) { this.uploadedAt = uploadedAt; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

