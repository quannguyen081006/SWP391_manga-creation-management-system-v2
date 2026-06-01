package manga.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.sql.Date;

public class ChapterSummary {
    private long id;
    private long seriesId;
    private int chapterNumber;
    private String title;
    private String status;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date submissionDeadline;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date publicationDate;
    private double completionPct;
    private boolean atRisk;
    private Integer totalPages;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getSeriesId() { return seriesId; }
    public void setSeriesId(long seriesId) { this.seriesId = seriesId; }
    public int getChapterNumber() { return chapterNumber; }
    public void setChapterNumber(int chapterNumber) { this.chapterNumber = chapterNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getSubmissionDeadline() { return submissionDeadline; }
    public void setSubmissionDeadline(Date submissionDeadline) { this.submissionDeadline = submissionDeadline; }
    public Date getPublicationDate() { return publicationDate; }
    public void setPublicationDate(Date publicationDate) { this.publicationDate = publicationDate; }
    public double getCompletionPct() { return completionPct; }
    public void setCompletionPct(double completionPct) { this.completionPct = completionPct; }
    public boolean isAtRisk() { return atRisk; }
    public void setAtRisk(boolean atRisk) { this.atRisk = atRisk; }
    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
}

