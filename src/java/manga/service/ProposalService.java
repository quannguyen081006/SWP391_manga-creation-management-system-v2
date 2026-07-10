package manga.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import manga.common.exception.ForbiddenException;
import manga.model.AuthenticatedUser;
import manga.model.BoardVoteUndoInfo;
import manga.model.Proposal;
import manga.model.ProposalHistory;
import manga.repository.ProposalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProposalService {

    private static final List<String> GENRES = Arrays.asList(
            "Action", "Adventure", "Romance", "Comedy", "Fantasy",
            "Horror", "Slice of Life", "Mystery", "Drama");

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private ProposalSettingsService proposalSettingsService;

    public List<String> listGenres() {
        return GENRES;
    }

    public List<Proposal> listForUser(AuthenticatedUser user) {
        if (user.hasRole("MANGAKA")) {
            return proposalRepository.findForMangaka(user.getId());
        }
        if (user.hasRole("EDITORIAL_BOARD") || user.hasRole("ADMIN")) {
            return proposalRepository.findForBoardAndEditor();
        }
        if (user.hasRole("TANTOU_EDITOR")) {
            return proposalRepository.findForAssignedEditor(user.getId());
        }
        throw new IllegalArgumentException("You do not have permission to view proposals");
    }

    public Proposal getDetail(AuthenticatedUser user, long proposalId) {
        Proposal p = proposalRepository.findById(proposalId);
        if (p == null) {
            throw new IllegalArgumentException("Proposal not found");
        }
        if (user.hasRole("MANGAKA")) {
            if (p.getMangakaId() != user.getId()) {
                throw new IllegalArgumentException("You do not have access to this proposal");
            }
            return p;
        }
        if (user.hasRole("EDITORIAL_BOARD") || user.hasRole("ADMIN")) {
            if ("DRAFT".equalsIgnoreCase(p.getStatus())) {
                throw new IllegalArgumentException("You do not have access to this proposal");
            }
            return p;
        }
        if (user.hasRole("TANTOU_EDITOR")) {
            if (p.getAssignedEditorId() == null || p.getAssignedEditorId().longValue() != user.getId()) {
                throw new IllegalArgumentException("Only assigned Tantou Editor can view this proposal");
            }
            return p;
        }
        throw new IllegalArgumentException("You do not have access to this proposal");
    }

    public long createProposal(AuthenticatedUser user, String title, String genre, String synopsis,
            String sampleFilePath, String originalFileName, Integer approximateChapter) {
        requireRole(user, "MANGAKA", "Only MANGAKA can create proposals");
        validateProposalContent(title, genre, synopsis, sampleFilePath, approximateChapter, true);
        if (proposalRepository.hasActiveDraft(user.getId(), 0L)) {
            throw new IllegalArgumentException("You already have an active draft proposal");
        }
        return proposalRepository.createDraft(user, title.trim(), genre.trim(), synopsis.trim(),
                sampleFilePath, safeTrim(originalFileName), approximateChapter.intValue());
    }

    public void updateDraft(AuthenticatedUser user, long proposalId, String title, String genre, String synopsis,
            String sampleFilePath, String originalFileName, Integer approximateChapter) {
        requireRole(user, "MANGAKA", "Only MANGAKA can update proposals");
        Proposal p = proposalRepository.findById(proposalId);
        if (p == null) {
            throw new IllegalArgumentException("Proposal not found");
        }
        boolean hasExistingFile = !isBlank(p.getSampleFilePath());
        if ("DRAFT".equalsIgnoreCase(p.getStatus()) && p.getSubmitAttemptCount() == 0) {
            title = p.getTitle();
            genre = p.getGenre();
        }
        validateProposalContent(title, genre, synopsis, hasExistingFile ? "existing" : sampleFilePath, approximateChapter, true);
        proposalRepository.updateDraft(user, proposalId, title.trim(), genre.trim(), synopsis.trim(),
                sampleFilePath, safeTrim(originalFileName), approximateChapter.intValue());
    }

    public void submitProposal(AuthenticatedUser user, long proposalId) {
        requireRole(user, "MANGAKA", "Only MANGAKA can submit proposals");
        Proposal p = proposalRepository.findById(proposalId);
        if (p == null) {
            throw new IllegalArgumentException("Proposal not found");
        }
        if (proposalRepository.hasActiveProposal(user.getId(), proposalId)) {
            throw new IllegalArgumentException("You already have an active proposal");
        }
        if (p.getSubmitAttemptCount() >= getMaxSubmitAttempts()) {
            throw new IllegalArgumentException("Proposal submit attempt limit reached");
        }
        if (isBlank(p.getSampleFilePath()) || p.getApproximateChapter() == null) {
            throw new IllegalArgumentException("Proposal file and approximate chapter are required before submit");
        }
        // Repository appends ProposalHistory for the submission and auto-assignment.
        proposalRepository.submitForTantouReview(user, proposalId);
    }

    public void reviewProposal(AuthenticatedUser user, long proposalId, String decision, String note) {
        requireRole(user, "TANTOU_EDITOR", "Only TANTOU_EDITOR can review proposals");
        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        if (!"APPROVE".equals(normalized) && !"REJECT".equals(normalized) && !"REVISE".equals(normalized)) {
            throw new IllegalArgumentException("Review decision must be APPROVE, REJECT, or REVISE");
        }
        if (("REJECT".equals(normalized) || "REVISE".equals(normalized)) && isBlank(note)) {
            throw new IllegalArgumentException("A note is required for REJECT and REVISE decisions");
        }
        // Repository appends the Tantou decision to ProposalHistory.
        proposalRepository.reviewByTantou(user, proposalId, normalized, safeTrim(note));
    }

    public void voteProposalAsBoard(AuthenticatedUser user, long proposalId, String decision, String note) {
        requireRole(user, "EDITORIAL_BOARD", "Only EDITORIAL_BOARD can vote on proposals");
        Proposal proposal = proposalRepository.findById(proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException("Proposal not found");
        }
        assertCanCastBoardVote(user, proposal);
        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        if (!"APPROVE".equals(normalized) && !"REVISE".equals(normalized) && !"REJECT".equals(normalized)) {
            throw new IllegalArgumentException("Board decision must be APPROVE, REVISE, or REJECT");
        }
        if (("REVISE".equals(normalized) || "REJECT".equals(normalized)) && isBlank(note)) {
            throw new IllegalArgumentException("A note is required when requesting revisions or rejecting");
        }
        // Repository records the board vote as a ProposalHistory row.
        proposalRepository.voteByEditorialBoard(user, proposalId, normalized, safeTrim(note));
    }

    public void undoBoardVote(AuthenticatedUser user, long proposalId) {
        requireRole(user, "EDITORIAL_BOARD", "Only EDITORIAL_BOARD can undo board votes");
        // Undo is the narrow exception to append-only history for board votes.
        proposalRepository.undoBoardVote(user, proposalId);
    }

    public boolean canCastBoardVote(AuthenticatedUser user, Proposal proposal) {
        if (user == null || proposal == null || !user.hasRole("EDITORIAL_BOARD")) {
            return false;
        }
        if (!"BOARD_REVIEW".equalsIgnoreCase(proposal.getStatus())) {
            return false;
        }
        if (proposal.getBoardRoundId() == null) {
            return false;
        }
        if (isManagingTantouForProposal(user, proposal)) {
            return false;
        }
        return !proposalRepository.hasBoardVote(proposal.getId(), user.getId());
    }

    public String boardVoteBlockMessage(AuthenticatedUser user, Proposal proposal) {
        if (user == null || proposal == null || !user.hasRole("EDITORIAL_BOARD")) {
            return null;
        }
        if (isManagingTantouForProposal(user, proposal)) {
            return "Tantou Editor cannot vote on a Proposal they manage.";
        }
        return null;
    }

    public BoardVoteUndoInfo getBoardVoteUndoInfo(AuthenticatedUser user, long proposalId) {
        if (user == null || !user.hasRole("EDITORIAL_BOARD")) {
            return null;
        }
        Proposal proposal = proposalRepository.findById(proposalId);
        if (proposal == null || proposal.getBoardRoundId() == null || !"BOARD_REVIEW".equalsIgnoreCase(proposal.getStatus())) {
            return null;
        }
        // Reads the latest ProposalHistory vote row to calculate the undo window.
        ProposalHistory vote = proposalRepository.findLatestBoardVote(proposalId, user.getId());
        if (vote == null || vote.getCreatedAt() == null) {
            return null;
        }
        long elapsedMs = System.currentTimeMillis() - vote.getCreatedAt().getTime();
        int remaining = (int) Math.max(0L, (60_000L - elapsedMs + 999L) / 1000L);
        if (remaining <= 0) {
            return null;
        }
        BoardVoteUndoInfo info = new BoardVoteUndoInfo();
        info.setHistoryId(vote.getId());
        info.setDecision(vote.getActionType());
        info.setRemainingSeconds(remaining);
        return info;
    }

    public List<Map<String, Object>> listBoardRoundVoters(AuthenticatedUser user, long proposalId) {
        getDetail(user, proposalId);
        return proposalRepository.listBoardRoundVoters(proposalId);
    }

    private void assertCanCastBoardVote(AuthenticatedUser user, Proposal proposal) {
        String blockMessage = boardVoteBlockMessage(user, proposal);
        if (blockMessage != null) {
            throw new ForbiddenException(blockMessage);
        }
        if (!"BOARD_REVIEW".equalsIgnoreCase(proposal.getStatus())) {
            throw new IllegalArgumentException("Proposal is not waiting for Editorial Board review");
        }
        if (proposalRepository.hasBoardVote(proposal.getId(), user.getId())) {
            throw new IllegalArgumentException("You have already voted on this proposal");
        }
    }

    private boolean isManagingTantouForProposal(AuthenticatedUser user, Proposal proposal) {
        return proposal.getAssignedEditorId() != null
                && proposal.getAssignedEditorId().longValue() == user.getId();
    }

    public List<ProposalHistory> listHistory(AuthenticatedUser user, long proposalId) {
        getDetail(user, proposalId);
        if (!user.hasRole("ADMIN") && !user.hasRole("MANGAKA") && !user.hasRole("TANTOU_EDITOR") && !user.hasRole("EDITORIAL_BOARD")) {
            throw new IllegalArgumentException("You do not have permission to view proposal history");
        }
        // History is displayed from append-only ProposalHistory rows.
        return proposalRepository.listHistory(proposalId);
    }

    public int getMaxSubmitAttempts() {
        return proposalSettingsService.getMaxSubmitAttempts();
    }

    public int getMinimumVoteQuorum() {
        return proposalSettingsService.getMinimumVoteQuorum();
    }

    private void validateProposalContent(String title, String genre, String synopsis, String sampleFilePath,
            Integer approximateChapter, boolean requireFile) {
        if (isBlank(title) || isBlank(genre) || isBlank(synopsis)) {
            throw new IllegalArgumentException("Title, genre, and synopsis are required");
        }
        if (!GENRES.contains(genre.trim())) {
            throw new IllegalArgumentException("Please select a valid genre");
        }
        if (requireFile && isBlank(sampleFilePath)) {
            throw new IllegalArgumentException("Proposal file upload is required");
        }
        if (approximateChapter == null || approximateChapter.intValue() < 1) {
            throw new IllegalArgumentException("Approximate chapter must be at least 1");
        }
    }

    private void requireRole(AuthenticatedUser user, String role, String message) {
        if (user == null || !user.hasRole(role)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }
}
