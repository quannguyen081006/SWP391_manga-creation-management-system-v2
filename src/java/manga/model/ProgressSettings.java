package manga.model;

public class ProgressSettings {
    private int lowThresholdPercent;
    private int highThresholdPercent;

    public int getLowThresholdPercent() { return lowThresholdPercent; }
    public void setLowThresholdPercent(int lowThresholdPercent) { this.lowThresholdPercent = lowThresholdPercent; }
    public int getHighThresholdPercent() { return highThresholdPercent; }
    public void setHighThresholdPercent(int highThresholdPercent) { this.highThresholdPercent = highThresholdPercent; }
}
