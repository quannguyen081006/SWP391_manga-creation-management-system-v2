package manga.service.salary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import manga.common.exception.BusinessRuleException;
import manga.common.util.SessionUserUtil;
import manga.model.AuthenticatedUser;
import manga.model.salary.TaskTypeRate;
import manga.repository.salary.TaskTypeRateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskTypeRateService {

    @Autowired
    private TaskTypeRateRepository taskTypeRateRepository;

    public List<TaskTypeRate> listAll(AuthenticatedUser user) {
        requireAdmin(user);
        return taskTypeRateRepository.findAll();
    }

    /** Saves every task type rate in one go, alongside the rest of the salary settings form. */
    public void updateRates(Map<String, BigDecimal> ratesByCode, AuthenticatedUser user) {
        requireAdmin(user);
        for (Map.Entry<String, BigDecimal> entry : ratesByCode.entrySet()) {
            validateRate(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, BigDecimal> entry : ratesByCode.entrySet()) {
            taskTypeRateRepository.updateRate(entry.getKey().trim(), entry.getValue());
        }
    }

    private void validateRate(String code, BigDecimal ratePerPage) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessRuleException("Task type code is required");
        }
        if (ratePerPage == null || ratePerPage.signum() <= 0) {
            throw new BusinessRuleException("Rate per page must be greater than 0");
        }
    }

    private void requireAdmin(AuthenticatedUser user) {
        try {
            SessionUserUtil.requireRole(user, "ADMIN", "Only ADMIN can update task type rates");
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ex.getMessage());
        }
    }
}
