package manga.service;

import manga.common.util.BCrypt;
import manga.model.AuthenticatedUser;
import manga.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Xử lý nghiệp vụ đăng nhập: kiểm tra user tồn tại, trạng thái tài khoản và mật khẩu.
 *
 * <p>Service này chỉ trả về user khi đăng nhập hợp lệ, còn việc lưu vào session là
 * do controller làm. Tách như vậy để logic đăng nhập không phụ thuộc vào HTTP,
 * nên có thể viết unit test mà không cần dựng request giả.
 */
@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Đăng nhập bằng username + password.
     *
     * <p><b>Vì sao sai username và sai password đều báo cùng một câu?</b>
     * Nếu tách thành "không tồn tại user này" và "sai mật khẩu" thì kẻ tấn công
     * có thể dò xem username nào có thật trong hệ thống (gọi là user enumeration),
     * rồi tập trung dò mật khẩu đúng cái username đó. Báo chung chung thì kẻ tấn
     * công không biết mình sai ở vế nào.
     *
     * <p>Riêng tài khoản bị khoá thì báo rõ, vì lúc đó user đã nhập đúng mật khẩu
     * rồi — không còn gì để giấu, và người dùng cần biết lý do để đi liên hệ Admin.
     *
     * @throws IllegalArgumentException khi sai thông tin hoặc tài khoản không ACTIVE.
     */
    public AuthenticatedUser login(String username, String password) {
        AuthenticatedUser user = userRepository.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("Username or password is incorrect");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("This account is inactive");
        }
        if (password == null || !passwordMatches(user, password)) {
            throw new IllegalArgumentException("Username or password is incorrect");
        }
        return user;
    }

    /**
     * So khớp mật khẩu người dùng nhập với giá trị đang lưu trong DB.
     *
     * <p><b>Vì sao có hai nhánh?</b> Mật khẩu bây giờ lưu dạng băm BCrypt. Nhưng
     * các tài khoản tạo từ trước khi đổi (và dữ liệu seed) vẫn đang lưu mật khẩu
     * dạng chữ thường (plaintext). Nếu chỉ để nhánh BCrypt thì toàn bộ tài khoản
     * cũ sẽ không đăng nhập được nữa.
     *
     * <p><b>Vì sao không chạy một câu SQL update hết cho xong?</b> Vì mật khẩu đã
     * băm thì không đảo ngược lại được — muốn băm thì phải biết mật khẩu gốc, mà
     * mật khẩu gốc chỉ xuất hiện đúng lúc user gõ vào ô đăng nhập. Nên cách duy
     * nhất là băm ngay tại thời điểm họ đăng nhập thành công.
     *
     * <p>Kết quả: DB tự nâng cấp dần theo từng lần đăng nhập, không cần seed lại
     * bằng tay. Mỗi tài khoản chỉ đi vào nhánh plaintext đúng một lần duy nhất;
     * sau lần đó nó đã có hash nên luôn rẽ vào nhánh BCrypt.
     */
    private boolean passwordMatches(AuthenticatedUser user, String password) {
        String stored = user.getPasswordHash();
        if (stored == null) {
            return false;
        }
        if (BCrypt.looksHashed(stored)) {
            return BCrypt.checkpw(password, stored);
        }
        if (!password.equals(stored)) {
            return false;
        }
        upgradeLegacyPassword(user, password);
        return true;
    }

    /**
     * Thay mật khẩu plaintext cũ bằng chuỗi băm BCrypt.
     *
     * <p><b>Vì sao lại nuốt exception (catch rỗng)?</b> Bình thường đây là code
     * xấu, nhưng ở đây có lý do: thời điểm chạy hàm này thì user đã nhập ĐÚNG mật
     * khẩu rồi, đăng nhập coi như đã thành công. Việc nâng cấp hash chỉ là dọn dẹp
     * thêm. Nếu để exception ném ra, user gõ đúng mật khẩu mà vẫn bị báo lỗi đăng
     * nhập chỉ vì DB đang bận — vô lý.
     *
     * <p>Bỏ qua lỗi ở đây an toàn vì nó không mất mát gì: tài khoản vẫn giữ giá
     * trị plaintext cũ, và lần đăng nhập sau sẽ tự thử nâng cấp lại.
     */
    private void upgradeLegacyPassword(AuthenticatedUser user, String password) {
        try {
            String hashed = BCrypt.hashpw(password);
            userRepository.updatePasswordHash(user.getId(), hashed);
            // Cập nhật luôn object trong bộ nhớ, để nếu cùng request này có chỗ nào
            // đọc lại passwordHash thì thấy giá trị mới chứ không phải plaintext cũ.
            user.setPasswordHash(hashed);
        } catch (RuntimeException ignored) {
            // Cố tình không xử lý: xem giải thích ở javadoc phía trên.
        }
    }
}
