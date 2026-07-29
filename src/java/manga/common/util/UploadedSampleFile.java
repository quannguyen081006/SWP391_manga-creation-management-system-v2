package manga.common.util;

/**
 * Result of storing a proposal sample file upload: where it was saved, the name the user
 * uploaded it under, and the SHA-256 of its content (used to block duplicate uploads).
 *
 * <p>All three values are {@code null} when the form was submitted without a new file
 * (allowed on edit, where the proposal keeps its current file).</p>
 */
public final class UploadedSampleFile {

    private static final UploadedSampleFile EMPTY = new UploadedSampleFile(null, null, null);

    private final String path;         // relative path stored in DB, e.g. /uploads/proposals/123_file.pdf
    private final String originalName; // original client file name, used when downloading
    private final String hash;         // SHA-256 of the content, used for duplicate detection

    public UploadedSampleFile(String path, String originalName, String hash) {
        this.path = path;
        this.originalName = originalName;
        this.hash = hash;
    }

    public static UploadedSampleFile empty() {
        return EMPTY;
    }

    public String getPath() {
        return path;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getHash() {
        return hash;
    }

    /** {@code true} when the user actually uploaded a file with this submission. */
    public boolean isPresent() {
        return path != null;
    }
}
