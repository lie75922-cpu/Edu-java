package com.smartlearning.graph.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.application.CourseAccessService;
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
    private static final String JUNYI_TOPIC_CANDIDATE = "JUNYI_TOPIC_CANDIDATE";
    private static final String PREREQUISITE = "PREREQUISITE";
    private static final String DERIVED_POLICY = "DERIVED_POLICY";
    private static final String TOPIC_SOURCE = "JUNYI_TOPIC";

    private final GraphVersionRepository graphVersionRepository;
    private final KnowledgeRelationEvidenceRepository evidenceRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository;
    private final KnowledgeRelationEvidenceImportRunRepository importRunRepository;
    private final KnowledgeRelationEvidenceConflictRepository conflictRepository;
    private final EvidenceResolutionResolver evidenceResolutionResolver;
    private final KnowledgePointRepository knowledgePointRepository;
    private final CandidateGraphPublicationGuard publicationGuard;
    private final ObjectMapper objectMapper;
    private final CourseAccessService courseAccessService;

    public EvidenceImportService(
            GraphVersionRepository graphVersionRepository,
            KnowledgeRelationEvidenceRepository evidenceRepository,
            KnowledgeRelationRepository relationRepository,
            KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository,
            KnowledgeRelationEvidenceImportRunRepository importRunRepository,
            KnowledgeRelationEvidenceConflictRepository conflictRepository,
            EvidenceResolutionResolver evidenceResolutionResolver,
            KnowledgePointRepository knowledgePointRepository,
            CandidateGraphPublicationGuard publicationGuard,
            ObjectMapper objectMapper,
            CourseAccessService courseAccessService
    ) {
        this.graphVersionRepository = graphVersionRepository;
        this.evidenceRepository = evidenceRepository;
        this.relationRepository = relationRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
        this.importRunRepository = importRunRepository;
        this.conflictRepository = conflictRepository;
        this.evidenceResolutionResolver = evidenceResolutionResolver;
        this.knowledgePointRepository = knowledgePointRepository;
        this.publicationGuard = publicationGuard;
        this.objectMapper = objectMapper;
        this.courseAccessService = courseAccessService;
    }

    @Transactional(readOnly = true)
    public GraphApi.EvidenceImportResult dryRunForTeaching(
            long graphVersionId,
            GraphApi.EvidenceImportRequest request,
            CurrentUser user
    ) {
        requireTeachingVersion(graphVersionId, user);
        return dryRun(graphVersionId, request, user.id());
    }

    @Transactional
    public GraphApi.EvidenceImportResult applyForTeaching(
            long graphVersionId,
            GraphApi.EvidenceImportRequest request,
            CurrentUser user
    ) {
        requireTeachingVersion(graphVersionId, user);
        return apply(graphVersionId, request, user.id());
    }

    @Transactional(readOnly = true)
    public GraphApi.CandidateRelationImportResult dryRunCandidatesForTeaching(
            long graphVersionId,
            GraphApi.CandidateRelationImportRequest request,
            CurrentUser user
    ) {
        requireTeachingVersion(graphVersionId, user);
        return dryRunCandidates(graphVersionId, request, user.id());
    }

    @Transactional
    public GraphApi.CandidateRelationImportResult applyCandidatesForTeaching(
            long graphVersionId,
            GraphApi.CandidateRelationImportRequest request,
            CurrentUser user
    ) {
        requireTeachingVersion(graphVersionId, user);
        return applyCandidates(graphVersionId, request, user.id());
    }

    public List<GraphApi.EvidenceResponse> listEvidenceForTeaching(long courseId, CurrentUser user) {
        courseAccessService.requireTeachingAccess(courseId, user);
        return listEvidence(courseId);
    }

    public List<GraphApi.EvidenceResponse> listEvidenceForRelationForTeaching(
            long graphVersionId,
            long relationId,
            CurrentUser user
    ) {
        requireTeachingVersion(graphVersionId, user);
        return listEvidenceForRelation(graphVersionId, relationId);
    }

    public List<GraphApi.EvidenceConflictResponse> conflictsForTeaching(long importRunId, CurrentUser user) {
        KnowledgeRelationEvidenceImportRun run = importRunRepository.findById(importRunId)
                .orElseThrow(() -> new NotFoundException("evidence import run does not exist"));
        courseAccessService.requireTeachingAccess(run.getCourseId(), user);
        return conflicts(importRunId);
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

    @Transactional(readOnly = true)
    public GraphApi.CandidateRelationImportResult dryRunCandidates(
            long graphVersionId,
            GraphApi.CandidateRelationImportRequest request,
            long requestedBy
    ) {
        return executeCandidates(graphVersionId, request, requestedBy, false);
    }

    @Transactional
    public GraphApi.CandidateRelationImportResult applyCandidates(
            long graphVersionId,
            GraphApi.CandidateRelationImportRequest request,
            long requestedBy
    ) {
        return executeCandidates(graphVersionId, request, requestedBy, true);
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

    private GraphVersion requireTeachingVersion(long graphVersionId, CurrentUser user) {
        GraphVersion version = graphVersionRepository.findById(graphVersionId)
                .orElseThrow(() -> new NotFoundException("graph version does not exist"));
        courseAccessService.requireTeachingAccess(version.getCourseId(), user);
        return version;
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

    /**
     * Imports the Foundation-derived Topic pairs only after raw evidence has been stored. Raw evidence
     * never calls this method implicitly. Candidate relations stay CANDIDATE and are guarded before any
     * separate reviewer can request publication.
     */
    private GraphApi.CandidateRelationImportResult executeCandidates(
            long graphVersionId,
            GraphApi.CandidateRelationImportRequest request,
            long requestedBy,
            boolean apply
    ) {
        GraphVersion version = requireImportVersion(graphVersionId, apply);
        String mode = apply ? "APPLY" : "DRY_RUN";
        KnowledgeRelationEvidenceImportRun run = apply ? importRunRepository.save(new KnowledgeRelationEvidenceImportRun(
                version.getId(), version.getCourseId(), requestedBy, mode, JUNYI_TOPIC_CANDIDATE
        )) : null;
        CandidateSummary summary = new CandidateSummary();
        List<ConflictDraft> conflicts = new ArrayList<>();
        Set<String> candidateIds = new HashSet<>();
        List<CandidateGraphPublicationGuard.Edge> requestedEdges = new ArrayList<>();

        for (GraphApi.CandidateTopicRelationRequest input : request.candidates()) {
            if (!candidateIds.add(input.candidateId())) {
                conflicts.add(candidateConflict(input, "DUPLICATE_CANDIDATE_RECORD", "duplicate candidate ID in this import request"));
                continue;
            }
            CandidateResolution resolution = resolveCandidate(version.getCourseId(), input);
            if (!resolution.accepted()) {
                conflicts.add(candidateConflict(input, resolution.conflictCode(), resolution.detail()));
                continue;
            }
            requestedEdges.add(new CandidateGraphPublicationGuard.Edge(
                    resolution.prerequisite().getId(), resolution.dependent().getId()
            ));
            Optional<KnowledgeRelation> existing = relationRepository
                    .findByGraphVersionIdAndSourceKnowledgePointIdAndTargetKnowledgePointIdAndRelationType(
                            version.getId(), resolution.prerequisite().getId(), resolution.dependent().getId(), PREREQUISITE
                    );
            KnowledgeRelation relation;
            if (existing.isPresent()) {
                relation = existing.get();
                if (!DERIVED_POLICY.equals(relation.getRelationSource())
                        || !input.candidateId().equals(relation.getCandidateInputId())
                        || !input.derivationPolicyVersion().equals(relation.getCandidatePolicyVersion())
                        || !input.candidateStatus().equals(relation.getCandidateStatus())
                        || !input.publishedGraphStatus().equals(relation.getPublishedGraphStatus())) {
                    conflicts.add(candidateConflict(input, "CANDIDATE_PAIR_ALREADY_OWNED",
                            "the draft already contains this Topic pair from a different source or policy"));
                    continue;
                }
                summary.reusedCandidateRelations++;
            } else {
                summary.createdCandidateRelations++;
                relation = apply ? relationRepository.save(new KnowledgeRelation(
                        version.getId(), resolution.prerequisite().getId(), resolution.dependent().getId(),
                        PREREQUISITE, DERIVED_POLICY, input.candidateId(), input.derivationPolicyVersion(),
                        input.candidateStatus(), input.publishedGraphStatus(),
                        null, 0, RelationReviewStatus.CANDIDATE, requestedBy
                )) : null;
            }
            if (apply && relation != null) {
                attachCandidateEvidence(relation, resolution.evidence());
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

        List<CandidateGraphPublicationGuard.Edge> allDraftEdges = relationRepository
                .findByGraphVersionIdOrderByIdAsc(version.getId()).stream()
                .filter(relation -> relation.getReviewStatus() != RelationReviewStatus.REJECTED)
                .map(relation -> new CandidateGraphPublicationGuard.Edge(
                        relation.getSourceKnowledgePointId(), relation.getTargetKnowledgePointId()
                ))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        allDraftEdges.addAll(requestedEdges);
        CandidateGraphPublicationGuard.Outcome guard = publicationGuard.assess(allDraftEdges);
        String status = summary.conflictCount == 0
                ? (apply ? "COMPLETED" : "DRY_RUN_COMPLETED")
                : (apply ? "COMPLETED_WITH_CONFLICTS" : "DRY_RUN_COMPLETED_WITH_CONFLICTS");
        if (apply) {
            run.complete(status, toJson(summary.snapshot(guard)));
        }
        return new GraphApi.CandidateRelationImportResult(
                run == null ? null : run.getId(), version.getId(), mode, status, summary.createdCandidateRelations,
                summary.reusedCandidateRelations, summary.conflictCount, guard.publicationStatus(), guard.selfLoopCount(),
                guard.cycleCount(), conflictResponses
        );
    }

    private CandidateResolution resolveCandidate(long courseId, GraphApi.CandidateTopicRelationRequest input) {
        if (!"REVIEW_REQUIRED_NOT_PUBLISHED".equals(input.candidateStatus())) {
            return CandidateResolution.rejected("INVALID_CANDIDATE_STATUS",
                    "derived Topic input must remain REVIEW_REQUIRED_NOT_PUBLISHED");
        }
        if (!"NOT_PUBLISHED".equals(input.publishedGraphStatus())) {
            return CandidateResolution.rejected("INVALID_CANDIDATE_PUBLICATION_STATUS",
                    "derived Topic input cannot claim a Published Graph status");
        }
        List<KnowledgePoint> prerequisites = knowledgePointRepository
                .findByCourseIdAndSourceTypeAndExternalId(courseId, TOPIC_SOURCE, input.prerequisiteTopicExternalId());
        if (prerequisites.size() != 1) {
            return CandidateResolution.rejected("UNRESOLVED_PREREQUISITE_TOPIC", "candidate prerequisite Topic does not resolve uniquely");
        }
        List<KnowledgePoint> dependents = knowledgePointRepository
                .findByCourseIdAndSourceTypeAndExternalId(courseId, TOPIC_SOURCE, input.dependentTopicExternalId());
        if (dependents.size() != 1) {
            return CandidateResolution.rejected("UNRESOLVED_DEPENDENT_TOPIC", "candidate dependent Topic does not resolve uniquely");
        }
        List<KnowledgeRelationEvidence> evidence = new ArrayList<>();
        for (String evidenceId : new java.util.LinkedHashSet<>(input.rawEvidenceIds())) {
            KnowledgeRelationEvidence item = evidenceRepository
                    .findByCourseIdAndSourceTypeAndExternalEvidenceId(courseId, JUNYI_RAW_PREREQUISITE, evidenceId)
                    .orElse(null);
            if (item == null) {
                return CandidateResolution.rejected("MISSING_RAW_EVIDENCE", "candidate references raw evidence that has not been imported");
            }
            if (!item.isResolved()
                    || !prerequisites.getFirst().getId().equals(item.getSourceKnowledgePointId())
                    || !dependents.getFirst().getId().equals(item.getTargetKnowledgePointId())) {
                return CandidateResolution.rejected("EVIDENCE_DOES_NOT_SUPPORT_CANDIDATE",
                        "candidate evidence is unresolved or does not project to the stated Topic pair");
            }
            evidence.add(item);
        }
        return CandidateResolution.accepted(prerequisites.getFirst(), dependents.getFirst(), List.copyOf(evidence));
    }

    private void attachCandidateEvidence(KnowledgeRelation relation, List<KnowledgeRelationEvidence> evidence) {
        for (KnowledgeRelationEvidence item : evidence) {
            if (!evidenceLinkRepository.existsByRelationIdAndEvidenceId(relation.getId(), item.getId())) {
                evidenceLinkRepository.save(new KnowledgeRelationEvidenceLink(relation.getId(), item.getId()));
            }
        }
        relation.reconcileEvidenceCount(linkCount(relation.getId()));
    }

    private ConflictDraft candidateConflict(
            GraphApi.CandidateTopicRelationRequest input,
            String code,
            String detail
    ) {
        return new ConflictDraft(input.candidateId(), input.prerequisiteTopicExternalId(), input.dependentTopicExternalId(), code, detail);
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

    private record CandidateResolution(
            boolean accepted,
            String conflictCode,
            String detail,
            KnowledgePoint prerequisite,
            KnowledgePoint dependent,
            List<KnowledgeRelationEvidence> evidence
    ) {
        static CandidateResolution rejected(String conflictCode, String detail) {
            return new CandidateResolution(false, conflictCode, detail, null, null, List.of());
        }

        static CandidateResolution accepted(
                KnowledgePoint prerequisite,
                KnowledgePoint dependent,
                List<KnowledgeRelationEvidence> evidence
        ) {
            return new CandidateResolution(true, null, null, prerequisite, dependent, evidence);
        }
    }

    private static final class CandidateSummary {
        private int createdCandidateRelations;
        private int reusedCandidateRelations;
        private int conflictCount;

        private CandidateSummarySnapshot snapshot(CandidateGraphPublicationGuard.Outcome guard) {
            return new CandidateSummarySnapshot(
                    createdCandidateRelations, reusedCandidateRelations, conflictCount,
                    guard.publicationStatus(), guard.selfLoopCount(), guard.cycleCount()
            );
        }
    }

    private record CandidateSummarySnapshot(
            int createdCandidateRelations,
            int reusedCandidateRelations,
            int conflictCount,
            String graphPublicationStatus,
            int selfLoopCount,
            int cycleCount
    ) {
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
