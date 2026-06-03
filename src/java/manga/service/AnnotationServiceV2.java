package manga.service;

import manga.common.exception.BusinessRuleException;
import manga.enums.AnnotationCategory;
import manga.enums.AnnotationSeverity;
import manga.enums.AnnotationStatus;
import manga.enums.ManuscriptStatus;
import manga.model.AuthenticatedUser;
import manga.model.Annotation;
import manga.repository.ChapterRepository;
import manga.repository.ManuscriptVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Enhanced Annotation Service for coordinate-based annotations.
 * 
 * Supports the new visual workspace workflow with:
 * - Coordinate anchoring (BR-8: responsive scaling)
 * - Thread/reply support
 * - Resolution states
 * - Severity levels
 */
@Service
@Transactional
public class AnnotationServiceV2 {

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private ManuscriptVersionRepository manuscriptVersionRepository;

    @Autowired
    private DataSource dataSource;

    /**
     * Add annotation with coordinates.
     * BR-5: Annotations belong to specific manuscript version
     * BR-8: Coordinates must be between 0-100
     */
    public long addAnnotation(Long manuscriptVersionId, Long editorId, Long manuscriptPageId,
                             AnnotationCategory category, AnnotationSeverity severity,
                             String content, Double xPercent, Double yPercent,
                             Double widthPercent, Double heightPercent,
                             Long parentAnnotationId, AuthenticatedUser user) {
        
        // Validate manuscript version exists and is UNDER_REVIEW
        manga.model.ManuscriptVersion version = manuscriptVersionRepository.findById(manuscriptVersionId);
        if (version == null) {
            throw new BusinessRuleException("Manuscript version not found");
        }

        if (version.getStatus() != ManuscriptStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("Can only annotate UNDER_REVIEW manuscripts");
        }

        // Check if manuscript version is immutable before adding annotation
        if (version.getStatus() == ManuscriptStatus.APPROVED || 
            version.getStatus() == ManuscriptStatus.PUBLISHED ||
            version.getStatus() == ManuscriptStatus.REJECTED ||
            version.getStatus() == ManuscriptStatus.ARCHIVED) {
            throw new BusinessRuleException(
                "Cannot add annotation: manuscript version is " + version.getStatus()
            );
        }

        // Validate manuscriptPageId belongs to this manuscriptVersionId
        if (manuscriptPageId != null) {
            String pageCheckSql = "SELECT manuscriptVersionId FROM ManuscriptPage WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(pageCheckSql)) {
                ps.setLong(1, manuscriptPageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new BusinessRuleException("Manuscript page not found");
                    }
                    Long pageVersionId = rs.getLong("manuscriptVersionId");
                    if (!pageVersionId.equals(manuscriptVersionId)) {
                        throw new BusinessRuleException("Page belongs to different manuscript version");
                    }
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Cannot validate manuscript page", ex);
            }
        }

        // Validate coordinates (BR-8)
        if (xPercent != null || yPercent != null || widthPercent != null || heightPercent != null) {
            if (xPercent == null || yPercent == null || widthPercent == null || heightPercent == null) {
                throw new BusinessRuleException("All coordinates must be provided together");
            }
            if (xPercent < 0 || xPercent > 100 || yPercent < 0 || yPercent > 100 ||
                widthPercent <= 0 || widthPercent > 100 || heightPercent <= 0 || heightPercent > 100) {
                throw new BusinessRuleException("Coordinates must be between 0-100 (BR-8)");
            }
            // Coordinates require manuscriptPageId to be set
            if (manuscriptPageId == null) {
                throw new BusinessRuleException("Coordinates require manuscriptPageId to be set");
            }
        }

        // Validate parent annotation if this is a reply
        if (parentAnnotationId != null) {
            // Check if parent exists and belongs to same manuscript version
            String parentCheckSql = "SELECT manuscriptVersionId FROM Annotation WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(parentCheckSql)) {
                ps.setLong(1, parentAnnotationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new BusinessRuleException("Parent annotation not found");
                    }
                    Long parentVersionId = rs.getLong("manuscriptVersionId");
                    if (parentVersionId != null && !parentVersionId.equals(manuscriptVersionId)) {
                        throw new BusinessRuleException("Parent annotation belongs to different manuscript version");
                    }
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Cannot validate parent annotation", ex);
            }
        }

        // Validate content
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessRuleException("Annotation content is required");
        }

        // Role-based authorization: Mangaka are not allowed to create annotations
        if (user != null && user.hasRole("MANGAKA")) {
            throw new BusinessRuleException("ANNOTATION_UNAUTHORIZED_MANGAKA: Mangaka users cannot create annotations");
        }

        // Get pageNumber from manuscriptPageId if needed for backward compatibility
        Integer pageNumber = null;
        if (manuscriptPageId != null) {
            String pageSql = "SELECT pageNumber FROM ManuscriptPage WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(pageSql)) {
                ps.setLong(1, manuscriptPageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        pageNumber = rs.getInt("pageNumber");
                    }
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Cannot get page number", ex);
            }
        }

        // Insert annotation with manuscriptVersionId and manuscriptPageId
        String sql = "INSERT INTO Annotation (manuscriptVersionId, manuscriptPageId, editorId, pageNumber, category, status, content, xPercent, yPercent, widthPercent, heightPercent, severity, parentAnnotationId, createdAt) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ,? ,GETDATE())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, manuscriptVersionId);
            if (manuscriptPageId != null) {
                ps.setLong(2, manuscriptPageId);
            } else {
                ps.setNull(2, java.sql.Types.BIGINT);
            }
            ps.setLong(3, editorId);
            if (pageNumber != null) {
                ps.setInt(4, pageNumber);
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setString(5, category.name());
            ps.setString(6, AnnotationStatus.OPEN.name());
            ps.setString(7, content);
            if (xPercent != null) {
                ps.setDouble(8, xPercent);
                ps.setDouble(9, yPercent);
                ps.setDouble(10, widthPercent);
                ps.setDouble(11, heightPercent);
            } else {
                ps.setNull(8, java.sql.Types.DOUBLE);
                ps.setNull(9, java.sql.Types.DOUBLE);
                ps.setNull(10, java.sql.Types.DOUBLE);
                ps.setNull(11, java.sql.Types.DOUBLE);
            }
            ps.setString(12, severity != null ? severity.name() : null);
            if (parentAnnotationId != null) {
                ps.setLong(13, parentAnnotationId);
            } else {
                ps.setNull(13, java.sql.Types.BIGINT);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot create annotation", ex);
        }
        throw new RuntimeException("Failed to create annotation");
    }

    /**
     * Resolve annotation.
     */
    public void resolveAnnotation(Long annotationId, Long resolvedBy, AuthenticatedUser user) {
        // Check if manuscript version is immutable before resolving annotation
        String versionCheckSql = "SELECT mv.status FROM Annotation a " +
                                "JOIN ManuscriptVersion mv ON mv.id = a.manuscriptVersionId " +
                                "WHERE a.id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(versionCheckSql)) {
            ps.setLong(1, annotationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    if ("APPROVED".equals(status) || "PUBLISHED".equals(status) || "REJECTED".equals(status) || "ARCHIVED".equals(status)) {
                        throw new BusinessRuleException(
                            "Cannot resolve annotation: manuscript version is " + status
                        );
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check manuscript status", ex);
        }

        // Role-based authorization: Mangaka cannot resolve annotations
        if (user != null && user.hasRole("MANGAKA")) {
            throw new BusinessRuleException("ANNOTATION_UNAUTHORIZED_MANGAKA: Mangaka users cannot modify annotations");
        }

        String sql = "UPDATE Annotation SET status = 'RESOLVED', resolvedAt = GETDATE(), resolvedBy = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, resolvedBy);
            ps.setLong(2, annotationId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new BusinessRuleException("Annotation not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot resolve annotation", ex);
        }
    }

    /**
     * Dismiss annotation (mark as not applicable).
     */
    public void dismissAnnotation(Long annotationId, Long dismissedBy, AuthenticatedUser user) {
        // Role-based authorization: Mangaka cannot dismiss annotations
        if (user != null && user.hasRole("MANGAKA")) {
            throw new BusinessRuleException("ANNOTATION_UNAUTHORIZED_MANGAKA: Mangaka users cannot modify annotations");
        }

        String sql = "UPDATE Annotation SET status = 'DISMISSED', resolvedAt = GETDATE(), resolvedBy = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, dismissedBy);
            ps.setLong(2, annotationId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new BusinessRuleException("Annotation not found");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot dismiss annotation", ex);
        }
    }

    /**
     * Add reply to annotation.
     */
    public long addReply(Long parentAnnotationId, Long editorId, String content, AuthenticatedUser user) {
        // Get parent annotation details
        String parentSql = "SELECT manuscriptVersionId, manuscriptPageId, pageNumber, category FROM Annotation WHERE id = ?";
        Long manuscriptVersionId = null;
        Long manuscriptPageId = null;
        Integer pageNumber = null;
        String category = null;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(parentSql)) {
            ps.setLong(1, parentAnnotationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BusinessRuleException("Parent annotation not found");
                }
                manuscriptVersionId = rs.getLong("manuscriptVersionId");
                manuscriptPageId = rs.getObject("manuscriptPageId") != null ? rs.getLong("manuscriptPageId") : null;
                pageNumber = rs.getObject("pageNumber") != null ? rs.getInt("pageNumber") : null;
                category = rs.getString("category");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot get parent annotation", ex);
        }

        // Validate parent is not resolved
        String statusCheckSql = "SELECT status FROM Annotation WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(statusCheckSql)) {
            ps.setLong(1, parentAnnotationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    if ("RESOLVED".equals(status) || "DISMISSED".equals(status)) {
                        throw new BusinessRuleException("Cannot reply to resolved/dismissed annotation");
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot check parent annotation status", ex);
        }

        // Create reply annotation with parentAnnotationId set
        // Role-based authorization: Mangaka cannot add replies/annotations
        if (user != null && user.hasRole("MANGAKA")) {
            throw new BusinessRuleException("ANNOTATION_UNAUTHORIZED_MANGAKA: Mangaka users cannot create annotation replies");
        }

        return addAnnotation(
            manuscriptVersionId,
            editorId,
            manuscriptPageId,
            AnnotationCategory.valueOf(category),
            null,
            content,
            null,
            null,
            null,
            null,
            parentAnnotationId,
            user
        );
    }

    /**
     * Count OPEN annotations for manuscript version.
     * Used for approval gate check - cannot approve with open annotations.
     */
    public long countOpenAnnotations(Long manuscriptVersionId) {
        String sql = "SELECT COUNT(*) FROM Annotation WHERE manuscriptVersionId = ? AND status = 'OPEN'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, manuscriptVersionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot count open annotations", ex);
        }
        return 0;
    }

    /**
     * List annotations for manuscript version.
     */
    public List<manga.model.AnnotationSummary> listAnnotations(Long manuscriptVersionId) {
        String sql = "SELECT a.id, a.manuscriptVersionId, a.manuscriptPageId, a.editorId, u.fullName AS editorName, a.pageNumber, " +
                    "a.category, a.status, a.content, a.createdAt, a.xPercent, a.yPercent, a.widthPercent, a.heightPercent, " +
                    "a.severity, a.parentAnnotationId, a.resolvedAt, a.resolvedBy " +
                    "FROM Annotation a " +
                    "LEFT JOIN [User] u ON u.id = a.editorId " +
                    "WHERE a.manuscriptVersionId = ? ORDER BY a.createdAt DESC";
        List<manga.model.AnnotationSummary> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, manuscriptVersionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapAnnotationSummary(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list annotations", ex);
        }
        return results;
    }

    /**
     * Get annotation by ID.
     */
    public manga.model.AnnotationSummary getAnnotation(Long annotationId) {
        String sql = "SELECT a.id, a.manuscriptVersionId, a.manuscriptPageId, a.editorId, u.fullName AS editorName, a.pageNumber, " +
                    "a.category, a.status, a.content, a.createdAt, a.xPercent, a.yPercent, a.widthPercent, a.heightPercent, " +
                    "a.severity, a.parentAnnotationId, a.resolvedAt, a.resolvedBy " +
                    "FROM Annotation a " +
                    "LEFT JOIN [User] u ON u.id = a.editorId " +
                    "WHERE a.id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, annotationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAnnotationSummary(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot get annotation", ex);
        }
        return null;
    }

    /**
     * List replies for annotation.
     */
    public List<manga.model.AnnotationSummary> listReplies(Long parentAnnotationId) {
        String sql = "SELECT a.id, a.manuscriptVersionId, a.manuscriptPageId, a.editorId, u.fullName AS editorName, a.pageNumber, " +
                    "a.category, a.status, a.content, a.createdAt, a.xPercent, a.yPercent, a.widthPercent, a.heightPercent, " +
                    "a.severity, a.parentAnnotationId, a.resolvedAt, a.resolvedBy " +
                    "FROM Annotation a " +
                    "LEFT JOIN [User] u ON u.id = a.editorId " +
                    "WHERE a.parentAnnotationId = ? ORDER BY a.createdAt ASC";
        List<manga.model.AnnotationSummary> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, parentAnnotationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapAnnotationSummary(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Cannot list replies", ex);
        }
        return results;
    }

    private manga.model.AnnotationSummary mapAnnotationSummary(ResultSet rs) throws SQLException {
        manga.model.AnnotationSummary summary = new manga.model.AnnotationSummary();
        summary.setId(rs.getLong("id"));
        summary.setManuscriptId(rs.getLong("manuscriptVersionId"));
        summary.setManuscriptVersionId(rs.getLong("manuscriptVersionId"));
        summary.setManuscriptPageId(rs.getObject("manuscriptPageId") != null ? rs.getLong("manuscriptPageId") : null);
        summary.setEditorId(rs.getLong("editorId"));
        summary.setEditorName(rs.getString("editorName"));
        summary.setPageNumber(rs.getObject("pageNumber") != null ? rs.getInt("pageNumber") : null);
        summary.setCategory(rs.getString("category"));
        summary.setStatus(rs.getString("status"));
        summary.setContent(rs.getString("content"));
        summary.setCreatedAt(rs.getTimestamp("createdAt"));
        summary.setXPercent(rs.getObject("xPercent") != null ? rs.getDouble("xPercent") : null);
        summary.setYPercent(rs.getObject("yPercent") != null ? rs.getDouble("yPercent") : null);
        summary.setWidthPercent(rs.getObject("widthPercent") != null ? rs.getDouble("widthPercent") : null);
        summary.setHeightPercent(rs.getObject("heightPercent") != null ? rs.getDouble("heightPercent") : null);
        summary.setSeverity(rs.getString("severity"));
        summary.setParentAnnotationId(rs.getObject("parentAnnotationId") != null ? rs.getLong("parentAnnotationId") : null);
        summary.setResolvedAt(rs.getTimestamp("resolvedAt"));
        summary.setResolvedBy(rs.getObject("resolvedBy") != null ? rs.getLong("resolvedBy") : null);
        return summary;
    }
}
