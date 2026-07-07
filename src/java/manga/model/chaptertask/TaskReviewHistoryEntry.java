package manga.model.chaptertask;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** One submit/review round in the submission history of a PageTask. */
public class TaskReviewHistoryEntry {
    private int roundNumber;
    private Timestamp submittedAt;
    private String submittedByName;
    private String decision;
    private Timestamp reviewedAt;
    private String reviewedByName;
    private String reviewComment;
    private List<ChapterImageItem> images = new ArrayList<ChapterImageItem>();

    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
    public Timestamp getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Timestamp submittedAt) { this.submittedAt = submittedAt; }
    public String getSubmittedByName() { return submittedByName; }
    public void setSubmittedByName(String submittedByName) { this.submittedByName = submittedByName; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public Timestamp getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Timestamp reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public List<ChapterImageItem> getImages() { return images; }
    public void setImages(List<ChapterImageItem> images) { this.images = images; }
}
