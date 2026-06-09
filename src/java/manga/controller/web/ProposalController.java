package manga.controller.web;

import manga.model.AuthenticatedUser;
import manga.model.Proposal;
import manga.service.ProposalService;
import java.io.File;
import java.io.IOException;
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
public class ProposalController {

    private static final long SAMPLE_FILE_MAX_SIZE_BYTES = 20L * 1024L * 1024L;

    @Autowired
    private ProposalService proposalService;

    @RequestMapping(value = "/proposals", method = RequestMethod.GET)
    public String list(HttpSession session, Model model) {
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
    public String createPage(HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (!user.hasRole("MANGAKA")) {
            return "redirect:/main/proposals";
        }
        model.addAttribute("genres", proposalService.listGenres());
        return "proposal/create";
    }

    @RequestMapping(value = "/proposals/create", method = RequestMethod.POST)
    public String create(
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
    public String detail(@PathVariable("id") long id, HttpSession session, Model model) {
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

        model.addAttribute("canBoardVote", proposalService.canCastBoardVote(user, proposal));
        model.addAttribute("boardVoteBlockMessage", proposalService.boardVoteBlockMessage(user, proposal));
        model.addAttribute("boardVoteUndo", proposalService.getBoardVoteUndoInfo(user, proposal.getId()));
        return "proposal/detail";
    }

    @RequestMapping(value = "/proposals/{id}/submit", method = RequestMethod.POST)
    public String submit(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        try {
            proposalService.submitProposal(user, id);
            return "redirect:/main/proposals/" + id;
        } catch (IllegalArgumentException ex) {
            return detailWithError(id, session, model, ex.getMessage());
        }
    }

    @RequestMapping(value = "/proposals/{id}/review", method = RequestMethod.POST)
    public String review(
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
            return detailWithError(id, session, model, ex.getMessage());
        }
    }

    @RequestMapping(value = "/proposals/{id}/board-vote", method = RequestMethod.POST)
    public String boardVote(
            @PathVariable("id") long id,
            @RequestParam("decision") String decision,
            @RequestParam(value = "note", required = false) String note,
            HttpSession session,
            Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        try {
            proposalService.voteProposalAsBoard(user, id, decision, note);
            return "redirect:/main/proposals/" + id;
        } catch (manga.common.exception.ForbiddenException ex) {
            return detailWithError(id, session, model, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return detailWithError(id, session, model, ex.getMessage());
        }
    }

    @RequestMapping(value = "/proposals/{id}/board-vote/undo", method = RequestMethod.POST)
    public String undoBoardVote(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        try {
            proposalService.undoBoardVote(user, id);
            return "redirect:/main/proposals/" + id;
        } catch (manga.common.exception.ForbiddenException ex) {
            return detailWithError(id, session, model, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return detailWithError(id, session, model, ex.getMessage());
        }
    }

    @RequestMapping(value = "/proposals/{id}/file", method = RequestMethod.GET)
    public void downloadFile(@PathVariable("id") long id, HttpSession session, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
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

    private String detailWithError(long id, HttpSession session, Model model, String error) {
        detail(id, session, model);
        model.addAttribute("error", error);
        return "proposal/detail";
    }

    private UploadInfo saveUpload(HttpServletRequest request, String fieldName) throws IOException, ServletException {
        Part part = request.getPart(fieldName);
        if (part == null || part.getSize() == 0) {
            return new UploadInfo(null, null);
        }
        if (part.getSize() > SAMPLE_FILE_MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Sample file must not exceed 20 MB");
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
}




