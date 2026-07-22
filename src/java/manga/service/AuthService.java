package manga.service;

import manga.common.util.BCrypt;
import manga.model.AuthenticatedUser;
import manga.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

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
     * Verifies the submitted password against the stored value.
     * <p>
     * Passwords are stored as BCrypt hashes. Accounts created before that change
     * still hold the password as plain text, so those rows fall back to a direct
     * comparison and are re-stored as a BCrypt hash immediately on the next
     * successful login. This lets an existing database migrate itself without a
     * manual SQL re-seed, and the legacy branch stops being reachable for an
     * account once it has logged in once.
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
     * Replaces a legacy plaintext password with a BCrypt hash. A failure here must
     * not block a login that already succeeded, so the account simply stays on the
     * legacy value and is retried at the next login.
     */
    private void upgradeLegacyPassword(AuthenticatedUser user, String password) {
        try {
            String hashed = BCrypt.hashpw(password);
            userRepository.updatePasswordHash(user.getId(), hashed);
            user.setPasswordHash(hashed);
        } catch (RuntimeException ignored) {
            // Keep the legacy value; the next successful login retries the upgrade.
        }
    }
}
