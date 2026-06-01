package manga.repository;

import manga.common.util.RoleCombinationValidator;
import manga.model.AuthenticatedUser;
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

    public List<Map<String, Object>> listUsers() {
        String sql = "SELECT id, username, fullName, email, status, createdAt, updatedAt FROM [User] ORDER BY id";
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

    public List<Map<String, Object>> listActiveUsersForSwitch() {
        String sql = "SELECT id, username, fullName, email, status, createdAt, updatedAt FROM [User] WHERE status = 'ACTIVE' ORDER BY id";
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = toMap(rs);
                row.put("roles", listRoles(conn, rs.getLong("id")));
                rows.add(row);
            }
            addSwitchItems(rows);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list switch users", ex);
        }
        return rows;
    }

    private void addSwitchItems(List<Map<String, Object>> rows) {
        Map<String, Integer> roleIndexes = new HashMap<String, Integer>();
        for (Map<String, Object> row : rows) {
            List<Map<String, Object>> switchItems = new ArrayList<Map<String, Object>>();
            Object rawRoles = row.get("roles");
            if (rawRoles instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) rawRoles;
                for (String role : roles) {
                    Integer current = roleIndexes.get(role);
                    int next = current == null ? 1 : current.intValue() + 1;
                    roleIndexes.put(role, Integer.valueOf(next));

                    Map<String, Object> item = new HashMap<String, Object>();
                    item.put("role", role);
                    item.put("label", roleDisplayLabel(role) + " " + next);
                    switchItems.add(item);
                }
            }
            row.put("switchItems", switchItems);
        }
    }

    private String roleDisplayLabel(String role) {
        if ("ADMIN".equals(role)) {
            return "Admin";
        }
        if ("MANGAKA".equals(role)) {
            return "Mangaka";
        }
        if ("ASSISTANT".equals(role)) {
            return "Assistant";
        }
        if ("TANTOU_EDITOR".equals(role)) {
            return "Tantou Editor";
        }
        if ("EDITORIAL_BOARD".equals(role)) {
            return "Board Member";
        }
        return role;
    }

    public Map<String, Object> getUser(long id) {
        String sql = "SELECT id, username, fullName, email, status, createdAt, updatedAt FROM [User] WHERE id = ?";
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

    public long createUser(String username, String passwordHash, String fullName, String email) {
        validateUserFields(username, passwordHash, fullName, email);
        String sql = "INSERT INTO [User] (username, passwordHash, fullName, email, status, createdAt, updatedAt) VALUES (?, ?, ?, ?, 'ACTIVE', GETDATE(), GETDATE())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            String normalizedUsername = username.trim();
            String normalizedEmail = email.trim();
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

    public void updateStatus(long id, String status) {
        String normalized = normalizeStatus(status);
        String sql = "UPDATE [User] SET status = ?, updatedAt = GETDATE() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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

    public void removeRole(long userId, String roleName) {
        String normalizedRole = normalizeRole(roleName);
        String sql = "DELETE ur FROM UserRole ur JOIN [Role] r ON ur.roleId = r.id WHERE ur.userId = ? AND r.name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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

    public List<String> listRoles(long userId) {
        try (Connection conn = dataSource.getConnection()) {
            return listRoles(conn, userId);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list user roles", ex);
        }
    }

    public List<Map<String, Object>> listRoleSwitchItems(long userId) {
        String sql =
            "WITH RankedRoles AS ("
            + "SELECT u.id AS userId, r.id AS roleId, r.name AS roleName, "
            + "ROW_NUMBER() OVER (PARTITION BY r.name ORDER BY u.id ASC) AS roleIndex "
            + "FROM [User] u "
            + "JOIN UserRole ur ON ur.userId = u.id "
            + "JOIN [Role] r ON r.id = ur.roleId "
            + "WHERE u.status = 'ACTIVE') "
            + "SELECT roleId, roleName, roleIndex FROM RankedRoles WHERE userId = ? ORDER BY roleId";
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String roleName = rs.getString("roleName");
                    int roleIndex = rs.getInt("roleIndex");
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("roleId", Long.valueOf(rs.getLong("roleId")));
                    row.put("roleName", roleName);
                    row.put("displayLabel", roleDisplayLabel(roleName) + " " + roleIndex);
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list current user roles", ex);
        }
        return rows;
    }

    public boolean hasAnyAdmin() {
        return countUsersWithRole("ADMIN") > 0;
    }

    public boolean hasRole(long userId, String roleName) {
        String normalizedRole = normalizeRole(roleName);
        try (Connection conn = dataSource.getConnection()) {
            return userHasRole(conn, userId, normalizedRole);
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check user role", ex);
        }
    }

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
        row.put("status", rs.getString("status"));
        row.put("createdAt", rs.getTimestamp("createdAt"));
        row.put("updatedAt", rs.getTimestamp("updatedAt"));
        return row;
    }

    /**
     * Get user full name by ID.
     * @param userId the user ID
     * @return the full name, or null if user not found
     */
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



