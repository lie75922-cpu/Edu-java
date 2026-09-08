package com.smartlearning.graph.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class GraphApi {

    private GraphApi() {
    }

    public record CreateGraphVersionRequest(
            @NotNull Long courseId,
            @Size(max = 500) String description,
            boolean copyActive
    ) {
    }

    public record GraphVersionResponse(
            Long id,
            Long courseId,
            int versionNo,
            String status,
            String description,
            Long createdBy,
            Instant createdAt,
            Instant validatedAt,
            Instant publishedAt,
            String failureReason,
            boolean active
    ) {
    }

    public record ManualRelationRequest(
            @NotNull Long sourceKnowledgePointId,
            @NotNull Long targetKnowledgePointId,
            @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal confidence
    ) {
    }

    public record RelationReviewRequest(
            @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String reviewStatus
    ) {
    }

    public record GraphRelationResponse(
            Long id,
            Long graphVersionId,
            Long sourceKnowledgePointId,
            String sourceKnowledgeCode,
            String sourceKnowledgeName,
            Long targetKnowledgePointId,
            String targetKnowledgeCode,
            String targetKnowledgeName,
            String relationType,
            String relationSource,
            BigDecimal confidence,
            int evidenceCount,
            String reviewStatus,
            Long createdBy,
            Instant createdAt
    ) {
    }

    public record RawPrerequisiteEvidenceRequest(
            @NotBlank @Size(max = 128) String externalEvidenceId,
            @NotBlank @Size(max = 128) String sourceExerciseExternalId,
            @NotBlank @Size(max = 128) String targetExerciseExternalId,
            Map<String, Object> rawPayload
    ) {
    }

    public record EvidenceImportRequest(
            @NotEmpty @Size(max = 10_000) List<@Valid RawPrerequisiteEvidenceRequest> evidence
    ) {
        public EvidenceImportRequest {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record EvidenceResponse(
            Long id,
            Long courseId,
            String sourceType,
            String externalEvidenceId,
            String sourceExternalId,
            String targetExternalId,
            Long sourceExerciseUnitId,
            Long targetExerciseUnitId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId,
            String rawPayloadJson,
            String resolutionStatus,
            String conflictCode,
            String resolutionDetail,
            Instant createdAt
    ) {
    }

    public record EvidenceConflictResponse(
            Long id,
            String externalEvidenceId,
            String sourceExternalId,
            String targetExternalId,
            String conflictCode,
            String detailJson,
            Instant createdAt
    ) {
    }

    public record EvidenceImportResult(
            Long importRunId,
            Long graphVersionId,
            String mode,
            String status,
            int createdEvidence,
            int reusedEvidence,
            int resolvedEvidence,
            int candidateRelationsCreated,
            int candidateRelationsAggregated,
            int conflictCount,
            List<EvidenceConflictResponse> conflicts
    ) {
    }

    public record GraphValidationIssueResponse(
            Long id,
            Long relationId,
            String severity,
            String issueCode,
            String nodeIdsJson,
            String detailJson,
            Instant createdAt
    ) {
    }

    public record GraphNodeResponse(Long id, String knowledgeCode, String knowledgeName) {
    }

    public record GraphEdgeResponse(
            Long relationId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId,
            String relationType,
            String relationSource
    ) {
    }

    public record GraphViewResponse(
            Long graphVersionId,
            List<GraphNodeResponse> nodes,
            List<GraphEdgeResponse> edges
    ) {
    }

    public record GraphPathResponse(Long graphVersionId, List<Long> knowledgePointIds) {
    }
}
