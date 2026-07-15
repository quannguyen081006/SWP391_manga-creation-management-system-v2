package manga.controller.web;

import manga.model.AuthenticatedUser;
import manga.model.RankingCsvUpload;
import manga.model.chaptertask.ChapterSummary;
import manga.model.Proposal;
import manga.model.SeriesSummary;
import manga.repository.ProductionRepository;
import manga.repository.UserAdminRepository;
import manga.service.AnnotationServiceV2;
import manga.service.ManuscriptVersionService;
import manga.service.NotificationService;
import manga.service.ProposalSettingsService;
import manga.service.salary.SalarySettingsService;
import manga.service.salary.TaskTypeRateService;
import manga.service.chaptertask.ChapterService;
import manga.service.chaptertask.PageTaskService;
import manga.service.ProposalService;
import manga.service.RankingCsvImportService;
import manga.service.RankingService;
import manga.dto.SubmitVoteEntryRequest;
import java.io.File;
import java.io.IOException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import manga.dto.CreateRankingPeriodRequestDTO;
import manga.dto.SubmitDecisionVoteRequest;
import manga.enums.ManuscriptStatus;
import manga.service.DecisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/main")
public class ModuleWebController {

    private static final long SAMPLE_FILE_MAX_SIZE_BYTES = 20L * 1024L * 1024L;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProductionRepository productionRepository;

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private PageTaskService pageTaskService;

    @Autowired
    private AnnotationServiceV2 annotationServiceV2;

    @Autowired
    private ManuscriptVersionService manuscriptVersionService;

    @Autowired
    private DecisionService decisionService;

    @Autowired
    private UserAdminRepository userAdminRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RankingCsvImportService rankingCsvImportService;

    @Autowired
    private RankingService rankingService;

    @Autowired
    private manga.service.ReviewTaskService reviewTaskService;

    @Autowired
    private ProposalSettingsService proposalSettingsService;

    @Autowired
    private SalarySettingsService salarySettingsService;

    @Autowired
    private TaskTypeRateService taskTypeRateService;

    @RequestMapping(value = "/proposals/{id}/edit", method = RequestMethod.GET)
    public String proposalEditPage(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        Proposal proposal = proposalService.getDetail(user, id);
        boolean editableStatus = "DRAFT".equalsIgnoreCase(proposal.getStatus()) || "REVISION_REQUESTED".equalsIgnoreCase(proposal.getStatus());
        if (!user.hasRole("MANGAKA") || proposal.getMangakaId() != user.getId()
                || !editableStatus || proposal.getSubmitAttemptCount() >= proposalService.getMaxSubmitAttempts()) {
            throw new IllegalArgumentException("Only editable proposal owner can edit proposal");
        }
        model.addAttribute("proposal", proposal);
        model.addAttribute("genres", proposalService.listGenres());
        model.addAttribute("lockIdentityFields", "DRAFT".equalsIgnoreCase(proposal.getStatus()) && proposal.getSubmitAttemptCount() == 0);
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
            model.addAttribute("lockIdentityFields", "DRAFT".equalsIgnoreCase(proposal.getStatus()) && proposal.getSubmitAttemptCount() == 0);
            return "proposal/edit";
        }
    }

    //Mo trang setting
    @RequestMapping(value = "/settings", method = RequestMethod.GET)
    public String settingsHub(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        requireAdmin(user);
        return "settings/hub";
    }

    //Mo trang setting cua salary
    @RequestMapping(value = "/settings/salary", method = RequestMethod.GET)
    public String salarySettings(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        requireAdmin(user);
        model.addAttribute("settings", salarySettingsService.getSettings());
        model.addAttribute("taskTypes", taskTypeRateService.listAll(user));
        return "settings/salary/index";
    }

    //Save all trong setting cua salary: KPI/bonus + every task type rate in one submit.
    //Rate inputs are named "rate_<code>" (task types are dynamic, not a fixed set of fields).
    @RequestMapping(value = "/settings/salary", method = RequestMethod.POST)
    public String salarySettingsSave(
            HttpSession session,
            @RequestParam("kpiBonusThreshold") Integer kpiBonusThreshold,
            @RequestParam("bonusPercent") java.math.BigDecimal bonusPercent,
            @RequestParam Map<String, String> allParams,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            Map<String, java.math.BigDecimal> rates = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("rate_")) {
                    rates.put(entry.getKey().substring("rate_".length()),
                            new java.math.BigDecimal(entry.getValue()));
                }
            }
            taskTypeRateService.updateRates(rates, user);
            salarySettingsService.updateSettings(kpiBonusThreshold.intValue(), bonusPercent);
            model.addAttribute("success", "Salary settings updated successfully");
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        model.addAttribute("settings", salarySettingsService.getSettings());
        model.addAttribute("taskTypes", taskTypeRateService.listAll(user));
        return "settings/salary/index";
    }

    @RequestMapping(value = "/settings/proposals", method = RequestMethod.GET)
    public String proposalSettings(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        requireAdmin(user);
        model.addAttribute("settings", proposalSettingsService.getSettings());
        return "settings/proposals";
    }

    @RequestMapping(value = "/settings/proposals", method = RequestMethod.POST)
    public String proposalSettingsSave(
            HttpSession session,
            @RequestParam("maxSubmitAttempts") Integer maxSubmitAttempts,
            @RequestParam("minimumVoteQuorum") Integer minimumVoteQuorum,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            RedirectAttributes redirectAttributes,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            proposalSettingsService.updateSettings(maxSubmitAttempts.intValue(), minimumVoteQuorum.intValue());
            if ("proposals".equalsIgnoreCase(returnTo)) {
                redirectAttributes.addFlashAttribute("success", "Proposal settings updated successfully");
                return "redirect:/main/proposals";
            }
            model.addAttribute("success", "Proposal settings updated successfully");
        } catch (RuntimeException ex) {
            if ("proposals".equalsIgnoreCase(returnTo)) {
                redirectAttributes.addFlashAttribute("error", ex.getMessage());
                return "redirect:/main/proposals";
            }
            model.addAttribute("error", ex.getMessage());
        }
        model.addAttribute("settings", proposalSettingsService.getSettings());
        return "settings/proposals";
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

    // ============================================================
    // 1. SERIES DETAIL
    // GET /main/series/{id}
    // - Lay danh sach series ma user co quyen xem qua productionRepository.listSeries(user)
    // - Tim series theo id trong danh sach do — KHONG query thang DB,
    //   neu user khong co quyen thi tra 404 (throw IllegalArgumentException)
    // - Load them danh sach chapter cua series qua chapterService.listBySeries(id)
    // - Tra ve view: series/detail
    // ============================================================
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
        model.addAttribute("chapters", chapterService.listBySeries(id));
        return "series/detail";
    }

    // ============================================================
    // 2. CHAPTER DETAIL PAGE (view trong)
    // GET /main/chapters/detail
    // - Chi check login, khong load data — JSP tu load qua query param ?id=
    // ============================================================
    @RequestMapping(value = "/chapters/detail", method = RequestMethod.GET)
    public String chapterDetailPage(HttpSession session) {
        requireUser(session);
        return "chapter/detail";
    }

    // ============================================================
    // 3. CHAPTER DETAIL (redirect)
    // GET /main/chapters/{id}
    // - Goi chapterService.getDetail(id) de validate chapter ton tai
    // - Redirect sang /main/chapters/detail?id={id}
    // - Logic hien thi thuc su nam o JSP phia sau /chapters/detail
    // ============================================================
    @RequestMapping(value = "/chapters/{id}", method = RequestMethod.GET)
    public String chapterDetail(@PathVariable("id") long id, HttpSession session) {
        requireUser(session);
        chapterService.getDetail(id);
        return "redirect:/main/chapters/detail?id=" + id;
    }

    // ============================================================
    // 4. CHAPTER SUBMIT REVIEW
    // POST /main/chapters/{id}/submit-review
    // - Goi chapterService.submitForReview(id, user)
    //   Business logic (kiem tra 100% task approved, v.v.) nam o service
    // - Thanh cong: redirect ve chapter detail
    // - Loi: redirect ve chapter detail kem param ?error= (URL-encoded)
    // NOTE: UnsupportedEncodingException bi nuot im -> redirect khong kem message
    // ============================================================
    @RequestMapping(value = "/chapters/{id}/submit-review", method = RequestMethod.POST)
    public String chapterSubmitReview(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            chapterService.submitForReview(id, user);
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

    // ============================================================
    // 5. TASK DETAIL
    // GET /main/tasks/{id}
    // - Goi pageTaskService.getDetailView(id, user) tra ve DetailView gom:
    //     + task: thong tin task
    //     + canAssistantUpdate: assistant duoc cap nhat status
    //     + canAssistantSubmit: assistant duoc nop
    //     + canMangakaTaskOwner: mangaka so huu task nay
    //     + canMangakaReview: mangaka duoc duyet/reject
    // - Cac flag dua vao model de JSP an/hien nut tuong ung
    // - Tra ve view: task/detail
    // ============================================================
    @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
    public String taskDetail(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        PageTaskService.DetailView view = pageTaskService.getDetailView(id, user);
        model.addAttribute("task", view.getTask());
        model.addAttribute("canAssistantUpdate", view.isCanAssistantUpdate());
        model.addAttribute("canAssistantSubmit", view.isCanAssistantSubmit());
        model.addAttribute("canMangakaTaskOwner", view.isCanMangakaTaskOwner());
        model.addAttribute("canMangakaReview", view.isCanMangakaReview());
        return "task/detail";
    }

    // ============================================================
    // 6. TASK UPDATE BY ASSISTANT
    // POST /main/tasks/{id}/assistant-status
    // - Param: status (string)
    // - Goi pageTaskService.updateStatusByAssistant(id, user, status)
    // - Flow hop le: Pending -> In-Progress -> Submitted (service enforce)
    // - Thanh cong: redirect task detail
    // - Loi: render lai task/detail kem error
    // ============================================================
    @RequestMapping(value = "/tasks/{id}/assistant-status", method = RequestMethod.POST)
    public String taskUpdateByAssistant(
            @PathVariable("id") long id,
            HttpSession session,
            @RequestParam("status") String status,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            pageTaskService.updateStatusByAssistant(id, user, status);
            return "redirect:/main/tasks/" + id;
        } catch (RuntimeException ex) {
            taskDetail(id, session, model);
            model.addAttribute("error", ex.getMessage());
            return "task/detail";
        }
    }

    // ============================================================
    // 7. TASK APPROVE
    // POST /main/tasks/{id}/approve
    // - Goi pageTaskService.approve(id, user, null)
    //   Tham so thu 3 (note) hardcode null — khong nhan input tu form
    // - Thanh cong: redirect task detail
    // - Loi: render lai task/detail kem error
    // ============================================================
    @RequestMapping(value = "/tasks/{id}/approve", method = RequestMethod.POST)
    public String taskApprove(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            pageTaskService.approve(id, user, null);
            return "redirect:/main/tasks/" + id;
        } catch (RuntimeException ex) {
            taskDetail(id, session, model);
            model.addAttribute("error", ex.getMessage());
            return "task/detail";
        }
    }

    // ============================================================
    // 8. TASK REJECT
    // POST /main/tasks/{id}/reject
    // - Goi pageTaskService.reject(id, user, "Rejected via web form")
    //   Ly do reject HARDCODE — khong cho nhap tu UI
    // - Sau 3 lan reject: service tu dong escalate len Tantou Editor (BR-TSK-05)
    //   Controller khong xu ly them sau escalation
    // - Thanh cong: redirect task detail
    // - Loi: render lai task/detail kem error
    // NOTE: Neu can ly do reject tu nguoi dung -> phai sua ca controller lan service
    // ============================================================
    @RequestMapping(value = "/tasks/{id}/reject", method = RequestMethod.POST)
    public String taskReject(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            pageTaskService.reject(id, user, "Rejected via web form");
            return "redirect:/main/tasks/" + id;
        } catch (RuntimeException ex) {
            taskDetail(id, session, model);
            model.addAttribute("error", ex.getMessage());
            return "task/detail";
        }
    }

    @RequestMapping(value = "/ranking/periods", method = RequestMethod.GET)
    public String rankingPeriods(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        model.addAttribute("periods", rankingService.listPeriods());
        model.addAttribute("seriesList", productionRepository.listSeries());

        // BR-RNK-06: Track which periods the current board member has already submitted for
        if (user.hasRole("EDITORIAL_BOARD")) {
            java.util.Set<Long> submittedPeriodIds = new java.util.HashSet<Long>();
            for (Map<String, Object> period : rankingService.listPeriods()) {
                long periodId = (Long) period.get("id");
                if (rankingService.hasSubmittedEntries(periodId, user.getId())) {
                    submittedPeriodIds.add(periodId);
                }
            }
            model.addAttribute("submittedRankingPeriodIds", submittedPeriodIds);
        } else {
            model.addAttribute("submittedRankingPeriodIds", new java.util.HashSet<Long>());
        }

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
            CreateRankingPeriodRequestDTO dto = new CreateRankingPeriodRequestDTO();
            dto.setName(name);
            dto.setEndDate(Date.valueOf(endDate));
            rankingService.createRankingPeriod(dto, user);
            //rankingRepository.createPeriod(name, startDate, Date.valueOf(endDate));
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
            if (!user.hasRole("EDITORIAL_BOARD")) {
                throw new IllegalArgumentException("Only EDITORIAL_BOARD can upload ranking CSV");
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

    @RequestMapping(value = "/ranking/periods/{id}/csv-uploads", method = RequestMethod.GET)
    public String rankingCsvUploads(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            List<Map<String, Object>> csvUploads = rankingService.findCsvByPeriod(id);
            model.addAttribute("periodId", id);
            model.addAttribute("csvUploads", csvUploads);
            return "ranking/csv-uploads";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            return "ranking/period";
        }
    }

    @RequestMapping(value = "/ranking/csv-uploads/{uploadId}", method = RequestMethod.GET)
    public String rankingViewCsvUpload(@PathVariable("uploadId") long uploadId, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            requireAdmin(user);
            RankingCsvUpload upload = rankingService.findCsvById(uploadId);
            if (upload == null) {
                throw new IllegalArgumentException("CSV upload not found");
            }
            model.addAttribute("csvUpload", upload);
            return "ranking/csv-view";
        } catch (RuntimeException ex) {
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
        AuthenticatedUser user = requireUser(session);
        model.addAttribute("period", rankingService.getPeriodById(id));
        model.addAttribute("results", rankingService.getRankingResults(id));
        model.addAttribute("entries", rankingService.listVoteEntries(id, user));
        return "ranking/results";
    }

    @RequestMapping(value = "/ranking/periods/{id}/mangaka", method = RequestMethod.GET)
    public String rankingMangaka(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        model.addAttribute("period", rankingService.getPeriodById(id));
        model.addAttribute("mangakaRanking", rankingService.getMangakaRanking(id, user));
        return "ranking/mangaka-ranking";
    }

    @RequestMapping(value = "/decisions", method = RequestMethod.GET)
    public String decisionSessions(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        if (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD")) {
            throw new IllegalArgumentException("Only ADMIN/EDITORIAL_BOARD can view decision sessions");
        }
        model.addAttribute("sessions", decisionService.listDecisionSessions(user));
        return "decision/session";
    }

    @RequestMapping(value = "/decisions/{id}", method = RequestMethod.GET)
    public String decisionDetail(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        if (!user.hasRole("ADMIN") && !user.hasRole("EDITORIAL_BOARD")) {
            throw new IllegalArgumentException("Only ADMIN/EDITORIAL_BOARD can view decision detail");
        }

        Map<String, Object> sessionDetail = decisionService.getDecisionSession(id, user);
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
            SubmitDecisionVoteRequest request = new SubmitDecisionVoteRequest();
            request.setDecision(decision);
            request.setJustification(justification);
            decisionService.submitDecisionVote(id, request, user);
            return "redirect:/main/decisions/" + id;
        } catch (RuntimeException ex) {
            decisionDetail(id, session, model);
            model.addAttribute("error", ex.getMessage());
            return "decision/session";
        }
    }

    /**
     * Shows the admin user-management table with current roles and status
     * actions.
     */
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

    /**
     * Opens the create-user form. The form uses one roleOption radio group so
     * the UI mirrors the allowed role combinations instead of letting admins
     * build an invalid checkbox combination.
     */
    @RequestMapping(value = "/users/new", method = RequestMethod.GET)
    public String userNew(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        requireAdmin(user);
        model.addAttribute("editing", false);
        model.addAttribute("availableRoles", availableRoles());
        model.addAttribute("adminRoleLocked", userAdminRepository.hasAnyAdmin());
        model.addAttribute("selectedRoleOption", "");
        return "user/form";
    }

    /**
     * Opens edit mode for profile fields only. Username is immutable identity,
     * and roles are managed from the list page so role changes stay
     * visible/auditable.
     */
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

    /**
     * Creates a user after early controller validation, then assigns the
     * selected role option. Repository checks remain the authority for
     * uniqueness and ADMIN singleton rules because they run next to the
     * database write.
     */
    @RequestMapping(value = "/users/create", method = RequestMethod.POST)
    public String userCreate(
            HttpSession session,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam(value = "roleOption", required = false) String roleOption,
            Model model) {
        AuthenticatedUser user = requireUser(session);
        List<String> roles = parseRoleOption(roleOption);
        try {
            requireAdmin(user);
            if (password == null || password.trim().isEmpty()) {
                password = "12345";
            }
            validateCreateUser(username, password, fullName, email, roles);
            long id = userAdminRepository.createUser(username, password, fullName, email);
            for (String role : roles) {
                userAdminRepository.addRole(id, role);
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
            model.addAttribute("selectedRoleOption", roleOption == null ? "" : roleOption.trim().toUpperCase());
            model.addAttribute("availableRoles", availableRoles());
            model.addAttribute("adminRoleLocked", userAdminRepository.hasAnyAdmin());
            return "user/form";
        }
    }

    /**
     * Updates only full name and email. Username and roles are intentionally
     * not accepted here because username identifies the account and roles have
     * their own list-page workflow.
     */
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

    /**
     * Changes ACTIVE/INACTIVE status; the repository blocks deactivating the
     * only active ADMIN so the system is never left without an administrator.
     */
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

    /**
     * Adds one or more roles from the list page. Controller validation gives a
     * quick error, while UserAdminRepository and RoleCombinationValidator
     * enforce the same rules before saving.
     */
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

    /**
     * Removes one role from a user. Removing the final ADMIN role is blocked in
     * the repository, which is the authoritative database-side guard.
     */
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

    /**
     * Reads AUTH_USER from session for controller methods that require login.
     */
    private AuthenticatedUser requireUser(HttpSession session) {
        Object auth = session.getAttribute("AUTH_USER");
        if (auth == null || !(auth instanceof AuthenticatedUser)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return (AuthenticatedUser) auth;
    }

    /**
     * Gives user-management pages an explicit ADMIN guard in addition to the
     * route-level RBAC interceptor.
     */
    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only ADMIN can perform this action");
        }
    }

    /**
     * Lists roles that can be assigned from the user UI. ADMIN is intentionally
     * omitted because this system has a single administrator account.
     */
    private List<String> availableRoles() {
        return Arrays.asList("MANGAKA", "ASSISTANT", "TANTOU_EDITOR", "EDITORIAL_BOARD");
    }

    /**
     * Normalizes role input from either the legacy single-role field or the
     * checkbox list used on the user table.
     */
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

    /**
     * Converts the create-form roleOption radio value into one or two role
     * names.
     */
    private List<String> parseRoleOption(String roleOption) {
        List<String> selected = new ArrayList<String>();
        if (isBlank(roleOption)) {
            return selected;
        }
        String[] roleValues = roleOption.split(",");
        for (String role : roleValues) {
            addSelectedRole(selected, role);
        }
        return selected;
    }

    /**
     * Adds a normalized role once, keeping duplicate request values harmless.
     */
    private void addSelectedRole(List<String> selected, String role) {
        if (isBlank(role)) {
            return;
        }
        String normalizedRole = role.trim().toUpperCase();
        if (!selected.contains(normalizedRole)) {
            selected.add(normalizedRole);
        }
    }

    /**
     * Performs quick create-form validation before calling the repository. The
     * repository is still the authoritative layer for uniqueness and ADMIN
     * singleton checks because those rules must be protected at save time.
     */
    private void validateCreateUser(String username, String password, String fullName, String email, List<String> roles) {
        if (isBlank(username) || isBlank(password) || isBlank(fullName) || isBlank(email)) {
            throw new IllegalArgumentException("All user fields are required");
        }
        if (password.length() < 5) {
            throw new IllegalArgumentException("Password must be at least 5 characters");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Email is invalid");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Select at least one role");
        }
        if (containsRole(roles, "ADMIN") && userAdminRepository.hasAnyAdmin()) {
            throw new IllegalArgumentException("Only one ADMIN account is allowed");
        }
        manga.common.util.RoleCombinationValidator.validate(roles);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Checks the requested role set before saving. This is an early-exit
     * helper; UserAdminRepository.addRole and RoleCombinationValidator still
     * enforce the final role-combination rules.
     */
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
    // MANUSCRIPT WORKSPACE (lien quan Chapter)
    // ============================================================
    // ============================================================
    // 9a. TAO MANUSCRIPT WORKSPACE
    // GET  /main/chapters/{chapterId}/manuscript-workspace/create -> hien form xac nhan
    // POST /main/chapters/{chapterId}/manuscript-workspace/create -> tao ManuscriptVersion moi
    // - Chi chapter o trang thai EDITORIAL_REVIEW moi hop le (enforce o service)
    // - Tao xong: redirect sang /main/manuscript-workspace/{versionId}
    // - Loi: render lai form create kem error
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/manuscript-workspace/create", method = RequestMethod.GET)
    public String manuscriptWorkspaceCreate(@PathVariable("chapterId") long chapterId, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        ChapterSummary chapter = chapterService.getDetail(chapterId);
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
            ChapterSummary chapter = chapterService.getDetail(chapterId);
            model.addAttribute("chapter", chapter);
            model.addAttribute("error", ex.getMessage());
            return "manuscript-version/create";
        }
    }

    @RequestMapping(value = "/manuscript-workspace/{id}", method = RequestMethod.GET)
    public String manuscriptWorkspaceView(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        manga.model.ManuscriptVersion version = manuscriptVersionService.getVersion(id);
        if (version == null) {
            throw new IllegalArgumentException("Manuscript version not found");
        }

        ChapterSummary chapter = chapterService.getDetail(version.getChapterId());
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
        // DEBUG
        if (!annotations.isEmpty()) {
            manga.model.AnnotationSummary a = annotations.get(0);

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
        long mangakaId = chapterService.findOwnerMangakaByChapter(version.getChapterId());
        long tantouId = chapterService.findSeriesTantou(chapter.getSeriesId());
        boolean isMangakaOwner = user.getId() == mangakaId;
        boolean isAssignedTantou = user.getId() == tantouId;
        boolean isAdmin = user.hasRole("ADMIN");

        // Readonly mode: approved, rejected, and published versions are readonly
        boolean isReadonly = version.getStatus() == ManuscriptStatus.APPROVED
                || version.getStatus() == ManuscriptStatus.REJECTED
                || version.getStatus() == ManuscriptStatus.PUBLISHED;

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
        model.addAttribute("isMangakaRole", user.hasRole("MANGAKA"));
        model.addAttribute("isMangakaOwner", isMangakaOwner);
        model.addAttribute("isAssignedTantou", isAssignedTantou);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isReadonly", isReadonly);
        model.addAttribute("productionLocked", manuscriptVersionService.isProductionLocked(version.getChapterId()));

        return "manuscript-version/workspace";
    }

    // ============================================================
    // 12. IMPORT PAGES VAO MANUSCRIPT WORKSPACE
    // POST /main/manuscript-workspace/{id}/import-pages
    // - Import cac page tu chapter task da APPROVED vao manuscript version nay
    // - Loi: render lai workspace view kem error
    // ============================================================
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

    // ============================================================
    // 10. TAO VERSION MOI SAU KHI BI REJECT
    // POST /main/chapters/{chapterId}/manuscript-workspace/new-version
    // - Goi manuscriptVersionService.createNewVersion(chapterId, user)
    // - Version cu bi reject giu nguyen (immutable — BR-MAN-11)
    // - Tao xong: redirect sang workspace cua version moi
    // - Loi: redirect ve chapter detail (khong render lai form)
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/manuscript-workspace/new-version", method = RequestMethod.POST)
    public String manuscriptWorkspaceNewVersion(@PathVariable("chapterId") long chapterId, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        try {
            manga.model.ManuscriptVersion version = manuscriptVersionService.createNewVersion(chapterId, user);
            return "redirect:/main/manuscript-workspace/" + version.getId();
        } catch (RuntimeException ex) {
            ChapterSummary chapter = chapterService.getDetail(chapterId);
            model.addAttribute("chapter", chapter);
            model.addAttribute("error", ex.getMessage());
            return "redirect:/main/chapters/" + chapterId;
        }
    }

    @RequestMapping(value = "/manuscript-workspace/{id}/dashboard", method = RequestMethod.GET)
    public String manuscriptWorkspaceDashboard(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        manga.dto.ReviewDashboardDTO dashboard = manuscriptVersionService.getReviewDashboard(id);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("currentUser", user);
        return "manuscript-version/dashboard";
    }

    // ============================================================
    // 11. XEM LICH SU VERSION
    // GET /main/chapters/{chapterId}/manuscript-workspace/history
    // - Lay danh sach tat ca version cua chapter, lay version moi nhat (index 0)
    // - Redirect thang sang workspace cua version do
    // - Khong co trang history rieng — sidebar trong workspace dam nhiem
    // - Loi: throw neu chua co version nao
    // ============================================================
    @RequestMapping(value = "/chapters/{chapterId}/manuscript-workspace/history", method = RequestMethod.GET)
    public String manuscriptWorkspaceHistory(@PathVariable("chapterId") long chapterId, HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        List<manga.model.ManuscriptVersion> versions = manuscriptVersionService.listVersions(chapterId);
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("No manuscript versions found for chapter");
        }
        // Redirect to the most recent version's workspace
        manga.model.ManuscriptVersion latestVersion = versions.get(0);
        return "redirect:/main/manuscript-workspace/" + latestVersion.getId();
    }

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

    @RequestMapping(value = "/manuscript-review", method = RequestMethod.GET)
    public String manuscriptReviewInbox(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);

        if (!user.hasRole("TANTOU_EDITOR") && !user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Only TANTOU_EDITOR or ADMIN can access review inbox");
        }

        boolean isAdmin = user.hasRole("ADMIN");
        List<manga.model.ManuscriptVersion> underReviewVersions
                = manuscriptVersionService.findUnderReviewForTantou(user.getId(), isAdmin);

        if (underReviewVersions == null) {
            underReviewVersions = new java.util.ArrayList<>();
        }

        // Load chapter and series information for each version
        java.util.Map<Long, ChapterSummary> chapterMap = new java.util.HashMap<>();
        java.util.Map<Long, String> mangakaNames = new java.util.HashMap<>();
        java.util.Map<Long, String> submittedAtMap = new java.util.HashMap<>();

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (manga.model.ManuscriptVersion version : underReviewVersions) {
            ChapterSummary chapter = chapterService.getDetail(version.getChapterId());
            if (chapter != null) {
                chapterMap.put(version.getId(), chapter);

                // Get mangaka name using UserAdminRepository
                long mangakaId = chapterService.findOwnerMangakaByChapter(version.getChapterId());
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
            // Attach review task SLA data for inbox UI
            try {
                manga.model.ReviewTask task = reviewTaskService.getReviewTask(version.getId());
                if (task != null) {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    long remaining = java.time.Duration.between(now, task.getDueAt()).getSeconds();
                    boolean overdue = remaining <= 0;
                    String urgency;
                    long s24 = 24 * 3600;
                    long s12 = 12 * 3600;
                    if (remaining <= 0) {
                        urgency = "OVERDUE";
                    } else if (remaining <= s12) {
                        urgency = "RED";
                    } else if (remaining <= s24) {
                        urgency = "YELLOW";
                    } else {
                        urgency = "GREEN";
                    }

                    // put into maps for JSP
                    // Format timestamps for display
                    java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    // reuse submittedAtMap to show dueAt and assignedAt via separate maps
                    // Using model attribute names: remainingSecondsMap, urgencyMap, dueAtMap, assignedAtMap
                    // Lazy-create maps if absent
                    // We'll collect maps outside loop by ensuring they're initialized earlier (below)
                }
            } catch (Exception ex) {
                // ignore per-display; backend remains functional
            }
        }
        // Build SLA maps
        java.util.Map<Long, Long> remainingSecondsMap = new java.util.HashMap<>();
        java.util.Map<Long, String> urgencyMap = new java.util.HashMap<>();
        java.util.Map<Long, String> dueAtMap = new java.util.HashMap<>();
        java.util.Map<Long, String> assignedAtMap = new java.util.HashMap<>();

        for (manga.model.ManuscriptVersion version : underReviewVersions) {
            manga.model.ReviewTask task = reviewTaskService.getReviewTask(version.getId());
            if (task != null) {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                long remaining = java.time.Duration.between(now, task.getDueAt()).getSeconds();
                boolean overdue = remaining <= 0;
                String urgency;
                long s24 = 24 * 3600;
                long s12 = 12 * 3600;
                if (remaining <= 0) {
                    urgency = "OVERDUE";
                } else if (remaining <= s12) {
                    urgency = "RED";
                } else if (remaining <= s24) {
                    urgency = "YELLOW";
                } else {
                    urgency = "GREEN";
                }

                remainingSecondsMap.put(version.getId(), remaining);
                urgencyMap.put(version.getId(), urgency);
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                dueAtMap.put(version.getId(), task.getDueAt() != null ? task.getDueAt().format(fmt) : "");
                assignedAtMap.put(version.getId(), task.getAssignedAt() != null ? task.getAssignedAt().format(fmt) : "");
            }
        }

        model.addAttribute("underReviewVersions", underReviewVersions);
        model.addAttribute("chapterMap", chapterMap);
        model.addAttribute("mangakaNames", mangakaNames);
        model.addAttribute("submittedAtMap", submittedAtMap);
        model.addAttribute("remainingSecondsMap", remainingSecondsMap);
        model.addAttribute("urgencyMap", urgencyMap);
        model.addAttribute("dueAtMap", dueAtMap);
        model.addAttribute("assignedAtMap", assignedAtMap);
        model.addAttribute("currentUser", user);
        model.addAttribute("isAdmin", isAdmin);

        return "manuscript-version/review-inbox";
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================
    private boolean containsRole(List<String> roles, String roleName) {
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
