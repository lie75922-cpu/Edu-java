package com.smartlearning.graph.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.smartlearning.graph.domain.EvidenceReresolutionTrigger;

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
            String candidateInputId,
            String candidatePolicyVersion,
            String candidateStatus,
            String publishedGraphStatus,
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

    /** Candidate records are derived input for a draft only; they are never a Published Graph request. */
    public record CandidateTopicRelationRequest(
            @NotBlank @Size(max = 128) String candidateId,
            @NotBlank @Size(max = 128) String prerequisiteTopicExternalId,
            @NotBlank @Size(max = 128) String dependentTopicExternalId,
            @NotBlank @Size(max = 128) String derivationPolicyVersion,
            @NotBlank @Size(max = 64) String candidateStatus,
            @NotBlank @Size(max = 64) String publishedGraphStatus,
            @NotEmpty @Size(max = 10_000) List<@NotBlank @Size(max = 128) String> rawEvidenceIds
    ) {
        public CandidateTopicRelationRequest {
            rawEvidenceIds = rawEvidenceIds == null ? List.of() : List.copyOf(rawEvidenceIds);
        }
    }

    public record CandidateRelationImportRequest(
            @NotEmpty @Size(max = 10_000) List<@Valid CandidateTopicRelationRequest> candidates
    ) {
        public CandidateRelationImportRequest {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record CandidateRelationImportResult(
            Long importRunId,
            Long graphVersionId,
            String mode,
            String status,
            int createdCandidateRelations,
            int reusedCandidateRelations,
            int conflictCount,
            String graphPublicationStatus,
            int selfLoopCount,
            int cycleCount,
            List<EvidenceConflictResponse> conflicts
    ) {
    }

    public record EvidenceReresolutionRequest(
            @NotEmpty @Size(max = 1_000) List<@NotNull @Positive Long> evidenceIds,
            @NotNull EvidenceReresolutionTrigger triggerType
    ) {
        public EvidenceReresolutionRequest {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record EvidenceResolutionStateResponse(
            String resolutionStatus,
            String conflictCode,
            String resolutionDetail,
            Long sourceExerciseUnitId,
            Long targetExerciseUnitId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId
    ) {
    }

    public record DraftRelationImpactResponse(
            Long relationId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId,
            String action,
            int beforeEvidenceCount,
            int afterEvidenceCount,
            String beforeReviewStatus,
            String afterReviewStatus
    ) {
    }

    public record EvidenceReresolutionItemResponse(
            Long evidenceId,
            EvidenceResponse rawEvidence,
            EvidenceResolutionStateResponse currentResolution,
            EvidenceResolutionStateResponse proposedResolution,
            boolean resolutionChanged,
            List<DraftRelationImpactResponse> affectedDraftRelations
    ) {
    }

    public record EvidenceReresolutionResult(
            Long graphVersionId,
            String mode,
            String triggerType,
            int changedEvidenceCount,
            int unchangedEvidenceCount,
            List<EvidenceReresolutionItemResponse> evidence
    ) {
    }

    public record EvidenceResolutionHistoryResponse(
            Long id,
            Long evidenceId,
            Long graphVersionId,
            String oldResolutionStatus,
            String newResolutionStatus,
            Long oldSourceExerciseUnitId,
            Long oldTargetExerciseUnitId,
            Long oldSourceKnowledgePointId,
            Long oldTargetKnowledgePointId,
            Long newSourceExerciseUnitId,
            Long newTargetExerciseUnitId,
            Long newSourceKnowledgePointId,
            Long newTargetKnowledgePointId,
            String oldConflictCode,
            String newConflictCode,
            String oldResolutionDetail,
            String newResolutionDetail,
            Long operatorId,
            String triggerType,
            String beforeRelationIdsJson,
            String afterRelationIdsJson,
            String reconciliationJson,
            Instant createdAt
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
