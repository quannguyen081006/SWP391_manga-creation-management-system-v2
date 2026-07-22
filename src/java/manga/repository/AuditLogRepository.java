package manga.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    private static final Logger LOGGER = Logger.getLogger(AuditLogRepository.class.getName());

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
            ps.executeUpdate();
        } catch (SQLException ex) {
            // The detail string is caller-supplied free text, so it is kept out of
            // the log message; the exception itself carries what is needed to
            // diagnose the failure.
            LOGGER.log(Level.SEVERE,
                    "Cannot insert audit log (action={0}, entityType={1}, entityId={2})",
                    new Object[]{action, entityType, entityId});
            LOGGER.log(Level.SEVERE, "Audit log insert failed", ex);
            throw new RuntimeException("Cannot insert audit log", ex);
        }
    }
}
