package manga.controller.api;

import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.dto.OpenDecisionSessionRequest;
import manga.dto.SubmitDecisionVoteRequest;
import manga.model.AuthenticatedUser;
import manga.service.DecisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionApiController {

    @Autowired
    private DecisionService decisionService;

    @RequestMapping(method = RequestMethod.GET)
    public ApiResponse<List<Map<String, Object>>> list(HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(decisionService.listDecisionSessions(user), "Decision sessions");
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public ApiResponse<Map<String, Object>> detail(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(decisionService.getDecisionSession(id, user), "Decision session detail");
    }

    @RequestMapping(method = RequestMethod.POST)
    public ApiResponse<Map<String, Object>> openSession(
            HttpSession session,
            @RequestBody OpenDecisionSessionRequest request) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "ADMIN", "Only ADMIN can open decision session");
        // Opening the session writes an AuditLog row through DecisionRepository.
        long sessionId = decisionService.openDecisionSession(request, user);
        return ApiResponse.ok(decisionService.getDecisionSession(sessionId, user), "Decision session opened");
    }

    @RequestMapping(value = "/{id}/votes", method = RequestMethod.POST)
    public ApiResponse<Object> vote(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestBody SubmitDecisionVoteRequest request) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        // Voting writes AuditLog rows for the vote and any quorum resolution.
        decisionService.submitDecisionVote(id, request, user);
        return ApiResponse.ok(null, "Decision vote submitted");
    }
    // Finalize endpoint removed - quorum-based finalization in resolveIfQuorum handles this automatically
}
