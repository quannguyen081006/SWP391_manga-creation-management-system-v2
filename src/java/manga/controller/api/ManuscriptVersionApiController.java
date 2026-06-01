package manga.controller.api;

import manga.common.ApiResponse;
import manga.dto.AddPageRequestDTO;
import manga.dto.ManuscriptApprovalRequestDTO;
import manga.dto.ManuscriptPageDTO;
import manga.dto.ManuscriptVersionDTO;
import manga.model.AuthenticatedUser;
import manga.model.ManuscriptPage;
import manga.model.ManuscriptVersion;
import manga.service.ManuscriptVersionService;
import manga.service.ReviewTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * API Controller for ManuscriptVersion operations.
 * 
 * Provides REST endpoints for the new visual manuscript workspace.
 */
@RestController
@RequestMapping("/api/v1/manuscript-versions")
public class ManuscriptVersionApiController {

    @Autowired
    private ManuscriptVersionService manuscriptVersionService;

    @Autowired
    private ReviewTaskService reviewTaskService;

    /**
     * Create new manuscript workspace.
     * POST /api/v1/manuscript-versions
     */
    @PostMapping
    public ApiResponse<ManuscriptVersionDTO> createWorkspace(
            @RequestParam Long chapterId,
            @ModelAttribute AuthenticatedUser user) {
        
        ManuscriptVersion version = manuscriptVersionService.createWorkspace(chapterId, user);
        return ApiResponse.success(toDTO(version));
    }

    /**
     * Get manuscript version by ID.
     * GET /api/v1/manuscript-versions/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<ManuscriptVersionDTO> getVersion(
            @PathVariable Long id,
            @ModelAttribute AuthenticatedUser user) {
        
        ManuscriptVersion version = manuscriptVersionService.getVersion(id);
        if (version == null) {
            return ApiResponse.error("Manuscript version not found");
        }
        
        ManuscriptVersionDTO dto = toDTO(version);
        dto.setProductionLocked(manuscriptVersionService.isProductionLocked(version.getChapterId()));
        return ApiResponse.success(dto);
    }

    /**
     * List versions for chapter.
     * GET /api/v1/manuscript-versions?chapterId={chapterId}
     */
    @GetMapping
    public ApiResponse<List<ManuscriptVersionDTO>> listVersions(
            @RequestParam Long chapterId,
            @ModelAttribute AuthenticatedUser user) {
        
        List<ManuscriptVersion> versions = manuscriptVersionService.listVersions(chapterId);
        List<ManuscriptVersionDTO> dtos = versions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    /**
     * Add page snapshot to manuscript.
     * POST /api/v1/manuscript-versions/{id}/pages
     */
    @PostMapping("/{id}/pages")
    public ApiResponse<ManuscriptPageDTO> addPage(
            @PathVariable Long id,
            @RequestBody AddPageRequestDTO request,
            @ModelAttribute AuthenticatedUser user) {
        
        ManuscriptPage page = manuscriptVersionService.addPageSnapshot(
                id,
                request.getChapterImageId(),
                request.getDisplayOrder(),
                user
        );
        return ApiResponse.success(toPageDTO(page));
    }

    /**
     * Get pages for manuscript version.
     * GET /api/v1/manuscript-versions/{id}/pages
     */
    @GetMapping("/{id}/pages")
    public ApiResponse<List<ManuscriptPageDTO>> getPages(
            @PathVariable Long id,
            @ModelAttribute AuthenticatedUser user) {
        
        List<ManuscriptPage> pages = manuscriptVersionService.getPages(id);
        List<ManuscriptPageDTO> dtos = pages.stream()
                .map(this::toPageDTO)
                .collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    /**
     * Submit manuscript for review.
     * POST /api/v1/manuscript-versions/{id}/submit
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submitForReview(
            @PathVariable Long id,
            @ModelAttribute AuthenticatedUser user) {
        
        manuscriptVersionService.submitForReview(id, user);
        return ApiResponse.success(null);
    }

    /**
     * Approve manuscript.
     * POST /api/v1/manuscript-versions/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ManuscriptApprovalRequestDTO request,
            @ModelAttribute AuthenticatedUser user) {
        
        manuscriptVersionService.approve(id, user);
        return ApiResponse.success(null);
    }

    /**
     * Publish manuscript.
     * POST /api/v1/manuscript-versions/{id}/publish
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publish(
            @PathVariable Long id,
            @ModelAttribute AuthenticatedUser user) {
        
        manuscriptVersionService.publish(id, user);
        return ApiResponse.success(null);
    }

    /**
     * Reject manuscript.
     * POST /api/v1/manuscript-versions/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(
            @PathVariable Long id,
            @RequestBody ManuscriptApprovalRequestDTO request,
            @ModelAttribute AuthenticatedUser user) {
        
        manuscriptVersionService.reject(id, request.getFeedback(), user);
        return ApiResponse.success(null);
    }

    /**
     * Get review task for manuscript version.
     * GET /api/v1/manuscript-versions/{id}/review-task
     */
    @GetMapping("/{id}/review-task")
    public ApiResponse<manga.model.ReviewTask> getReviewTask(
            @PathVariable Long id,
            @ModelAttribute AuthenticatedUser user) {
        
        manga.model.ReviewTask task = reviewTaskService.getReviewTask(id);
        return ApiResponse.success(task);
    }

    /**
     * Create new version after rejection.
     * POST /api/v1/manuscript-versions/new-version
     */
    @PostMapping("/new-version")
    public ApiResponse<ManuscriptVersionDTO> createNewVersion(
            @RequestParam Long chapterId,
            @ModelAttribute AuthenticatedUser user) {
        
        ManuscriptVersion version = manuscriptVersionService.createNewVersion(chapterId, user);
        return ApiResponse.success(toDTO(version));
    }

    /**
     * Get review decision history for manuscript version.
     * GET /api/v1/manuscript-versions/{id}/decisions
     */
    @GetMapping("/{id}/decisions")
    public ApiResponse<List<manga.model.ReviewDecision>> getReviewDecisions(
            @PathVariable Long id,
            @ModelAttribute AuthenticatedUser user) {
        
        List<manga.model.ReviewDecision> decisions = manuscriptVersionService.getReviewDecisions(id);
        return ApiResponse.success(decisions);
    }

    /**
     * Get annotations for a specific manuscript page.
     * GET /api/v1/manuscript-versions/{id}/pages/{pageId}/annotations
     */
    @GetMapping("/{id}/pages/{pageId}/annotations")
    public ApiResponse<List<manga.model.AnnotationSummary>> getPageAnnotations(
            @PathVariable Long id,
            @PathVariable Long pageId,
            @ModelAttribute AuthenticatedUser user) {
        
        List<manga.model.AnnotationSummary> annotations = manuscriptVersionService.getPageAnnotations(id, pageId);
        return ApiResponse.success(annotations);
    }

    /**
     * Get candidate chapter images for manuscript builder.
     * GET /api/v1/manuscript-versions/candidate-pages?chapterId={chapterId}
     */
    @GetMapping("/candidate-pages")
    public ApiResponse<List<manga.dto.ChapterImageDTO>> getCandidatePages(
            @RequestParam Long chapterId,
            @ModelAttribute AuthenticatedUser user) {

        List<manga.dto.ChapterImageDTO> candidatePages = manuscriptVersionService.getCandidatePages(chapterId);
        return ApiResponse.success(candidatePages);
    }

    /**
     * Bulk import all chapter pages into manuscript workspace.
     * POST /api/v1/manuscript-versions/{id}/import-pages
     */
    @PostMapping("/{id}/import-pages")
    public ApiResponse<List<ManuscriptPageDTO>> importChapterPages(
            @PathVariable Long id,
            @RequestParam Long chapterId,
            @ModelAttribute AuthenticatedUser user) {

        List<ManuscriptPage> pages = manuscriptVersionService.importChapterPages(id, chapterId, user);
        List<ManuscriptPageDTO> dtos = pages.stream()
                .map(this::toPageDTO)
                .collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    /**
     * Compare two manuscript versions.
     * GET /api/v1/manuscript-versions/compare?versionId1={id1}&versionId2={id2}
     */
    @GetMapping("/compare")
    public ApiResponse<manga.dto.VersionComparisonDTO> compareVersions(
            @RequestParam Long versionId1,
            @RequestParam Long versionId2,
            @ModelAttribute AuthenticatedUser user) {

        manga.dto.VersionComparisonDTO comparison = manuscriptVersionService.compareVersions(versionId1, versionId2);
        return ApiResponse.success(comparison);
    }

    /**
     * Get review dashboard for manuscript version.
     * GET /api/v1/manuscript-versions/{id}/dashboard
     */
    @GetMapping("/{id}/dashboard")
    public ApiResponse<manga.dto.ReviewDashboardDTO> getReviewDashboard(
            @PathVariable Long id,
            @ModelAttribute AuthenticatedUser user) {

        manga.dto.ReviewDashboardDTO dashboard = manuscriptVersionService.getReviewDashboard(id);
        return ApiResponse.success(dashboard);
    }

    /**
     * Get active workspace for a chapter.
     * GET /api/v1/manuscript-versions/workspace?chapterId={chapterId}
     * 
     * Returns workspace status information for the chapter.
     * Frontend should use this endpoint before showing action buttons.
     */
    @GetMapping("/workspace")
    public ApiResponse<WorkspaceStatusDTO> getWorkspaceStatus(
            @RequestParam Long chapterId,
            @ModelAttribute AuthenticatedUser user) {

        manga.model.ManuscriptVersion workspace = manuscriptVersionService.getActiveWorkspace(chapterId);
        
        WorkspaceStatusDTO dto = new WorkspaceStatusDTO();
        if (workspace != null) {
            dto.setWorkspaceExists(true);
            dto.setWorkspaceId(workspace.getId());
            dto.setStatus(workspace.getStatus().name());
            dto.setEditable(workspace.getStatus().isEditable());
            dto.setVersionNumber(workspace.getVersion());
            dto.setTotalPageCount(workspace.getTotalPageCount());
        } else {
            dto.setWorkspaceExists(false);
            dto.setWorkspaceId(null);
            dto.setStatus(null);
            dto.setEditable(true); // Can create new workspace
            dto.setVersionNumber(null);
            dto.setTotalPageCount(0);
        }
        
        return ApiResponse.success(dto);
    }

    /**
     * DTO for workspace status response.
     */
    public static class WorkspaceStatusDTO {
        private boolean workspaceExists;
        private Long workspaceId;
        private String status;
        private boolean editable;
        private Integer versionNumber;
        private Integer totalPageCount;

        public boolean isWorkspaceExists() {
            return workspaceExists;
        }

        public void setWorkspaceExists(boolean workspaceExists) {
            this.workspaceExists = workspaceExists;
        }

        public Long getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(Long workspaceId) {
            this.workspaceId = workspaceId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isEditable() {
            return editable;
        }

        public void setEditable(boolean editable) {
            this.editable = editable;
        }

        public Integer getVersionNumber() {
            return versionNumber;
        }

        public void setVersionNumber(Integer versionNumber) {
            this.versionNumber = versionNumber;
        }

        public Integer getTotalPageCount() {
            return totalPageCount;
        }

        public void setTotalPageCount(Integer totalPageCount) {
            this.totalPageCount = totalPageCount;
        }
    }

    private ManuscriptVersionDTO toDTO(ManuscriptVersion version) {
        ManuscriptVersionDTO dto = new ManuscriptVersionDTO();
        dto.setId(version.getId());
        dto.setChapterId(version.getChapterId());
        dto.setVersion(version.getVersion());
        dto.setPreviousVersionId(version.getPreviousVersionId());
        dto.setStatus(version.getStatus().name());
        dto.setCreatedAt(version.getCreatedAt());
        dto.setSubmittedAt(version.getSubmittedAt());
        dto.setApprovedAt(version.getApprovedAt());
        dto.setRejectedAt(version.getRejectedAt());
        dto.setPublishedAt(version.getPublishedAt());
        dto.setCreatedBy(version.getCreatedBy());
        dto.setSubmittedBy(version.getSubmittedBy());
        dto.setApprovedBy(version.getApprovedBy());
        dto.setRejectedBy(version.getRejectedBy());
        dto.setFeedback(version.getFeedback());
        dto.setRevisionNotes(version.getRevisionNotes());
        dto.setTotalPageCount(version.getTotalPageCount());
        return dto;
    }

    private ManuscriptPageDTO toPageDTO(ManuscriptPage page) {
        ManuscriptPageDTO dto = new ManuscriptPageDTO();
        dto.setId(page.getId());
        dto.setManuscriptVersionId(page.getManuscriptVersionId());
        dto.setDisplayOrder(page.getDisplayOrder());
        dto.setSnapshotFileUrl(page.getSnapshotFileUrl());
        dto.setOriginalFileUrl(page.getOriginalFileUrl());
        dto.setSourceChapterImageId(page.getSourceChapterImageId());
        dto.setSourcePageTaskId(page.getSourcePageTaskId());
        dto.setPageNumber(page.getPageNumber());
        dto.setSnapshotCreatedAt(page.getSnapshotCreatedAt());
        dto.setSnapshotChecksum(page.getSnapshotChecksum());
        return dto;
    }
}
