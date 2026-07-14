package manga.controller.web.salary;

import javax.servlet.http.HttpSession;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.service.salary.SalaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/main/salary")
public class SalaryWebController {

    @Autowired
    private SalaryService salaryService;
    
    //Mangaka xem list Ky luong
    @RequestMapping(value = "/periods", method = RequestMethod.GET)
    public String periods(HttpSession session, Model model) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        model.addAttribute("periods", salaryService.listMyPeriods(user));
        return "salary/period";
    }
    
    //Assistant coi luong cua minh
    @RequestMapping(value = "/my", method = RequestMethod.GET)
    public String mySalary(HttpSession session, Model model) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        model.addAttribute("records", salaryService.getMySettledSalaryRecords(user));
        return "salary/assistant-salary";
    }
    
    //Chi tiet 1 ky luong
    @RequestMapping(value = "/periods/{id}", method = RequestMethod.GET)
    public String detail(@PathVariable("id") long id, HttpSession session, Model model) {
        AuthenticatedUser user = SessionUserUtil.requireUser(session);
        try {
            salaryService.refreshOpenPeriod(id, user);
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        loadDetail(id, user, model);
        return "salary/detail";
    }

    private void loadDetail(long periodId, AuthenticatedUser user, Model model) {
        model.addAttribute("period", salaryService.getPeriodOwnedByUser(periodId, user));
        model.addAttribute("records", salaryService.getRecords(periodId, user));
    }
}
