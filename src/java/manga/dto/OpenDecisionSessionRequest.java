package manga.dto;

public class OpenDecisionSessionRequest {
    private long seriesId;
    private long rankingRecordId;

    public long getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(long seriesId) {
        this.seriesId = seriesId;
    }

    public long getRankingRecordId() {
        return rankingRecordId;
    }

    public void setRankingRecordId(long rankingRecordId) {
        this.rankingRecordId = rankingRecordId;
    }
}
