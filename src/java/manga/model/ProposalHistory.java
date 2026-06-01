package manga.model;

import java.sql.Timestamp;

public class ProposalHistory {
    private long id;
    private long proposalId;
    private Long actorId;
    private String actorName;
    private String actorRole;
    private String actionType;
    private String note;
    private Timestamp createdAt;
    private int submitAttemptNumber;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getProposalId() { return proposalId; }
    public void setProposalId(long proposalId) { this.proposalId = proposalId; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public int getSubmitAttemptNumber() { return submitAttemptNumber; }
    public void setSubmitAttemptNumber(int submitAttemptNumber) { this.submitAttemptNumber = submitAttemptNumber; }
}
