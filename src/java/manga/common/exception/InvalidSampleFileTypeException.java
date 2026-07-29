package manga.common.exception;

/**
 * Thrown when the sample file uploaded for a proposal is not one of the accepted document
 * formats (only {@code .pdf} and {@code .docx} are allowed).
 *
 * <p>Extends {@link IllegalArgumentException} so the existing proposal controllers keep
 * catching it in their {@code catch (IllegalArgumentException)} branches, while still
 * letting those controllers detect the wrong-format case specifically (to render the
 * bottom-right "choose another file" toast, like the duplicate-file case).</p>
 */
public class InvalidSampleFileTypeException extends IllegalArgumentException {
    public InvalidSampleFileTypeException(String message) {
        super(message);
    }
}
