package manga.controller.web;

import manga.model.AuthenticatedUser;
import manga.model.ManuscriptSummary;
import manga.model.TaskSummary;
import manga.common.util.SessionUserUtil;
import manga.repository.PageTaskRepository;
import manga.repository.ProductionRepository;
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
public class ProductionController {

    @Autowired
    private ProductionRepository productionRepository;

    @Autowired
    private PageTaskRepository pageTaskRepository;

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

    @RequestMapping(value = "/manuscripts", method = RequestMethod.GET)
    public String manuscripts(HttpSession session, Model model) {
        List<ManuscriptSummary> manuscripts = productionRepository.listManuscripts();
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
        return "manuscript/list";
    }
}
