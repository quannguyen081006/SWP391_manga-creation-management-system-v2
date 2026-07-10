package manga.repository;

import manga.common.util.RoleCombinationValidator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class UserAdminRepository {

    @Autowired
    private DataSource dataSource;

    /**
     * Lists users with their roles for the admin table.
     */
    public List<Map<String, Object>> listUsers() {
        String sql = "SELECT id, username, fullName, email, avatarUrl, status, createdAt, updatedAt FROM [User] ORDER BY id";
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = toMap(rs);
                row.put("roles", listRoles(conn, rs.getLong("id")));
                rows.add(row);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list users", ex);
        }
        return rows;
    }

    /**
     * Loads one user and its roles for edit/detail views.
     */
    public Map<String, Object> getUser(long id) {
        String sql = "SELECT id, username, fullName, email, avatarUrl, status, createdAt, updatedAt FROM [User] WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> row = toMap(rs);
                row.put("roles", listRoles(conn, id));
                return row;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load user", ex);
        }
    }

    /**
     * Creates the account row after validating required fields and unique username/email.
     * These checks live here as the authority because this method writes to the database.
     */
    public long createUser(String username, String passwordHash, String fullName, String email) {
        validateUserFields(username, passwordHash, fullName, email);
        String sql = "INSERT INTO [User] (username, passwordHash, fullName, email, status, createdAt, updatedAt) VALUES (?, ?, ?, ?, 'ACTIVE', GETDATE(), GETDATE())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            String normalizedUsername = username.trim();
            String normalizedEmail = email.trim();
            // BR-SYS: usernames and emails must stay unique for account management.
            if (existsByColumn(conn, "username", normalizedUsername)) {
                throw new IllegalArgumentException("Username already exists");
            }
            if (existsByColumn(conn, "email", normalizedEmail)) {
                throw new IllegalArgumentException("Email already exists");
            }
            ps.setString(1, normalizedUsername);
            ps.setString(2, passwordHash);
            ps.setString(3, fullName.trim());
            ps.setString(4, normalizedEmail);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            throw new IllegalStateException("Cannot create user");
        } catch (SQLException ex) {
            throwDuplicateUserMessage(ex);
            throw new RuntimeException("Cannot create user", ex);
        }
    }

    /**
     * Updates editable profile fields only. Username is not changed after creation
     * because it is treated as the account identity.
     */
    public void updateUser(long id, String fullName, String email) {
        if (isBlank(fullName) || isBlank(email) || !email.contains("@")) {
            throw new IllegalArgumentException("Full name and valid email are required");
        }
        String normalizedEmail = email.trim();
        String sql = "UPDATE [User] SET fullName = ?, email = ?, updatedAt = GETDATE() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (existsByColumnExceptId(conn, "email", normalizedEmail, id)) {
                throw new IllegalArgumentException("Email already exists");
            }
            ps.setString(1, fullName.trim());
            ps.setString(2, normalizedEmail);
            ps.setLong(3, id);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("User not found");
            }
        } catch (SQLException ex) {
            throwDuplicateUserMessage(ex);
            throw new RuntimeException("Cannot update user", ex);
        }
    }

    /**
     * Updates account status while protecting the final active ADMIN account.
     */
    public void updateStatus(long id, String status) {
        String normalized = normalizeStatus(status);
        String sql = "UPDATE [User] SET status = ?, updatedAt = GETDATE() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // BR-SYS-01: the system must always keep at least one active ADMIN.
            if ("INACTIVE".equals(normalized)
                    && userHasRole(conn, id, "ADMIN")
                    && countActiveUsersWithRole(conn, "ADMIN") <= 1) {
                throw new IllegalArgumentException("The only ADMIN account cannot be deactivated");
            }
            ps.setString(1, normalized);
            ps.setLong(2, id);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("User not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update status", ex);
        }
    }

    /**
     * Adds a role after enforcing ADMIN singleton and valid role combinations.
     * The repository is the real guard because UI checks can be bypassed.
     */
    public void addRole(long userId, String roleName) {
        String normalizedRole = normalizeRole(roleName);
        String roleSql = "SELECT id FROM [Role] WHERE name = ?";
        String existsSql = "SELECT 1 FROM UserRole WHERE userId = ? AND roleId = ?";
        String insertSql = "INSERT INTO UserRole (userId, roleId) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement rolePs = conn.prepareStatement(roleSql)) {
            ensureAdminRoleCanBeAssigned(conn, userId, normalizedRole);
            List<String> nextRoles = new ArrayList<String>(listRoles(conn, userId));
            nextRoles.add(normalizedRole);
            // BR-SYS: only single Mangaka, single Assistant, or Tantou+Board is valid.
            RoleCombinationValidator.validate(nextRoles);
            rolePs.setString(1, normalizedRole);
            long roleId;
            try (ResultSet rs = rolePs.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Role not found");
                }
                roleId = rs.getLong(1);
            }
            try (PreparedStatement existsPs = conn.prepareStatement(existsSql)) {
                existsPs.setLong(1, userId);
                existsPs.setLong(2, roleId);
                try (ResultSet rs = existsPs.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                insertPs.setLong(1, userId);
                insertPs.setLong(2, roleId);
                insertPs.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot add role", ex);
        }
    }

    /**
     * Removes a role while preventing the system from losing its only ADMIN role.
     */
    public void removeRole(long userId, String roleName) {
        String normalizedRole = normalizeRole(roleName);
        String sql = "DELETE ur FROM UserRole ur JOIN [Role] r ON ur.roleId = r.id WHERE ur.userId = ? AND r.name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // BR-SYS-01: never allow removing the only ADMIN role.
            if ("ADMIN".equals(normalizedRole)
                    && userHasRole(conn, userId, "ADMIN")
                    && countUsersWithRole(conn, "ADMIN") <= 1) {
                throw new IllegalArgumentException("The only ADMIN role cannot be removed");
            }
            ps.setLong(1, userId);
            ps.setString(2, normalizedRole);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot remove role", ex);
        }
    }

    /**
     * Returns role names for one user, used by the list page and role validation.
     */
    public List<String> listRoles(long userId) {
        try (Connection conn = dataSource.getConnection()) {
            return listRoles(conn, userId);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list user roles", ex);
        }
    }

    /**
     * Checks whether any ADMIN role already exists; used for early UI/controller exits.
     */
    public boolean hasAnyAdmin() {
        return countUsersWithRole("ADMIN") > 0;
    }

        public void updateProfile(long userId, String fullName, String email, String avatarUrl) {
        if (isBlank(fullName) || !isValidEmail(email)) {
            throw new IllegalArgumentException("Full name and valid email are required");
        }
        String normalizedEmail = email.trim();
        String sql = "UPDATE [User] SET fullName = ?, email = ?, avatarUrl = ?, updatedAt = GETDATE() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (existsByColumnExceptId(conn, "email", normalizedEmail, userId)) {
                throw new IllegalArgumentException("Email already exists");
            }
            ps.setString(1, fullName.trim());
            ps.setString(2, normalizedEmail);
            ps.setString(3, avatarUrl);
            ps.setLong(4, userId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("User not found");
            }
        } catch (SQLException ex) {
            throwDuplicateUserMessage(ex);
            throw new RuntimeException("Cannot update profile", ex);
        }
    }

        public void updatePassword(long userId, String newPasswordHash) {
        if (isBlank(newPasswordHash) || newPasswordHash.length() < 5) {
            throw new IllegalArgumentException("Password must be at least 5 characters");
        }
        String sql = "UPDATE [User] SET passwordHash = ?, updatedAt = GETDATE() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setLong(2, userId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("User not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot update password", ex);
        }
    }

        public String getPasswordHash(long userId) {
        String sql = "SELECT passwordHash FROM [User] WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("passwordHash") : null;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load password", ex);
        }
    }

        public boolean emailExistsExcludingUser(String email, long userId) {
        if (isBlank(email)) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            return existsByColumnExceptId(conn, "email", email.trim(), userId);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check email", ex);
        }
    }

    /**
     * Checks whether a user already has a role before role assignment validation.
     */
    public boolean hasRole(long userId, String roleName) {
        String normalizedRole = normalizeRole(roleName);
        try (Connection conn = dataSource.getConnection()) {
            return userHasRole(conn, userId, normalizedRole);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check user role", ex);
        }
    }

    /**
     * Counts users by role for singleton ADMIN checks.
     */
    public int countUsersWithRole(String roleName) {
        String normalizedRole = normalizeRole(roleName);
        try (Connection conn = dataSource.getConnection()) {
            return countUsersWithRole(conn, normalizedRole);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot count users by role", ex);
        }
    }

    private List<String> listRoles(Connection conn, long userId) throws SQLException {
        String sql = "SELECT r.name FROM UserRole ur JOIN [Role] r ON ur.roleId = r.id WHERE ur.userId = ? ORDER BY r.id";
        List<String> roles = new ArrayList<String>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roles.add(rs.getString("name"));
                }
            }
        }
        return roles;
    }

    private void ensureAdminRoleCanBeAssigned(Connection conn, long userId, String normalizedRole) throws SQLException {
        if (!"ADMIN".equals(normalizedRole) || userHasRole(conn, userId, "ADMIN")) {
            return;
        }
        // ADMIN is singleton so there is always one clearly accountable system owner.
        if (countUsersWithRole(conn, "ADMIN") > 0) {
            throw new IllegalArgumentException("Only one ADMIN account is allowed");
        }
    }

    private boolean userHasRole(Connection conn, long userId, String roleName) throws SQLException {
        String sql = "SELECT 1 FROM UserRole ur JOIN [Role] r ON ur.roleId = r.id WHERE ur.userId = ? AND r.name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int countUsersWithRole(Connection conn, String roleName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM UserRole ur JOIN [Role] r ON ur.roleId = r.id WHERE r.name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int countActiveUsersWithRole(Connection conn, String roleName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM UserRole ur JOIN [Role] r ON ur.roleId = r.id JOIN [User] u ON ur.userId = u.id WHERE r.name = ? AND u.status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalized) && !"INACTIVE".equals(normalized)) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        return normalized;
    }

    private String normalizeRole(String roleName) {
        String normalized = roleName == null ? "" : roleName.trim().toUpperCase();
        // Reject unknown roles before touching UserRole.
        if (!"ADMIN".equals(normalized)
                && !"MANGAKA".equals(normalized)
                && !"ASSISTANT".equals(normalized)
                && !"TANTOU_EDITOR".equals(normalized)
                && !"EDITORIAL_BOARD".equals(normalized)) {
            throw new IllegalArgumentException("Role is invalid");
        }
        return normalized;
    }

    private void validateUserFields(String username, String passwordHash, String fullName, String email) {
        if (isBlank(username) || isBlank(passwordHash) || isBlank(fullName) || isBlank(email)) {
            throw new IllegalArgumentException("Username, password, full name, and email are required");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Email is invalid");
        }
        if (passwordHash.length() < 5) {
            throw new IllegalArgumentException("Password must be at least 5 characters");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isValidEmail(String email) {
        return !isBlank(email) && email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private void throwDuplicateUserMessage(SQLException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("uq_user_username")) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (message.contains("uq_user_email")) {
            throw new IllegalArgumentException("Email already exists");
        }
    }

    private boolean existsByColumn(Connection conn, String column, String value) throws SQLException {
        String sql = "SELECT 1 FROM [User] WHERE " + column + " = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean existsByColumnExceptId(Connection conn, String column, String value, long excludedId) throws SQLException {
        String sql = "SELECT 1 FROM [User] WHERE " + column + " = ? AND id <> ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setLong(2, excludedId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Map<String, Object> toMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("id", rs.getLong("id"));
        row.put("username", rs.getString("username"));
        row.put("fullName", rs.getString("fullName"));
        row.put("email", rs.getString("email"));
        row.put("avatarUrl", rs.getString("avatarUrl"));
        row.put("status", rs.getString("status"));
        row.put("createdAt", rs.getTimestamp("createdAt"));
        row.put("updatedAt", rs.getTimestamp("updatedAt"));
        return row;
    }

        public String getFullNameById(long userId) {
        String sql = "SELECT fullName FROM [User] WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("fullName");
                }
                return null;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot get user full name", ex);
        }
    }
}

