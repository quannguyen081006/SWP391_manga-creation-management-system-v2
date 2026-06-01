package manga.model;

import java.sql.Timestamp;

public class Proposal {
    private long id;
    private long mangakaId;
    private String title;
    private String genre;
    private String synopsis;
    private String sampleFilePath;
    private String originalFileName;
    private Integer approximateChapter;
    private String status;
    private Timestamp submittedAt;
    private Timestamp rejectedAt;
    private Long assignedEditorId;
    private int submitAttemptCount;
    private int boardApproveVotes;
    private int boardReviseVotes;
    private int boardRejectVotes;
    private int boardTotalVotes;
    private Long boardRoundId;
    private Integer boardRoundNumber;
    private String boardRoundStatus;
    private Timestamp boardVotingOpenedAt;
    private Timestamp boardVotingClosesAt;
    private Timestamp boardVotingClosedAt;
    private int boardEligibleVoterCount;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getMangakaId() { return mangakaId; }
    public void setMangakaId(long mangakaId) { this.mangakaId = mangakaId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }

    public String getSampleFilePath() { return sampleFilePath; }
    public void setSampleFilePath(String sampleFilePath) { this.sampleFilePath = sampleFilePath; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public Integer getApproximateChapter() { return approximateChapter; }
    public void setApproximateChapter(Integer approximateChapter) { this.approximateChapter = approximateChapter; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Timestamp submittedAt) { this.submittedAt = submittedAt; }

    public Timestamp getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(Timestamp rejectedAt) { this.rejectedAt = rejectedAt; }

    public Long getAssignedEditorId() { return assignedEditorId; }
    public void setAssignedEditorId(Long assignedEditorId) { this.assignedEditorId = assignedEditorId; }

    public int getSubmitAttemptCount() { return submitAttemptCount; }
    public void setSubmitAttemptCount(int submitAttemptCount) { this.submitAttemptCount = submitAttemptCount; }

    public int getBoardApproveVotes() { return boardApproveVotes; }
    public void setBoardApproveVotes(int boardApproveVotes) { this.boardApproveVotes = boardApproveVotes; }

    public int getBoardReviseVotes() { return boardReviseVotes; }
    public void setBoardReviseVotes(int boardReviseVotes) { this.boardReviseVotes = boardReviseVotes; }

    public int getBoardRejectVotes() { return boardRejectVotes; }
    public void setBoardRejectVotes(int boardRejectVotes) { this.boardRejectVotes = boardRejectVotes; }

    public int getBoardTotalVotes() { return boardTotalVotes; }
    public void setBoardTotalVotes(int boardTotalVotes) { this.boardTotalVotes = boardTotalVotes; }

    public Long getBoardRoundId() { return boardRoundId; }
    public void setBoardRoundId(Long boardRoundId) { this.boardRoundId = boardRoundId; }

    public Integer getBoardRoundNumber() { return boardRoundNumber; }
    public void setBoardRoundNumber(Integer boardRoundNumber) { this.boardRoundNumber = boardRoundNumber; }

    public String getBoardRoundStatus() { return boardRoundStatus; }
    public void setBoardRoundStatus(String boardRoundStatus) { this.boardRoundStatus = boardRoundStatus; }

    public Timestamp getBoardVotingOpenedAt() { return boardVotingOpenedAt; }
    public void setBoardVotingOpenedAt(Timestamp boardVotingOpenedAt) { this.boardVotingOpenedAt = boardVotingOpenedAt; }

    public Timestamp getBoardVotingClosesAt() { return boardVotingClosesAt; }
    public void setBoardVotingClosesAt(Timestamp boardVotingClosesAt) { this.boardVotingClosesAt = boardVotingClosesAt; }

    public Timestamp getBoardVotingClosedAt() { return boardVotingClosedAt; }
    public void setBoardVotingClosedAt(Timestamp boardVotingClosedAt) { this.boardVotingClosedAt = boardVotingClosedAt; }

    public int getBoardEligibleVoterCount() { return boardEligibleVoterCount; }
    public void setBoardEligibleVoterCount(int boardEligibleVoterCount) { this.boardEligibleVoterCount = boardEligibleVoterCount; }
}
