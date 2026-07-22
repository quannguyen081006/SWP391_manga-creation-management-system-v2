package manga.common.util;

import manga.model.AuthenticatedUser;
import javax.servlet.http.HttpSession;

/**
 * Hai hàm chốt chặn dùng ở đầu các method trong controller.
 *
 * <p><b>AuthInterceptor chặn rồi, sao còn cần class này nữa?</b> Nhìn qua thì
 * thấy trùng việc, nhưng thực ra hai chỗ làm hai mức độ khác nhau:
 * <ul>
 *   <li>Interceptor chặn theo <b>đường dẫn</b>. Nó chỉ biết "URL này role nào được
 *       vào", vì lúc đó request còn chưa vào controller, chưa biết user đang thao
 *       tác trên bản ghi nào.</li>
 *   <li>Class này chặn theo <b>hành động cụ thể</b>. Nhiều chức năng khác nhau
 *       dùng chung một tiền tố URL, ví dụ mọi thứ dưới {@code /main/proposals}
 *       đều lọt qua interceptor bằng cùng một luật, nhưng "xem đề xuất" và "duyệt
 *       đề xuất" rõ ràng không thể cùng quyền.</li>
 * </ul>
 * Ngoài ra interceptor chỉ có đúng một luật cho API ({@code /api/v1/users}), nên
 * phần lớn API còn lại phải tự gác bằng chính hai hàm này.
 *
 * <p>Nói ngắn gọn: interceptor lọc thô ở ngoài cổng, class này kiểm tra kỹ ở
 * từng phòng bên trong. Bỏ lớp nào cũng hở.
 */
public final class SessionUserUtil {

    private SessionUserUtil() {
    }

    /**
     * Lấy user đang đăng nhập từ session, không có thì dừng luôn.
     *
     * <p>Viết kiểu "require" (trả về user hoặc ném lỗi) thay vì trả về null, để
     * bên gọi không thể lỡ quên kiểm tra null rồi vô tình chạy tiếp với user rỗng.
     * Muốn dùng được user thì buộc phải đi qua cửa này.
     *
     * <p>Điều kiện kiểm tra gồm cả {@code instanceof}, không chỉ khác null: session
     * là kho key–value chung, về lý thuyết chỗ khác có thể ghi đè key AUTH_USER
     * bằng kiểu dữ liệu khác. Kiểm tra kiểu trước khi ép giúp tránh ClassCastException.
     */
    public static AuthenticatedUser requireUser(HttpSession session) {
        Object auth = session == null ? null : session.getAttribute("AUTH_USER");
        if (auth == null || !(auth instanceof AuthenticatedUser)) {
            throw new IllegalStateException("Unauthorized");
        }
        return (AuthenticatedUser) auth;
    }

    /**
     * Bắt buộc user phải có một role cụ thể mới cho làm tiếp.
     *
     * <p>Gọi sau {@link #requireUser}: hàm kia trả lời "đã đăng nhập chưa",
     * hàm này trả lời "có đúng quyền không". Tách hai câu hỏi để lỗi trả về đúng
     * bản chất, giống cách AuthInterceptor phân biệt 401 với 403.
     *
     * @param message câu báo lỗi truyền từ bên gọi, vì mỗi chức năng cần một câu
     *                khác nhau ("Chỉ Mangaka mới được tạo bản thảo"...) — để ở đây
     *                một câu chung chung thì user đọc không hiểu mình thiếu gì.
     */
    public static void requireRole(AuthenticatedUser user, String role, String message) {
        if (user == null || !user.hasRole(role)) {
            throw new IllegalArgumentException(message);
        }
    }
}

