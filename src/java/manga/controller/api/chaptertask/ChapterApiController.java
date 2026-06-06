    package manga.controller.api.chaptertask;

    // Chapter/task API group: chapter CRUD endpoints are kept separate from general APIs for easier tracing.
    import manga.repository.chaptertask.ChapterRepository;
    import manga.common.ApiResponse;
    import manga.common.util.SessionUserUtil;
    import manga.model.AuthenticatedUser;
    import manga.model.chaptertask.ChapterSummary;
    import java.sql.Date;
    import java.util.List;
    import javax.servlet.http.HttpSession;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.web.bind.annotation.PathVariable;
    import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.RequestMethod;
    import org.springframework.web.bind.annotation.RequestParam;
    import org.springframework.web.bind.annotation.RestController;

    @RestController
    @RequestMapping("/api/v1")
    public class ChapterApiController {

        @Autowired
        private ChapterRepository chapterRepository;

            @RequestMapping(value = "/chapters", method = RequestMethod.GET)
            public ApiResponse<List<ChapterSummary>> listAll(HttpSession session) {
                AuthenticatedUser user = SessionUserUtil.requireUser(session);
                return ApiResponse.ok(chapterRepository.listAll(user), "Chapters");
            }
        @RequestMapping(value = "/series/{seriesId}/chapters", method = RequestMethod.GET)
        public ApiResponse<List<ChapterSummary>> list(@PathVariable("seriesId") long seriesId, HttpSession session) {
            SessionUserUtil.requireUser(session);
            return ApiResponse.ok(chapterRepository.listBySeries(seriesId), "Chapters");
        }

        @RequestMapping(value = "/series/{seriesId}/chapters", method = RequestMethod.POST)
        public ApiResponse<ChapterSummary> create(
                @PathVariable("seriesId") long seriesId,
                HttpSession session,
                @RequestParam("title") String title,
                @RequestParam("submissionDeadline") String submissionDeadline,
                @RequestParam(value = "totalPages", defaultValue = "0") int totalPages) {
            AuthenticatedUser user = SessionUserUtil.requireUser(session);
            SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can create chapter");

            long ownerId = chapterRepository.findSeriesOwnerMangaka(seriesId);
            if (ownerId != user.getId()) {
                throw new IllegalArgumentException("Only series owner can create chapter");
            }

            if (totalPages < 1) {
                throw new IllegalArgumentException("totalPages must be at least 1");
            }
            long id = chapterRepository.createNext(seriesId, title, Date.valueOf(submissionDeadline), totalPages);
            return ApiResponse.ok(chapterRepository.findById(id), "Chapter created");
        }

        @RequestMapping(value = "/chapters/{id}", method = RequestMethod.GET)
        public ApiResponse<ChapterSummary> detail(@PathVariable("id") long id, HttpSession session) {
            SessionUserUtil.requireUser(session);
            ChapterSummary chapter = chapterRepository.findById(id);
            if (chapter == null) {
                throw new IllegalArgumentException("Chapter not found");
            }
            return ApiResponse.ok(chapter, "Chapter detail");
        }

        @RequestMapping(value = "/chapters/{id}", method = RequestMethod.PUT)
        public ApiResponse<ChapterSummary> update(
                @PathVariable("id") long id,
                HttpSession session,
                @RequestParam(value = "title", required = false) String title,
                @RequestParam(value = "submissionDeadline", required = false) String submissionDeadline,
                @RequestParam(value = "publicationDate", required = false) String publicationDate,
                @RequestParam(value = "deadline", required = false) String deadline,
                @RequestParam(value = "chapterDeadline", required = false) String chapterDeadline) {
            AuthenticatedUser user = SessionUserUtil.requireUser(session);
            SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can update chapter");

            long ownerId = chapterRepository.findOwnerMangakaByChapter(id);
            if (ownerId != user.getId()) {
                throw new IllegalArgumentException("Only series owner can update chapter");
            }

            ChapterSummary existing = chapterRepository.findById(id);
            if (existing == null) {
                throw new IllegalArgumentException("Chapter not found");
            }
            String nextTitle = (title == null || title.trim().isEmpty()) ? existing.getTitle() : title;
            String deadlineText = (submissionDeadline == null || submissionDeadline.trim().isEmpty()) ? publicationDate : submissionDeadline;
            if (deadlineText == null || deadlineText.trim().isEmpty()) {
                deadlineText = deadline;
            }
            if (deadlineText == null || deadlineText.trim().isEmpty()) {
                deadlineText = chapterDeadline;
            }
            if (deadlineText == null || deadlineText.trim().isEmpty()) {
                chapterRepository.updateChapterTitle(id, nextTitle);
                return ApiResponse.ok(chapterRepository.findById(id), "Chapter updated");
            }

            chapterRepository.updateChapterMetadata(id, nextTitle, Date.valueOf(deadlineText));
            return ApiResponse.ok(chapterRepository.findById(id), "Chapter updated");
        }

        @RequestMapping(value = "/chapters/{id}/submit-review", method = RequestMethod.POST)
        public ApiResponse<Object> submitReview(@PathVariable("id") long id, HttpSession session) {
            AuthenticatedUser user = SessionUserUtil.requireUser(session);
            SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can submit chapter for review");
            chapterRepository.submitForReview(id, user.getId());
            return ApiResponse.ok(null, "Chapter submitted for editorial review");
        }

        @RequestMapping(value = "/chapters/{id}", method = RequestMethod.DELETE)
        public ApiResponse<Object> delete(@PathVariable("id") long id, HttpSession session) {
            AuthenticatedUser user = SessionUserUtil.requireUser(session);
            SessionUserUtil.requireRole(user, "MANGAKA", "Only MANGAKA can delete chapter");
            chapterRepository.deleteChapter(id, user.getId());
            return ApiResponse.ok(null, "Chapter deleted");
        }
    }

