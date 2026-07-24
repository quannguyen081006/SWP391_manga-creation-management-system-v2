package manga.web.interceptor;

import manga.model.AuthenticatedUser;
import manga.web.ActiveSessionRegistry;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Cổng kiểm soát duy nhất cho mọi request trước khi vào controller.
 *
 * <p><b>Vì sao dùng interceptor thay vì kiểm tra trong từng controller?</b>
 * Kiểm tra viết trong controller chỉ bảo vệ đúng method đó, và rất dễ quên khi
 * thêm endpoint mới. Interceptor chạy TRƯỚC mọi handler, nên route thêm sau này
 * mặc định đã được bảo vệ. Toàn bộ luật phân quyền nằm gọn trong một file, đọc
 * từ trên xuống là nắm được.
 *
 * <p><b>Hai câu hỏi tách biệt, theo đúng thứ tự này:</b>
 * <ol>
 *   <li><i>Authentication</i> (xác thực) — bạn là ai? Lấy từ session key
 *       {@code AUTH_USER}. Không có session = chưa đăng nhập.</li>
 *   <li><i>Authorization</i> (phân quyền) — bạn có được vào đây không? Do
 *       {@link #isAllowed} quyết định, dựa trên tiền tố URL và role của user.</li>
 * </ol>
 * Trượt câu 1 → 401 / chuyển về trang login.
 * Trượt câu 2 → 403 / chuyển về dashboard.
 * Hai tình huống khác nhau nên xử lý ở hai nhánh khác nhau.
 *
 * <p><b>Sidebar đã ẩn link rồi, sao server còn phải chặn?</b>
 * Ẩn menu chỉ là chuyện hiển thị. Người dùng hoàn toàn có thể gõ thẳng URL, nên
 * server bắt buộc phải là nơi quyết định cuối cùng. Đây chính là lỗi đã được vá
 * trong {@link #isAllowed} ở nhóm route manuscript: link thì đã ẩn, nhưng server
 * vẫn cho mọi user đã đăng nhập đi qua.
 */
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * Sổ ghi phiên hợp lệ duy nhất của mỗi tài khoản. Được tiêm vì bean này khai
     * báo trong dispatcher-servlet.xml nằm trong ngữ cảnh có bật xử lý annotation.
     */
    @Autowired
    private ActiveSessionRegistry activeSessionRegistry;

    /**
     * Chạy trước mọi method của controller.
     *
     * @return {@code true} = cho request đi tiếp vào controller.
     *         {@code false} = chặn tại đây (lúc này redirect hoặc JSON lỗi đã
     *         được ghi vào response rồi).
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String context = request.getContextPath();

        // BƯỚC 0 - Route công khai: phải vào được khi CHƯA đăng nhập.
        // Nếu chặn cả /login thì user không bao giờ đăng nhập được (vòng lặp vô tận
        // redirect về login). /assets/ mở vì CSS/JS phải tải được ở trang login.
        if (uri.endsWith("/login") || uri.endsWith("/logout")
                || uri.contains("/assets/") || uri.endsWith("/redirect.jsp")) {
            return true;
        }

        // BƯỚC 1 - XÁC THỰC: user này là ai?
        AuthenticatedUser user = getSessionUser(request);
        if (user == null) {
            // Chưa đăng nhập. Cách báo lỗi khác nhau tuỳ loại client:
            // - Gọi API (AJAX): trả JSON 401, vì redirect sang HTML trang login
            //   sẽ làm JavaScript parse JSON bị lỗi.
            // - Mở trang web: redirect sang /login cho người dùng đăng nhập.
            if (uri.contains("/api/v1/")) {
                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            } else {
                response.sendRedirect(context + "/login");
            }
            return false;
        }

        // BƯỚC 1b - MỘT TÀI KHOẢN CHỈ MỘT PHIÊN: session hiện tại có còn là phiên
        // hợp lệ của tài khoản không? Nếu tài khoản vừa được đăng nhập ở nơi khác,
        // phiên này đã bị đá khỏi registry -> huỷ nó và bắt đăng nhập lại.
        HttpSession session = request.getSession(false);
        String sessionId = session == null ? null : session.getId();
        if (!activeSessionRegistry.isCurrent(user.getId(), sessionId)) {
            if (session != null) {
                session.invalidate();
            }
            if (uri.contains("/api/v1/")) {
                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Session ended: signed in elsewhere");
            } else {
                // Kèm cờ để trang login có thể hiện thông báo "tài khoản vừa đăng
                // nhập ở nơi khác" thay vì để người dùng bối rối không rõ vì sao.
                response.sendRedirect(context + "/login?reason=session_replaced");
            }
            return false;
        }

        // Đã xác thực xong -> đưa user vào request để controller/JSP dùng lại,
        // khỏi phải đọc session và ép kiểu thêm lần nữa.
        request.setAttribute("AUTH_USER_CHECKED", user);

        // Chặn trình duyệt cache trang đã đăng nhập. Nếu không có bước này, sau khi
        // Logout mà bấm nút Back thì trình duyệt vẫn dựng lại trang cũ từ cache,
        // làm lộ dữ liệu dù session ở server đã bị huỷ.
        preventCachedAuthenticatedPage(response, uri);

        // BƯỚC 2 - PHÂN QUYỀN: user này có được vào đường dẫn này không?
        if (!isAllowed(user, uri, context)) {
            // Đã đăng nhập nhưng không đủ quyền -> 403, KHÁC với 401 ở trên.
            // Web thì đẩy về dashboard thay vì hiện trang lỗi, để user không bị kẹt.
            if (uri.contains("/api/v1/")) {
                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            } else {
                response.sendRedirect(context + "/main/dashboard");
            }
            return false;
        }

        return true;
    }

    /**
     * Reads AUTH_USER from HttpSession when it is present and has the expected type.
     */
    private AuthenticatedUser getSessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object auth = session == null ? null : session.getAttribute("AUTH_USER");
        if (auth instanceof AuthenticatedUser) {
            return (AuthenticatedUser) auth;
        }
        return null;
    }

    /**
     * Prevents browser cache from showing protected pages after logout.
     */
    private void preventCachedAuthenticatedPage(HttpServletResponse response, String uri) {
        if (uri.contains("/assets/")) {
            return;
        }
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    /**
     * Sends the small JSON error shape expected by API clients.
     */
    private void writeJsonError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"data\":null,\"errors\":[\"" + message + "\"]}");
        response.getWriter().flush();
    }

    /**
     * Applies route-level RBAC rules after the user is known to be logged in.
     * Rules are kept as clear if statements so a beginner can trace each URL
     * prefix to its allowed roles without learning an extra mapping structure.
     */
    private boolean isAllowed(AuthenticatedUser user, String uri, String context) {
        String path = uri.substring(context.length());
        if (path.startsWith("/api/v1/users")) {
            return user.hasRole("ADMIN");
        }
        if (path.startsWith("/main/users")) {
            return user.hasRole("ADMIN");
        }
        if (path.startsWith("/main/settings")) {
            return user.hasRole("ADMIN");
        }
        if (path.startsWith("/main/proposals")) {
            return user.hasRole("ADMIN") || user.hasRole("MANGAKA") || user.hasRole("TANTOU_EDITOR") || user.hasRole("EDITORIAL_BOARD");
        }
        if (path.startsWith("/main/series") || path.startsWith("/main/chapters")) {
            return user.hasRole("ADMIN") || user.hasRole("MANGAKA") || user.hasRole("TANTOU_EDITOR");
        }
        if (path.startsWith("/main/decisions")) {
            return user.hasRole("ADMIN") || user.hasRole("EDITORIAL_BOARD");
        }
        if (path.startsWith("/main/ranking")) {
            return true;
        }
        if (path.startsWith("/main/salary")) {
            if (path.startsWith("/main/salary/my")) {
                return user.hasRole("ASSISTANT");
            }
            return user.hasRole("MANGAKA");
        }
        if (path.startsWith("/main/profile")) {
            return true;
        }
        if (path.startsWith("/main/tasks")) {
            return user.hasRole("ADMIN") || user.hasRole("MANGAKA") || user.hasRole("ASSISTANT") || user.hasRole("TANTOU_EDITOR");
        }
        // All three prefixes are listed explicitly: the real manuscript screens live
        // under /main/manuscript-review and /main/manuscript-workspace, neither of
        // which starts with "/main/manuscripts". Matching only the plural form let
        // every authenticated user (including ASSISTANT) reach those pages through
        // the default allow at the end of this method.
        if (path.startsWith("/main/manuscripts")
                || path.startsWith("/main/manuscript-review")
                || path.startsWith("/main/manuscript-workspace")) {
            return user.hasRole("ADMIN") || user.hasRole("MANGAKA") || user.hasRole("TANTOU_EDITOR");
        }
        return true;
    }

    /**
     * No-op hook required by HandlerInterceptor; work is done in preHandle.
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
    }

    /**
     * No-op hook required by HandlerInterceptor after request completion.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    }
}
