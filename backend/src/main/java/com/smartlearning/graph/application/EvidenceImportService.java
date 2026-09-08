package com.smartlearning.graph.application;

import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.domain.EvidenceResolutionStatus;
import com.smartlearning.graph.domain.GraphVersion;
import com.smartlearning.graph.domain.KnowledgeRelation;
import com.smartlearning.graph.domain.KnowledgeRelationEvidence;
import com.smartlearning.graph.domain.KnowledgeRelationEvidenceConflict;
import com.smartlearning.graph.domain.KnowledgeRelationEvidenceImportRun;
import com.smartlearning.graph.domain.KnowledgeRelationEvidenceLink;
import com.smartlearning.graph.domain.RelationReviewStatus;
import com.smartlearning.graph.infrastructure.persistence.GraphVersionRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceConflictRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceImportRunRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceLinkRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class EvidenceImportService {

    private static final String JUNYI_RAW_PREREQUISITE = "JUNYI_RAW_PREREQUISITE";
    private static final String JUNYI_CATALOG = "JUNYI_CATALOG";
    private static final String PREREQUISITE = "PREREQUISITE";

    private final GraphVersionRepository graphVersionRepository;
    private final KnowledgeRelationEvidenceRepository evidenceRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository;
    private final KnowledgeRelationEvidenceImportRunRepository importRunRepository;
    private final KnowledgeRelationEvidenceConflictRepository conflictRepository;
    private final ExerciseUnitRepository exerciseUnitRepository;
    private final ExerciseKnowledgeRepository exerciseKnowledgeRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final ObjectMapper objectMapper;

    public EvidenceImportService(
            GraphVersionRepository graphVersionRepository,
            KnowledgeRelationEvidenceRepository evidenceRepository,
            KnowledgeRelationRepository relationRepository,
            KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository,
            KnowledgeRelationEvidenceImportRunRepository importRunRepository,
            KnowledgeRelationEvidenceConflictRepository conflictRepository,
            ExerciseUnitRepository exerciseUnitRepository,
            ExerciseKnowledgeRepository exerciseKnowledgeRepository,
            KnowledgePointRepository knowledgePointRepository,
            ObjectMapper objectMapper
    ) {
        this.graphVersionRepository = graphVersionRepository;
        this.evidenceRepository = evidenceRepository;
        this.relationRepository = relationRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
        this.importRunRepository = importRunRepository;
        this.conflictRepository = conflictRepository;
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.exerciseKnowledgeRepository = exerciseKnowledgeRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GraphApi.EvidenceImportResult dryRun(
            long graphVersionId,
            GraphApi.EvidenceImportRequest request,
            long requestedBy
    ) {
        return execute(graphVersionId, request, requestedBy, false);
    }

    @Transactional
    public GraphApi.EvidenceImportResult apply(
            long graphVersionId,
            GraphApi.EvidenceImportRequest request,
            long requestedBy
    ) {
        return execute(graphVersionId, request, requestedBy, true);
    }

    public List<GraphApi.EvidenceResponse> listEvidence(long courseId) {
        return evidenceRepository.findByCourseIdOrderByIdAsc(courseId).stream().map(this::toEvidenceResponse).toList();
    }

    public List<GraphApi.EvidenceResponse> listEvidenceForRelation(long graphVersionId, long relationId) {
        KnowledgeRelation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new NotFoundException("knowledge relation does not exist"));
        if (!relation.getGraphVersionId().equals(graphVersionId)) {
            throw new ConflictException("knowledge relation does not belong to this graph version");
        }
        return evidenceLinkRepository.findByRelationIdOrderByIdAsc(relationId).stream()
                .map(link -> evidenceRepository.findById(link.getEvidenceId())
                        .orElseThrow(() -> new IllegalStateException("evidence link references a missing evidence record")))
                .map(this::toEvidenceResponse)
                .toList();
    }

    public List<GraphApi.EvidenceConflictResponse> conflicts(long importRunId) {
        if (!importRunRepository.existsById(importRunId)) {
            throw new NotFoundException("evidence import run does not exist");
        }
        return conflictRepository.findByImportRunIdOrderByIdAsc(importRunId).stream().map(this::toConflictResponse).toList();
    }

    private GraphApi.EvidenceImportResult execute(
            long graphVersionId,
            GraphApi.EvidenceImportRequest request,
            long requestedBy,
            boolean apply
    ) {
        GraphVersion version = requireEditableVersion(graphVersionId);
        String mode = apply ? "APPLY" : "DRY_RUN";
        KnowledgeRelationEvidenceImportRun run = importRunRepository.save(new KnowledgeRelationEvidenceImportRun(
                version.getId(), version.getCourseId(), requestedBy, mode, JUNYI_RAW_PREREQUISITE
        ));
        MutableSummary summary = new MutableSummary();
        List<ConflictDraft> conflicts = new ArrayList<>();
        Set<String> inputEvidenceIds = new HashSet<>();
        Set<PointPair> dryRunCandidatePairs = new LinkedHashSet<>();

        for (GraphApi.RawPrerequisiteEvidenceRequest input : request.evidence()) {
            if (!inputEvidenceIds.add(input.externalEvidenceId())) {
                conflicts.add(ConflictDraft.of(input, "DUPLICATE_EVIDENCE_RECORD", "duplicate external evidence ID in this import request"));
                continue;
            }

            Optional<KnowledgeRelationEvidence> existing = evidenceRepository
                    .findByCourseIdAndSourceTypeAndExternalEvidenceId(
                            version.getCourseId(), JUNYI_RAW_PREREQUISITE, input.externalEvidenceId()
                    );
            KnowledgeRelationEvidence evidence;
            if (existing.isPresent()) {
                evidence = existing.get();
                if (!sameEndpoints(evidence, input)) {
                    conflicts.add(ConflictDraft.of(input, "EVIDENCE_RECORD_ID_REUSED_WITH_DIFFERENT_ENDPOINTS",
                            "existing evidence record ID is bound to different exercise external IDs"));
                    continue;
                }
                summary.reusedEvidence++;
            } else {
                Resolution resolution = resolve(version.getCourseId(), input);
                if (!resolution.resolved()) {
                    conflicts.add(ConflictDraft.of(input, resolution.conflictCode(), resolution.detail()));
                }
                evidence = new KnowledgeRelationEvidence(
                        version.getCourseId(),
                        JUNYI_RAW_PREREQUISITE,
                        input.externalEvidenceId(),
                        input.sourceExerciseExternalId(),
                        input.targetExerciseExternalId(),
                        resolution.sourceExerciseUnitId(),
                        resolution.targetExerciseUnitId(),
                        resolution.sourceKnowledgePointId(),
                        resolution.targetKnowledgePointId(),
                        toJson(input.rawPayload() == null ? Map.of() : input.rawPayload()),
                        resolution.status(),
                        resolution.conflictCode(),
                        resolution.detail()
                );
                summary.createdEvidence++;
                if (apply) {
                    evidence = evidenceRepository.save(evidence);
                }
            }

            if (evidence.isResolved()) {
                summary.resolvedEvidence++;
                PointPair pair = new PointPair(evidence.getSourceKnowledgePointId(), evidence.getTargetKnowledgePointId());
                if (apply) {
                    attachResolvedEvidence(version, evidence, requestedBy, summary);
                } else {
                    accountForDryRunCandidate(version, pair, dryRunCandidatePairs, summary);
                }
            }
        }

        List<KnowledgeRelationEvidenceConflict> persisted = conflicts.stream()
                .map(conflict -> conflictRepository.save(new KnowledgeRelationEvidenceConflict(
                        run.getId(), conflict.externalEvidenceId(), conflict.sourceExternalId(), conflict.targetExternalId(),
                        conflict.code(), toJson(Map.of("message", conflict.detail()))
                )))
                .toList();
        summary.conflictCount = persisted.size();
        String status = summary.conflictCount == 0
                ? (apply ? "COMPLETED" : "DRY_RUN_COMPLETED")
                : (apply ? "COMPLETED_WITH_CONFLICTS" : "DRY_RUN_COMPLETED_WITH_CONFLICTS");
        run.complete(status, toJson(summary.snapshot()));

        return new GraphApi.EvidenceImportResult(
                run.getId(), version.getId(), mode, status, summary.createdEvidence, summary.reusedEvidence,
                summary.resolvedEvidence, summary.candidateRelationsCreated, summary.candidateRelationsAggregated,
                summary.conflictCount, persisted.stream().map(this::toConflictResponse).toList()
        );
    }

    private void attachResolvedEvidence(
            GraphVersion version,
            KnowledgeRelationEvidence evidence,
            long requestedBy,
            MutableSummary summary
    ) {
        KnowledgeRelation relation = relationRepository
                .findByGraphVersionIdAndSourceKnowledgePointIdAndTargetKnowledgePointIdAndRelationType(
                        version.getId(), evidence.getSourceKnowledgePointId(), evidence.getTargetKnowledgePointId(), PREREQUISITE
                )
                .orElseGet(() -> {
                    summary.candidateRelationsCreated++;
                    return relationRepository.save(new KnowledgeRelation(
                            version.getId(), evidence.getSourceKnowledgePointId(), evidence.getTargetKnowledgePointId(),
                            PREREQUISITE, "EVIDENCE", null, 0, RelationReviewStatus.CANDIDATE, requestedBy
                    ));
                });
        if (!evidenceLinkRepository.existsByRelationIdAndEvidenceId(relation.getId(), evidence.getId())) {
            evidenceLinkRepository.save(new KnowledgeRelationEvidenceLink(relation.getId(), evidence.getId()));
            relation.incrementEvidenceCount();
            if (relation.getEvidenceCount() > 1) {
                summary.candidateRelationsAggregated++;
            }
        }
    }

    private void accountForDryRunCandidate(
            GraphVersion version,
            PointPair pair,
            Set<PointPair> requestedPairs,
            MutableSummary summary
    ) {
        boolean exists = relationRepository
                .findByGraphVersionIdAndSourceKnowledgePointIdAndTargetKnowledgePointIdAndRelationType(
                        version.getId(), pair.sourceKnowledgePointId(), pair.targetKnowledgePointId(), PREREQUISITE
                )
                .isPresent();
        if (!exists && requestedPairs.add(pair)) {
            summary.candidateRelationsCreated++;
        } else {
            summary.candidateRelationsAggregated++;
        }
    }

    private Resolution resolve(long courseId, GraphApi.RawPrerequisiteEvidenceRequest input) {
        List<ExerciseUnit> sourceMatches = exerciseUnitRepository.findByCourseIdAndSourceTypeAndExternalId(
                courseId, JUNYI_CATALOG, input.sourceExerciseExternalId()
        );
        if (sourceMatches.isEmpty()) {
            return Resolution.unresolved(EvidenceResolutionStatus.UNRESOLVED_IDENTITY, "MISSING_SOURCE_EXERCISE",
                    "source exercise external ID does not resolve to an ExerciseUnit");
        }
        if (sourceMatches.size() > 1 || !"RESOLVED".equals(sourceMatches.getFirst().getIdentityStatus())) {
            return Resolution.unresolved(EvidenceResolutionStatus.UNRESOLVED_IDENTITY, "AMBIGUOUS_SOURCE_EXERCISE",
                    "source exercise external ID does not resolve uniquely to an ExerciseUnit");
        }
        ExerciseUnit sourceExercise = sourceMatches.getFirst();

        List<ExerciseUnit> targetMatches = exerciseUnitRepository.findByCourseIdAndSourceTypeAndExternalId(
                courseId, JUNYI_CATALOG, input.targetExerciseExternalId()
        );
        if (targetMatches.isEmpty()) {
            return Resolution.unresolvedWithSource(sourceExercise.getId(), "MISSING_TARGET_EXERCISE",
                    "target exercise external ID does not resolve to an ExerciseUnit");
        }
        if (targetMatches.size() > 1 || !"RESOLVED".equals(targetMatches.getFirst().getIdentityStatus())) {
            return Resolution.unresolvedWithSource(sourceExercise.getId(), "AMBIGUOUS_TARGET_EXERCISE",
                    "target exercise external ID does not resolve uniquely to an ExerciseUnit");
        }
        ExerciseUnit targetExercise = targetMatches.getFirst();

        List<ExerciseKnowledge> sourceMappings = exerciseKnowledgeRepository.findByExerciseUnitIdOrderByIdAsc(sourceExercise.getId());
        if (sourceMappings.isEmpty()) {
            return Resolution.unmappedSource(sourceExercise.getId(), targetExercise.getId(), "source ExerciseUnit has no KnowledgePoint mapping");
        }
        if (sourceMappings.size() > 1) {
            return Resolution.ambiguousSourceMapping(sourceExercise.getId(), targetExercise.getId(),
                    "source ExerciseUnit maps to multiple KnowledgePoints and cannot be auto-projected");
        }
        List<ExerciseKnowledge> targetMappings = exerciseKnowledgeRepository.findByExerciseUnitIdOrderByIdAsc(targetExercise.getId());
        if (targetMappings.isEmpty()) {
            return Resolution.unmappedTarget(sourceExercise.getId(), targetExercise.getId(), "target ExerciseUnit has no KnowledgePoint mapping");
        }
        if (targetMappings.size() > 1) {
            return Resolution.ambiguousTargetMapping(sourceExercise.getId(), targetExercise.getId(),
                    "target ExerciseUnit maps to multiple KnowledgePoints and cannot be auto-projected");
        }
        Long sourcePointId = sourceMappings.getFirst().getKnowledgePointId();
        Long targetPointId = targetMappings.getFirst().getKnowledgePointId();
        KnowledgePoint sourcePoint = knowledgePointRepository.findById(sourcePointId).orElse(null);
        KnowledgePoint targetPoint = knowledgePointRepository.findById(targetPointId).orElse(null);
        if (sourcePoint == null || targetPoint == null || sourcePoint.getCourseId() != courseId || targetPoint.getCourseId() != courseId) {
            return Resolution.unresolved(EvidenceResolutionStatus.UNRESOLVED_IDENTITY, "INVALID_KNOWLEDGE_MAPPING",
                    "ExerciseUnit mapping does not resolve to same-course KnowledgePoints");
        }
        if (sourcePointId.equals(targetPointId)) {
            return Resolution.rejectedSelfLoop(sourceExercise.getId(), targetExercise.getId(), sourcePointId,
                    "source and target ExerciseUnits map to the same KnowledgePoint");
        }
        return Resolution.resolved(sourceExercise.getId(), targetExercise.getId(), sourcePointId, targetPointId);
    }

    private GraphVersion requireEditableVersion(long graphVersionId) {
        GraphVersion version = graphVersionRepository.findById(graphVersionId)
                .orElseThrow(() -> new NotFoundException("graph version does not exist"));
        version.reopenForEditing();
        return version;
    }

    private boolean sameEndpoints(KnowledgeRelationEvidence existing, GraphApi.RawPrerequisiteEvidenceRequest input) {
        return existing.getSourceExternalId().equals(input.sourceExerciseExternalId())
                && existing.getTargetExternalId().equals(input.targetExerciseExternalId());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("unable to serialize evidence import audit data", ex);
        }
    }

    private GraphApi.EvidenceResponse toEvidenceResponse(KnowledgeRelationEvidence evidence) {
        return new GraphApi.EvidenceResponse(
                evidence.getId(), evidence.getCourseId(), evidence.getSourceType(), evidence.getExternalEvidenceId(),
                evidence.getSourceExternalId(), evidence.getTargetExternalId(), evidence.getSourceExerciseUnitId(),
                evidence.getTargetExerciseUnitId(), evidence.getSourceKnowledgePointId(), evidence.getTargetKnowledgePointId(),
                evidence.getRawPayloadJson(), evidence.getResolutionStatus().name(), evidence.getConflictCode(),
                evidence.getResolutionDetail(), evidence.getCreatedAt()
        );
    }

    private GraphApi.EvidenceConflictResponse toConflictResponse(KnowledgeRelationEvidenceConflict conflict) {
        return new GraphApi.EvidenceConflictResponse(
                conflict.getId(), conflict.getExternalEvidenceId(), conflict.getSourceExternalId(), conflict.getTargetExternalId(),
                conflict.getConflictCode(), conflict.getDetailJson(), conflict.getCreatedAt()
        );
    }

    private record PointPair(Long sourceKnowledgePointId, Long targetKnowledgePointId) {
    }

    private record ConflictDraft(
            String externalEvidenceId,
            String sourceExternalId,
            String targetExternalId,
            String code,
            String detail
    ) {
        static ConflictDraft of(GraphApi.RawPrerequisiteEvidenceRequest input, String code, String detail) {
            return new ConflictDraft(input.externalEvidenceId(), input.sourceExerciseExternalId(), input.targetExerciseExternalId(), code, detail);
        }
    }

    private record Resolution(
            EvidenceResolutionStatus status,
            String conflictCode,
            String detail,
            Long sourceExerciseUnitId,
            Long targetExerciseUnitId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId
    ) {
        static Resolution resolved(Long sourceExerciseId, Long targetExerciseId, Long sourcePointId, Long targetPointId) {
            return new Resolution(EvidenceResolutionStatus.RESOLVED, null, null,
                    sourceExerciseId, targetExerciseId, sourcePointId, targetPointId);
        }

        static Resolution unresolved(EvidenceResolutionStatus status, String code, String detail) {
            return new Resolution(status, code, detail, null, null, null, null);
        }

        static Resolution unresolvedWithSource(Long sourceExerciseId, String code, String detail) {
            return new Resolution(EvidenceResolutionStatus.UNRESOLVED_IDENTITY, code, detail,
                    sourceExerciseId, null, null, null);
        }

        static Resolution unmappedSource(Long sourceExerciseId, Long targetExerciseId, String detail) {
            return new Resolution(EvidenceResolutionStatus.UNMAPPED_SOURCE, "UNMAPPED_SOURCE", detail,
                    sourceExerciseId, targetExerciseId, null, null);
        }

        static Resolution unmappedTarget(Long sourceExerciseId, Long targetExerciseId, String detail) {
            return new Resolution(EvidenceResolutionStatus.UNMAPPED_TARGET, "UNMAPPED_TARGET", detail,
                    sourceExerciseId, targetExerciseId, null, null);
        }

        static Resolution ambiguousSourceMapping(Long sourceExerciseId, Long targetExerciseId, String detail) {
            return new Resolution(EvidenceResolutionStatus.AMBIGUOUS_SOURCE_MAPPING, "AMBIGUOUS_SOURCE_MAPPING", detail,
                    sourceExerciseId, targetExerciseId, null, null);
        }

        static Resolution ambiguousTargetMapping(Long sourceExerciseId, Long targetExerciseId, String detail) {
            return new Resolution(EvidenceResolutionStatus.AMBIGUOUS_TARGET_MAPPING, "AMBIGUOUS_TARGET_MAPPING", detail,
                    sourceExerciseId, targetExerciseId, null, null);
        }

        static Resolution rejectedSelfLoop(Long sourceExerciseId, Long targetExerciseId, Long pointId, String detail) {
            return new Resolution(EvidenceResolutionStatus.REJECTED_SELF_LOOP, "REJECTED_SELF_LOOP", detail,
                    sourceExerciseId, targetExerciseId, pointId, pointId);
        }

        boolean resolved() {
            return status == EvidenceResolutionStatus.RESOLVED;
        }
    }

    private static final class MutableSummary {
        private int createdEvidence;
        private int reusedEvidence;
        private int resolvedEvidence;
        private int candidateRelationsCreated;
        private int candidateRelationsAggregated;
        private int conflictCount;

        private SummarySnapshot snapshot() {
            return new SummarySnapshot(
                    createdEvidence, reusedEvidence, resolvedEvidence, candidateRelationsCreated,
                    candidateRelationsAggregated, conflictCount
            );
        }
    }

    private record SummarySnapshot(
            int createdEvidence,
            int reusedEvidence,
            int resolvedEvidence,
            int candidateRelationsCreated,
            int candidateRelationsAggregated,
            int conflictCount
    ) {
    }
}
