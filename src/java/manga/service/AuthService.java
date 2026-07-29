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
     * So khớp mật khẩu người dùng nhập với giá trị hash đang lưu trong DB.
     * Mọi tài khoản đều đã được migrate sang BCrypt (xem
     * database/migrate_plaintext_passwords.sql); không còn nhánh plaintext.
     */
    private boolean passwordMatches(AuthenticatedUser user, String password) {
        String stored = user.getPasswordHash();
        if (stored == null || !BCrypt.looksHashed(stored)) {
            return false;
        }
        return BCrypt.checkpw(password, stored);
    }
}
