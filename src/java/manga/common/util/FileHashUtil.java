package manga.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes a content hash of an uploaded file so we can detect two proposals uploading
 * byte-for-byte identical sample files. SHA-256 gives a 64-char hex fingerprint that is
 * safe to store in a fixed-width column and compare with a simple equality query.
 */
public final class FileHashUtil {

    private FileHashUtil() {
    }

    /** Returns the lowercase hex SHA-256 of the file content, or {@code null} on failure. */
    public static String sha256Hex(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try ( InputStream in = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException ex) {
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
