package manga.model;

import java.sql.Timestamp;

public class ManuscriptSummary {
    private long id;
    private long chapterId;
    private int version;
    private String status;
    private Timestamp submittedAt;
    private Timestamp reviewDeadline;
    private String fileUrl;
    private String originalFileName;
    private Timestamp uploadedAt;
    private Long fileSize;
    private String fileExtension;
    private Timestamp revisionDeadline;
    private String feedback;
    private String notes;
    private String genre;
    private String seriesTitle;
    private String chapterTitle;
    private String title;
    private Integer chapterNumber;
    private String synopsis;
    private String mangakaName;
    private String reviewerName;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getChapterId() { return chapterId; }
    public void setChapterId(long chapterId) { this.chapterId = chapterId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Timestamp submittedAt) { this.submittedAt = submittedAt; }
    public Timestamp getReviewDeadline() { return reviewDeadline; }
    public void setReviewDeadline(Timestamp reviewDeadline) { this.reviewDeadline = reviewDeadline; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public Timestamp getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Timestamp uploadedAt) { this.uploadedAt = uploadedAt; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileExtension() { return fileExtension; }
    public void setFileExtension(String fileExtension) { this.fileExtension = fileExtension; }
    public Timestamp getRevisionDeadline() { return revisionDeadline; }
    public void setRevisionDeadline(Timestamp revisionDeadline) { this.revisionDeadline = revisionDeadline; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getSeriesTitle() { return seriesTitle; }
    public void setSeriesTitle(String seriesTitle) { this.seriesTitle = seriesTitle; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; this.title = chapterTitle; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getChapterNumber() { return chapterNumber; }
    public void setChapterNumber(Integer chapterNumber) { this.chapterNumber = chapterNumber; }
    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }
    public String getMangakaName() { return mangakaName; }
    public void setMangakaName(String mangakaName) { this.mangakaName = mangakaName; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
}
