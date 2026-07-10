package manga.service;

import manga.model.AuthenticatedUser;
import manga.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Authenticates an active account and returns the session user model.
     * Password comparison intentionally keeps the existing plain passwordHash
     * behavior because changing password storage is outside this cleanup.
     */
    public AuthenticatedUser login(String username, String password) {
        AuthenticatedUser user = userRepository.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("Username or password is incorrect");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("This account is inactive");
        }

        // Current project stores the plain password value in passwordHash.
        if (password == null || !password.equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("Username or password is incorrect");
        }

        return user;
    }
}
