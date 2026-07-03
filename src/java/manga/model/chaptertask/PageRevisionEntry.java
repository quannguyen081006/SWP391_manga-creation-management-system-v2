package manga.model.chaptertask;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.sql.Timestamp;

/** 1 mốc lịch sử thay đổi ảnh/stage của 1 page (chapter workspace). */
public class PageRevisionEntry {
    private long id;
    private String imageUrl;
    private String completedStage;
    private String changedByName;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Timestamp changedAt;
    private String source;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getCompletedStage() { return completedStage; }
    public void setCompletedStage(String completedStage) { this.completedStage = completedStage; }
    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }
    public Timestamp getChangedAt() { return changedAt; }
    public void setChangedAt(Timestamp changedAt) { this.changedAt = changedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
