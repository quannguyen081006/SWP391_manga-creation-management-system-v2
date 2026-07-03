package manga.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class SystemSettingRepository {

    public static final String MAX_SUBMIT_ATTEMPTS = "proposal.maxSubmitAttempts";
    public static final String MINIMUM_VOTE_QUORUM = "proposal.minimumVoteQuorum";
    public static final String SALARY_KPI_BONUS_THRESHOLD = "salary.kpiBonusThreshold";
    public static final String SALARY_BONUS_PERCENT = "salary.bonusPercent";
    public static final String SALARY_PENALTY_PER_LATE_TASK = "salary.penaltyPerLateTask";
    public static final String SALARY_REJECTION_PENALTY_THRESHOLD = "salary.rejectionPenaltyThreshold";
    public static final String SALARY_PENALTY_PER_REJECTED_TASK = "salary.penaltyPerRejectedTask";
    public static final String SALARY_KPI_ON_TIME_WEIGHT = "salary.kpiOnTimeWeight";
    public static final String SALARY_KPI_QUALITY_WEIGHT = "salary.kpiQualityWeight";
    public static final String PAGE_TASK_PHASH_THRESHOLD = "pageTask.phashHammingThreshold";

    @Autowired
    private DataSource dataSource;

    private Boolean settingsTableReady;

    public int getInt(String key, int defaultValue) {
        try (Connection conn = dataSource.getConnection()) {
            ensureSettingsTable(conn);
            String sql = "SELECT settingValue FROM SystemSetting WHERE settingKey = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return defaultValue;
                    }
                    String value = rs.getString(1);
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        return defaultValue;
                    }
                }
            }
        } catch (SQLException ex) {
            return defaultValue;
        }
    }

    public void setInt(String key, int value) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ensureSettingsTable(conn);
                upsertValue(conn, key, String.valueOf(value));
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot save system setting", ex);
        }
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        try (Connection conn = dataSource.getConnection()) {
            ensureSettingsTable(conn);
            String sql = "SELECT settingValue FROM SystemSetting WHERE settingKey = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return defaultValue;
                    }
                    try {
                        return new BigDecimal(rs.getString(1));
                    } catch (NumberFormatException ex) {
                        return defaultValue;
                    }
                }
            }
        } catch (SQLException ex) {
            return defaultValue;
        }
    }

    public void setDecimal(String key, BigDecimal value) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ensureSettingsTable(conn);
                upsertValue(conn, key, value.toPlainString());
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot save system setting", ex);
        }
    }

    public void setProposalSettings(int maxSubmitAttempts, int minimumVoteQuorum) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ensureSettingsTable(conn);
                ensureSubmitAttemptConstraint(conn, maxSubmitAttempts);
                upsertValue(conn, MAX_SUBMIT_ATTEMPTS, String.valueOf(maxSubmitAttempts));
                upsertValue(conn, MINIMUM_VOTE_QUORUM, String.valueOf(minimumVoteQuorum));
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot save proposal settings", ex);
        }
    }

    public void setSalarySettings(int kpiBonusThreshold, BigDecimal bonusPercent,
            BigDecimal penaltyPerLateTask, int rejectionPenaltyThreshold,
            BigDecimal penaltyPerRejectedTask, int kpiOnTimeWeight,
            int kpiQualityWeight) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ensureSettingsTable(conn);
                upsertValue(conn, SALARY_KPI_BONUS_THRESHOLD, String.valueOf(kpiBonusThreshold));
                upsertValue(conn, SALARY_BONUS_PERCENT, bonusPercent.toPlainString());
                upsertValue(conn, SALARY_PENALTY_PER_LATE_TASK, penaltyPerLateTask.toPlainString());
                upsertValue(conn, SALARY_REJECTION_PENALTY_THRESHOLD,
                        String.valueOf(rejectionPenaltyThreshold));
                upsertValue(conn, SALARY_PENALTY_PER_REJECTED_TASK,
                        penaltyPerRejectedTask.toPlainString());
                upsertValue(conn, SALARY_KPI_ON_TIME_WEIGHT,
                        String.valueOf(kpiOnTimeWeight));
                upsertValue(conn, SALARY_KPI_QUALITY_WEIGHT,
                        String.valueOf(kpiQualityWeight));
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot save salary settings", ex);
        }
    }

    private void upsertValue(Connection conn, String key, String value) throws SQLException {
        String updateSql = "UPDATE SystemSetting SET settingValue = ?, updatedAt = GETDATE() WHERE settingKey = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, value);
            ps.setString(2, key);
            if (ps.executeUpdate() > 0) {
                return;
            }
        }
        String insertSql = "INSERT INTO SystemSetting (settingKey, settingValue, updatedAt) VALUES (?, ?, GETDATE())";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private void ensureSettingsTable(Connection conn) throws SQLException {
        if (Boolean.TRUE.equals(settingsTableReady)) {
            return;
        }
        String sql = "IF OBJECT_ID('dbo.SystemSetting', 'U') IS NULL "
                + "CREATE TABLE dbo.SystemSetting ("
                + "settingKey varchar(100) NOT NULL PRIMARY KEY, "
                + "settingValue varchar(255) NOT NULL, "
                + "updatedAt datetime NOT NULL DEFAULT GETDATE())";
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
        settingsTableReady = Boolean.TRUE;
    }

    private void ensureSubmitAttemptConstraint(Connection conn, int maxSubmitAttempts) throws SQLException {
        int currentMax = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT ISNULL(MAX(submitAttemptCount), 0) FROM Proposal")) {
            if (rs.next()) {
                currentMax = rs.getInt(1);
            }
        }
        if (maxSubmitAttempts < currentMax) {
            throw new IllegalArgumentException("Max submit attempts cannot be lower than existing proposal attempts: " + currentMax);
        }
        String sql = "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Proposal_submitAttempts') "
                + "ALTER TABLE dbo.Proposal DROP CONSTRAINT CK_Proposal_submitAttempts";
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
            st.executeUpdate("ALTER TABLE dbo.Proposal WITH CHECK ADD CONSTRAINT CK_Proposal_submitAttempts "
                    + "CHECK (submitAttemptCount >= 0 AND submitAttemptCount <= " + maxSubmitAttempts + ")");
            st.executeUpdate("ALTER TABLE dbo.Proposal CHECK CONSTRAINT CK_Proposal_submitAttempts");
        }
    }
}
