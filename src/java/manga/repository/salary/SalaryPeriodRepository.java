package manga.repository.salary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class SalaryPeriodRepository {

    @Autowired
    private DataSource dataSource;

    public long createPeriod(long mangakaId, String name) {
        String sql = "INSERT INTO SalaryPeriod (mangakaId, name, status, createdAt) "
                + "VALUES (?, ?, 'OPEN', GETDATE())";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, mangakaId);
            ps.setString(2, name);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            throw new IllegalStateException("Cannot create salary period");
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create salary period", ex);
        }
    }

    public Long findOpenPeriodId(long mangakaId) {
        String sql = "SELECT TOP 1 id FROM SalaryPeriod "
                + "WHERE mangakaId = ? AND status = 'OPEN' ORDER BY createdAt DESC, id DESC";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, mangakaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Long.valueOf(rs.getLong("id")) : null;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot find open salary period", ex);
        }
    }

    /**
     * All Mangaka who currently have at least one assistant - the full set of Mangaka
     * the automatic monthly rotation must open/settle a salary period for. Using
     * MangakaAssistant (rather than "has approved tasks") means a period always exists
     * for every active Mangaka, ready to accumulate tasks as they get approved.
     */
    public List<Long> listMangakaIdsWithAssistants() {
        String sql = "SELECT DISTINCT mangakaId FROM MangakaAssistant";
        List<Long> ids = new ArrayList<Long>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list Mangakas with assistants", ex);
        }
        return ids;
    }

    public List<Map<String, Object>> listPeriodsByMangaka(long mangakaId) {
        String sql = "SELECT id, mangakaId, name, status, settledAt, createdAt "
                + "FROM SalaryPeriod WHERE mangakaId = ? ORDER BY createdAt DESC, id DESC";
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, mangakaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapPeriod(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list salary periods", ex);
        }
        return rows;
    }

    public Map<String, Object> findById(long periodId) {
        String sql = "SELECT id, mangakaId, name, status, settledAt, createdAt "
                + "FROM SalaryPeriod WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapPeriod(rs) : null;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot load salary period", ex);
        }
    }

    public void markSettled(long periodId) {
        String sql = "UPDATE SalaryPeriod SET status = 'SETTLED', settledAt = GETDATE() "
                + "WHERE id = ? AND status = 'OPEN'";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, periodId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("Salary period is no longer OPEN");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot settle salary period", ex);
        }
    }

    private Map<String, Object> mapPeriod(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("id", rs.getLong("id"));
        row.put("mangakaId", rs.getLong("mangakaId"));
        row.put("name", rs.getString("name"));
        row.put("status", rs.getString("status"));
        row.put("settledAt", rs.getTimestamp("settledAt"));
        row.put("createdAt", rs.getTimestamp("createdAt"));
        return row;
    }
}
