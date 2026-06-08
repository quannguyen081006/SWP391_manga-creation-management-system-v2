package manga.scheduler.chaptertask;

/**
 * Chapter/task scheduler group.
 * Tách biệt khỏi các scheduler ranking/proposal để dễ quản lý và tránh xung đột job.
 */
import manga.service.chaptertask.ChapterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ============================================================
 * ChapterDeadlineScheduler - Nhắc nhở deadline chapter
 * ============================================================
 *
 * MỤC LỤC:
 * ----------------------------------------------------------
 * [1] remindApproachingDeadlines - Nhắc các chapter sắp đến hạn (09:00 hàng ngày)
 * ============================================================
 */
@Component
public class ChapterDeadlineScheduler {

    @Autowired
    private ChapterService chapterService;

    /**
     * [1] Nhắc nhở các chapter có deadline sắp đến.
     * Chạy hàng ngày lúc 09:00.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void remindApproachingDeadlines() {
        chapterService.remindApproachingDeadlines();
    }
}
