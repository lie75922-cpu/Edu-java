package com.smartlearning.graph.api;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.graph.application.EvidenceImportService;
import com.smartlearning.graph.application.EvidenceReresolutionService;
import com.smartlearning.graph.application.GraphValidationService;
import com.smartlearning.graph.application.GraphVersionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TEACH_ADMIN', 'TEACHER')")
public class AdminGraphController {

    private final GraphVersionService graphVersionService;
    private final GraphValidationService graphValidationService;
    private final EvidenceImportService evidenceImportService;
    private final EvidenceReresolutionService evidenceReresolutionService;

    public AdminGraphController(
            GraphVersionService graphVersionService,
            GraphValidationService graphValidationService,
            EvidenceImportService evidenceImportService,
            EvidenceReresolutionService evidenceReresolutionService
    ) {
        this.graphVersionService = graphVersionService;
        this.graphValidationService = graphValidationService;
        this.evidenceImportService = evidenceImportService;
        this.evidenceReresolutionService = evidenceReresolutionService;
    }

    @PostMapping("/graph-versions")
    public ApiResponse<GraphApi.GraphVersionResponse> createVersion(
            @Valid @RequestBody GraphApi.CreateGraphVersionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphVersionService.createForTeaching(request, CurrentUser.from(jwt)));
    }

    @GetMapping("/graph-versions")
    public ApiResponse<List<GraphApi.GraphVersionResponse>> listVersions(
            @RequestParam long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphVersionService.listForTeaching(courseId, CurrentUser.from(jwt)));
    }

    @GetMapping("/graph-versions/{graphVersionId}")
    public ApiResponse<GraphApi.GraphVersionResponse> getVersion(
            @PathVariable long graphVersionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphVersionService.getForTeaching(graphVersionId, CurrentUser.from(jwt)));
    }

    @GetMapping("/graph-versions/{graphVersionId}/relations")
    public ApiResponse<List<GraphApi.GraphRelationResponse>> listRelations(
            @PathVariable long graphVersionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphVersionService.listRelationsForTeaching(graphVersionId, CurrentUser.from(jwt)));
    }

    @PostMapping("/graph-versions/{graphVersionId}/relations")
    public ApiResponse<GraphApi.GraphRelationResponse> addManualRelation(
            @PathVariable long graphVersionId,
            @Valid @RequestBody GraphApi.ManualRelationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphVersionService.addManualRelationForTeaching(
                graphVersionId, request, CurrentUser.from(jwt)
        ));
    }

    @PutMapping("/graph-versions/{graphVersionId}/relations/{relationId}/review")
    public ApiResponse<GraphApi.GraphRelationResponse> reviewRelation(
            @PathVariable long graphVersionId,
            @PathVariable long relationId,
            @Valid @RequestBody GraphApi.RelationReviewRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphVersionService.reviewRelationForTeaching(
                graphVersionId, relationId, request, CurrentUser.from(jwt)
        ));
    }

    @DeleteMapping("/graph-versions/{graphVersionId}/relations/{relationId}")
    public ApiResponse<Void> rejectRelation(
            @PathVariable long graphVersionId,
            @PathVariable long relationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        graphVersionService.rejectRelationForTeaching(graphVersionId, relationId, CurrentUser.from(jwt));
        return ApiResponse.ok(null);
    }

    @PostMapping("/graph-versions/{graphVersionId}/evidence-imports/dry-run")
    public ApiResponse<GraphApi.EvidenceImportResult> dryRunEvidenceImport(
            @PathVariable long graphVersionId,
            @Valid @RequestBody GraphApi.EvidenceImportRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceImportService.dryRunForTeaching(graphVersionId, request, CurrentUser.from(jwt)));
    }

    @PostMapping("/graph-versions/{graphVersionId}/evidence-imports/apply")
    public ApiResponse<GraphApi.EvidenceImportResult> applyEvidenceImport(
            @PathVariable long graphVersionId,
            @Valid @RequestBody GraphApi.EvidenceImportRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceImportService.applyForTeaching(graphVersionId, request, CurrentUser.from(jwt)));
    }

    @PostMapping("/graph-versions/{graphVersionId}/evidence-reresolutions/dry-run")
    public ApiResponse<GraphApi.EvidenceReresolutionResult> dryRunEvidenceReresolution(
            @PathVariable long graphVersionId,
            @Valid @RequestBody GraphApi.EvidenceReresolutionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceReresolutionService.dryRunForTeaching(
                graphVersionId, request, CurrentUser.from(jwt)
        ));
    }

    @PostMapping("/graph-versions/{graphVersionId}/evidence-reresolutions/apply")
    public ApiResponse<GraphApi.EvidenceReresolutionResult> applyEvidenceReresolution(
            @PathVariable long graphVersionId,
            @Valid @RequestBody GraphApi.EvidenceReresolutionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceReresolutionService.applyForTeaching(
                graphVersionId, request, CurrentUser.from(jwt)
        ));
    }

    @GetMapping("/graph-versions/{graphVersionId}/relations/{relationId}/evidence")
    public ApiResponse<List<GraphApi.EvidenceResponse>> relationEvidence(
            @PathVariable long graphVersionId,
            @PathVariable long relationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceImportService.listEvidenceForRelationForTeaching(
                graphVersionId, relationId, CurrentUser.from(jwt)
        ));
    }

    @GetMapping("/graph-versions/{graphVersionId}/validation-issues")
    public ApiResponse<List<GraphApi.GraphValidationIssueResponse>> validationIssues(
            @PathVariable long graphVersionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphValidationService.listIssuesForTeaching(graphVersionId, CurrentUser.from(jwt)));
    }

    @PostMapping("/graph-versions/{graphVersionId}/validate")
    public ApiResponse<GraphApi.GraphVersionResponse> validate(
            @PathVariable long graphVersionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        CurrentUser user = CurrentUser.from(jwt);
        long validatedVersionId = graphValidationService.validateForTeaching(graphVersionId, user).graphVersion().getId();
        return ApiResponse.ok(graphVersionService.getForTeaching(validatedVersionId, user));
    }

    @PostMapping("/graph-versions/{graphVersionId}/publish")
    public ApiResponse<GraphApi.GraphVersionResponse> publish(
            @PathVariable long graphVersionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphVersionService.requestPublishForTeaching(graphVersionId, CurrentUser.from(jwt)));
    }

    @PostMapping("/graph-versions/{graphVersionId}/retry-projection")
    public ApiResponse<GraphApi.GraphVersionResponse> retryProjection(
            @PathVariable long graphVersionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphVersionService.retryProjectionForTeaching(graphVersionId, CurrentUser.from(jwt)));
    }

    @GetMapping("/evidence")
    public ApiResponse<List<GraphApi.EvidenceResponse>> listEvidence(
            @RequestParam long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceImportService.listEvidenceForTeaching(courseId, CurrentUser.from(jwt)));
    }

    @GetMapping("/evidence/{evidenceId}/resolution-history")
    public ApiResponse<List<GraphApi.EvidenceResolutionHistoryResponse>> evidenceResolutionHistory(
            @PathVariable long evidenceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceReresolutionService.historyForTeaching(evidenceId, CurrentUser.from(jwt)));
    }

    @GetMapping("/evidence-imports/{importRunId}/conflicts")
    public ApiResponse<List<GraphApi.EvidenceConflictResponse>> evidenceConflicts(
            @PathVariable long importRunId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceImportService.conflictsForTeaching(importRunId, CurrentUser.from(jwt)));
    }
}
