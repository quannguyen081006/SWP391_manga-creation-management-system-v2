package manga.web;

import manga.model.AuthenticatedUser;
import manga.model.NotificationItem;
import manga.service.NotificationService;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Cung cấp dữ liệu chuông thông báo cho MỌI trang JSP, tự động.
 *
 * <p><b>Vấn đề cần giải:</b> chuông thông báo nằm trên header, mà header thì
 * xuất hiện ở mọi trang. Nếu không có class này, mỗi controller đều phải tự gọi
 * notificationService rồi nhét số chưa đọc vào model — lặp lại ở hàng chục chỗ,
 * và chỉ cần quên một controller là trang đó mất chuông.
 *
 * <p><b>Cách giải:</b> {@code @ControllerAdvice} là cơ chế của Spring cho phép
 * khai báo thứ dùng chung cho nhiều controller ở một nơi duy nhất. Các method gắn
 * {@code @ModelAttribute} bên dưới sẽ được Spring chạy tự động trước khi render
 * bất kỳ view nào, và kết quả được đẩy sẵn vào model. JSP cứ thế lấy ra dùng.
 *
 * <p><b>Vì sao có {@code annotations = Controller.class}?</b> Để giới hạn chỉ áp
 * dụng cho controller trả về trang JSP. Nếu bỏ tham số này, advice sẽ chạy cho cả
 * {@code @RestController}, nghĩa là mỗi lần gọi API đều tốn thêm hai câu truy vấn
 * thông báo hoàn toàn vô ích — API trả JSON, làm gì có header để hiển thị.
 *
 * <p><i>Lưu ý đánh đổi:</i> cách này chạy query trên mọi lần mở trang. Chấp nhận
 * được với quy mô đồ án và hai câu query đơn giản; nếu hệ thống lớn hơn thì nên
 * cache hoặc để phía client tự gọi AJAX lấy số chưa đọc.
 */
@ControllerAdvice(annotations = Controller.class)
public class NotificationViewAdvice {

    @Autowired
    private NotificationService notificationService;

    /**
     * Số thông báo chưa đọc, hiển thị thành con số đỏ trên chuông ở header.jsp.
     *
     * <p>Trả 0 khi chưa đăng nhập thay vì ném lỗi: advice này chạy cho cả trang
     * login (trang công khai, chưa có user trong session). Ném lỗi ở đây sẽ làm
     * chết nguyên trang đăng nhập.
     */
    @ModelAttribute("headerUnreadNotificationCount")
    public int unreadCount(HttpSession session) {
        AuthenticatedUser user = getUser(session);
        if (user == null) {
            return 0;
        }
        return notificationService.unreadCount(user.getId());
    }

    /**
     * Danh sách thông báo gần đây, đổ vào dropdown khi bấm vào chuông.
     *
     * <p>Đây chỉ là danh sách rút gọn cho dropdown. Trang xem tất cả thông báo
     * không dùng dữ liệu này mà tự chạy query riêng của nó, vì hai chỗ cần số
     * lượng bản ghi khác nhau.
     */
    @ModelAttribute("headerNotifications")
    public List<NotificationItem> latestNotifications(HttpSession session) {
        AuthenticatedUser user = getUser(session);
        if (user == null) {
            return java.util.Collections.emptyList();
        }
        return notificationService.listByUser(user.getId());
    }

    /**
     * Lấy user từ session, trả null nếu chưa đăng nhập.
     *
     * <p>Cố ý KHÔNG dùng {@code SessionUserUtil.requireUser} ở đây, dù cùng đọc
     * một key AUTH_USER. Hàm kia thiết kế để ném lỗi khi không có user — đúng cho
     * controller (không đăng nhập thì chặn luôn), nhưng sai cho advice này: nó
     * chạy trên cả trang công khai, nơi "chưa đăng nhập" là chuyện bình thường
     * chứ không phải lỗi. Ở đây cần trả null để hiển thị chuông rỗng.
     */
    private AuthenticatedUser getUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object raw = session.getAttribute("AUTH_USER");
        // Kiểm tra kiểu trước khi ép, tránh ClassCastException nếu key bị ghi đè.
        if (!(raw instanceof AuthenticatedUser)) {
            return null;
        }
        return (AuthenticatedUser) raw;
    }
}
