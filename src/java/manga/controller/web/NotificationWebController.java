package manga.controller.web;

import manga.model.AuthenticatedUser;
import manga.service.NotificationService;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/main/notifications")
public class NotificationWebController {

    @Autowired
    private NotificationService notificationService;

    /**
     * Shows the full notification list. The page is server-rendered, while row
     * delete and read/unread toggles still use the existing API/JS behavior.
     */
    @RequestMapping(method = RequestMethod.GET)
    public String list(HttpSession session, Model model) {
        AuthenticatedUser user = requireUser(session);
        model.addAttribute("notifications", notificationService.listByUser(user.getId(), 100));
        model.addAttribute("unreadCount", notificationService.unreadCount(user.getId()));
        return "notification/list";
    }

    /**
     * Web fallback for marking one notification read from a form submit.
     */
    @RequestMapping(value = "/{id}/read", method = RequestMethod.POST)
    public String markRead(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = requireUser(session);
        notificationService.markRead(user.getId(), id);
        return "redirect:/main/notifications";
    }

    @RequestMapping(value = "/{id}/click", method = RequestMethod.GET)
    public RedirectView click(@PathVariable("id") long id, HttpSession session) {
        AuthenticatedUser user = requireUser(session);
        notificationService.markRead(user.getId(), id);
        String viewUrl = notificationService.viewUrlByUser(user.getId(), id);
        if (isSupportedViewUrl(viewUrl)) {
            return redirectTo(viewUrl);
        }
        return redirectTo("/main/notifications");
    }

    @RequestMapping(value = "/mark-all-read", method = RequestMethod.POST)
    public String markAllRead(HttpSession session) {
        AuthenticatedUser user = requireUser(session);
        notificationService.markAllRead(user.getId());
        return "redirect:/main/notifications";
    }

    private AuthenticatedUser requireUser(HttpSession session) {
        Object auth = session == null ? null : session.getAttribute("AUTH_USER");
        // Web endpoints depend on the interceptor, but this guard keeps direct calls safe.
        if (!(auth instanceof AuthenticatedUser)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return (AuthenticatedUser) auth;
    }

    /**
     * Open-redirect guard for /{id}/click: only allowlisted internal /main/**
     * paths. viewUrl is stored at creation time, but we re-validate here in
     * case of stale or tampered rows; unknown paths fall back to the
     * notification list.
     */
    private boolean isSupportedViewUrl(String viewUrl) {
        if (viewUrl == null) {
            return false;
        }
        String path = viewUrl.trim();
        if (path.isEmpty() || !path.startsWith("/main/")) {
            return false;
        }
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        // Regex allowlist of known app routes — external URLs never pass.
        return path.matches("/main/notifications")
                || path.matches("/main/proposals/\\d+")
                || path.matches("/main/proposals/\\d+/vote")
                || path.matches("/main/proposals/\\d+/edit")
                || path.matches("/main/tasks/\\d+")
                || path.matches("/main/chapters")
                || path.matches("/main/chapters/\\d+")
                || path.matches("/main/chapters/detail")
                || path.startsWith("/main/series/")
                || path.matches("/main/manuscript-workspace/\\d+")
                || path.matches("/main/decisions/\\d+")
                || path.matches("/main/ranking/periods(/\\d+/(results|mangaka))?");
    }

    private RedirectView redirectTo(String path) {
        RedirectView view = new RedirectView(path, true);
        view.setExposeModelAttributes(false);
        return view;
    }
}
