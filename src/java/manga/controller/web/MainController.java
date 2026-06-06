
package manga.controller.web;

import manga.common.exception.ForbiddenException;
import manga.model.AuthenticatedUser;
import manga.model.ManuscriptSummary;
import manga.model.Proposal;
import manga.model.chaptertask.TaskSummary;
import manga.common.util.SessionUserUtil;
import manga.repository.chaptertask.PageTaskRepository;
import manga.repository.ProductionRepository;
import manga.service.ProposalService;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
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

@Controller
@RequestMapping("/main")
public class MainController {

    @Autowired
    private AuthController authController;

    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProductionRepository productionRepository;

    @Autowired
    private PageTaskRepository pageTaskRepository;

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

    @RequestMapping(value = "/switch-role", method = RequestMethod.GET)
    public String switchRole(
            @RequestParam("username") String username,
            @RequestParam(value = "back", required = false) String back,
            HttpServletRequest request) {
        return authController.switchRole(username, back, request);
    }

    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    public String logout(HttpServletRequest request) {
        return authController.logout(request);
    }

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    public String dashboard(HttpSession session, Model model) {
        return dashboardController.dashboard(session, model);
    }

    @RequestMapping(value = "/series", method = RequestMethod.GET)
    public String series(HttpSession session, Model model) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        model.addAttribute("seriesList", productionRepository.listSeries(user));
        return "series/list";
    }

    @RequestMapping(value = "/chapters", method = RequestMethod.GET)
    public String chapters(HttpSession session, Model model) {
        return "chapter/list";
    }

    @RequestMapping(value = "/tasks", method = RequestMethod.GET)
    public String tasks(HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        pageTaskRepository.markDelayedTasks();
        pageTaskRepository.markOverdueTasks();
        List<TaskSummary> tasks = visibleTasks(user, productionRepository.listTasks());
        int active = 0;
        int submitted = 0;
        int completed = 0;
        int overdue = 0;
        LocalDate now = LocalDate.now();

        for (TaskSummary task : tasks) {
            String st = task.getStatus() == null ? "" : task.getStatus().toUpperCase();
            if ("PENDING".equals(st) || "IN_PROGRESS".equals(st)) {
                active++;
            }
            if ("SUBMITTED".equals(st)) {
                submitted++;
            }
            if ("APPROVED".equals(st)) {
                completed++;
            }
            if ("OVERDUE".equals(st) || (task.getDueDate() != null && task.getDueDate().toLocalDate().isBefore(now) && !"APPROVED".equals(st))) {
                overdue++;
            }
        }

        model.addAttribute("tasks", tasks);
        model.addAttribute("activeTasks", active);
        model.addAttribute("submittedTasks", submitted);
        model.addAttribute("completedTasks", completed);
        model.addAttribute("overdueTasks", overdue);
        return "task/list";
    }

    @RequestMapping(value = "/manuscripts", method = RequestMethod.GET)
    public String manuscripts(
            HttpSession session,
            @RequestParam(value = "seriesId", required = false) Long seriesId,
            Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        List<ManuscriptSummary> manuscripts = productionRepository.listManuscripts(user, seriesId);
        int pendingReview = 0;
        int urgent = 0;
        int breached = 0;

        for (ManuscriptSummary manuscript : manuscripts) {
            String st = manuscript.getStatus() == null ? "" : manuscript.getStatus().toUpperCase();
            if ("SUBMITTED".equals(st) || "UNDER_REVIEW".equals(st)) {
                pendingReview++;
            }
            if (manuscript.getReviewDeadline() != null) {
                long hoursLeft = (manuscript.getReviewDeadline().getTime() - System.currentTimeMillis()) / (1000L * 60L * 60L);
                if (hoursLeft < 0) {
                    breached++;
                } else if (hoursLeft <= 12) {
                    urgent++;
                }
            }
        }

        model.addAttribute("manuscripts", manuscripts);
        model.addAttribute("pendingReview", pendingReview);
        model.addAttribute("urgentManuscripts", urgent);
        model.addAttribute("slaBreached", breached);
        model.addAttribute("currentUser", user);
        model.addAttribute("isMangaka", user != null && user.hasRole("MANGAKA"));
        model.addAttribute("seriesList", productionRepository.listSeries(user));
        model.addAttribute("selectedSeriesId", seriesId);
        return "manuscript/list";
    }

    @RequestMapping(value = "/proposals", method = RequestMethod.GET)
    public String proposals(HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        List<Proposal> proposals = proposalService.listForUser(user);
        model.addAttribute("proposals", proposals);
        model.addAttribute("user", user);
        model.addAttribute("isMangaka", user.hasRole("MANGAKA"));
        model.addAttribute("isTantou", user.hasRole("TANTOU_EDITOR"));
        model.addAttribute("isBoard", user.hasRole("EDITORIAL_BOARD"));
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

    @RequestMapping(value = "/proposals/{id}", method = RequestMethod.GET)
    public String proposalDetail(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        Proposal proposal = proposalService.getDetail(user, id);
        model.addAttribute("proposal", proposal);
        model.addAttribute("history", proposalService.listHistory(user, id));
        model.addAttribute("user", user);
        boolean editableStatus = "DRAFT".equalsIgnoreCase(proposal.getStatus()) || "REVISION_REQUESTED".equalsIgnoreCase(proposal.getStatus());
        boolean canEditDraft = user.hasRole("MANGAKA") && proposal.getMangakaId() == user.getId()
                && editableStatus && proposal.getSubmitAttemptCount() < ProposalService.MAX_SUBMIT_ATTEMPTS;
        model.addAttribute("canEdit", canEditDraft);
        model.addAttribute("canSubmit", canEditDraft);
        model.addAttribute("canReview", user.hasRole("TANTOU_EDITOR") && proposal.getAssignedEditorId() != null
                && proposal.getAssignedEditorId().longValue() == user.getId() && "UNDER_REVIEW".equalsIgnoreCase(proposal.getStatus()));
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
            return proposalDetailWithError(id, session, model, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return proposalDetailWithError(id, session, model, ex.getMessage());
        }
    }

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

    private String proposalDetailWithError(long id, HttpSession session, Model model, String error) {
        proposalDetail(id, session, model);
        model.addAttribute("error", error);
        return "proposal/detail";
    }

    private UploadInfo saveUpload(HttpServletRequest request, String fieldName) throws IOException, ServletException {
        Part part = request.getPart(fieldName);
        if (part == null || part.getSize() == 0) {
            return new UploadInfo(null, null);
        }
        String submittedName = part.getSubmittedFileName();
        String originalName = submittedName == null ? "proposal-file" : new File(submittedName).getName();
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
        return new UploadInfo("/uploads/proposals/" + storedName, originalName);
    }

    private static class UploadInfo {
        private final String path;
        private final String originalName;

        private UploadInfo(String path, String originalName) {
            this.path = path;
            this.originalName = originalName;
        }
    }

    private List<TaskSummary> visibleTasks(AuthenticatedUser user, List<TaskSummary> allTasks) {
        if (user == null || !user.hasRole("ASSISTANT")) {
            return allTasks;
        }
        List<TaskSummary> assigned = new ArrayList<TaskSummary>();
        for (TaskSummary task : allTasks) {
            if (task.getAssistantId() == user.getId()) {
                assigned.add(task);
            }
        }
        return assigned;
    }
}

