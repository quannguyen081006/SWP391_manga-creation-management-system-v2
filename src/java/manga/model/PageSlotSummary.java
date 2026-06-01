package manga.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.sql.Timestamp;

public class PageSlotSummary {
    private long id;
    private long chapterId;
    private int pageNumber;
    private String imageUrl;
    private Long uploadedBy;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Timestamp uploadedAt;
    private String status;
    private String completedStage;
    private Long taskId;
    private String taskType;
    private String taskStatus;
    private Long assistantId;
    private String assistantName;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getChapterId() { return chapterId; }
    public void setChapterId(long chapterId) { this.chapterId = chapterId; }
    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }
    public Timestamp getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Timestamp uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCompletedStage() { return completedStage; }
    public void setCompletedStage(String completedStage) { this.completedStage = completedStage; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public Long getAssistantId() { return assistantId; }
    public void setAssistantId(Long assistantId) { this.assistantId = assistantId; }
    public String getAssistantName() { return assistantName; }
    public void setAssistantName(String assistantName) { this.assistantName = assistantName; }
}
