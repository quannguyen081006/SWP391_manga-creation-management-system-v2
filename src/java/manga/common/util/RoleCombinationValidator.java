package manga.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kiểm tra luật nghiệp vụ khi gán role cho user.
 *
 * <p><b>Luật tóm tắt:</b> mỗi tài khoản chỉ được một role. Ngoại lệ duy nhất là
 * cặp TANTOU_EDITOR + EDITORIAL_BOARD được phép đi chung.
 *
 * <p><b>Vì sao cặp đó được phép?</b> Vì ngoài đời một biên tập viên phụ trách
 * (tantou) hoàn toàn có thể đồng thời ngồi trong hội đồng biên tập. Đó là hai
 * việc khác nhau của cùng một con người, không mâu thuẫn nhau.
 *
 * <p><b>Vì sao ADMIN không được ghép với gì cả?</b> ADMIN là vai trò quản trị hệ
 * thống, không tham gia quy trình sản xuất truyện. Cho ADMIN kiêm thêm role
 * nghiệp vụ sẽ tạo ra xung đột lợi ích: tự tạo dữ liệu rồi tự duyệt dữ liệu của
 * chính mình.
 *
 * <p><b>Vì sao là class static, không phải {@code @Service}?</b> Vì nó không cần
 * DB, không giữ trạng thái gì — chỉ nhận vào danh sách chuỗi rồi kết luận đúng/sai.
 * Để static thì gọi ở đâu cũng được mà không phải tiêm dependency.
 *
 * <p><b>Giới hạn của class này:</b> nó chỉ kiểm tra HÌNH DẠNG của tổ hợp role,
 * không kiểm tra role đó có tồn tại thật hay không. Truyền vào đúng một chuỗi
 * không có thật như "ABC" thì vẫn lọt qua vì rơi vào nhánh "chỉ có một role".
 * Việc đối chiếu với danh mục role hợp lệ là do
 * {@code UserAdminRepository.normalizeRole} làm ở tầng dưới.
 */
public final class RoleCombinationValidator {

    private static final Set<String> DUAL_ROLE_ALLOWED = new HashSet<String>(
            Arrays.asList("TANTOU_EDITOR", "EDITORIAL_BOARD"));

    // Constructor private để không ai lỡ tay tạo object của class tiện ích này.
    private RoleCombinationValidator() {
    }

    /**
     * Kiểm tra tổ hợp role trước khi lưu xuống DB.
     *
     * <p>Thứ tự các bước có chủ đích: chuẩn hoá dữ liệu trước, rồi mới xét luật.
     * Nếu xét luật trên dữ liệu thô thì chọn ["ADMIN", "admin"] sẽ bị hiểu nhầm
     * thành hai role khác nhau và báo lỗi sai.
     *
     * @param roles danh sách role người dùng chọn trên giao diện.
     * @throws IllegalArgumentException kèm câu thông báo hiển thị thẳng cho user.
     */
    public static void validate(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Select at least one role");
        }
        // BƯỚC 1 - Chuẩn hoá: bỏ khoảng trắng thừa, bỏ ô rỗng, viết hoa hết, và
        // loại trùng lặp. Form gửi lên có thể kèm giá trị rỗng hoặc trùng nhau.
        List<String> distinct = new ArrayList<String>();
        for (String role : roles) {
            if (role == null || role.trim().isEmpty()) {
                continue;
            }
            String normalized = role.trim().toUpperCase();
            if (!distinct.contains(normalized)) {
                distinct.add(normalized);
            }
        }

        // Sau khi lọc mà rỗng nghĩa là user chỉ gửi toàn ô trống -> vẫn là lỗi.
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("Select at least one role");
        }

        // BƯỚC 2 - Một role thì luôn hợp lệ ở tầng tổ hợp, thoát sớm.
        // Các luật bên dưới chỉ có ý nghĩa khi user chọn từ hai role trở lên.
        if (distinct.size() <= 1) {
            return;
        }

        // BƯỚC 3 - Từ đây trở xuống chắc chắn đang có nhiều role.
        // Mangaka và Assistant là vai trò chuyên trách, không kiêm nhiệm.
        if (distinct.contains("MANGAKA") || distinct.contains("ASSISTANT")) {
            throw new IllegalArgumentException(
                    "Mangaka and Assistant accounts must have a single role only");
        }
        // ADMIN đứng riêng, không trộn với role nghiệp vụ (tránh xung đột lợi ích).
        if (distinct.contains("ADMIN")) {
            throw new IllegalArgumentException("ADMIN cannot be combined with other roles");
        }
        // Chỉ cho tối đa 2 role, và phải đúng cặp editor/board.
        if (distinct.size() > 2) {
            throw new IllegalArgumentException(
                    "Only Tantou Editor and Editorial Board can hold dual roles");
        }
        // Cả hai role đều phải nằm trong danh sách được phép kiêm nhiệm.
        for (String role : distinct) {
            if (!DUAL_ROLE_ALLOWED.contains(role)) {
                throw new IllegalArgumentException(
                        "Only Tantou Editor and Editorial Board can hold dual roles");
            }
        }
        // Kiểm tra cuối này trông như thừa, nhưng không phải: vòng lặp trên chỉ
        // bảo đảm "không có role lạ", còn tổ hợp [TANTOU_EDITOR, TANTOU_EDITOR]
        // vẫn lọt qua được nếu khâu loại trùng ở trên có sai sót. Dòng này chốt
        // lại đúng yêu cầu cuối cùng: phải có ĐỦ CẢ HAI role, mỗi cái một lần.
        if (!distinct.contains("TANTOU_EDITOR") || !distinct.contains("EDITORIAL_BOARD")) {
            throw new IllegalArgumentException(
                    "Only Tantou Editor and Editorial Board can hold dual roles");
        }
    }
}
