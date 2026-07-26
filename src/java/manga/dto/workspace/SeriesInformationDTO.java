package manga.dto.workspace;

/**
 * DTO for series information displayed in the manuscript workspace.
 * Contains aggregated series metadata for quick reference.
 */
public class SeriesInformationDTO {
    private String seriesTitle;
    private String status;
    private String genre;
    private String authorName;
    private String magazine;
    private int currentChapter;
    private int currentVersion;
    private int publishedChapterCount;
    private String currentReviewChapter;
    private long totalViews;

    public String getSeriesTitle() {
        return seriesTitle;
    }

    public void setSeriesTitle(String seriesTitle) {
        this.seriesTitle = seriesTitle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getMagazine() {
        return magazine;
    }

    public void setMagazine(String magazine) {
        this.magazine = magazine;
    }

    public int getCurrentChapter() {
        return currentChapter;
    }

    public void setCurrentChapter(int currentChapter) {
        this.currentChapter = currentChapter;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public int getPublishedChapterCount() {
        return publishedChapterCount;
    }

    public void setPublishedChapterCount(int publishedChapterCount) {
        this.publishedChapterCount = publishedChapterCount;
    }

    public String getCurrentReviewChapter() {
        return currentReviewChapter;
    }

    public void setCurrentReviewChapter(String currentReviewChapter) {
        this.currentReviewChapter = currentReviewChapter;
    }

    public long getTotalViews() {
        return totalViews;
    }

    public void setTotalViews(long totalViews) {
        this.totalViews = totalViews;
    }
}
