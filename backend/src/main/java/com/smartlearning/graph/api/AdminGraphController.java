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
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TEACHER')")
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
        return ApiResponse.ok(graphVersionService.create(request, CurrentUser.from(jwt).id()));
    }

    @GetMapping("/graph-versions")
    public ApiResponse<List<GraphApi.GraphVersionResponse>> listVersions(@RequestParam long courseId) {
        return ApiResponse.ok(graphVersionService.list(courseId));
    }

    @GetMapping("/graph-versions/{graphVersionId}")
    public ApiResponse<GraphApi.GraphVersionResponse> getVersion(@PathVariable long graphVersionId) {
        return ApiResponse.ok(graphVersionService.get(graphVersionId));
    }

    @GetMapping("/graph-versions/{graphVersionId}/relations")
    public ApiResponse<List<GraphApi.GraphRelationResponse>> listRelations(@PathVariable long graphVersionId) {
        return ApiResponse.ok(graphVersionService.listRelations(graphVersionId));
    }

    @PostMapping("/graph-versions/{graphVersionId}/relations")
    public ApiResponse<GraphApi.GraphRelationResponse> addManualRelation(
            @PathVariable long graphVersionId,
            @Valid @RequestBody GraphApi.ManualRelationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphVersionService.addManualRelation(graphVersionId, request, CurrentUser.from(jwt).id()));
    }

    @PutMapping("/graph-versions/{graphVersionId}/relations/{relationId}/review")
    public ApiResponse<GraphApi.GraphRelationResponse> reviewRelation(
            @PathVariable long graphVersionId,
            @PathVariable long relationId,
            @Valid @RequestBody GraphApi.RelationReviewRequest request
    ) {
        return ApiResponse.ok(graphVersionService.reviewRelation(graphVersionId, relationId, request));
    }

    @DeleteMapping("/graph-versions/{graphVersionId}/relations/{relationId}")
    public ApiResponse<Void> rejectRelation(@PathVariable long graphVersionId, @PathVariable long relationId) {
        graphVersionService.rejectRelation(graphVersionId, relationId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/graph-versions/{graphVersionId}/evidence-imports/dry-run")
    public ApiResponse<GraphApi.EvidenceImportResult> dryRunEvidenceImport(
            @PathVariable long graphVersionId,
            @Valid @RequestBody GraphApi.EvidenceImportRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceImportService.dryRun(graphVersionId, request, CurrentUser.from(jwt).id()));
    }

    @PostMapping("/graph-versions/{graphVersionId}/evidence-imports/apply")
    public ApiResponse<GraphApi.EvidenceImportResult> applyEvidenceImport(
            @PathVariable long graphVersionId,
            @Valid @RequestBody GraphApi.EvidenceImportRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceImportService.apply(graphVersionId, request, CurrentUser.from(jwt).id()));
    }

    @PostMapping("/graph-versions/{graphVersionId}/evidence-reresolutions/dry-run")
    public ApiResponse<GraphApi.EvidenceReresolutionResult> dryRunEvidenceReresolution(
            @PathVariable long graphVersionId,
            @Valid @RequestBody GraphApi.EvidenceReresolutionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceReresolutionService.dryRun(graphVersionId, request, CurrentUser.from(jwt).id()));
    }

    @PostMapping("/graph-versions/{graphVersionId}/evidence-reresolutions/apply")
    public ApiResponse<GraphApi.EvidenceReresolutionResult> applyEvidenceReresolution(
            @PathVariable long graphVersionId,
            @Valid @RequestBody GraphApi.EvidenceReresolutionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(evidenceReresolutionService.apply(graphVersionId, request, CurrentUser.from(jwt).id()));
    }

    @GetMapping("/graph-versions/{graphVersionId}/relations/{relationId}/evidence")
    public ApiResponse<List<GraphApi.EvidenceResponse>> relationEvidence(
            @PathVariable long graphVersionId,
            @PathVariable long relationId
    ) {
        return ApiResponse.ok(evidenceImportService.listEvidenceForRelation(graphVersionId, relationId));
    }

    @GetMapping("/graph-versions/{graphVersionId}/validation-issues")
    public ApiResponse<List<GraphApi.GraphValidationIssueResponse>> validationIssues(@PathVariable long graphVersionId) {
        return ApiResponse.ok(graphValidationService.listIssues(graphVersionId));
    }

    @PostMapping("/graph-versions/{graphVersionId}/validate")
    public ApiResponse<GraphApi.GraphVersionResponse> validate(@PathVariable long graphVersionId) {
        return ApiResponse.ok(graphVersionService.get(graphValidationService.validate(graphVersionId).graphVersion().getId()));
    }

    @PostMapping("/graph-versions/{graphVersionId}/publish")
    public ApiResponse<GraphApi.GraphVersionResponse> publish(@PathVariable long graphVersionId) {
        return ApiResponse.ok(graphVersionService.requestPublish(graphVersionId));
    }

    @PostMapping("/graph-versions/{graphVersionId}/retry-projection")
    public ApiResponse<GraphApi.GraphVersionResponse> retryProjection(@PathVariable long graphVersionId) {
        return ApiResponse.ok(graphVersionService.retryProjection(graphVersionId));
    }

    @GetMapping("/evidence")
    public ApiResponse<List<GraphApi.EvidenceResponse>> listEvidence(@RequestParam long courseId) {
        return ApiResponse.ok(evidenceImportService.listEvidence(courseId));
    }

    @GetMapping("/evidence/{evidenceId}/resolution-history")
    public ApiResponse<List<GraphApi.EvidenceResolutionHistoryResponse>> evidenceResolutionHistory(
            @PathVariable long evidenceId
    ) {
        return ApiResponse.ok(evidenceReresolutionService.history(evidenceId));
    }

    @GetMapping("/evidence-imports/{importRunId}/conflicts")
    public ApiResponse<List<GraphApi.EvidenceConflictResponse>> evidenceConflicts(@PathVariable long importRunId) {
        return ApiResponse.ok(evidenceImportService.conflicts(importRunId));
    }
}
