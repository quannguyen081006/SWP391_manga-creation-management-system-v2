package manga.controller.api;

import java.util.Enumeration;
import manga.common.ApiResponse;
import manga.dto.CreateAnnotationRequestDTO;
import manga.model.AnnotationSummary;
import manga.model.AuthenticatedUser;
import manga.service.AnnotationServiceV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import javax.servlet.http.HttpSession;

/**
 * API Controller for Annotation operations.
 *
 * Provides REST endpoints for coordinate-based annotations.
 */
@RestController
@RequestMapping("/api/v1/annotations")
public class AnnotationApiController {

    @Autowired
    private AnnotationServiceV2 annotationService;

    /**
     * Add annotation with coordinates. POST /api/v1/annotations
     */
    @PostMapping(
            consumes = "application/json",
            produces = "application/json"
    )
    public ApiResponse<Long> addAnnotation(
            @RequestBody CreateAnnotationRequestDTO request,
            HttpSession session) {
        AuthenticatedUser user
                = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (user == null) {
            return ApiResponse.error("Unauthorized");
        }

        System.out.println("=== CONTROLLER DEBUG ===");
        System.out.println("request = " + request);

        System.out.println("x = " + request.getxPercent());
        System.out.println("y = " + request.getyPercent());
        System.out.println("w = " + request.getWidthPercent());
        System.out.println("h = " + request.getHeightPercent());

        long annotationId = annotationService.addAnnotation(
                request.getManuscriptVersionId(),
                user.getId(),
                request.getManuscriptPageId(),
                manga.enums.AnnotationCategory.valueOf(request.getCategory()),
                request.getSeverity() != null
                ? manga.enums.AnnotationSeverity.valueOf(request.getSeverity())
                : null,
                request.getContent(),
                request.getxPercent(),
                request.getyPercent(),
                request.getWidthPercent(),
                request.getHeightPercent(),
                request.getParentAnnotationId(),
                user
        );

        return ApiResponse.success(annotationId);

    }

    /**
     * List annotations for manuscript version. GET
     * /api/v1/annotations?manuscriptVersionId={id}
     */
    @GetMapping
    public ApiResponse<List<AnnotationSummary>> listAnnotations(
            @RequestParam Long manuscriptVersionId,
            HttpSession session) {
        AuthenticatedUser user
                = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (user == null) {
            return ApiResponse.error("Unauthorized");
        }
        List<AnnotationSummary> annotations = annotationService.listAnnotations(manuscriptVersionId);
        return ApiResponse.success(annotations);
    }

    /**
     * Get annotation by ID. GET /api/v1/annotations/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<AnnotationSummary> getAnnotation(
            @PathVariable Long id,
            HttpSession session) {
        AuthenticatedUser user
                = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (user == null) {
            return ApiResponse.error("Unauthorized");
        }
        AnnotationSummary annotation = annotationService.getAnnotation(id);
        if (annotation == null) {
            return ApiResponse.error("Annotation not found");
        }
        return ApiResponse.success(annotation);
    }

    /**
     * Resolve annotation. POST /api/v1/annotations/{id}/resolve
     */
    @PostMapping("/{id}/resolve")
    public ApiResponse<Void> resolveAnnotation(
            @PathVariable Long id,
            HttpSession session) {
        AuthenticatedUser user
                = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (user == null) {
            return ApiResponse.error("Unauthorized");
        }
        annotationService.resolveAnnotation(id, user.getId(), user);
        return ApiResponse.success(null);
    }

    /**
     * Dismiss annotation. POST /api/v1/annotations/{id}/dismiss
     */
    @PostMapping("/{id}/dismiss")
    public ApiResponse<Void> dismissAnnotation(
            @PathVariable Long id,
            HttpSession session) {
        AuthenticatedUser user
                = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (user == null) {
            return ApiResponse.error("Unauthorized");
        }
        annotationService.dismissAnnotation(id, user.getId(), user);
        return ApiResponse.success(null);
    }

    /**
     * Add reply to annotation. POST /api/v1/annotations/{id}/replies
     */
    @PostMapping("/{id}/replies")
    public ApiResponse<Long> addReply(
            @PathVariable Long id,
            @RequestBody CreateAnnotationRequestDTO request,
            HttpSession session) {
        AuthenticatedUser user
                = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (user == null) {
            return ApiResponse.error("Unauthorized");
        }

        long replyId = annotationService.addReply(id, user.getId(), request.getContent(), user);
        return ApiResponse.success(replyId);
    }

    /**
     * List replies for annotation. GET /api/v1/annotations/{id}/replies
     */
    @GetMapping("/{id}/replies")
    public ApiResponse<List<AnnotationSummary>> listReplies(
            @PathVariable Long id,
            HttpSession session) {
        AuthenticatedUser user
                = (AuthenticatedUser) session.getAttribute("AUTH_USER");
        if (user == null) {
            return ApiResponse.error("Unauthorized");
        }

        List<AnnotationSummary> replies = annotationService.listReplies(id);
        return ApiResponse.success(replies);
    }

    @PostMapping(
            value = "/debug",
            consumes = "application/json"
    )
    public String debug(
            @RequestBody CreateAnnotationRequestDTO request,
            HttpSession session
    ) {

        System.out.println("SESSION:");
        System.out.println(session);

        System.out.println("SESSION ATTRIBUTES:");

        Enumeration<String> names
                = session.getAttributeNames();

        while (names.hasMoreElements()) {

            String name = names.nextElement();

            System.out.println(
                    name + " = "
                    + session.getAttribute(name)
            );
        }

        System.out.println(request);

        return "OK";

    }
}
