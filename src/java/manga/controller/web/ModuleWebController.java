package manga.controller.web;

import manga.model.AuthenticatedUser;
import manga.model.ChapterSummary;
import manga.model.Proposal;
import manga.model.SeriesSummary;
import manga.model.TaskSummary;
import manga.repository.ChapterRepository;
import manga.repository.DecisionRepository;
import manga.repository.PageTaskRepository;
import manga.repository.ProductionRepository;
import manga.repository.RankingRepository;
import manga.repository.UserAdminRepository;
import manga.service.AnnotationServiceV2;
import manga.service.ManuscriptVersionService;
import manga.service.NotificationService;
import manga.service.ProposalService;
import manga.service.RankingCsvImportService;
import manga.service.RankingService;
import manga.dto.SubmitVoteEntryRequest;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import static manga.common.util.SessionUserUtil.requireRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/main")
public class ModuleWebController {

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProductionRepository productionRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private PageTaskRepository pageTaskRepository;

    // @Autowired
    // private AnnotationService annotationService;
    @Autowired
    private AnnotationServiceV2 annotationServiceV2;

    @Autowired
    private ManuscriptVersionService manuscriptVersionService;

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private UserAdminRepository userAdminRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RankingCsvImportService rankingCsvImportService;

    @Autowired
    private RankingService rankingService;

    @RequestMapping(value = "/proposals/{id}/edit", method = RequestMethod.GET)
    public String proposalEditPage(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        Proposal proposal = proposalService.getDetail(user, id);
        boolean editableStatus = "DRAFT".equalsIgnoreCase(proposal.getStatus()) || "REVISION_REQUESTED".equalsIgnoreCase(proposal.getStatus());
        if (!user.hasRole("MANGAKA") || proposal.getMangakaId() != user.getId()
                || !editableStatus || proposal.getSubmitAttemptCount() >= ProposalService.MAX_SUBMIT_ATTEMPTS) {
            throw new IllegalArgumentException("Only editable proposal owner can edit proposal");
        }
        model.addAttribute("proposal", proposal);
        model.addAttribute("genres", proposalService.listGenres());
        return "proposal/edit";
    }

    @RequestMapping(value = "/proposals/{id}/edit", method = RequestMethod.POST)
    public String proposalEdit(
            @PathVariable("id") long id,
            HttpSession session,
            HttpServletRequest request,
            @RequestParam("title") String title,
            @RequestParam("genre") String genre,
            @RequestParam("synopsis") String synopsis,
            @RequestParam("approximateChapter") Integer approximateChapter,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            UploadInfo upload = saveUpload(request, "sampleFile");
            proposalService.updateDraft(user, id, title, genre, synopsis,
                    upload.path, upload.originalName, approximateChapter);
            return "redirect:/main/proposals/" + id;
        } catch (Exception ex) {
            Proposal proposal = proposalService.getDetail(user, id);
            model.addAttribute("proposal", proposal);
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("genres", proposalService.listGenres());
            return "proposal/edit";
        }
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

    @RequestMapping(value = "/series/{id}", method = RequestMethod.GET)
    public String seriesDetail(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        SeriesSummary found = null;
        for (SeriesSummary s : productionRepository.listSeries(user)) {
            if (s.getId() == id) {
                found = s;
                break;
            }
        }
        if (found == null) {
            throw new IllegalArgumentException("Series not found");
        }
        model.addAttribute("series", found);
        model.addAttribute("chapters", chapterRepository.listBySeries(id));
        return "series/detail";
    }

    @RequestMapping(value = "/chapters/detail", method = RequestMethod.GET)
    public String chapterDetailPage(HttpSession session) {
        requireUser(session);
        return "chapter/detail";
    }

    @RequestMapping(value = "/chapters/{id}", method = RequestMethod.GET)
    public String chapterDetail(@PathVariable("id") long id, HttpSession session) {
        requireUser(session);
        ChapterSummary chapter = chapterRepository.findById(id);
        if (chapter == null) {
            throw new IllegalArgumentException("Chapter not found");
        }
        return "redirect:/main/chapters/detail?id=" + id;
    }

    @RequestMapping(value = "/chapters/{id}/submit-review", method = RequestMethod.POST)
    public String chapterSubmitReview(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            chapterRepository.submitForReview(id, user.getId());
            return "redirect:/main/chapters/detail?id=" + id;
        } catch (RuntimeException ex) {
            try {
                return "redirect:/main/chapters/detail?id=" + id + "&error="
                        + java.net.URLEncoder.encode(ex.getMessage(), "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                return "redirect:/main/chapters/detail?id=" + id;
            }
        }
    }

    @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
    public String taskDetail(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        pageTaskRepository.markDelayedTasks();
        pageTaskRepository.markOverdueTasks();
        TaskSummary task = pageTaskRepository.findById(id);
        if (task == null) {
            throw new IllegalArgumentException("Task not found");
        }
        boolean isAssignedAssistant = user.hasRole("ASSISTANT") && user.getId() == task.getAssistantId();
        boolean canAssistantUpdate = isAssignedAssistant
                && ("IN_PROGRESS".equalsIgnoreCase(task.getStatus())
                || "REJECTED".equalsIgnoreCase(task.getStatus())
                || "OVERDUE".equalsIgnoreCase(task.getStatus()));
        boolean canAssistantSubmit = isAssignedAssistant
                && ("IN_PROGRESS".equalsIgnoreCase(task.getStatus())
                || "REJECTED".equalsIgnoreCase(task.getStatus())
                || "OVERDUE".equalsIgnoreCase(task.getStatus()));
        boolean isOwnerMangaka = user.hasRole("MANGAKA") && pageTaskRepository.getTaskOwnerMangaka(id) == user.getId();
        boolean canMangakaReview = isOwnerMangaka
                && "SUBMITTED".equalsIgnoreCase(task.getStatus());
        boolean canTantouView = user.hasRole("TANTOU_EDITOR") && pageTaskRepository.getTaskTantouEditor(id) == user.getId();
        if (!user.hasRole("ADMIN") && !isAssignedAssistant && !isOwnerMangaka && !canTantouView) {
            throw new IllegalArgumentException("You can only view tasks assigned to your role");
        }
        model.addAttribute("task", task);
        model.addAttribute("canAssistantUpdate", canAssistantUpdate);
        model.addAttribute("canAssistantSubmit", canAssistantSubmit);
        model.addAttribute("canMangakaTaskOwner", isOwnerMangaka);
        model.addAttribute("canMangakaReview", canMangakaReview);
        return "task/detail";
    }

    @RequestMapping(value = "/tasks/{id}/assistant-status", method = RequestMethod.POST)
    public String taskUpdateByAssistant(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("status") String status,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            pageTaskRepository.updateStatusByAssistant(id, user.getId(), status);
            return "redirect:/main/tasks/" + id;
        } catch (RuntimeException ex) {
            taskDetail(id, session, model);
            model.addAttribute("error", ex.getMessage());
            return "task/detail";
        }
    }

    @RequestMapping(value = "/tasks/{id}/approve", method = RequestMethod.POST)
    public String taskApprove(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            if (pageTaskRepository.getTaskOwnerMangaka(id) != user.getId()) {
                throw new IllegalArgumentException("Only owner can approve");
            }
            pageTaskRepository.approveByMangaka(id, user.getId(), null);
            return "redirect:/main/tasks/" + id;
        } catch (RuntimeException ex) {
            taskDetail(id, session, model);
            model.addAttribute("error", ex.getMessage());
            return "task/detail";
        }
    }

    @RequestMapping(value = "/tasks/{id}/reject", method = RequestMethod.POST)
    public String taskReject(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            if (pageTaskRepository.getTaskOwnerMangaka(id) != user.getId()) {
                throw new IllegalArgumentException("Only owner can reject");
            }
            pageTaskRepository.rejectByMangaka(id, user.getId(), "Rejected via web form");
            return "redirect:/main/tasks/" + id;
        } catch (RuntimeException ex) {
            taskDetail(id, session, model);
            model.addAttribute("error", ex.getMessage());
            return "task/detail";
        }
    }

    @RequestMapping(value = "/ranking/periods", method = RequestMethod.GET)
    public String rankingPeriods(HttpSession session, Model model) {
        requireUser(session);
        model.addAttribute("periods", rankingRepository.listPeriods());
        model.addAttribute("seriesList", productionRepository.listSeries());
        return "ranking/period";
    }

    @RequestMapping(value = "/ranking/periods/create", method = RequestMethod.POST)
    public String rankingCreate(
            HttpSession session,
            @RequestParam("name") String name,
            @RequestParam("endDate") String endDate,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            Date startDate = new Date(System.currentTimeMillis());
            rankingRepository.createPeriod(name, startDate, Date.valueOf(endDate));
            return "redirect:/main/ranking/periods";
        } catch (RuntimeException ex) {
            rankingPeriods(session, model);
            model.addAttribute("error", ex.getMessage());
            return "ranking/period";
        }
    }

    @RequestMapping(value = "/ranking/periods/{id}/upload", method = RequestMethod.POST)
    public String rankingUploadCsv(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("csvFile") org.springframework.web.multipart.MultipartFile csvFile,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            if (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD")) {
                throw new IllegalArgumentException("Only ADMIN or EDITORIAL_BOARD can upload ranking CSV");
            }
            int count = rankingCsvImportService.importCsv(id, csvFile, user);
            model.addAttribute("success", "CSV imported successfully. " + count + " ranking rows imported.");
            rankingPeriods(session, model);
            return "ranking/period";
        } catch (RuntimeException ex) {
            rankingPeriods(session, model);
            model.addAttribute("error", ex.getMessage());
            return "ranking/period";
        }
    }

    @RequestMapping(value = "/ranking/periods/{id}/close", method = RequestMethod.POST)
    public String rankingClose(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            rankingService.closeRankingPeriod(id, user);
            return "redirect:/main/ranking/periods/" + id + "/results";
        } catch (RuntimeException ex) {
            rankingPeriods(session, model);
            model.addAttribute("error", ex.getMessage());
            return "ranking/period";
        }
    }

    @RequestMapping(value = "/ranking/periods/{id}/entries", method = RequestMethod.POST)
    public String rankingSubmitEntry(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("seriesId") long seriesId,
            @RequestParam("voteCount") int voteCount,
            @RequestParam("readerCount") int readerCount,
            @RequestParam("revenue") java.math.BigDecimal revenue,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            if (!user.hasRole("EDITORIAL_BOARD")) {
                throw new IllegalArgumentException("Only EDITORIAL_BOARD can submit entries");
            }
            SubmitVoteEntryRequest req = new SubmitVoteEntryRequest();
            req.setSeriesId(seriesId);
            req.setVoteCount(voteCount);
            req.setReaderCount(readerCount);
            req.setRevenue(revenue);
            rankingService.submitVoteEntry(id, req, user);
            return "redirect:/main/ranking/periods";
        } catch (RuntimeException ex) {
            rankingPeriods(session, model);
            model.addAttribute("error", ex.getMessage());
            return "ranking/period";
        }
    }

    @RequestMapping(value = "/ranking/periods/{id}/results", method = RequestMethod.GET)
    public String rankingResults(@PathVariable("id") long id, HttpSession session, Model model) {
        requireUser(session);
        model.addAttribute("period", rankingRepository.findPeriodById(id));
        model.addAttribute("results", rankingRepository.results(id));
        model.addAttribute("entries", rankingRepository.listEntries(id));
        return "ranking/results";
    }

    @RequestMapping(value = "/ranking/periods/{id}/mangaka", method = RequestMethod.GET)
    public String rankingMangaka(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        model.addAttribute("period", rankingRepository.findPeriodById(id));
        model.addAttribute("mangakaRanking", rankingService.getMangakaRanking(id, user));
        return "ranking/mangaka-ranking";
    }

    @RequestMapping(value = "/decisions", method = RequestMethod.GET)
    public String decisionSessions(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        if (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD")) {
            throw new IllegalArgumentException("Only ADMIN/EDITORIAL_BOARD can view decision sessions");
        }
        model.addAttribute("sessions", decisionRepository.listSessions());
        return "decision/session";
    }

    @RequestMapping(value = "/decisions/{id}", method = RequestMethod.GET)
    public String decisionDetail(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        if (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD")) {
            throw new IllegalArgumentException("Only ADMIN/EDITORIAL_BOARD can view decision detail");
        }

        Map<String, Object> sessionDetail = decisionRepository.getSessionDetail(id);
        model.addAttribute("sessionDetail", sessionDetail);

        if (sessionDetail != null) {
            // Read revenue trend snapshot from DecisionSession (calculated during CLOSE PERIOD)
            // This eliminates runtime revenue aggregation on page load
            String revenueTrendSnapshot = (String) sessionDetail.get("revenueTrendSnapshot");
            if (revenueTrendSnapshot != null && !revenueTrendSnapshot.isEmpty()) {
                model.addAttribute("revenueHistory", revenueTrendSnapshot);
            } else {
                model.addAttribute("revenueHistory", "[]");
            }

            // Check if user has voted
            boolean hasVoted = false;
            List<Map<String, Object>> votes = (List<Map<String, Object>>) sessionDetail.get("votes");
            if (votes != null) {
                for (Map<String, Object> vote : votes) {
                    Long voterId = (Long) vote.get("voterId");
                    if (voterId != null && voterId == user.getId()) {
                        hasVoted = true;
                        break;
                    }
                }
            }
            model.addAttribute("hasVoted", hasVoted);
        }

        return "decision/session";
    }

    @RequestMapping(value = "/decisions/{id}/votes", method = RequestMethod.POST)
    public String decisionVote(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("decision") String decision,
            @RequestParam(value = "justification", required = false) String justification,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            if (!user.hasRole("EDITORIAL_BOARD")) {
                throw new IllegalArgumentException("Only EDITORIAL_BOARD can vote");
            }
            decisionRepository.castVote(id, user.getId(), decision, justification);
            return "redirect:/main/decisions/" + id;
        } catch (RuntimeException ex) {
            decisionDetail(id, session, model);
            model.addAttribute("error", ex.getMessage());
            return "decision/session";
        }
    }

    @RequestMapping(value = "/users", method = RequestMethod.GET)
    public String users(
            HttpSession session,
            @RequestParam(value = "created", required = false) Long created,
            @RequestParam(value = "username", required = false) String createdUsername,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        requireAdmin(user);
        model.addAttribute("users", userAdminRepository.listUsers());
        model.addAttribute("availableRoles", availableRoles());
        model.addAttribute("adminRoleLocked", userAdminRepository.hasAnyAdmin());
        if (created != null) {
            model.addAttribute("success", "User " + createdUsername + " created successfully");
            model.addAttribute("createdUserId", created);
        }
        return "user/list";
    }

    private String users(HttpSession session, Model model) {
        return users(session, null, null, model);
    }

    @RequestMapping(value = "/users/new", method = RequestMethod.GET)
    public String userNew(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        requireAdmin(user);
        model.addAttribute("editing", false);
        model.addAttribute("availableRoles", availableRoles());
        model.addAttribute("adminRoleLocked", userAdminRepository.hasAnyAdmin());
        model.addAttribute("selectedRolesCsv", "");
        return "user/form";
    }

    @RequestMapping(value = "/users/{id}/edit", method = RequestMethod.GET)
    public String userEdit(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        requireAdmin(user);
        Map<String, Object> row = userAdminRepository.getUser(id);
        if (row == null) {
            throw new IllegalArgumentException("User not found");
        }
        model.addAttribute("editing", true);
        model.addAttribute("editUser", row);
        model.addAttribute("availableRoles", availableRoles());
        model.addAttribute("adminRoleLocked", userAdminRepository.hasAnyAdmin());
        return "user/form";
    }

    @RequestMapping(value = "/users/create", method = RequestMethod.POST)
    public String userCreate(
            HttpSession session,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam(value = "roles", required = false) String[] roles,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            validateCreateUser(username, password, confirmPassword, fullName, email, roles);
            long id = userAdminRepository.createUser(username, password, fullName, email);
            for (String role : roles) {
                String normalizedRole = role.trim().toUpperCase();
                userAdminRepository.addRole(id, normalizedRole);
            }
            notificationService.notifyUser(id, "ACCOUNT_CREATED", "Your MangaFlow account has been created.", 0, null);
            return "redirect:/main/users?created=" + id + "&username=" + username.trim();
        } catch (RuntimeException ex) {
            model.addAttribute("editing", false);
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("formUsername", username);
            model.addAttribute("formFullName", fullName);
            model.addAttribute("formEmail", email);
            model.addAttribute("formPassword", password);
            model.addAttribute("selectedRoles", roles == null ? new String[0] : roles);
            model.addAttribute("selectedRolesCsv", rolesCsv(roles));
            model.addAttribute("availableRoles", availableRoles());
            model.addAttribute("adminRoleLocked", userAdminRepository.hasAnyAdmin());
            return "user/form";
        }
    }

    @RequestMapping(value = "/users/{id}/update", method = RequestMethod.POST)
    public String userUpdate(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            userAdminRepository.updateUser(id, fullName, email);
            return "redirect:/main/users";
        } catch (RuntimeException ex) {
            model.addAttribute("editing", true);
            model.addAttribute("editUser", userAdminRepository.getUser(id));
            model.addAttribute("availableRoles", availableRoles());
            model.addAttribute("adminRoleLocked", userAdminRepository.hasAnyAdmin());
            model.addAttribute("error", ex.getMessage());
            return "user/form";
        }
    }

    @RequestMapping(value = "/users/{id}/status", method = RequestMethod.POST)
    public String userStatus(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("status") String status,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            String normalizedStatus = status.trim().toUpperCase();
            userAdminRepository.updateStatus(id, normalizedStatus);
            notificationService.notifyUser(id, "ACCOUNT_STATUS_CHANGED", "Your account status changed to " + normalizedStatus + ".", 0, null);
            return "redirect:/main/users";
        } catch (RuntimeException ex) {
            users(session, model);
            model.addAttribute("error", ex.getMessage());
            return "user/list";
        }
    }

    @RequestMapping(value = "/users/{id}/roles", method = RequestMethod.POST)
    public String userRole(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "roles", required = false) String[] roles,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            List<String> requestedRoles = selectedRoles(role, roles);
            if (requestedRoles.isEmpty()) {
                throw new IllegalArgumentException("Select at least one role");
            }
            validateAssignableRoles(id, requestedRoles);
            List<String> currentRoles = userAdminRepository.listRoles(id);
            for (String normalizedRole : requestedRoles) {
                userAdminRepository.addRole(id, normalizedRole);
                if (!currentRoles.contains(normalizedRole)) {
                    notificationService.notifyUser(id, "ROLE_ASSIGNED", "Role " + normalizedRole + " was assigned to your account.", 0, null);
                }
            }
            return "redirect:/main/users";
        } catch (RuntimeException ex) {
            users(session, model);
            model.addAttribute("error", ex.getMessage());
            return "user/list";
        }
    }

    @RequestMapping(value = "/users/{id}/roles/remove", method = RequestMethod.POST)
    public String userRoleRemove(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("role") String role,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            String normalizedRole = role.trim().toUpperCase();
            List<String> currentRoles = userAdminRepository.listRoles(id);
            userAdminRepository.removeRole(id, normalizedRole);
            if (currentRoles.contains(normalizedRole)) {
                notificationService.notifyUser(id, "ROLE_REMOVED", "Role " + normalizedRole + " was removed from your account.", 0, null);
            }
            return "redirect:/main/users";
        } catch (RuntimeException ex) {
            users(session, model);
            model.addAttribute("error", ex.getMessage());
            return "user/list";
        }
    }

    private AuthenticatedUser requireUser(HttpSession session) {
        Object auth = session.getAttribute("AUTH_USER");
        if (auth == null || !(auth instanceof AuthenticatedUser)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return (AuthenticatedUser) auth;
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only ADMIN can perform this action");
        }
    }

    private List<String> availableRoles() {
        return Arrays.asList("MANGAKA", "ASSISTANT", "TANTOU_EDITOR", "EDITORIAL_BOARD");
    }

    private String rolesCsv(String[] roles) {
        if (roles == null || roles.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder("|");
        for (String role : roles) {
            if (!isBlank(role)) {
                builder.append(role.trim().toUpperCase()).append("|");
            }
        }
        return builder.toString();
    }

    private List<String> selectedRoles(String role, String[] roles) {
        List<String> selected = new ArrayList<String>();
        if (!isBlank(role)) {
            addSelectedRole(selected, role);
        }
        if (roles != null) {
            for (String item : roles) {
                addSelectedRole(selected, item);
            }
        }
        return selected;
    }

    private void addSelectedRole(List<String> selected, String role) {
        if (isBlank(role)) {
            return;
        }
        String normalizedRole = role.trim().toUpperCase();
        if (!selected.contains(normalizedRole)) {
            selected.add(normalizedRole);
        }
    }

    private void validateCreateUser(String username, String password, String confirmPassword, String fullName, String email, String[] roles) {
        if (isBlank(username) || isBlank(password) || isBlank(confirmPassword) || isBlank(fullName) || isBlank(email)) {
            throw new IllegalArgumentException("All user fields are required");
        }
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Password confirmation does not match");
        }
        if (password.length() < 5) {
            throw new IllegalArgumentException("Password must be at least 5 characters");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Email is invalid");
        }
        if (roles == null || roles.length == 0) {
            throw new IllegalArgumentException("Select at least one role");
        }
        if (containsRole(roles, "ADMIN") && userAdminRepository.hasAnyAdmin()) {
            throw new IllegalArgumentException("Only one ADMIN account is allowed");
        }
        List<String> selected = new ArrayList<String>();
        for (String role : roles) {
            addSelectedRole(selected, role);
        }
        manga.common.util.RoleCombinationValidator.validate(selected);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void validateAssignableRoles(long userId, List<String> roles) {
        if (roles.contains("ADMIN")
                && !userAdminRepository.hasRole(userId, "ADMIN")
                && userAdminRepository.hasAnyAdmin()) {
            throw new IllegalArgumentException("Only one ADMIN account is allowed");
        }
        List<String> merged = new ArrayList<String>(userAdminRepository.listRoles(userId));
        for (String role : roles) {
            addSelectedRole(merged, role);
        }
        manga.common.util.RoleCombinationValidator.validate(merged);
    }

    // ============================================================
    // Manuscript Version Workspace (V2) - Editorial Review Workspace
    // ============================================================
    /**
     * Create manuscript workspace for chapter. BR-1: Only chapters in
     * EDITORIAL_REVIEW can create manuscripts
     */
    @RequestMapping(value = "/chapters/{chapterId}/manuscript-workspace/create", method = RequestMethod.GET)
    public String manuscriptWorkspaceCreate(@PathVariable("chapterId") long chapterId, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        ChapterSummary chapter = chapterRepository.findById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("Chapter not found");
        }
        model.addAttribute("chapter", chapter);
        return "manuscript-version/create";
    }

    @RequestMapping(value = "/chapters/{chapterId}/manuscript-workspace/create", method = RequestMethod.POST)
    public String manuscriptWorkspaceCreatePost(@PathVariable("chapterId") long chapterId, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            manga.model.ManuscriptVersion version = manuscriptVersionService.createWorkspace(chapterId, user);
            return "redirect:/main/manuscript-workspace/" + version.getId();
        } catch (RuntimeException ex) {
            ChapterSummary chapter = chapterRepository.findById(chapterId);
            model.addAttribute("chapter", chapter);
            model.addAttribute("error", ex.getMessage());
            return "manuscript-version/create";
        }
    }

    /**
     * View manuscript workspace - page-centric review interface.
     */
    @RequestMapping(value = "/manuscript-workspace/{id}", method = RequestMethod.GET)
    public String manuscriptWorkspaceView(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        manga.model.ManuscriptVersion version = manuscriptVersionService.getVersion(id);
        if (version == null) {
            throw new IllegalArgumentException("Manuscript version not found");
        }

        ChapterSummary chapter = chapterRepository.findById(version.getChapterId());
        if (chapter == null) {
            throw new IllegalArgumentException("Chapter not found");
        }

        // Get pages for this version - ensure never null
        List<manga.model.ManuscriptPage> pages = manuscriptVersionService.getPages(id);
        if (pages == null) {
            pages = new java.util.ArrayList<>();
        }

        // Get annotations for this version - ensure never null
        List<manga.model.AnnotationSummary> annotations = annotationServiceV2.listAnnotations(id);
        if (annotations == null) {
            annotations = new java.util.ArrayList<>();
        }

        // Get review dashboard data - ensure never null
        manga.dto.ReviewDashboardDTO dashboard = manuscriptVersionService.getReviewDashboard(id);
        if (dashboard == null) {
            dashboard = new manga.dto.ReviewDashboardDTO();
        }

        // Get version history - ensure never null
        List<manga.model.ManuscriptVersion> versionHistory = manuscriptVersionService.listVersions(version.getChapterId());
        if (versionHistory == null) {
            versionHistory = new java.util.ArrayList<>();
        }

        // Permission checks
        long mangakaId = chapterRepository.findOwnerMangakaByChapter(version.getChapterId());
        long tantouId = chapterRepository.findSeriesTantou(chapter.getSeriesId());
        boolean isMangakaOwner = user.getId() == mangakaId;
        boolean isAssignedTantou = user.getId() == tantouId;
        boolean isAdmin = user.hasRole("ADMIN");

        // Format LocalDateTime fields for JSP compatibility (Tomcat/JSTL doesn't support LocalDateTime with fmt:formatDate)
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String createdAtFormatted = version.getCreatedAt() != null ? version.getCreatedAt().format(formatter) : "";
        String submittedAtFormatted = version.getSubmittedAt() != null ? version.getSubmittedAt().format(formatter) : "";

        // Format version history dates
        java.util.Map<Long, String> versionHistoryDates = new java.util.HashMap<>();
        for (manga.model.ManuscriptVersion v : versionHistory) {
            if (v.getCreatedAt() != null) {
                versionHistoryDates.put(v.getId(), v.getCreatedAt().format(formatter));
            }
        }

        model.addAttribute("version", version);
        model.addAttribute("chapter", chapter);
        model.addAttribute("pages", pages);
        model.addAttribute("annotations", annotations);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("versionHistory", versionHistory);
        model.addAttribute("versionHistoryDates", versionHistoryDates);
        model.addAttribute("createdAtFormatted", createdAtFormatted);
        model.addAttribute("submittedAtFormatted", submittedAtFormatted);
        model.addAttribute("currentUser", user);
        model.addAttribute("isMangakaOwner", isMangakaOwner);
        model.addAttribute("isAssignedTantou", isAssignedTantou);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("productionLocked", manuscriptVersionService.isProductionLocked(version.getChapterId()));

        // Runtime logging for diagnostics
        System.out.println("Workspace loaded: version=" + version.getId()
                + ", chapter=" + chapter.getId()
                + ", pages=" + pages.size()
                + ", annotations=" + annotations.size()
                + ", versionHistory=" + versionHistory.size());

        return "manuscript-version/workspace";
    }

    /**
     * Import chapter pages into manuscript workspace.
     */
    @RequestMapping(value = "/manuscript-workspace/{id}/import-pages", method = RequestMethod.POST)
    public String manuscriptWorkspaceImportPages(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            manga.model.ManuscriptVersion version = manuscriptVersionService.getVersion(id);
            manuscriptVersionService.importChapterPages(id, version.getChapterId(), user);
            return "redirect:/main/manuscript-workspace/" + id;
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            return manuscriptWorkspaceView(id, session, model);
        }
    }

    /**
     * Submit manuscript for review.
     */
    @RequestMapping(value = "/manuscript-workspace/{id}/submit", method = RequestMethod.POST)
    public String manuscriptWorkspaceSubmit(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            manuscriptVersionService.submitForReview(id, user);
            return "redirect:/main/manuscript-workspace/" + id;
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            return manuscriptWorkspaceView(id, session, model);
        }
    }

    /**
     * Approve manuscript.
     */
    @RequestMapping(value = "/manuscript-workspace/{id}/approve", method = RequestMethod.POST)
    public String manuscriptWorkspaceApprove(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            manuscriptVersionService.approve(id, user);
            return "redirect:/main/manuscript-workspace/" + id;
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            return manuscriptWorkspaceView(id, session, model);
        }
    }

    /**
     * Reject manuscript with feedback.
     */
    @RequestMapping(value = "/manuscript-workspace/{id}/reject", method = RequestMethod.POST)
    public String manuscriptWorkspaceReject(@PathVariable("id") long id, HttpSession session,
            @RequestParam("feedback") String feedback, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            manuscriptVersionService.reject(id, feedback, user);
            return "redirect:/main/manuscript-workspace/" + id;
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            return manuscriptWorkspaceView(id, session, model);
        }
    }

    /**
     * Publish manuscript.
     */
    @RequestMapping(value = "/manuscript-workspace/{id}/publish", method = RequestMethod.POST)
    public String manuscriptWorkspacePublish(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            manuscriptVersionService.publish(id, user);
            return "redirect:/main/manuscript-workspace/" + id;
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            return manuscriptWorkspaceView(id, session, model);
        }
    }

    /**
     * Create new version after rejection. BR-3: Previous REJECTED version
     * remains immutable
     */
    @RequestMapping(value = "/chapters/{chapterId}/manuscript-workspace/new-version", method = RequestMethod.POST)
    public String manuscriptWorkspaceNewVersion(@PathVariable("chapterId") long chapterId, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            manga.model.ManuscriptVersion version = manuscriptVersionService.createNewVersion(chapterId, user);
            return "redirect:/main/manuscript-workspace/" + version.getId();
        } catch (RuntimeException ex) {
            ChapterSummary chapter = chapterRepository.findById(chapterId);
            model.addAttribute("chapter", chapter);
            model.addAttribute("error", ex.getMessage());
            return "redirect:/main/chapters/" + chapterId;
        }
    }

    /**
     * View review dashboard.
     */
    @RequestMapping(value = "/manuscript-workspace/{id}/dashboard", method = RequestMethod.GET)
    public String manuscriptWorkspaceDashboard(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        manga.dto.ReviewDashboardDTO dashboard = manuscriptVersionService.getReviewDashboard(id);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("currentUser", user);
        return "manuscript-version/dashboard";
    }

    /**
     * View version history for chapter.
     */
    @RequestMapping(value = "/chapters/{chapterId}/manuscript-workspace/history", method = RequestMethod.GET)
    public String manuscriptWorkspaceHistory(@PathVariable("chapterId") long chapterId, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        List<manga.model.ManuscriptVersion> versions = manuscriptVersionService.listVersions(chapterId);
        if (versions == null) {
            versions = new java.util.ArrayList<>();
        }
        ChapterSummary chapter = chapterRepository.findById(chapterId);

        // Format LocalDateTime fields for JSP compatibility (Tomcat/JSTL doesn't support LocalDateTime with fmt:formatDate)
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        java.util.Map<String, String> versionDates = new java.util.HashMap<>();

        for (manga.model.ManuscriptVersion v : versions) {
            if (v.getCreatedAt() != null) {
                versionDates.put(v.getId() + "_created", v.getCreatedAt().format(formatter));
            }
            if (v.getSubmittedAt() != null) {
                versionDates.put(v.getId() + "_submitted", v.getSubmittedAt().format(formatter));
            }
            if (v.getApprovedAt() != null) {
                versionDates.put(v.getId() + "_approved", v.getApprovedAt().format(formatter));
            }
            if (v.getRejectedAt() != null) {
                versionDates.put(v.getId() + "_rejected", v.getRejectedAt().format(formatter));
            }
        }

        model.addAttribute("versions", versions);
        model.addAttribute("chapter", chapter);
        model.addAttribute("versionDates", versionDates);
        model.addAttribute("currentUser", user);
        return "manuscript-version/history";
    }

    /**
     * Compare two manuscript versions.
     */
    @RequestMapping(value = "/manuscript-workspace/compare", method = RequestMethod.GET)
    public String manuscriptWorkspaceCompare(
            @RequestParam("versionId1") long versionId1,
            @RequestParam("versionId2") long versionId2,
            HttpSession session,
            Model model) {

        AuthenticatedUser user = requireUser(session);

        manga.dto.VersionComparisonDTO comparison
                = manuscriptVersionService.compareVersions(versionId1, versionId2);

        manga.model.ManuscriptVersion version1
                = manuscriptVersionService.getVersion(versionId1);

        manga.model.ManuscriptVersion version2
                = manuscriptVersionService.getVersion(versionId2);

        java.time.format.DateTimeFormatter formatter
                = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        String v1CreatedAtFormatted = "";
        String v1SubmittedAtFormatted = "";
        String v2CreatedAtFormatted = "";
        String v2SubmittedAtFormatted = "";

        if (version1 != null) {
            v1CreatedAtFormatted = version1.getCreatedAt() != null
                    ? version1.getCreatedAt().format(formatter)
                    : "";

            v1SubmittedAtFormatted = version1.getSubmittedAt() != null
                    ? version1.getSubmittedAt().format(formatter)
                    : "";
        }

        if (version2 != null) {
            v2CreatedAtFormatted = version2.getCreatedAt() != null
                    ? version2.getCreatedAt().format(formatter)
                    : "";

            v2SubmittedAtFormatted = version2.getSubmittedAt() != null
                    ? version2.getSubmittedAt().format(formatter)
                    : "";
        }

        model.addAttribute("comparison", comparison);

        model.addAttribute("version1", version1);
        model.addAttribute("version2", version2);

        model.addAttribute("v1CreatedAtFormatted", v1CreatedAtFormatted);
        model.addAttribute("v1SubmittedAtFormatted", v1SubmittedAtFormatted);

        model.addAttribute("v2CreatedAtFormatted", v2CreatedAtFormatted);
        model.addAttribute("v2SubmittedAtFormatted", v2SubmittedAtFormatted);

        model.addAttribute("currentUser", user);

        return "manuscript-version/compare";
    }

    /**
     * Tantou Review Inbox - Show all manuscripts waiting for Tantou review.
     * Tantou only sees manuscripts from their assigned series.
     * Admin sees all under-review manuscripts.
     */
    @RequestMapping(value = "/manuscript-review", method = RequestMethod.GET)
    public String manuscriptReviewInbox(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        
        if (!user.hasRole("TANTOU_EDITOR") && !user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only TANTOU_EDITOR or ADMIN can access review inbox");
        }
        
        boolean isAdmin = user.hasRole("ADMIN");
        List<manga.model.ManuscriptVersion> underReviewVersions = 
            manuscriptVersionService.findUnderReviewForTantou(user.getId(), isAdmin);
        
        if (underReviewVersions == null) {
            underReviewVersions = new java.util.ArrayList<>();
        }
        
        // Load chapter and series information for each version
        java.util.Map<Long, ChapterSummary> chapterMap = new java.util.HashMap<>();
        java.util.Map<Long, String> mangakaNames = new java.util.HashMap<>();
        java.util.Map<Long, String> submittedAtMap = new java.util.HashMap<>();
        
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        for (manga.model.ManuscriptVersion version : underReviewVersions) {
            ChapterSummary chapter = chapterRepository.findById(version.getChapterId());
            if (chapter != null) {
                chapterMap.put(version.getId(), chapter);
                
                // Get mangaka name using UserAdminRepository
                long mangakaId = chapterRepository.findOwnerMangakaByChapter(version.getChapterId());
                String mangakaName = userAdminRepository.getFullNameById(mangakaId);
                if (mangakaName == null) {
                    mangakaName = "Unknown";
                }
                mangakaNames.put(version.getId(), mangakaName);
            }
            
            // Format submittedAt
            if (version.getSubmittedAt() != null) {
                submittedAtMap.put(version.getId(), version.getSubmittedAt().format(formatter));
            }
        }
        
        model.addAttribute("underReviewVersions", underReviewVersions);
        model.addAttribute("chapterMap", chapterMap);
        model.addAttribute("mangakaNames", mangakaNames);
        model.addAttribute("submittedAtMap", submittedAtMap);
        model.addAttribute("currentUser", user);
        model.addAttribute("isAdmin", isAdmin);
        
        return "manuscript-version/review-inbox";
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================
    private boolean containsRole(String[] roles, String roleName) {
        if (roles == null) {
            return false;
        }
        for (String role : roles) {
            if (roleName.equalsIgnoreCase(role == null ? "" : role.trim())) {
                return true;
            }
        }
        return false;
    }
}
