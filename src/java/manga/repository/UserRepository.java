package manga.repository;

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
public class UserRepository {

    @Autowired
    private DataSource dataSource;

    public AuthenticatedUser findByUsername(String username) {
        String sql = "SELECT id, username, passwordHash, fullName, status FROM [User] WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                AuthenticatedUser user = new AuthenticatedUser();
                user.setId(rs.getLong("id"));
                user.setUsername(rs.getString("username"));
                user.setPasswordHash(rs.getString("passwordHash"));
                user.setFullName(rs.getString("fullName"));
                user.setStatus(rs.getString("status"));
                loadRoles(conn, user);
                return user;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load user by username", ex);
        }
    }

    private void loadRoles(Connection conn, AuthenticatedUser user) throws SQLException {
        String sql = "SELECT r.name FROM UserRole ur JOIN [Role] r ON ur.roleId = r.id WHERE ur.userId = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    user.addRole(rs.getString("name"));
                }
            }
        }
    }

    public List<Map<String, Object>> findByRole(String roleName) {
        String sql = "SELECT u.id, u.username, u.fullName FROM [User] u " +
                     "JOIN UserRole ur ON u.id = ur.userId " +
                     "JOIN [Role] r ON ur.roleId = r.id " +
                     "WHERE r.name = ?";
        List<Map<String, Object>> users = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("id", rs.getLong("id"));
                    user.put("username", rs.getString("username"));
                    user.put("fullName", rs.getString("fullName"));
                    users.add(user);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find users by role", ex);
        }
        return users;
    }
}


