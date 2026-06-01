package manga.enums;

/**
 * Manuscript status enum for the new visual workspace workflow.
 * 
 * Workflow: DRAFT → IN_PROGRESS → SUBMITTED_FOR_REVIEW → UNDER_REVIEW → APPROVED/REJECTED
 * - DRAFT: Mangaka creating workspace, can add/reorder pages
 * - IN_PROGRESS: Mangaka actively editing workspace
 * - SUBMITTED_FOR_REVIEW: Submitted but not yet assigned to reviewer
 * - UNDER_REVIEW: Tantou reviewing, production locked (BR-2: only one per chapter)
 * - APPROVED: Ready for publish, immutable (BR-4)
 * - REJECTED: Requires revision, immutable (BR-3)
 * - ARCHIVED: Historical record
 * 
 * Active (editable) statuses: DRAFT, IN_PROGRESS
 * Review (read-only) statuses: SUBMITTED_FOR_REVIEW, UNDER_REVIEW
 * Immutable statuses: APPROVED, PUBLISHED, REJECTED, ARCHIVED
 */
public enum ManuscriptStatus {
    DRAFT,                   // Mangaka creating workspace
    IN_PROGRESS,             // Mangaka actively editing
    SUBMITTED_FOR_REVIEW,    // Submitted pending assignment
    UNDER_REVIEW,            // Tantou reviewing (BR-2: only one per chapter)
    APPROVED,                // Ready for publish (BR-4: immutable)
    PUBLISHED,               // Published and live
    REJECTED,                // Requires revision (BR-3: immutable)
    ARCHIVED                 // Historical record
    
    ;
    
    /**
     * Check if this status is editable (user can modify pages).
     */
    public boolean isEditable() {
        return this == DRAFT || this == IN_PROGRESS;
    }
    
    /**
     * Check if this status is in review workflow (read-only).
     */
    public boolean isInReview() {
        return this == SUBMITTED_FOR_REVIEW || this == UNDER_REVIEW;
    }
    
    /**
     * Check if this status is immutable (cannot be modified).
     */
    public boolean isImmutable() {
        return this == APPROVED || this == PUBLISHED || this == REJECTED || this == ARCHIVED;
    }
    
    /**
     * Check if this status is active (not final/immutable).
     */
    public boolean isActive() {
        return !isImmutable();
    }
}

