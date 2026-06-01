package manga.dto;

/**
 * DTO for manuscript approval/rejection requests.
 */
public class ManuscriptApprovalRequestDTO {
    private String feedback;

    // Getters and Setters
    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
