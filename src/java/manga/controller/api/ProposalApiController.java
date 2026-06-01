package manga.controller.api;

import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.Proposal;
import manga.service.ProposalService;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/proposals")
public class ProposalApiController {

    @Autowired
    private ProposalService proposalService;

    @RequestMapping(method = RequestMethod.GET)
    public ApiResponse<List<Proposal>> list(HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(proposalService.listForUser(user), "Proposal list");
    }

    @RequestMapping(method = RequestMethod.POST)
    public ApiResponse<Proposal> create(
            HttpSession session,
            @RequestParam("title") String title,
            @RequestParam("genre") String genre,
            @RequestParam("synopsis") String synopsis,
            @RequestParam("sampleFilePath") String sampleFilePath,
            @RequestParam("originalFileName") String originalFileName,
            @RequestParam("approximateChapter") Integer approximateChapter) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        long id = proposalService.createProposal(user, title, genre, synopsis, sampleFilePath, originalFileName, approximateChapter);
        return ApiResponse.ok(proposalService.getDetail(user, id), "Draft proposal created");
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public ApiResponse<Proposal> detail(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(proposalService.getDetail(user, id), "Proposal detail");
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    public ApiResponse<Proposal> updateDraft(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("title") String title,
            @RequestParam("genre") String genre,
            @RequestParam("synopsis") String synopsis,
            @RequestParam(value = "sampleFilePath", required = false) String sampleFilePath,
            @RequestParam(value = "originalFileName", required = false) String originalFileName,
            @RequestParam("approximateChapter") Integer approximateChapter) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        proposalService.updateDraft(user, id, title, genre, synopsis, sampleFilePath, originalFileName, approximateChapter);
        return ApiResponse.ok(proposalService.getDetail(user, id), "Draft proposal updated");
    }

    @RequestMapping(value = "/{id}/submit", method = RequestMethod.POST)
    public ApiResponse<Object> submit(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        proposalService.submitProposal(user, id);
        return ApiResponse.ok(null, "Proposal submitted for Tantou review");
    }

    @RequestMapping(value = "/{id}/review", method = RequestMethod.POST)
    public ApiResponse<Object> review(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("decision") String decision,
            @RequestParam(value = "note", required = false) String note) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        proposalService.reviewProposal(user, id, decision, note);
        return ApiResponse.ok(null, "Proposal reviewed");
    }

    @RequestMapping(value = "/{id}/board-vote", method = RequestMethod.POST)
    public ApiResponse<Object> boardVote(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("decision") String decision,
            @RequestParam(value = "note", required = false) String note) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        proposalService.voteProposalAsBoard(user, id, decision, note);
        return ApiResponse.ok(proposalService.getBoardVoteUndoInfo(user, id), "Proposal board vote submitted");
    }

    @RequestMapping(value = "/{id}/board-vote/undo", method = RequestMethod.PATCH)
    public ApiResponse<Object> undoBoardVote(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        proposalService.undoBoardVote(user, id);
        return ApiResponse.ok(null, "Board vote undone");
    }

    @RequestMapping(value = "/{id}/my-board-vote", method = RequestMethod.GET)
    public ApiResponse<Object> myBoardVote(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(proposalService.getBoardVoteUndoInfo(user, id), "Current board vote undo window");
    }
}


