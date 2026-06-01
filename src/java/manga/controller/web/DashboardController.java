package manga.controller.web;

import manga.model.AuthenticatedUser;
import manga.model.ChapterSummary;
import manga.model.ManuscriptSummary;
import manga.model.Proposal;
import manga.model.SeriesSummary;
import manga.model.TaskSummary;
import manga.repository.ProductionRepository;
import manga.service.ProposalService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class DashboardController {

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProductionRepository productionRepository;

    @RequestMapping(value = {"/", "/dashboard"}, method = RequestMethod.GET)
    public String dashboard(HttpSession session, Model model) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("AUTH_USER");

        List<Proposal> proposals = new ArrayList<Proposal>();
        try {
            proposals = proposalService.listForUser(user);
        } catch (IllegalArgumentException ignored) {
            // Some roles do not have proposal permission; keep dashboard usable.
        }

        List<SeriesSummary> seriesList = productionRepository.listSeries();
        List<TaskSummary> tasks = productionRepository.listTasks();
        List<ManuscriptSummary> manuscripts = productionRepository.listManuscripts();
        List<ChapterSummary> chapters = productionRepository.listChapters();

        int activeProposalCount = 0;
        int approved = 0;
        Proposal activeProposal = null;
        for (Proposal p : proposals) {
            if ("UNDER_REVIEW".equalsIgnoreCase(p.getStatus()) || "REVISION_REQUESTED".equalsIgnoreCase(p.getStatus())) {
                activeProposalCount++;
                if (activeProposal == null) {
                    activeProposal = p;
                }
            }
            if ("APPROVED".equalsIgnoreCase(p.getStatus())) {
                approved++;
            }
        }

        int overdueTasks = 0;
        int pendingManuscripts = 0;
        LocalDate now = LocalDate.now();
        for (TaskSummary t : tasks) {
            if ("OVERDUE".equalsIgnoreCase(t.getStatus()) || (t.getDueDate() != null && t.getDueDate().toLocalDate().isBefore(now) && !"APPROVED".equalsIgnoreCase(t.getStatus()))) {
                overdueTasks++;
            }
        }
        for (ManuscriptSummary m : manuscripts) {
            if ("SUBMITTED".equalsIgnoreCase(m.getStatus()) || "UNDER_REVIEW".equalsIgnoreCase(m.getStatus())) {
                pendingManuscripts++;
            }
        }

        List<ChapterSummary> inProgressChapters = new ArrayList<ChapterSummary>();
        for (ChapterSummary c : chapters) {
            if ("IN_PROGRESS".equalsIgnoreCase(c.getStatus()) || "PLANNING".equalsIgnoreCase(c.getStatus()) || "EDITORIAL_REVIEW".equalsIgnoreCase(c.getStatus())) {
                inProgressChapters.add(c);
            }
            if (inProgressChapters.size() >= 5) {
                break;
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("proposalCount", proposals.size());
        model.addAttribute("activeProposalCount", activeProposalCount);
        model.addAttribute("approvedCount", approved);
        model.addAttribute("roles", user.getRoles());
        model.addAttribute("seriesCount", seriesList.size());
        model.addAttribute("overdueTasks", overdueTasks);
        model.addAttribute("pendingManuscripts", pendingManuscripts);
        model.addAttribute("activeProposal", activeProposal);
        model.addAttribute("inProgressChapters", inProgressChapters);

        return "dashboard/index";
    }
}



