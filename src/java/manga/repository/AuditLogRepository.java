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

    /**
     * Appends one general-purpose audit event.
     * AuditLog is shared infrastructure for modules such as decisions; unlike
     * ProposalHistory or ReviewDecision, it is not tied to one domain screen.
     * No JSP currently displays raw AuditLog rows; DB queries are the accepted
     * verification path for now.
     */
    public void insertLog(Long actorId, String action, String entityType, Long entityId, String detail) {
        String sql = "INSERT INTO AuditLog (actorId, action, entityType, entityId, detail, performedAt) VALUES (?, ?, ?, ?, ?, GETDATE())";
        try ( Connection conn = dataSource.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
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
            System.out.println("actorId=" + actorId);
            System.out.println("action=" + action);
            System.out.println("entityType=" + entityType);
            System.out.println("entityId=" + entityId);
            System.out.println("detail=" + detail);
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("SQL State: " + ex.getSQLState());
            System.err.println("Error Code: " + ex.getErrorCode());
            System.err.println("Message: " + ex.getMessage());

            ex.printStackTrace();

            throw new RuntimeException("Cannot insert audit log", ex);
        }
    }
}
