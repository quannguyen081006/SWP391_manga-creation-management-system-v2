package manga.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import manga.common.exception.InvalidSampleFileTypeException;

/**
 * Stores the sample file uploaded on the proposal create / edit forms.
 *
 * <p>Shared by every controller that accepts a proposal upload so the same rules apply
 * everywhere: max size, allowed format ({@code .pdf} / {@code .docx} only, verified by both
 * extension and file signature), sanitized stored name, and the SHA-256 content hash used to
 * reject a file identical to one already stored on another proposal.</p>
 */
public final class ProposalSampleFileUploader {

    public static final long MAX_SIZE_BYTES = 20L * 1024L * 1024L;
    public static final String TYPE_ERROR_MESSAGE
            = "Sample file must be a PDF (.pdf) or Word (.docx) document. Please choose another file.";
    public static final String SIZE_ERROR_MESSAGE = "Sample file must not exceed 20 MB";

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "docx");
    private static final String UPLOAD_DIR = "/uploads/proposals";

    private ProposalSampleFileUploader() {
    }

    /**
     * Saves the uploaded part and returns its stored path, original name, and content hash.
     * Returns {@link UploadedSampleFile#empty()} when no file was submitted (edit keeps the old file).
     *
     * @throws InvalidSampleFileTypeException when the file is not a PDF or DOCX
     * @throws IllegalArgumentException       when the file is larger than {@link #MAX_SIZE_BYTES}
     */
    public static UploadedSampleFile save(HttpServletRequest request, String fieldName)
            throws IOException, ServletException {
        Part part = request.getPart(fieldName);
        if (part == null || part.getSize() == 0) {
            return UploadedSampleFile.empty();
        }
        if (part.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(SIZE_ERROR_MESSAGE);
        }
        String submittedName = part.getSubmittedFileName();
        // Only the file name, in case the browser sends a full client path.
        String originalName = submittedName == null ? "proposal-file" : new File(submittedName).getName();
        // Reject the wrong format before anything touches the disk.
        assertAllowedFileName(originalName);

        String safeName = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        String storedName = System.currentTimeMillis() + "_" + safeName;
        String uploadPath = request.getServletContext().getRealPath(UPLOAD_DIR);
        if (uploadPath == null) {
            throw new IOException("Upload directory is not available");
        }
        File dir = new File(uploadPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create upload directory");
        }
        File saved = new File(dir, storedName);
        part.write(saved.getAbsolutePath());
        // A .pdf/.docx name is not proof of content — check the file signature too and drop
        // the file again when it does not match, so renamed executables never get stored.
        if (!hasAllowedSignature(saved, extensionOf(originalName))) {
            saved.delete();
            throw new InvalidSampleFileTypeException(TYPE_ERROR_MESSAGE);
        }
        String hash = FileHashUtil.sha256Hex(saved);
        return new UploadedSampleFile(UPLOAD_DIR + "/" + storedName, originalName, hash);
    }

    /** Throws {@link InvalidSampleFileTypeException} unless the name ends with .pdf or .docx. */
    public static void assertAllowedFileName(String fileName) {
        if (!isAllowedFileName(fileName)) {
            throw new InvalidSampleFileTypeException(TYPE_ERROR_MESSAGE);
        }
    }

    public static boolean isAllowedFileName(String fileName) {
        return ALLOWED_EXTENSIONS.contains(extensionOf(fileName));
    }

    /** Removes a stored upload that was rejected afterwards (duplicate content, failed save). */
    public static void deleteQuietly(HttpServletRequest request, String relativePath) {
        if (request == null || relativePath == null || relativePath.trim().isEmpty()) {
            return;
        }
        try {
            ServletContext context = request.getServletContext();
            String realPath = context == null ? null : context.getRealPath(relativePath);
            if (realPath == null) {
                return;
            }
            File file = new File(realPath);
            if (file.isFile()) {
                file.delete();
            }
        } catch (RuntimeException ignored) {
            // Best effort only: a leftover file must never break the user-facing error page.
        }
    }

    /** Lowercase extension without the dot, or an empty string when there is none. */
    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        String name = fileName.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    /**
     * Checks the leading bytes: PDF files start with {@code %PDF-}, DOCX files are ZIP
     * containers so they start with a {@code PK} local-file-header signature.
     */
    private static boolean hasAllowedSignature(File file, String extension) {
        byte[] head = readHead(file, 5);
        if (head == null) {
            return false;
        }
        if ("pdf".equals(extension)) {
            return head.length >= 5
                    && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F' && head[4] == '-';
        }
        if ("docx".equals(extension)) {
            return head.length >= 4 && head[0] == 'P' && head[1] == 'K'
                    && ((head[2] == 3 && head[3] == 4) || (head[2] == 5 && head[3] == 6) || (head[2] == 7 && head[3] == 8));
        }
        return false;
    }

    private static byte[] readHead(File file, int length) {
        try ( InputStream in = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[length];
            int read = 0;
            while (read < length) {
                int n = in.read(buffer, read, length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return read == length ? buffer : Arrays.copyOf(buffer, read);
        } catch (IOException ex) {
            return null;
        }
    }
}
