package manga.model;

import java.time.LocalDateTime;

/**
 * ManuscriptProductionLock entity - Locks production during manuscript review.
 * 
 * Enforces BR-9: Production pages/tasks should become locked from mutation
 * during manuscript review to prevent conflicts between production and editorial phases.
 * 
 * Only one lock can exist per chapter at a time.
 */
public class ManuscriptProductionLock {
    private Long id;
    private Long chapterId;
    private Long manuscriptVersionId;
    private LocalDateTime lockedAt;
    private Long lockedBy;
    private LocalDateTime unlockedAt;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getChapterId() {
        return chapterId;
    }
    
    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }
    
    public Long getManuscriptVersionId() {
        return manuscriptVersionId;
    }
    
    public void setManuscriptVersionId(Long manuscriptVersionId) {
        this.manuscriptVersionId = manuscriptVersionId;
    }
    
    public LocalDateTime getLockedAt() {
        return lockedAt;
    }
    
    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }
    
    public Long getLockedBy() {
        return lockedBy;
    }
    
    public void setLockedBy(Long lockedBy) {
        this.lockedBy = lockedBy;
    }
    
    public LocalDateTime getUnlockedAt() {
        return unlockedAt;
    }
    
    public void setUnlockedAt(LocalDateTime unlockedAt) {
        this.unlockedAt = unlockedAt;
    }
    
    /**
     * Check if the lock is currently active.
     */
    public boolean isActive() {
        return unlockedAt == null;
    }
    
    /**
     * Check if the lock has expired (e.g., after 48 hours).
     * This would be used by a cleanup job to release stale locks.
     */
    public boolean isExpired() {
        if (unlockedAt != null) {
            return false;
        }
        // Lock expires after 48 hours
        return lockedAt.plusHours(48).isBefore(LocalDateTime.now());
    }
}
