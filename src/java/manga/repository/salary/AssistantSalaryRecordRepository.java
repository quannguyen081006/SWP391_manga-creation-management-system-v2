package manga.repository.salary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import manga.model.salary.AssistantSalaryRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AssistantSalaryRecordRepository {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Autowired
    private DataSource dataSource;

    public boolean existsForPeriod(long periodId) {
        String sql = "SELECT COUNT(1) FROM AssistantSalaryRecord WHERE periodId = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check salary records", ex);
        }
    }

    public List<Map<String, Object>> findByPeriodId(long periodId) {
        String sql = "SELECT r.id, r.periodId, r.assistantId, u.fullName AS assistantName, "
                + "r.totalTasksApproved, r.totalPagesCompleted, r.onTimeRate, r.kpiScore, "
                + "r.grossSalary, r.bonus, r.deduction, r.netSalary, r.calculatedAt "
                + "FROM AssistantSalaryRecord r "
                + "JOIN [User] u ON u.id = r.assistantId "
                + "WHERE r.periodId = ? ORDER BY u.fullName ASC";
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("id", rs.getLong("id"));
                    row.put("periodId", rs.getLong("periodId"));
                    row.put("assistantId", rs.getLong("assistantId"));
                    row.put("assistantName", rs.getString("assistantName"));
                    row.put("totalTasksApproved", rs.getInt("totalTasksApproved"));
                    row.put("totalPagesCompleted", rs.getInt("totalPagesCompleted"));
                    row.put("onTimeRate", rs.getBigDecimal("onTimeRate"));
                    row.put("kpiScore", rs.getBigDecimal("kpiScore"));
                    row.put("grossSalary", rs.getBigDecimal("grossSalary"));
                    row.put("bonus", rs.getBigDecimal("bonus"));
                    row.put("deduction", rs.getBigDecimal("deduction"));
                    row.put("netSalary", rs.getBigDecimal("netSalary"));
                    row.put("calculatedAt", rs.getTimestamp("calculatedAt"));
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load assistant salary records", ex);
        }
        return rows;
    }

    public List<AssistantSalaryRecord> calculatePreview(long periodId, long mangakaId,
            int kpiOnTimeWeight, int kpiQualityWeight) {
        String sql = "WITH ApprovedTasks AS ("
                + "SELECT t.id, t.assistantId, t.pageRangeStart, t.pageRangeEnd, t.dueDate, "
                + "t.updatedAt, t.rejectionCount FROM PageTask t "
                + "JOIN Chapter c ON c.id = t.chapterId "
                + "JOIN Series s ON s.id = c.seriesId "
                + "WHERE UPPER(t.status) = 'APPROVED' AND t.isSalaried = 0 "
                + "AND s.mangakaId = ?"
                + "), TaskMetrics AS ("
                + "SELECT assistantId, COUNT(id) AS totalTasksApproved, "
                + "SUM(CASE WHEN updatedAt <= DATEADD(DAY, 1, CAST(dueDate AS DATETIME)) THEN 1 ELSE 0 END) AS onTimeTasks, "
                + "SUM(CASE WHEN rejectionCount > 0 THEN 1 ELSE 0 END) AS rejectedTasks "
                + "FROM ApprovedTasks GROUP BY assistantId"
                + "), PageMetrics AS ("
                + "SELECT t.assistantId, COUNT(pps.pageNumber) AS totalPagesCompleted "
                + "FROM ApprovedTasks t "
                + "JOIN PageTaskPageStage pps ON pps.taskId = t.id "
                + "GROUP BY t.assistantId"
                + "), SalaryTotals AS ("
                + "SELECT t.assistantId, SUM(COALESCE(tt.ratePerPage, 0)) AS grossSalary "
                + "FROM ApprovedTasks t "
                + "JOIN PageTaskPageStage pps ON pps.taskId = t.id "
                + "JOIN TaskType tt ON tt.code = pps.taskTypeCode "
                + "GROUP BY t.assistantId"
                + ") SELECT ma.assistantId, COALESCE(tm.totalTasksApproved, 0) AS totalTasksApproved, "
                + "COALESCE(pm.totalPagesCompleted, 0) AS totalPagesCompleted, "
                + "COALESCE(st.grossSalary, 0) AS grossSalary, "
                + "CAST(COALESCE(100.0 * tm.onTimeTasks / NULLIF(tm.totalTasksApproved, 0), 0) "
                + "AS DECIMAL(5,2)) AS onTimeRate, COALESCE(tm.rejectedTasks, 0) AS rejectedTasks "
                + "FROM MangakaAssistant ma "
                + "JOIN TaskMetrics tm ON tm.assistantId = ma.assistantId "
                + "JOIN PageMetrics pm ON pm.assistantId = ma.assistantId "
                + "JOIN SalaryTotals st ON st.assistantId = ma.assistantId "
                + "WHERE ma.mangakaId = ?";
        List<AssistantSalaryRecord> rows = new ArrayList<AssistantSalaryRecord>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, mangakaId);
            ps.setLong(2, mangakaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int totalTasks = rs.getInt("totalTasksApproved");
                    BigDecimal onTimeRate = totalTasks == 0 ? ZERO : rs.getBigDecimal("onTimeRate");
                    int rejectedTasks = rs.getInt("rejectedTasks");
                    BigDecimal rejectionRatio = totalTasks == 0
                            ? ZERO
                            : new BigDecimal(rejectedTasks).multiply(new BigDecimal("100"))
                                    .divide(new BigDecimal(totalTasks), 6, RoundingMode.HALF_UP);
                    BigDecimal wOnTime = new BigDecimal(kpiOnTimeWeight)
                            .divide(new BigDecimal("100"));
                    BigDecimal wQuality = new BigDecimal(kpiQualityWeight)
                            .divide(new BigDecimal("100"));
                    BigDecimal kpiScore = totalTasks == 0
                            ? ZERO
                            : onTimeRate.multiply(wOnTime)
                                    .add(new BigDecimal("100").subtract(rejectionRatio)
                                            .multiply(wQuality))
                                    .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal grossSalary = rs.getBigDecimal("grossSalary");

                    AssistantSalaryRecord row = new AssistantSalaryRecord();
                    row.setAssistantId(rs.getLong("assistantId"));
                    row.setTotalTasksApproved(totalTasks);
                    row.setTotalPagesCompleted(rs.getInt("totalPagesCompleted"));
                    row.setOnTimeRate(onTimeRate == null ? ZERO : onTimeRate);
                    row.setKpiScore(kpiScore);
                    row.setGrossSalary(grossSalary == null ? ZERO : grossSalary);
                    row.setBonus(ZERO);
                    row.setDeduction(ZERO);
                    row.setNetSalary(grossSalary == null ? ZERO : grossSalary);
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot calculate salary preview", ex);
        }
        return rows;
    }

    public void upsertCalculated(long periodId, List<AssistantSalaryRecord> rows) {
        String updateSql = "UPDATE AssistantSalaryRecord SET totalTasksApproved = ?, "
                + "totalPagesCompleted = ?, onTimeRate = ?, kpiScore = ?, grossSalary = ?, "
                + "bonus = ?, deduction = ?, netSalary = ?, calculatedAt = GETDATE() "
                + "WHERE periodId = ? AND assistantId = ?";
        String insertSql = "INSERT INTO AssistantSalaryRecord "
                + "(periodId, assistantId, totalTasksApproved, totalPagesCompleted, onTimeRate, "
                + "kpiScore, grossSalary, bonus, deduction, netSalary, calculatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE())";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement update = conn.prepareStatement(updateSql);
                    PreparedStatement insert = conn.prepareStatement(insertSql)) {
                for (AssistantSalaryRecord row : rows) {
                    update.setInt(1, row.getTotalTasksApproved());
                    update.setInt(2, row.getTotalPagesCompleted());
                    update.setBigDecimal(3, row.getOnTimeRate());
                    update.setBigDecimal(4, row.getKpiScore());
                    update.setBigDecimal(5, row.getGrossSalary());
                    update.setBigDecimal(6, row.getBonus());
                    update.setBigDecimal(7, row.getDeduction());
                    update.setBigDecimal(8, row.getGrossSalary()
                            .add(row.getBonus()).subtract(row.getDeduction()));
                    update.setLong(9, periodId);
                    update.setLong(10, row.getAssistantId());
                    if (update.executeUpdate() == 0) {
                        insert.setLong(1, periodId);
                        insert.setLong(2, row.getAssistantId());
                        insert.setInt(3, row.getTotalTasksApproved());
                        insert.setInt(4, row.getTotalPagesCompleted());
                        insert.setBigDecimal(5, row.getOnTimeRate());
                        insert.setBigDecimal(6, row.getKpiScore());
                        insert.setBigDecimal(7, row.getGrossSalary());
                        insert.setBigDecimal(8, row.getBonus());
                        insert.setBigDecimal(9, row.getDeduction());
                        insert.setBigDecimal(10, row.getGrossSalary()
                                .add(row.getBonus()).subtract(row.getDeduction()));
                        insert.executeUpdate();
                    }
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot save calculated salary records", ex);
        }
    }

    public List<Long> findAssistantIdsByPeriod(long periodId) {
        String sql = "SELECT assistantId FROM AssistantSalaryRecord "
                + "WHERE periodId = ? AND totalTasksApproved > 0";
        List<Long> ids = new ArrayList<Long>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list salary period assistants", ex);
        }
        return ids;
    }

    public int markPeriodTasksSalaried(long periodId) {
        String sql = "UPDATE PageTask SET isSalaried = 1 "
                + "WHERE UPPER(status) = 'APPROVED' AND isSalaried = 0 "
                + "AND assistantId IN ("
                + "SELECT assistantId FROM AssistantSalaryRecord WHERE periodId = ?)";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot mark salary period tasks as salaried", ex);
        }
    }

    public List<AssistantSalaryRecord> findSettledByAssistant(long assistantId) {
        String sql = "SELECT r.periodId, sp.name AS periodName, r.assistantId, "
                + "r.totalTasksApproved, r.totalPagesCompleted, r.onTimeRate, r.kpiScore, "
                + "r.grossSalary, r.bonus, r.deduction, r.netSalary "
                + "FROM AssistantSalaryRecord r "
                + "JOIN SalaryPeriod sp ON sp.id = r.periodId "
                + "WHERE r.assistantId = ? AND sp.status = 'SETTLED' "
                + "ORDER BY sp.settledAt DESC, sp.id DESC";
        List<AssistantSalaryRecord> rows = new ArrayList<AssistantSalaryRecord>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, assistantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AssistantSalaryRecord row = new AssistantSalaryRecord();
                    row.setPeriodId(rs.getLong("periodId"));
                    row.setPeriodName(rs.getString("periodName"));
                    row.setAssistantId(rs.getLong("assistantId"));
                    row.setTotalTasksApproved(rs.getInt("totalTasksApproved"));
                    row.setTotalPagesCompleted(rs.getInt("totalPagesCompleted"));
                    row.setOnTimeRate(rs.getBigDecimal("onTimeRate"));
                    row.setKpiScore(rs.getBigDecimal("kpiScore"));
                    row.setGrossSalary(rs.getBigDecimal("grossSalary"));
                    row.setBonus(rs.getBigDecimal("bonus"));
                    row.setDeduction(rs.getBigDecimal("deduction"));
                    row.setNetSalary(rs.getBigDecimal("netSalary"));
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load assistant salary history", ex);
        }
        return rows;
    }
}
