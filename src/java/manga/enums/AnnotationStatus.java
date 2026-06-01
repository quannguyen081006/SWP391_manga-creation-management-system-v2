package manga.enums;

/**
 * Annotation status for tracking resolution state.
 * Supports thread-based resolution workflow.
 */
public enum AnnotationStatus {
    OPEN,          // Newly created, not yet addressed
    IN_PROGRESS,   // Being worked on by mangaka
    RESOLVED,      // Fixed and approved by editor
    DISMISSED      // Marked as not applicable by editor
}
