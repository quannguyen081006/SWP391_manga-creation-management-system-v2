package manga.service;

import manga.common.exception.BusinessRuleException;
import manga.model.AuthenticatedUser;
import manga.model.ReviewTask;
import manga.repository.ChapterRepository;
import manga.repository.ManuscriptVersionRepository;
import manga.repository.ReviewTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for ReviewTask entity.
 * 
 * Implements SLA tracking and review task management.
 * Business Rules:
 * - BR-51: ReviewTask must contain versionId, reviewerId, assignedAt, dueAt, reviewStatus
 * - BR-52: SLA: 48h review deadline, 36h warning threshold, overdue state
 */
@Service
@Transactional
public class ReviewTaskService {

    @Autowired
    private ReviewTaskRepository reviewTaskRepository;
    
    @Autowired
    private ManuscriptVersionRepository manuscriptVersionRepository;
    
    @Autowired
    private ChapterRepository chapterRepository;
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * Create review task when manuscript is submitted for review.
     * BR-52: 48h review deadline
     * 
     * @throws BusinessRuleException with domain-specific error codes:
     * - REVIEW_TASK_TABLE_MISSING: ReviewTask table doesn't exist (run migration)
     * - REVIEW_TASK_REVIEWER_NOT_FOUND: No tantou editor assigned
     * - REVIEW_TASK_FK_VIOLATION_VERSION: ManuscriptVersion doesn't exist
     * - REVIEW_TASK_FK_VIOLATION_REVIEWER: Reviewer user doesn't exist
     */
    public ReviewTask createReviewTask(Long manuscriptVersionId, AuthenticatedUser user) {
        // Validate manuscript version exists
        manga.model.ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("REVIEW_TASK_VERSION_NOT_FOUND: Manuscript version with id " + manuscriptVersionId + " does not exist");
        }
        
        // Get tantou editor for the chapter
        Long tantouId = chapterRepository.getChapterTantou(version.getChapterId());
        if (tantouId == null) {
            throw new BusinessRuleException("REVIEW_TASK_REVIEWER_NOT_FOUND: No tantou editor assigned to chapter " + version.getChapterId() + ". Assign a tantou editor before submitting for review.");
        }
        
        // Create review task
        ReviewTask task = new ReviewTask();
        task.setVersionId(manuscriptVersionId);
        task.setReviewerId(tantouId);
        task.setAssignedAt(LocalDateTime.now());
        task.setDueAt(LocalDateTime.now().plusHours(48)); // BR-52: 48h deadline
        task.setReviewStatus("ASSIGNED");
        
        try {
            long taskId = reviewTaskRepository.create(task);
            task.setId(taskId);
        } catch (RuntimeException ex) {
            // Convert repository exceptions to BusinessRuleException with domain codes
            String message = ex.getMessage();
            if (message != null) {
                if (message.startsWith("REVIEW_TASK_")) {
                    throw new BusinessRuleException(message);
                }
            }
            throw new BusinessRuleException("REVIEW_TASK_CREATION_FAILED: " + ex.getMessage());
        }
        
        // Notify reviewer
        try {
            notificationService.notifyUser(
                tantouId,
                "REVIEW_ASSIGNED",
                "Manuscript review assigned. Due in 48 hours.",
                manuscriptVersionId,
                "MANUSCRIPT"
            );
        } catch (Exception ex) {
            // Notification failure should not fail the entire operation
            System.err.println("Warning: Failed to send review assignment notification to user " + tantouId + ": " + ex.getMessage());
        }
        
        return task;
    }
    
    /**
     * Update review task status.
     */
    public void updateStatus(Long taskId, String status, AuthenticatedUser user) {
        ReviewTask task = reviewTaskRepository.findByVersionId(taskId);
        if (task == null) {
            throw new BusinessRuleException("Review task not found");
        }
        
        reviewTaskRepository.updateStatus(taskId, status);
    }
    
    /**
     * Check for overdue review tasks and send notifications.
     * BR-52: Overdue state after 48h
     */
    public void checkOverdueTasks() {
        List<ReviewTask> overdueTasks = reviewTaskRepository.findOverdueTasks();
        for (ReviewTask task : overdueTasks) {
            // Update status to OVERDUE
            reviewTaskRepository.updateStatus(task.getId(), "OVERDUE");
            
            // Send notification to reviewer
            notificationService.notifyUser(
                task.getReviewerId(),
                "REVIEW_OVERDUE",
                "Manuscript review is overdue. Please complete immediately.",
                task.getVersionId(),
                "MANUSCRIPT"
            );
            
            // Note: Admin notification would be implemented separately
        }
    }
    
    /**
     * Check for warning threshold (36h before due).
     * BR-52: 36h warning threshold
     */
    public void checkWarningThreshold() {
        List<ReviewTask> warningTasks = reviewTaskRepository.findWarningThresholdTasks();
        for (ReviewTask task : warningTasks) {
            // Avoid duplicate reminders: only notify once per (user, type, reference)
            String notifType = "REVIEW_WARNING";
            boolean already = notificationService.existsNotification(task.getReviewerId(), notifType, task.getVersionId());
            if (already) {
                continue;
            }
            // Send warning notification to reviewer
            notificationService.notifyUser(
                task.getReviewerId(),
                notifType,
                "Manuscript review due in 12 hours. Please complete soon.",
                task.getVersionId(),
                "MANUSCRIPT"
            );
        }
    }
    
    /**
     * Get review task by version ID.
     */
    public ReviewTask getReviewTask(Long manuscriptVersionId) {
        return reviewTaskRepository.findByVersionId(manuscriptVersionId);
    }
    
    /**
     * Get review tasks for a reviewer.
     */
    public List<ReviewTask> getReviewerTasks(Long reviewerId) {
        return reviewTaskRepository.findByReviewerId(reviewerId);
    }
    
    /**
     * Complete review task when manuscript is approved or rejected.
     */
    public void completeReviewTask(Long manuscriptVersionId, AuthenticatedUser user) {
        ReviewTask task = reviewTaskRepository.findByVersionId(manuscriptVersionId);
        if (task != null) {
            reviewTaskRepository.updateStatus(task.getId(), "COMPLETED");
        }
    }
    
    /**
     * Delete review task (for cleanup or rollback).
     */
    public void deleteReviewTask(Long manuscriptVersionId) {
        reviewTaskRepository.deleteByVersionId(manuscriptVersionId);
    }
}
