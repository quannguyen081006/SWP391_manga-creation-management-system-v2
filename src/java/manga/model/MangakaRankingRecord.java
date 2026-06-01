package manga.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class MangakaRankingRecord {
    private long id;
    private long periodId;
    private long mangakaId;
    private String mangakaName;
    private long totalReads;
    private BigDecimal totalRevenue;
    private long totalLikes;
    private int rankPosition;
    private Timestamp calculatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPeriodId() {
        return periodId;
    }

    public void setPeriodId(long periodId) {
        this.periodId = periodId;
    }

    public long getMangakaId() {
        return mangakaId;
    }

    public void setMangakaId(long mangakaId) {
        this.mangakaId = mangakaId;
    }

    public String getMangakaName() {
        return mangakaName;
    }

    public void setMangakaName(String mangakaName) {
        this.mangakaName = mangakaName;
    }

    public long getTotalReads() {
        return totalReads;
    }

    public void setTotalReads(long totalReads) {
        this.totalReads = totalReads;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public long getTotalLikes() {
        return totalLikes;
    }

    public void setTotalLikes(long totalLikes) {
        this.totalLikes = totalLikes;
    }

    public int getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(int rankPosition) {
        this.rankPosition = rankPosition;
    }

    public Timestamp getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Timestamp calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}
