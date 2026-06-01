package manga.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AuthenticatedUser {
    private long id;
    private String username;
    private String fullName;
    private String passwordHash;
    private String status;
    private final Set<String> roles = new HashSet<String>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void addRole(String role) {
        if (role != null) {
            roles.add(role.trim().toUpperCase());
        }
    }

    public Set<String> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public boolean hasRole(String role) {
        return role != null && roles.contains(role.toUpperCase());
    }
}

