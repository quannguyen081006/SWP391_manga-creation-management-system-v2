package manga.service.chaptertask;

import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.chaptertask.ChapterSummary;
import manga.repository.chaptertask.ChapterRepository;
import manga.repository.chaptertask.PageTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.List;

/**
 * ============================================================
 * ChapterService - Nghiệp vụ quản lý Chapter
 * ============================================================
 *
 * MỤC LỤC:
 * ----------------------------------------------------------
 * [1] TRUY VẤN CHAPTER
 *     - listAll()              : Liệt kê chapter theo quyền người dùng
 *     - listBySeries()         : Liệt kê chapter theo series
 *     - getDetail()            : Lấy chi tiết một chapter
 * [2] TẠO & CẬP NHẬT CHAPTER (Mangaka)
 *     - create()               : Tạo chapter mới
 *     - update()               : Cập nhật title / submissionDeadline
 * [3] VÒNG ĐỜI CHAPTER
 *     - submitForReview()      : Nộp chapter để Tantou Editor review
 *     - delete()               : Xoá chapter
 * [4] NHẮC NHỞ DEADLINE (Scheduler)
 *     - remindApproachingDeadlines(): Nhắc 7 ngày, 3 ngày, và missed deadline
 * [5] HELPER
 *     - firstPresent()         : Lấy giá trị đầu tiên không null/rỗng
 * ============================================================
 */
@Service
public class ChapterService {

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private PageTaskRepository pageTaskRepository;

    // ============================================================
    // [1] TRUY VẤN CHAPTER
    // ============================================================

    /** Liệt kê chapter hiển thị với người dùng (phân quyền xử lý ở repository) */
    public List<ChapterSummary> listAll(AuthenticatedUser user) {
        return chapterRepository.listAll(user);
    }

    /** Liệt kê tất cả chapter thuộc một series */
    public List<ChapterSummary> listBySeries(long seriesId) {
        return chapterRepository.listBySeries(seriesId);
    }

    /** Lấy chi tiết chapter; ném exception nếu không tồn tại */
    public ChapterSummary getDetail(long chapterId) {
        ChapterSummary chapter = chapterRepository.findById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("Chapter not found");
        }
        return chapter;
    }

    // ============================================================
    // [2] TẠO & CẬP NHẬT CHAPTER (Mangaka)
    // ============================================================

    /**
     * Tạo chapter mới trong series.
     * Yêu cầu: người dùng phải là MANGAKA và là chủ sở hữu series.
     * totalPages phải ≥ 1.
     */
    public ChapterSummary create(long seriesId, AuthenticatedUser user, String title, String submissionDeadline, int totalPages) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can create chapter");

        long ownerId = chapterRepository.findSeriesOwnerMangaka(seriesId);
        if (ownerId != user.getId()) {
            throw new IllegalArgumentException("Only series owner can create chapter");
        }

        if (totalPages < 1) {
            throw new IllegalArgumentException("totalPages must be at least 1");
        }

        long id = chapterRepository.createNext(seriesId, title, Date.valueOf(submissionDeadline), totalPages);
        return getDetail(id);
    }

    /**
     * Cập nhật title và/hoặc submissionDeadline của chapter.
     * Hỗ trợ nhiều tên tham số deadline khác nhau từ các client (submissionDeadline,
     * publicationDate, deadline, chapterDeadline) — lấy giá trị đầu tiên có mặt.
     * Nếu không có deadline nào được truyền, chỉ cập nhật title.
     */
    public ChapterSummary update(
            long chapterId,
            AuthenticatedUser user,
            String title,
            String submissionDeadline,
            String publicationDate,
            String deadline,
            String chapterDeadline) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can update chapter");

        long ownerId = chapterRepository.findOwnerMangakaByChapter(chapterId);
        if (ownerId != user.getId()) {
            throw new IllegalArgumentException("Only series owner can update chapter");
        }

        ChapterSummary existing = getDetail(chapterId);
        // Giữ nguyên title cũ nếu không truyền title mới
        String nextTitle = (title == null || title.trim().isEmpty()) ? existing.getTitle() : title;

        String deadlineText = firstPresent(submissionDeadline, publicationDate, deadline, chapterDeadline);
        if (deadlineText == null) {
            // Không có deadline → chỉ cập nhật title
            chapterRepository.updateChapterTitle(chapterId, nextTitle);
            return getDetail(chapterId);
        }

        chapterRepository.updateChapterMetadata(chapterId, nextTitle, Date.valueOf(deadlineText));
        return getDetail(chapterId);
    }

    // ============================================================
    // [3] VÒNG ĐỜI CHAPTER
    // ============================================================

    /** Mangaka nộp chapter để Tantou Editor review */
    public void submitForReview(long chapterId, AuthenticatedUser user) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can submit chapter for review");
        chapterRepository.submitForReview(chapterId, user.getId());
    }

    /** Mangaka xoá chapter (chỉ được xoá chapter của chính mình) */
    public void delete(long chapterId, AuthenticatedUser user) {
        SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can delete chapter");
        chapterRepository.deleteChapter(chapterId, user.getId());
    }

    // ============================================================
    // [4] NHẮC NHỞ DEADLINE (Scheduler)
    // ============================================================

    /**
     * Gửi thông báo nhắc deadline chapter theo 3 mức, được gọi bởi ChapterDeadlineScheduler (09:00 hàng ngày):
     *
     *   - 7 ngày trước deadline → thông báo CHAPTER_DEADLINE_SOON cho Mangaka
     *   - 3 ngày trước deadline → thông báo CHAPTER_DEADLINE_URGENT cho Mangaka
     *   - Đã bỏ lỡ deadline    → thông báo CHAPTER_SUBMISSION_MISSED cho Tantou Editor
     *
     * Dùng createNotificationIfAbsentToday để tránh gửi trùng trong cùng một ngày.
     */
    public void remindApproachingDeadlines() {
        // Nhắc Mangaka: còn 7 ngày
        for (ChapterSummary ch : chapterRepository.findChaptersWithDeadlineInDays(7)) {
            long mangakaId = chapterRepository.findOwnerMangakaByChapter(ch.getId());
            pageTaskRepository.createNotificationIfAbsentToday(
                mangakaId,
                "CHAPTER_DEADLINE_SOON",
                "Chapter #" + ch.getId() + " (Ch." + ch.getChapterNumber() + " - " + ch.getTitle()
                    + ") submission deadline in 7 days (" + ch.getSubmissionDeadline() + "). "
                    + "Current progress: " + ch.getCompletionPct() + "%",
                ch.getId(),
                "CHAPTER"
            );
        }

        // Nhắc Mangaka: chỉ còn 3 ngày (urgent)
        for (ChapterSummary ch : chapterRepository.findChaptersWithDeadlineInDays(3)) {
            long mangakaId = chapterRepository.findOwnerMangakaByChapter(ch.getId());
            pageTaskRepository.createNotificationIfAbsentToday(
                mangakaId,
                "CHAPTER_DEADLINE_URGENT",
                "URGENT: Chapter #" + ch.getId() + " (Ch." + ch.getChapterNumber() + " - " + ch.getTitle()
                    + ") deadline in 3 days! Status: " + ch.getStatus()
                    + ", Progress: " + ch.getCompletionPct() + "%",
                ch.getId(),
                "CHAPTER"
            );
        }

        // Báo Tantou Editor: chapter đã bỏ lỡ submission deadline
        for (ChapterSummary ch : chapterRepository.findMissedSubmissionDeadlineChapters()) {
            long tantouId = chapterRepository.findSeriesTantou(ch.getSeriesId());
            pageTaskRepository.createNotificationIfAbsentToday(
                tantouId,
                "CHAPTER_SUBMISSION_MISSED",
                "Chapter #" + ch.getId() + " (Ch." + ch.getChapterNumber() + " - " + ch.getTitle()
                    + ") missed its submission deadline (" + ch.getSubmissionDeadline() + ").",
                ch.getId(),
                "CHAPTER"
            );
        }
    }

    // ============================================================
    // [5] HELPER
    // ============================================================

    /** Trả về giá trị đầu tiên không null và không rỗng trong danh sách, hoặc null nếu không có */
    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
