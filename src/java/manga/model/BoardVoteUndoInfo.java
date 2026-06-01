package manga.model;

public class BoardVoteUndoInfo {
    private long historyId;
    private String decision;
    private int remainingSeconds;

    public long getHistoryId() { return historyId; }
    public void setHistoryId(long historyId) { this.historyId = historyId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public int getRemainingSeconds() { return remainingSeconds; }
    public void setRemainingSeconds(int remainingSeconds) { this.remainingSeconds = remainingSeconds; }
}
