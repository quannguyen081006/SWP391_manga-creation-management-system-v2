package manga.controller.api;

import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.dto.CreateRankingPeriodRequestDTO;
import manga.dto.SubmitVoteEntryRequest;
import manga.model.AuthenticatedUser;
import manga.service.RankingService;
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
@RequestMapping("/api/v1/ranking")
public class RankingApiController {

    @Autowired
    private RankingService rankingService;

    @RequestMapping(value = "/periods", method = RequestMethod.GET)
    public ApiResponse<List<Map<String, Object>>> listPeriods(HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(rankingService.listPeriods(), "Ranking periods");
    }

    @RequestMapping(value = "/periods", method = RequestMethod.POST)
    public ApiResponse<Map<String, Object>> createPeriod(
            HttpSession session,
            @RequestBody CreateRankingPeriodRequestDTO request) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "ADMIN", "Only ADMIN can create ranking period");
        long id = rankingService.createRankingPeriod(request, user);
        return ApiResponse.ok(rankingService.getPeriodById(id), "Ranking period created");
    }

    @RequestMapping(value = "/periods/{id}/close", method = RequestMethod.POST)
    public ApiResponse<Object> closePeriod(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "ADMIN", "Only ADMIN can close ranking period");
        rankingService.closeRankingPeriod(id, user);
        return ApiResponse.ok(null, "Ranking period closed");
    }

    @RequestMapping(value = "/periods/{id}/results", method = RequestMethod.GET)
    public ApiResponse<List<Map<String, Object>>> results(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(rankingService.getRankingResults(id, user), "Ranking results");
    }

    @RequestMapping(value = "/periods/{id}/calculate", method = RequestMethod.POST)
    public ApiResponse<Object> calculate(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "ADMIN", "Only ADMIN can trigger ranking calculation");
        rankingService.calculateRanking(id, user);
        return ApiResponse.ok(null, "Ranking calculation completed");
    }

    @RequestMapping(value = "/periods/{id}/entries", method = RequestMethod.POST)
    public ApiResponse<Object> submitEntry(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestBody SubmitVoteEntryRequest request) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        SessionUserUtil.requireRole(user, "EDITORIAL_BOARD", "Only EDITORIAL_BOARD can submit vote entries");
        rankingService.submitVoteEntry(id, request, user);
        return ApiResponse.ok(null, "Vote entry submitted");
    }

    @RequestMapping(value = "/periods/{id}/entries", method = RequestMethod.GET)
    public ApiResponse<List<Map<String, Object>>> listEntries(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        return ApiResponse.ok(rankingService.listVoteEntries(id, user), "Vote entries");
    }
}
