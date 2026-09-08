package com.smartlearning.graph.application;

import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.graph.api.GraphApi;
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
    private static final String PREREQUISITE = "PREREQUISITE";

    private final GraphVersionRepository graphVersionRepository;
    private final KnowledgeRelationEvidenceRepository evidenceRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository;
    private final KnowledgeRelationEvidenceImportRunRepository importRunRepository;
    private final KnowledgeRelationEvidenceConflictRepository conflictRepository;
    private final EvidenceResolutionResolver evidenceResolutionResolver;
    private final ObjectMapper objectMapper;

    public EvidenceImportService(
            GraphVersionRepository graphVersionRepository,
            KnowledgeRelationEvidenceRepository evidenceRepository,
            KnowledgeRelationRepository relationRepository,
            KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository,
            KnowledgeRelationEvidenceImportRunRepository importRunRepository,
            KnowledgeRelationEvidenceConflictRepository conflictRepository,
            EvidenceResolutionResolver evidenceResolutionResolver,
            ObjectMapper objectMapper
    ) {
        this.graphVersionRepository = graphVersionRepository;
        this.evidenceRepository = evidenceRepository;
        this.relationRepository = relationRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
        this.importRunRepository = importRunRepository;
        this.conflictRepository = conflictRepository;
        this.evidenceResolutionResolver = evidenceResolutionResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
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
        GraphVersion version = requireImportVersion(graphVersionId, apply);
        String mode = apply ? "APPLY" : "DRY_RUN";
        KnowledgeRelationEvidenceImportRun run = apply ? importRunRepository.save(new KnowledgeRelationEvidenceImportRun(
                version.getId(), version.getCourseId(), requestedBy, mode, JUNYI_RAW_PREREQUISITE
        )) : null;
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
                KnowledgeRelationEvidence.ResolutionState resolution = evidenceResolutionResolver.resolve(
                        version.getCourseId(), JUNYI_RAW_PREREQUISITE,
                        input.sourceExerciseExternalId(), input.targetExerciseExternalId()
                );
                if (!resolution.isResolved()) {
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

        List<GraphApi.EvidenceConflictResponse> conflictResponses;
        if (apply) {
            List<KnowledgeRelationEvidenceConflict> persisted = conflicts.stream()
                    .map(conflict -> conflictRepository.save(new KnowledgeRelationEvidenceConflict(
                            run.getId(), conflict.externalEvidenceId(), conflict.sourceExternalId(), conflict.targetExternalId(),
                            conflict.code(), toJson(Map.of("message", conflict.detail()))
                    )))
                    .toList();
            summary.conflictCount = persisted.size();
            conflictResponses = persisted.stream().map(this::toConflictResponse).toList();
        } else {
            summary.conflictCount = conflicts.size();
            conflictResponses = conflicts.stream().map(this::toConflictResponse).toList();
        }
        String status = summary.conflictCount == 0
                ? (apply ? "COMPLETED" : "DRY_RUN_COMPLETED")
                : (apply ? "COMPLETED_WITH_CONFLICTS" : "DRY_RUN_COMPLETED_WITH_CONFLICTS");
        if (apply) {
            run.complete(status, toJson(summary.snapshot()));
        }

        return new GraphApi.EvidenceImportResult(
                run == null ? null : run.getId(), version.getId(), mode, status, summary.createdEvidence, summary.reusedEvidence,
                summary.resolvedEvidence, summary.candidateRelationsCreated, summary.candidateRelationsAggregated,
                summary.conflictCount, conflictResponses
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
            relation.reconcileEvidenceCount(linkCount(relation.getId()));
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

    private GraphVersion requireImportVersion(long graphVersionId, boolean apply) {
        GraphVersion version = graphVersionRepository.findById(graphVersionId)
                .orElseThrow(() -> new NotFoundException("graph version does not exist"));
        if (version.getStatus() != com.smartlearning.graph.domain.GraphVersionStatus.DRAFT
                && version.getStatus() != com.smartlearning.graph.domain.GraphVersionStatus.VALIDATION_FAILED) {
            throw new ConflictException("only a draft or validation-failed graph version can import evidence");
        }
        if (apply && version.getStatus() == com.smartlearning.graph.domain.GraphVersionStatus.VALIDATION_FAILED) {
            version.reopenForEditing();
        }
        return version;
    }

    private int linkCount(long relationId) {
        return Math.toIntExact(evidenceLinkRepository.countByRelationId(relationId));
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

    private GraphApi.EvidenceConflictResponse toConflictResponse(ConflictDraft conflict) {
        return new GraphApi.EvidenceConflictResponse(
                null, conflict.externalEvidenceId(), conflict.sourceExternalId(), conflict.targetExternalId(),
                conflict.code(), toJson(Map.of("message", conflict.detail())), null
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
