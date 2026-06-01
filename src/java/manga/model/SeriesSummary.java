package manga.model;

import java.sql.Date;

public class SeriesSummary {
    private long id;
    private String title;
    private String genre;
    private String status;
    private long mangakaId;
    private long tantouEditorId;
    private Date publicationDate;
    private int chapterCount;
    private int inProgressChapters;
    private double progressPct;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getMangakaId() { return mangakaId; }
    public void setMangakaId(long mangakaId) { this.mangakaId = mangakaId; }
    public long getTantouEditorId() { return tantouEditorId; }
    public void setTantouEditorId(long tantouEditorId) { this.tantouEditorId = tantouEditorId; }
    public Date getPublicationDate() { return publicationDate; }
    public void setPublicationDate(Date publicationDate) { this.publicationDate = publicationDate; }
    public int getChapterCount() { return chapterCount; }
    public void setChapterCount(int chapterCount) { this.chapterCount = chapterCount; }
    public int getInProgressChapters() { return inProgressChapters; }
    public void setInProgressChapters(int inProgressChapters) { this.inProgressChapters = inProgressChapters; }
    public double getProgressPct() { return progressPct; }
    public void setProgressPct(double progressPct) { this.progressPct = progressPct; }
}
