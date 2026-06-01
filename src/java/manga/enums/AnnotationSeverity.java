package manga.enums;

/**
 * Annotation severity levels for prioritizing editorial feedback.
 * Helps editors and mangaka understand the urgency of each annotation.
 */
public enum AnnotationSeverity {
    CRITICAL,      // Must fix before approval
    HIGH,          // Should fix before approval
    MEDIUM,        // Important but not blocking
    LOW,           // Nice to have
    SUGGESTION     // Optional improvement
}
