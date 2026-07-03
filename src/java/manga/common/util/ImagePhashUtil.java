package manga.common.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Tính perceptual hash (average-hash 8x8, 64-bit) cho ảnh, dùng để chống nộp trùng ảnh.
 * Dùng thuần java.awt/javax.imageio (có sẵn trong JDK) - không cần thêm thư viện ngoài,
 * tránh phải sửa tay classpath Ant/NetBeans (project không có Maven).
 *
 * Không dùng MD5/SHA vì các hash đó đổi hoàn toàn khi ảnh bị re-export/resize/nén lại,
 * trong khi average-hash vẫn giữ nguyên với các biến đổi không đáng kể về nội dung.
 */
public final class ImagePhashUtil {

    private static final int HASH_SIZE = 8;

    private ImagePhashUtil() {
    }

    /** Tính pHash của 1 file ảnh, trả về chuỗi hex 16 ký tự (64-bit). */
    public static String hashOf(File imageFile) {
        BufferedImage original;
        try {
            original = ImageIO.read(imageFile);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Cannot read uploaded file as image", ex);
        }
        if (original == null) {
            throw new IllegalArgumentException("File is not a readable image");
        }

        BufferedImage small = new BufferedImage(HASH_SIZE, HASH_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = small.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, HASH_SIZE, HASH_SIZE, null);
        } finally {
            g.dispose();
        }

        int[] gray = new int[HASH_SIZE * HASH_SIZE];
        long sum = 0;
        for (int y = 0; y < HASH_SIZE; y++) {
            for (int x = 0; x < HASH_SIZE; x++) {
                int value = small.getRaster().getSample(x, y, 0);
                gray[y * HASH_SIZE + x] = value;
                sum += value;
            }
        }
        double average = sum / (double) gray.length;

        long hash = 0L;
        for (int i = 0; i < gray.length; i++) {
            hash <<= 1;
            if (gray[i] >= average) {
                hash |= 1L;
            }
        }
        return String.format("%016x", Long.valueOf(hash));
    }

    /** Khoảng cách Hamming giữa 2 hash hex - số bit khác nhau, càng nhỏ càng giống nhau. */
    public static int hammingDistance(String hexA, String hexB) {
        long a = Long.parseUnsignedLong(hexA, 16);
        long b = Long.parseUnsignedLong(hexB, 16);
        return Long.bitCount(a ^ b);
    }
}
