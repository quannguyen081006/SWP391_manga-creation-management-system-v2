package manga.common.exception;

/**
 * Thrown when the sample file a Mangaka uploads for a proposal has the exact same
 * content (identical SHA-256 hash) as a sample file already stored for another proposal.
 *
 * <p>Extends {@link IllegalArgumentException} so the existing proposal controllers keep
 * catching it in their {@code catch (IllegalArgumentException)} branches, while still
 * letting those controllers detect the duplicate-file case specifically (to render the
 * bottom-right "choose another file" toast).</p>
 */
public class DuplicateSampleFileException extends IllegalArgumentException {
    public DuplicateSampleFileException(String message) {
        super(message);
    }
}
