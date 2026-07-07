package manga.controller.web;

// ============================================================
// MỤC LỤC (TABLE OF CONTENTS)
// ============================================================
// [3] DANH SÁCH SERIES
// [4] DANH SÁCH CHAPTER          
// [5] DANH SÁCH TASK             
// [6] DANH SÁCH MANUSCRIPT      
// ============================================================

import manga.common.exception.ForbiddenException;
import manga.model.AuthenticatedUser;
import manga.model.ManuscriptSummary;
import manga.model.Proposal;
import manga.model.chaptertask.TaskSummary;
import manga.common.util.SessionUserUtil;
import manga.repository.ProductionRepository;
import manga.service.chaptertask.PageTaskService;
import manga.service.ProposalService;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

// ============================================================
// [1] KHAI BÁO CLASS & INJECT DEPENDENCIES
// ============================================================

@Controller
@RequestMapping("/main")
public class MainController {

    private static final long SAMPLE_FILE_MAX_SIZE_BYTES = 20L * 1024L * 1024L;

    @Autowired
    private AuthController authController;

    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProductionRepository productionRepository;

    @Autowired
    private PageTaskService pageTaskService;

    // ============================================================
    // [2] COMMON NAVIGATION
    // ============================================================

    /** GET /main → redirect to dashboard. */
    @RequestMapping(value = "", method = RequestMethod.GET)
    public String root() {
        return "redirect:/main/dashboard";
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String loginPage() {
        return authController.loginPage();
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String login(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletRequest request,
            Model model) {
        return authController.login(username, password, request, model);
    }

    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    public String logout(HttpServletRequest request) {
        return authController.logout(request);
    }

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    public String dashboard(HttpSession session, Model model) {
        return dashboardController.dashboard(session, model);
    }

    // ============================================================
    // [3] SERIES LIST
    // ============================================================

    /**
     * GET /main/series – Displays the list of series the user can see.
     */
    @RequestMapping(value = "/series", method = RequestMethod.GET)
    public String series(HttpSession session, Model model) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        model.addAttribute("seriesList", productionRepository.listSeries(user));
        return "series/list";
    }

    // ============================================================
    // [4] CHAPTER LIST
    // ============================================================

    @RequestMapping(value = "/chapters", method = RequestMethod.GET)
    public String chapters(HttpSession session, Model model) {
        // No server-side data loading here.
        // The frontend calls the REST API directly to fetch the chapter list by seriesId.
        return "chapter/list";
    }

    // ============================================================
    // [5] TASK LIST
    // ============================================================

    /**
     * GET /main/tasks – Displays the list of page drawing tasks (PageTask).
     */
    @RequestMapping(value = "/tasks", method = RequestMethod.GET)
    public String tasks(HttpSession session, Model model) {
        // Get the user from the session (may be null if not logged in – no guard here,
        // AuthInterceptor blocks before reaching the controller so this is fine for now).
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");

        // Step 1: Get all tasks (including chapter/series/assistant info).
        // Step 2: Filter by role – ASSISTANT only sees their own tasks.
        List<TaskSummary> tasks = pageTaskService.listForWebTaskPage(user, productionRepository.listTasks());

        // --- Compute stat counters to display on the header/badge ---
        int active = 0;
        int submitted = 0;
        int completed = 0;
        int overdue = 0;
        LocalDate now = LocalDate.now();

        for (TaskSummary task : tasks) {
            String st = task.getStatus() == null ? "" : task.getStatus().toUpperCase();

            // "Active" = currently in progress (not yet submitted, not yet done)
            if ("PENDING".equals(st) || "IN_PROGRESS".equals(st)) {
                active++;
            }

            // Task submitted, awaiting Mangaka review
            if ("SUBMITTED".equals(st)) {
                submitted++;
            }

            // Task approved by Mangaka (completed)
            if ("APPROVED".equals(st)) {
                completed++;
            }

            // Overdue: status is already OVERDUE OR dueDate has passed without being APPROVED.
            // Note: an overdue SUBMITTED task is also counted here.
            if ("OVERDUE".equals(st)
                    || (task.getDueDate() != null
                        && task.getDueDate().toLocalDate().isBefore(now)
                        && !"APPROVED".equals(st))) {
                overdue++;
            }
        }

        // Push data into the model for Thymeleaf to render
        model.addAttribute("tasks", tasks);
        model.addAttribute("activeTasks", active);
        model.addAttribute("submittedTasks", submitted);
        model.addAttribute("completedTasks", completed);
        model.addAttribute("overdueTasks", overdue);
        return "task/list";
    }

    // ============================================================
    // [6] MANUSCRIPT LIST (related to chapter & SLA deadline)
    // ============================================================

    @RequestMapping(value = "/manuscripts", method = RequestMethod.GET)
    public String manuscripts(
            HttpSession session,
            @RequestParam(value = "seriesId", required = false) Long seriesId,
            Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");

        List<ManuscriptSummary> manuscripts = productionRepository.listManuscripts(user, seriesId);

        // --- Compute SLA counters ---
        int pendingReview = 0;
        int urgent = 0;
        int breached = 0;

        for (ManuscriptSummary manuscript : manuscripts) {
            String st = manuscript.getStatus() == null ? "" : manuscript.getStatus().toUpperCase();

            // Awaiting review (no approve/reject decision yet)
            if ("SUBMITTED".equals(st) || "UNDER_REVIEW".equals(st)) {
                pendingReview++;
            }

            // Check SLA based on reviewDeadline (from the Manuscript table)
            if (manuscript.getReviewDeadline() != null) {
                long hoursLeft = (manuscript.getReviewDeadline().getTime() - System.currentTimeMillis())
                        / (1000L * 60L * 60L);
                if (hoursLeft < 0) {
                    breached++;          // Deadline already passed
                } else if (hoursLeft <= 12) {
                    urgent++;            // Less than 12h left – needs urgent review
                }
            }
        }

        model.addAttribute("manuscripts", manuscripts);
        model.addAttribute("pendingReview", pendingReview);
        model.addAttribute("urgentManuscripts", urgent);
        model.addAttribute("slaBreached", breached);
        model.addAttribute("currentUser", user);
        // Used to show/hide the "Create manuscript" button on the view
        model.addAttribute("isMangaka", user != null && user.hasRole("MANGAKA"));
        // Used for the series filter dropdown
        model.addAttribute("seriesList", productionRepository.listSeries(user));
        model.addAttribute("selectedSeriesId", seriesId);
        return "manuscript/list";
    }

    // ============================================================
    // [7] PROPOSAL – DANH SÁCH & TẠO MỚI
    // ============================================================

    @RequestMapping(value = "/proposals", method = RequestMethod.GET)
    public String proposals(HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        List<Proposal> proposals = proposalService.listForUser(user);
        model.addAttribute("proposals", proposals);
        model.addAttribute("user", user);
        model.addAttribute("isMangaka", user.hasRole("MANGAKA"));
        model.addAttribute("isTantou", user.hasRole("TANTOU_EDITOR"));
        model.addAttribute("isBoard", user.hasRole("EDITORIAL_BOARD"));
        model.addAttribute("isAdmin", user.hasRole("ADMIN"));
        model.addAttribute("maxSubmitAttempts", proposalService.getMaxSubmitAttempts());
        model.addAttribute("minimumVoteQuorum", proposalService.getMinimumVoteQuorum());
        return "proposal/list";
    }

    @RequestMapping(value = "/proposals/create", method = RequestMethod.GET)
    public String createProposalPage(HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (!user.hasRole("MANGAKA")) {
            return "redirect:/main/proposals";
        }
        model.addAttribute("genres", proposalService.listGenres());
        return "proposal/create";
    }

    @RequestMapping(value = "/proposals/create", method = RequestMethod.POST)
    public String createProposal(
            HttpSession session,
            HttpServletRequest request,
            @RequestParam("title") String title,
            @RequestParam("genre") String genre,
            @RequestParam("synopsis") String synopsis,
            @RequestParam("approximateChapter") Integer approximateChapter,
            Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        try {
            UploadInfo upload = saveUpload(request, "sampleFile");
            long id = proposalService.createProposal(user, title, genre, synopsis,
                    upload.path, upload.originalName, approximateChapter);
            return "redirect:/main/proposals/" + id;
        } catch (IllegalArgumentException ex) {
            // Validation từ service (thiếu field, genre không hợp lệ, v.v.)
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("title", title);
            model.addAttribute("genre", genre);
            model.addAttribute("synopsis", synopsis);
            model.addAttribute("approximateChapter", approximateChapter);
            model.addAttribute("genres", proposalService.listGenres());
            return "proposal/create";
        } catch (IOException ex) {
            model.addAttribute("error", "Cannot save uploaded file");
            model.addAttribute("genres", proposalService.listGenres());
            return "proposal/create";
        } catch (ServletException ex) {
            model.addAttribute("error", "Invalid uploaded file");
            model.addAttribute("genres", proposalService.listGenres());
            return "proposal/create";
        }
    }

    // ============================================================
    // [8] PROPOSAL – CHI TIẾT, VOTE, NỘP FILE, SUBMIT, REVIEW
    // ============================================================

    @RequestMapping(value = "/proposals/{id}", method = RequestMethod.GET)
    public String proposalDetail(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        Proposal proposal = proposalService.getDetail(user, id);
        model.addAttribute("proposal", proposal);
        model.addAttribute("history", proposalService.listHistory(user, id));
        model.addAttribute("user", user);

        boolean editableStatus = "DRAFT".equalsIgnoreCase(proposal.getStatus())
                || "REVISION_REQUESTED".equalsIgnoreCase(proposal.getStatus());
        boolean canEditDraft = user.hasRole("MANGAKA")
                && proposal.getMangakaId() == user.getId()
                && editableStatus
                && proposal.getSubmitAttemptCount() < proposalService.getMaxSubmitAttempts();
        model.addAttribute("canEdit", canEditDraft);
        model.addAttribute("canSubmit", canEditDraft);
        model.addAttribute("maxSubmitAttempts", proposalService.getMaxSubmitAttempts());
        model.addAttribute("minimumVoteQuorum", proposalService.getMinimumVoteQuorum());
        model.addAttribute("canReview", user.hasRole("TANTOU_EDITOR")
                && proposal.getAssignedEditorId() != null
                && proposal.getAssignedEditorId().longValue() == user.getId()
                && "UNDER_REVIEW".equalsIgnoreCase(proposal.getStatus()));
        model.addAttribute("isTantou", user.hasRole("TANTOU_EDITOR"));
        model.addAttribute("isBoard", user.hasRole("EDITORIAL_BOARD"));
        model.addAttribute("boardVoters", proposalService.listBoardRoundVoters(user, id));
        addBoardVoteAttributes(user, proposal, model);
        return "proposal/detail";
    }

    private void addBoardVoteAttributes(AuthenticatedUser user, Proposal proposal, Model model) {
        model.addAttribute("canBoardVote", proposalService.canCastBoardVote(user, proposal));
        model.addAttribute("boardVoteBlockMessage", proposalService.boardVoteBlockMessage(user, proposal));
        model.addAttribute("boardVoteUndo", proposalService.getBoardVoteUndoInfo(user, proposal.getId()));
    }

    @RequestMapping(value = "/proposals/{id}/vote", method = RequestMethod.GET)
    public String proposalVoteDeepLink(@PathVariable("id") long id, HttpSession session, Model model) {
        return proposalDetail(id, session, model);
    }

    @RequestMapping(value = "/proposals/{id}/file", method = RequestMethod.GET)
    public void proposalFile(@PathVariable("id") long id, HttpSession session,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        Proposal proposal = proposalService.getDetail(user, id);
        if (proposal.getSampleFilePath() == null || proposal.getSampleFilePath().trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String realPath = request.getServletContext().getRealPath(proposal.getSampleFilePath());
        if (realPath == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File file = new File(realPath);
        if (!file.exists() || !file.isFile()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String mime = request.getServletContext().getMimeType(file.getName());
        response.setContentType(mime == null ? "application/octet-stream" : mime);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + proposal.getOriginalFileName() + "\"");
        java.nio.file.Files.copy(file.toPath(), response.getOutputStream());
    }

    /** POST /main/proposals/{id}/submit – MANGAKA nộp proposal để Tantou review. */
    @RequestMapping(value = "/proposals/{id}/submit", method = RequestMethod.POST)
    public String submitProposal(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        try {
            proposalService.submitProposal(user, id);
            return "redirect:/main/proposals/" + id;
        } catch (IllegalArgumentException ex) {
            return proposalDetailWithError(id, session, model, ex.getMessage());
        }
    }

    /** POST /main/proposals/{id}/review – TANTOU_EDITOR quyết định APPROVE/REJECT/REVISE. */
    @RequestMapping(value = "/proposals/{id}/review", method = RequestMethod.POST)
    public String reviewProposal(
            @PathVariable("id") long id,
            @RequestParam("decision") String decision,
            @RequestParam(value = "note", required = false) String note,
            HttpSession session,
            Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        try {
            proposalService.reviewProposal(user, id, decision, note);
            return "redirect:/main/proposals/" + id;
        } catch (IllegalArgumentException ex) {
            return proposalDetailWithError(id, session, model, ex.getMessage());
        }
    }

    /** POST /main/proposals/{id}/board-vote – EDITORIAL_BOARD bỏ phiếu APPROVE/REVISE/REJECT. */
    @RequestMapping(value = "/proposals/{id}/board-vote", method = RequestMethod.POST)
    public String boardVoteProposal(
            @PathVariable("id") long id,
            @RequestParam("decision") String decision,
            @RequestParam(value = "note", required = false) String note,
            HttpSession session,
            Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        try {
            proposalService.voteProposalAsBoard(user, id, decision, note);
            return "redirect:/main/proposals/" + id;
        } catch (ForbiddenException ex) {
            // ForbiddenException: vi phạm quy tắc (tantou không được vote chính proposal mình quản)
            return proposalDetailWithError(id, session, model, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return proposalDetailWithError(id, session, model, ex.getMessage());
        }
    }

    /**
     * POST /main/proposals/{id}/board-vote/undo – Rút lại phiếu vote trong vòng 60 giây.
     * Xem {@link ProposalService#getBoardVoteUndoInfo} để hiểu logic cửa sổ undo.
     */
    @RequestMapping(value = "/proposals/{id}/board-vote/undo", method = RequestMethod.POST)
    public String undoBoardVote(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        try {
            proposalService.undoBoardVote(user, id);
            return "redirect:/main/proposals/" + id;
        } catch (ForbiddenException ex) {
            return proposalDetailWithError(id, session, model, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return proposalDetailWithError(id, session, model, ex.getMessage());
        }
    }

    // ============================================================
    // [9] HELPER METHODS
    // ============================================================

    private String proposalDetailWithError(long id, HttpSession session, Model model, String error) {
        proposalDetail(id, session, model);
        model.addAttribute("error", error);
        return "proposal/detail";
    }

    /**
     * Lưu file upload lên server và trả về thông tin đường dẫn.
     *
     * <p>Quy trình:
     * <ol>
     *   <li>Lấy {@link Part} từ multipart request theo {@code fieldName}.</li>
     *   <li>Lấy tên file gốc, sanitize (xoá ký tự đặc biệt) để tránh lỗi filesystem.</li>
     *   <li>Thêm timestamp vào đầu tên file để tránh trùng lặp.</li>
     *   <li>Lưu vào {@code /uploads/proposals/} dưới webroot.</li>
     * </ol>
     * </p>
     *
     * @param fieldName tên field file trong HTML form (vd: "sampleFile")
     * @return {@link UploadInfo} chứa đường dẫn relative và tên file gốc;
     *         hoặc {@code UploadInfo(null, null)} nếu không có file được upload.
     */
    private UploadInfo saveUpload(HttpServletRequest request, String fieldName) throws IOException, ServletException {
        Part part = request.getPart(fieldName);
        if (part == null || part.getSize() == 0) {
            return new UploadInfo(null, null);
        }
        if (part.getSize() > SAMPLE_FILE_MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Sample file must not exceed 20 MB");
        }
        String submittedName = part.getSubmittedFileName();
        // Lấy chỉ tên file (bỏ qua đường dẫn nếu browser gửi cả path)
        String originalName = submittedName == null ? "proposal-file" : new File(submittedName).getName();
        // Sanitize: chỉ giữ lại ký tự an toàn cho tên file
        String safeName = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        String storedName = System.currentTimeMillis() + "_" + safeName;
        String uploadPath = request.getServletContext().getRealPath("/uploads/proposals");
        if (uploadPath == null) {
            throw new IOException("Upload directory is not available");
        }
        File dir = new File(uploadPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create upload directory");
        }
        part.write(new File(dir, storedName).getAbsolutePath());
        // Trả về đường dẫn relative để lưu vào DB
        return new UploadInfo("/uploads/proposals/" + storedName, originalName);
    }

    private static class UploadInfo {
        private final String path;         // Đường dẫn relative trên server (lưu vào DB)
        private final String originalName; // Tên file gốc từ client (dùng khi download)

        private UploadInfo(String path, String originalName) {
            this.path = path;
            this.originalName = originalName;
        }
    }
}
