package manga.model;

public class DeadlineSettings {
    private int taskChapterBufferDays;
    private int chapterSeriesBufferDays;

    public int getTaskChapterBufferDays() { return taskChapterBufferDays; }
    public void setTaskChapterBufferDays(int taskChapterBufferDays) { this.taskChapterBufferDays = taskChapterBufferDays; }
    public int getChapterSeriesBufferDays() { return chapterSeriesBufferDays; }
    public void setChapterSeriesBufferDays(int chapterSeriesBufferDays) { this.chapterSeriesBufferDays = chapterSeriesBufferDays; }
}
