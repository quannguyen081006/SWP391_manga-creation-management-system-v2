package manga.web;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Giữ đúng MỘT phiên đăng nhập hợp lệ cho mỗi tài khoản (single active session).
 *
 * <p><b>Yêu cầu:</b> không cho hai người dùng chung một tài khoản đăng nhập cùng
 * lúc. Khi một máy đăng nhập lần nữa vào cùng username, phiên cũ ở máy kia phải
 * bị "đá" ra — lần request kế tiếp của máy cũ sẽ bị đẩy về trang login.
 *
 * <p><b>Cách làm:</b> lưu bản đồ {@code userId -> sessionId đang được coi là hợp
 * lệ}. Mỗi lần đăng nhập thành công, ta ghi đè sessionId mới vào đây. Từ giờ chỉ
 * còn sessionId mới là "chủ" của tài khoản; mọi sessionId khác (kể cả phiên cũ
 * vẫn còn sống ở server) đều bị coi là hết hiệu lực.
 *
 * <p><b>Vì sao không cần đụng tới cơ chế session của Tomcat?</b> Phiên cũ vẫn tồn
 * tại trong Tomcat, nhưng {@code AuthInterceptor} sẽ hỏi registry này ở mỗi
 * request: "sessionId của anh còn là phiên hợp lệ của tài khoản không?". Nếu
 * không, interceptor tự huỷ session cũ đó và bắt đăng nhập lại. Nhờ vậy không
 * phải đi tìm và thao tác trực tiếp lên object HttpSession của máy khác.
 *
 * <p><b>Phạm vi:</b> bản đồ nằm trong bộ nhớ của một tiến trình. App đang chạy
 * một instance trên Render nên đủ dùng. Nếu sau này chạy nhiều instance sau load
 * balancer, cần chuyển chỗ lưu này sang nơi dùng chung (DB/Redis) thì luật mới
 * còn đúng trên toàn cụm.
 */
@Component
public class ActiveSessionRegistry {

    /** userId -> sessionId đang được coi là phiên hợp lệ duy nhất của tài khoản đó. */
    private final ConcurrentMap<Long, String> activeSessionByUser = new ConcurrentHashMap<Long, String>();

    /**
     * Đánh dấu {@code sessionId} là phiên hợp lệ mới của {@code userId}, đẩy phiên
     * cũ (nếu có) ra khỏi vị trí hợp lệ. Gọi ngay sau khi đăng nhập thành công.
     */
    public void register(long userId, String sessionId) {
        activeSessionByUser.put(userId, sessionId);
    }

    /**
     * Kiểm tra {@code sessionId} có phải phiên hợp lệ hiện tại của {@code userId}.
     *
     * <p>Trường hợp registry chưa có mục nào cho userId (ví dụ ngay sau khi server
     * khởi động lại trong khi session cũ vẫn còn sống): thay vì đá tất cả mọi
     * người ra, ta nhận session đầu tiên gặp lại làm chủ hợp lệ và trả về true.
     * Đây là lựa chọn cân bằng — không gây phiền toái khi restart, và ràng buộc
     * "một phiên" vẫn được tái lập ngay từ lần đăng nhập kế tiếp.
     */
    public boolean isCurrent(long userId, String sessionId) {
        if (sessionId == null) {
            return false;
        }
        String current = activeSessionByUser.putIfAbsent(userId, sessionId);
        return current == null || current.equals(sessionId);
    }

    /**
     * Gỡ bỏ phiên khi người dùng tự đăng xuất. Chỉ gỡ khi {@code sessionId} đúng là
     * phiên đang giữ chỗ, để một lần logout không vô tình xoá phiên mới hơn vừa
     * được đăng nhập ở máy khác.
     */
    public void unregister(long userId, String sessionId) {
        if (sessionId != null) {
            activeSessionByUser.remove(userId, sessionId);
        }
    }
}
