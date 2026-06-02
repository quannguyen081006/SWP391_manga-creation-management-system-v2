package manga.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    @Autowired
    private DataSource dataSource;

    public void insertLog(Long actorId, String action, String entityType, Long entityId, String detail) {
        String sql = "INSERT INTO AuditLog (actorId, action, entityType, entityId, detail, performedAt) VALUES (?, ?, ?, ?, ?, GETDATE())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (actorId == null) {
                ps.setNull(1, java.sql.Types.BIGINT);
            } else {
                ps.setLong(1, actorId);
            }
            ps.setString(2, action);
            ps.setString(3, entityType);
            if (entityId == null) {
                ps.setNull(4, java.sql.Types.BIGINT);
            } else {
                ps.setLong(4, entityId);
            }
            ps.setString(5, detail);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot insert audit log", ex);
        }
    }
}
