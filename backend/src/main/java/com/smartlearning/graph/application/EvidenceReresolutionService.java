package com.smartlearning.graph.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.domain.EvidenceReresolutionTrigger;
import com.smartlearning.graph.domain.GraphVersion;
import com.smartlearning.graph.domain.GraphVersionStatus;
import com.smartlearning.graph.domain.KnowledgeRelation;
import com.smartlearning.graph.domain.KnowledgeRelationEvidence;
import com.smartlearning.graph.domain.KnowledgeRelationEvidenceLink;
import com.smartlearning.graph.domain.KnowledgeRelationEvidenceResolutionHistory;
import com.smartlearning.graph.domain.RelationReviewStatus;
import com.smartlearning.graph.infrastructure.persistence.GraphVersionRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceLinkRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceResolutionHistoryRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recomputes the mutable mapping projection of immutable raw Evidence and reconciles one explicit editable Draft.
 * It never publishes, archives, projects, or changes a course active-graph pointer.
 */
@Service
public class EvidenceReresolutionService {

    private static final String PREREQUISITE = "PREREQUISITE";

    private final GraphVersionRepository graphVersionRepository;
    private final KnowledgeRelationEvidenceRepository evidenceRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository;
    private final KnowledgeRelationEvidenceResolutionHistoryRepository historyRepository;
    private final EvidenceResolutionResolver resolutionResolver;
    private final ObjectMapper objectMapper;
    private final CourseAccessService courseAccessService;

    public EvidenceReresolutionService(
            GraphVersionRepository graphVersionRepository,
            KnowledgeRelationEvidenceRepository evidenceRepository,
            KnowledgeRelationRepository relationRepository,
            KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository,
            KnowledgeRelationEvidenceResolutionHistoryRepository historyRepository,
            EvidenceResolutionResolver resolutionResolver,
            ObjectMapper objectMapper,
            CourseAccessService courseAccessService
    ) {
        this.graphVersionRepository = graphVersionRepository;
        this.evidenceRepository = evidenceRepository;
        this.relationRepository = relationRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
        this.historyRepository = historyRepository;
        this.resolutionResolver = resolutionResolver;
        this.objectMapper = objectMapper;
        this.courseAccessService = courseAccessService;
    }

    @Transactional(readOnly = true)
    public GraphApi.EvidenceReresolutionResult dryRunForTeaching(
            long graphVersionId,
            GraphApi.EvidenceReresolutionRequest request,
            CurrentUser user
    ) {
        requireTeachingVersion(graphVersionId, user);
        return dryRun(graphVersionId, request, user.id());
    }

    @Transactional
    public GraphApi.EvidenceReresolutionResult applyForTeaching(
            long graphVersionId,
            GraphApi.EvidenceReresolutionRequest request,
            CurrentUser user
    ) {
        requireTeachingVersion(graphVersionId, user);
        return apply(graphVersionId, request, user.id());
    }

    @Transactional(readOnly = true)
    public List<GraphApi.EvidenceResolutionHistoryResponse> historyForTeaching(long evidenceId, CurrentUser user) {
        KnowledgeRelationEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new NotFoundException("relation evidence does not exist"));
        courseAccessService.requireTeachingAccess(evidence.getCourseId(), user);
        return history(evidenceId);
    }

    /** Computes prospective changes only. It intentionally creates no run, conflict, link, relation, or history rows. */
    @Transactional(readOnly = true)
    public GraphApi.EvidenceReresolutionResult dryRun(
            long graphVersionId,
            GraphApi.EvidenceReresolutionRequest request,
            long operatorId
    ) {
        GraphVersion draft = requireEditableDraft(graphVersionId);
        List<GraphApi.EvidenceReresolutionItemResponse> items = new ArrayList<>();
        for (Long evidenceId : uniqueEvidenceIds(request)) {
            KnowledgeRelationEvidence evidence = requireCourseEvidence(evidenceId, draft.getCourseId(), false);
            items.add(previewItem(draft, evidence));
        }
        return toResult(draft.getId(), "DRY_RUN", request.triggerType(), items);
    }

    @Transactional
    public GraphApi.EvidenceReresolutionResult apply(
            long graphVersionId,
            GraphApi.EvidenceReresolutionRequest request,
            long operatorId
    ) {
        GraphVersion draft = requireEditableDraft(graphVersionId);

        List<GraphApi.EvidenceReresolutionItemResponse> items = new ArrayList<>();
        for (Long evidenceId : uniqueEvidenceIds(request)) {
            KnowledgeRelationEvidence evidence = requireCourseEvidence(evidenceId, draft.getCourseId(), true);
            KnowledgeRelationEvidence.ResolutionState before = evidence.resolutionState();
            KnowledgeRelationEvidence.ResolutionState proposed = resolutionResolver.resolve(evidence);
            List<GraphApi.DraftRelationImpactResponse> preview = previewDraftReconciliation(draft, evidence, proposed);
            boolean resolutionChanged = !before.equals(proposed);
            if (!resolutionChanged && preview.isEmpty()) {
                items.add(toItem(evidence, before, proposed, false, List.of()));
                continue;
            }

            List<Long> beforeRelationIds = relationIdsForEvidenceInDraft(draft.getId(), evidence.getId());
            List<GraphApi.DraftRelationImpactResponse> appliedImpacts = reconcileDraft(draft, evidence, proposed, operatorId);
            evidence.applyResolution(proposed);
            evidenceRepository.flush();
            List<Long> afterRelationIds = relationIdsForEvidenceInDraft(draft.getId(), evidence.getId());
            historyRepository.save(new KnowledgeRelationEvidenceResolutionHistory(
                    evidence.getId(), draft.getId(), before, proposed, operatorId, request.triggerType(),
                    toJson(beforeRelationIds), toJson(afterRelationIds), toJson(appliedImpacts)
            ));
            items.add(toItem(evidence, before, proposed, resolutionChanged, appliedImpacts));
        }
        return toResult(draft.getId(), "APPLY", request.triggerType(), items);
    }

    @Transactional(readOnly = true)
    public List<GraphApi.EvidenceResolutionHistoryResponse> history(long evidenceId) {
        if (!evidenceRepository.existsById(evidenceId)) {
            throw new NotFoundException("relation evidence does not exist");
        }
        return historyRepository.findByEvidenceIdOrderByIdAsc(evidenceId).stream().map(this::toHistoryResponse).toList();
    }

    private GraphApi.EvidenceReresolutionItemResponse previewItem(
            GraphVersion draft,
            KnowledgeRelationEvidence evidence
    ) {
        KnowledgeRelationEvidence.ResolutionState current = evidence.resolutionState();
        KnowledgeRelationEvidence.ResolutionState proposed = resolutionResolver.resolve(evidence);
        return toItem(evidence, current, proposed, !current.equals(proposed), previewDraftReconciliation(draft, evidence, proposed));
    }

    private List<GraphApi.DraftRelationImpactResponse> previewDraftReconciliation(
            GraphVersion draft,
            KnowledgeRelationEvidence evidence,
            KnowledgeRelationEvidence.ResolutionState proposed
    ) {
        List<RelationLink> existingLinks = linksForEvidenceInDraft(draft.getId(), evidence.getId());
        Optional<KnowledgeRelation> desired = desiredRelation(draft, proposed);
        Long desiredRelationId = desired.map(KnowledgeRelation::getId).orElse(null);
        boolean desiredLinkExists = desiredRelationId != null && existingLinks.stream()
                .anyMatch(link -> desiredRelationId.equals(link.relation().getId()));
        List<GraphApi.DraftRelationImpactResponse> impacts = new ArrayList<>();
        Map<Long, Integer> projectedCounts = new java.util.HashMap<>();

        for (RelationLink relationLink : existingLinks) {
            KnowledgeRelation relation = relationLink.relation();
            if (desiredRelationId != null && desiredRelationId.equals(relation.getId())) {
                continue;
            }
            int beforeCount = projectedCounts.computeIfAbsent(relation.getId(), ignored -> linkCount(relation.getId()));
            int afterCount = Math.max(0, beforeCount - 1);
            projectedCounts.put(relation.getId(), afterCount);
            String action = afterCount == 0 && "EVIDENCE".equals(relation.getRelationSource())
                    ? "DETACH_STALE_LINK_AND_REJECT_UNSUPPORTED_CANDIDATE"
                    : "DETACH_STALE_LINK";
            impacts.add(impact(relation, action, beforeCount, afterCount,
                    relation.getReviewStatus(), reviewAfterDetach(relation, afterCount)));
        }

        if (proposed.isResolved() && !desiredLinkExists) {
            if (desired.isPresent()) {
                KnowledgeRelation relation = desired.get();
                int beforeCount = projectedCounts.computeIfAbsent(relation.getId(), ignored -> linkCount(relation.getId()));
                impacts.add(impact(relation, "ATTACH_EVIDENCE", beforeCount, beforeCount + 1,
                        relation.getReviewStatus(), relation.getReviewStatus()));
            } else {
                impacts.add(new GraphApi.DraftRelationImpactResponse(
                        null, proposed.sourceKnowledgePointId(), proposed.targetKnowledgePointId(),
                        "CREATE_CANDIDATE_AND_ATTACH_EVIDENCE", 0, 1,
                        null, RelationReviewStatus.CANDIDATE.name()
                ));
            }
        }
        return List.copyOf(impacts);
    }

    private List<GraphApi.DraftRelationImpactResponse> reconcileDraft(
            GraphVersion draft,
            KnowledgeRelationEvidence evidence,
            KnowledgeRelationEvidence.ResolutionState proposed,
            long operatorId
    ) {
        List<RelationLink> existingLinks = linksForEvidenceInDraft(draft.getId(), evidence.getId());
        Optional<KnowledgeRelation> desired = desiredRelation(draft, proposed);
        Long desiredRelationId = desired.map(KnowledgeRelation::getId).orElse(null);
        List<KnowledgeRelation> detachedRelations = new ArrayList<>();
        List<GraphApi.DraftRelationImpactResponse> impacts = new ArrayList<>();

        for (RelationLink relationLink : existingLinks) {
            KnowledgeRelation relation = relationLink.relation();
            if (desiredRelationId != null && desiredRelationId.equals(relation.getId())) {
                continue;
            }
            int beforeCount = linkCount(relation.getId());
            RelationReviewStatus beforeStatus = relation.getReviewStatus();
            evidenceLinkRepository.delete(relationLink.link());
            detachedRelations.add(relation);
            impacts.add(impact(relation,
                    "EVIDENCE".equals(relation.getRelationSource()) && beforeCount == 1
                            ? "DETACH_STALE_LINK_AND_REJECT_UNSUPPORTED_CANDIDATE"
                            : "DETACH_STALE_LINK",
                    beforeCount, -1, beforeStatus, beforeStatus));
        }
        if (!detachedRelations.isEmpty()) {
            evidenceLinkRepository.flush();
            for (int index = 0; index < impacts.size(); index++) {
                GraphApi.DraftRelationImpactResponse impact = impacts.get(index);
                if (impact.relationId() == null || !detachedRelations.stream().anyMatch(relation -> relation.getId().equals(impact.relationId()))) {
                    continue;
                }
                KnowledgeRelation relation = detachedRelations.stream()
                        .filter(candidate -> candidate.getId().equals(impact.relationId()))
                        .findFirst().orElseThrow();
                int afterCount = linkCount(relation.getId());
                relation.reconcileEvidenceCount(afterCount);
                impacts.set(index, impact(relation, impact.action(), impact.beforeEvidenceCount(), afterCount,
                        impact.beforeReviewStatus() == null ? null : RelationReviewStatus.valueOf(impact.beforeReviewStatus()),
                        relation.getReviewStatus()));
            }
        }

        if (proposed.isResolved()) {
            KnowledgeRelation relation = desired.orElseGet(() -> relationRepository.save(new KnowledgeRelation(
                    draft.getId(), proposed.sourceKnowledgePointId(), proposed.targetKnowledgePointId(),
                    PREREQUISITE, "EVIDENCE", null, 0, RelationReviewStatus.CANDIDATE, operatorId
            )));
            if (!evidenceLinkRepository.existsByRelationIdAndEvidenceId(relation.getId(), evidence.getId())) {
                int beforeCount = linkCount(relation.getId());
                RelationReviewStatus beforeStatus = relation.getReviewStatus();
                evidenceLinkRepository.save(new KnowledgeRelationEvidenceLink(relation.getId(), evidence.getId()));
                evidenceLinkRepository.flush();
                int afterCount = linkCount(relation.getId());
                relation.reconcileEvidenceCount(afterCount);
                impacts.add(impact(relation, desired.isPresent() ? "ATTACH_EVIDENCE" : "CREATE_CANDIDATE_AND_ATTACH_EVIDENCE",
                        beforeCount, afterCount, beforeStatus, relation.getReviewStatus()));
            }
        }
        return List.copyOf(impacts);
    }

    private Optional<KnowledgeRelation> desiredRelation(
            GraphVersion draft,
            KnowledgeRelationEvidence.ResolutionState proposed
    ) {
        if (!proposed.isResolved()) {
            return Optional.empty();
        }
        return relationRepository.findByGraphVersionIdAndSourceKnowledgePointIdAndTargetKnowledgePointIdAndRelationType(
                draft.getId(), proposed.sourceKnowledgePointId(), proposed.targetKnowledgePointId(), PREREQUISITE
        );
    }

    private List<RelationLink> linksForEvidenceInDraft(long graphVersionId, long evidenceId) {
        return evidenceLinkRepository.findByEvidenceIdAndGraphVersionIdOrderByIdAsc(evidenceId, graphVersionId).stream()
                .map(link -> new RelationLink(link, relationRepository.findById(link.getRelationId())
                        .orElseThrow(() -> new IllegalStateException("evidence link references a missing relation"))))
                .toList();
    }

    private List<Long> relationIdsForEvidenceInDraft(long graphVersionId, long evidenceId) {
        return linksForEvidenceInDraft(graphVersionId, evidenceId).stream()
                .map(link -> link.relation().getId()).sorted().toList();
    }

    private int linkCount(long relationId) {
        return Math.toIntExact(evidenceLinkRepository.countByRelationId(relationId));
    }

    private GraphVersion requireEditableDraft(long graphVersionId) {
        GraphVersion version = graphVersionRepository.findById(graphVersionId)
                .orElseThrow(() -> new NotFoundException("graph version does not exist"));
        if (version.getStatus() == GraphVersionStatus.PUBLISHED || version.getStatus() == GraphVersionStatus.ARCHIVED) {
            throw new ConflictException("published and archived graph versions are immutable evidence re-resolution contexts");
        }
        if (version.getStatus() != GraphVersionStatus.DRAFT && version.getStatus() != GraphVersionStatus.VALIDATION_FAILED) {
            throw new ConflictException("evidence re-resolution requires an editable draft graph version");
        }
        return version;
    }

    private GraphVersion requireTeachingVersion(long graphVersionId, CurrentUser user) {
        GraphVersion version = graphVersionRepository.findById(graphVersionId)
                .orElseThrow(() -> new NotFoundException("graph version does not exist"));
        courseAccessService.requireTeachingAccess(version.getCourseId(), user);
        return version;
    }

    private KnowledgeRelationEvidence requireCourseEvidence(long evidenceId, long courseId, boolean forUpdate) {
        KnowledgeRelationEvidence evidence = (forUpdate
                ? evidenceRepository.findForUpdateById(evidenceId)
                : evidenceRepository.findById(evidenceId))
                .orElseThrow(() -> new NotFoundException("relation evidence does not exist"));
        if (!Long.valueOf(courseId).equals(evidence.getCourseId())) {
            throw new ConflictException("relation evidence does not belong to the target draft course");
        }
        return evidence;
    }

    private List<Long> uniqueEvidenceIds(GraphApi.EvidenceReresolutionRequest request) {
        Set<Long> ids = new LinkedHashSet<>(request.evidenceIds());
        if (ids.size() != request.evidenceIds().size()) {
            throw new ConflictException("an evidence re-resolution request cannot contain the same evidence ID more than once");
        }
        return List.copyOf(ids);
    }

    private GraphApi.DraftRelationImpactResponse impact(
            KnowledgeRelation relation,
            String action,
            int beforeCount,
            int afterCount,
            RelationReviewStatus beforeStatus,
            RelationReviewStatus afterStatus
    ) {
        return new GraphApi.DraftRelationImpactResponse(
                relation.getId(), relation.getSourceKnowledgePointId(), relation.getTargetKnowledgePointId(), action,
                beforeCount, afterCount,
                beforeStatus == null ? null : beforeStatus.name(),
                afterStatus == null ? null : afterStatus.name()
        );
    }

    private RelationReviewStatus reviewAfterDetach(KnowledgeRelation relation, int afterCount) {
        if (afterCount == 0 && "EVIDENCE".equals(relation.getRelationSource())) {
            return RelationReviewStatus.REJECTED;
        }
        return relation.getReviewStatus();
    }

    private GraphApi.EvidenceReresolutionItemResponse toItem(
            KnowledgeRelationEvidence evidence,
            KnowledgeRelationEvidence.ResolutionState current,
            KnowledgeRelationEvidence.ResolutionState proposed,
            boolean resolutionChanged,
            List<GraphApi.DraftRelationImpactResponse> impacts
    ) {
        return new GraphApi.EvidenceReresolutionItemResponse(
                evidence.getId(), toEvidenceResponse(evidence), toResolutionResponse(current), toResolutionResponse(proposed),
                resolutionChanged, impacts
        );
    }

    private GraphApi.EvidenceReresolutionResult toResult(
            long graphVersionId,
            String mode,
            EvidenceReresolutionTrigger trigger,
            List<GraphApi.EvidenceReresolutionItemResponse> items
    ) {
        int changed = (int) items.stream().filter(item -> item.resolutionChanged() || !item.affectedDraftRelations().isEmpty()).count();
        return new GraphApi.EvidenceReresolutionResult(
                graphVersionId, mode, trigger.name(), changed, items.size() - changed, List.copyOf(items)
        );
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

    private GraphApi.EvidenceResolutionStateResponse toResolutionResponse(KnowledgeRelationEvidence.ResolutionState state) {
        return new GraphApi.EvidenceResolutionStateResponse(
                state.status().name(), state.conflictCode(), state.detail(), state.sourceExerciseUnitId(),
                state.targetExerciseUnitId(), state.sourceKnowledgePointId(), state.targetKnowledgePointId()
        );
    }

    private GraphApi.EvidenceResolutionHistoryResponse toHistoryResponse(KnowledgeRelationEvidenceResolutionHistory history) {
        return new GraphApi.EvidenceResolutionHistoryResponse(
                history.getId(), history.getEvidenceId(), history.getGraphVersionId(),
                history.getOldResolutionStatus().name(), history.getNewResolutionStatus().name(),
                history.getOldSourceExerciseUnitId(), history.getOldTargetExerciseUnitId(),
                history.getOldSourceKnowledgePointId(), history.getOldTargetKnowledgePointId(),
                history.getNewSourceExerciseUnitId(), history.getNewTargetExerciseUnitId(),
                history.getNewSourceKnowledgePointId(), history.getNewTargetKnowledgePointId(),
                history.getOldConflictCode(), history.getNewConflictCode(),
                history.getOldResolutionDetail(), history.getNewResolutionDetail(),
                history.getOperatorId(), history.getTriggerType().name(), history.getBeforeRelationIdsJson(),
                history.getAfterRelationIdsJson(), history.getReconciliationJson(), history.getCreatedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("unable to serialize evidence re-resolution audit data", exception);
        }
    }

    private record RelationLink(KnowledgeRelationEvidenceLink link, KnowledgeRelation relation) {
    }
}
