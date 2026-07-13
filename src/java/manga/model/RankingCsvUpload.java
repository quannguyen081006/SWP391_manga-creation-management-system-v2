package manga.model;

import java.sql.Timestamp;

public class RankingCsvUpload {
    private Long id;
    private Long periodId;
    private Long boardMemberId;
    private String csvFileName;
    private String csvContent;
    private Timestamp uploadedAt;

    public RankingCsvUpload() {
    }

    public RankingCsvUpload(Long periodId, Long boardMemberId, String csvFileName, String csvContent) {
        this.periodId = periodId;
        this.boardMemberId = boardMemberId;
        this.csvFileName = csvFileName;
        this.csvContent = csvContent;
        this.uploadedAt = new Timestamp(System.currentTimeMillis());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPeriodId() {
        return periodId;
    }

    public void setPeriodId(Long periodId) {
        this.periodId = periodId;
    }

    public Long getBoardMemberId() {
        return boardMemberId;
    }

    public void setBoardMemberId(Long boardMemberId) {
        this.boardMemberId = boardMemberId;
    }

    public String getCsvFileName() {
        return csvFileName;
    }

    public void setCsvFileName(String csvFileName) {
        this.csvFileName = csvFileName;
    }

    public String getCsvContent() {
        return csvContent;
    }

    public void setCsvContent(String csvContent) {
        this.csvContent = csvContent;
    }

    public Timestamp getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Timestamp uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
