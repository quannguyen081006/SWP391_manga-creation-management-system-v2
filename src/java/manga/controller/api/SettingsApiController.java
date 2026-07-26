package manga.controller.api;

import manga.common.ApiResponse;
import manga.common.util.SessionUserUtil;
import manga.model.DeadlineSettings;
import manga.model.ProgressSettings;
import manga.service.DeadlineSettingsService;
import manga.service.ProgressSettingsService;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only settings the frontend needs to mirror server-side validation (e.g. the min/max
 * bounds shown on date pickers). Any logged-in user can read these - they're not secrets,
 * and the actual enforcement always happens server-side regardless of what the UI shows.
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsApiController {

    @Autowired
    private DeadlineSettingsService deadlineSettingsService;

    @Autowired
    private ProgressSettingsService progressSettingsService;

    @RequestMapping(value = "/deadlines", method = RequestMethod.GET)
    public ApiResponse<DeadlineSettings> deadlines(HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(deadlineSettingsService.getSettings(), "Deadline settings");
    }

    @RequestMapping(value = "/progress", method = RequestMethod.GET)
    public ApiResponse<ProgressSettings> progress(HttpSession session) {
        SessionUserUtil.requireUser(session);
        return ApiResponse.ok(progressSettingsService.getSettings(), "Progress display settings");
    }
}
