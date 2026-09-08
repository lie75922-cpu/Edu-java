package com.smartlearning.graph.application;

import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.graph.domain.EvidenceResolutionStatus;
import com.smartlearning.graph.domain.KnowledgeRelationEvidence;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Resolves immutable raw Exercise identifiers into a derived, current KnowledgePoint projection. */
@Component
public class EvidenceResolutionResolver {

    public static final String JUNYI_RAW_PREREQUISITE = "JUNYI_RAW_PREREQUISITE";
    private static final String JUNYI_CATALOG = "JUNYI_CATALOG";

    private final ExerciseUnitRepository exerciseUnitRepository;
    private final ExerciseKnowledgeRepository exerciseKnowledgeRepository;
    private final KnowledgePointRepository knowledgePointRepository;

    public EvidenceResolutionResolver(
            ExerciseUnitRepository exerciseUnitRepository,
            ExerciseKnowledgeRepository exerciseKnowledgeRepository,
            KnowledgePointRepository knowledgePointRepository
    ) {
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.exerciseKnowledgeRepository = exerciseKnowledgeRepository;
        this.knowledgePointRepository = knowledgePointRepository;
    }

    public KnowledgeRelationEvidence.ResolutionState resolve(KnowledgeRelationEvidence evidence) {
        return resolve(
                evidence.getCourseId(), evidence.getSourceType(), evidence.getSourceExternalId(), evidence.getTargetExternalId()
        );
    }

    public KnowledgeRelationEvidence.ResolutionState resolve(
            long courseId,
            String sourceType,
            String sourceExternalId,
            String targetExternalId
    ) {
        if (!JUNYI_RAW_PREREQUISITE.equals(sourceType)) {
            return unresolved(EvidenceResolutionStatus.UNRESOLVED_IDENTITY, "UNSUPPORTED_EVIDENCE_SOURCE_TYPE",
                    "this evidence source type does not have an Exercise-to-KnowledgePoint resolver");
        }

        List<ExerciseUnit> sourceMatches = exerciseUnitRepository.findByCourseIdAndSourceTypeAndExternalId(
                courseId, JUNYI_CATALOG, sourceExternalId
        );
        if (sourceMatches.isEmpty()) {
            return unresolved(EvidenceResolutionStatus.UNRESOLVED_IDENTITY, "MISSING_SOURCE_EXERCISE",
                    "source exercise external ID does not resolve to an ExerciseUnit");
        }
        if (sourceMatches.size() > 1 || !"RESOLVED".equals(sourceMatches.getFirst().getIdentityStatus())) {
            return unresolved(EvidenceResolutionStatus.UNRESOLVED_IDENTITY, "AMBIGUOUS_SOURCE_EXERCISE",
                    "source exercise external ID does not resolve uniquely to an ExerciseUnit");
        }
        ExerciseUnit sourceExercise = sourceMatches.getFirst();

        List<ExerciseUnit> targetMatches = exerciseUnitRepository.findByCourseIdAndSourceTypeAndExternalId(
                courseId, JUNYI_CATALOG, targetExternalId
        );
        if (targetMatches.isEmpty()) {
            return state(EvidenceResolutionStatus.UNRESOLVED_IDENTITY, "MISSING_TARGET_EXERCISE",
                    "target exercise external ID does not resolve to an ExerciseUnit", sourceExercise.getId(), null, null, null);
        }
        if (targetMatches.size() > 1 || !"RESOLVED".equals(targetMatches.getFirst().getIdentityStatus())) {
            return state(EvidenceResolutionStatus.UNRESOLVED_IDENTITY, "AMBIGUOUS_TARGET_EXERCISE",
                    "target exercise external ID does not resolve uniquely to an ExerciseUnit", sourceExercise.getId(), null, null, null);
        }
        ExerciseUnit targetExercise = targetMatches.getFirst();

        List<ExerciseKnowledge> sourceMappings = exerciseKnowledgeRepository
                .findByExerciseUnitIdOrderByIdAsc(sourceExercise.getId());
        if (sourceMappings.isEmpty()) {
            return state(EvidenceResolutionStatus.UNMAPPED_SOURCE, "UNMAPPED_SOURCE",
                    "source ExerciseUnit has no KnowledgePoint mapping", sourceExercise.getId(), targetExercise.getId(), null, null);
        }
        if (sourceMappings.size() > 1) {
            return state(EvidenceResolutionStatus.AMBIGUOUS_SOURCE_MAPPING, "AMBIGUOUS_SOURCE_MAPPING",
                    "source ExerciseUnit maps to multiple KnowledgePoints and cannot be auto-projected",
                    sourceExercise.getId(), targetExercise.getId(), null, null);
        }
        List<ExerciseKnowledge> targetMappings = exerciseKnowledgeRepository
                .findByExerciseUnitIdOrderByIdAsc(targetExercise.getId());
        if (targetMappings.isEmpty()) {
            return state(EvidenceResolutionStatus.UNMAPPED_TARGET, "UNMAPPED_TARGET",
                    "target ExerciseUnit has no KnowledgePoint mapping", sourceExercise.getId(), targetExercise.getId(), null, null);
        }
        if (targetMappings.size() > 1) {
            return state(EvidenceResolutionStatus.AMBIGUOUS_TARGET_MAPPING, "AMBIGUOUS_TARGET_MAPPING",
                    "target ExerciseUnit maps to multiple KnowledgePoints and cannot be auto-projected",
                    sourceExercise.getId(), targetExercise.getId(), null, null);
        }
        Long sourcePointId = sourceMappings.getFirst().getKnowledgePointId();
        Long targetPointId = targetMappings.getFirst().getKnowledgePointId();
        KnowledgePoint sourcePoint = knowledgePointRepository.findById(sourcePointId).orElse(null);
        KnowledgePoint targetPoint = knowledgePointRepository.findById(targetPointId).orElse(null);
        if (sourcePoint == null || targetPoint == null
                || !Long.valueOf(courseId).equals(sourcePoint.getCourseId())
                || !Long.valueOf(courseId).equals(targetPoint.getCourseId())) {
            return unresolved(EvidenceResolutionStatus.UNRESOLVED_IDENTITY, "INVALID_KNOWLEDGE_MAPPING",
                    "ExerciseUnit mapping does not resolve to same-course KnowledgePoints");
        }
        if (sourcePointId.equals(targetPointId)) {
            return state(EvidenceResolutionStatus.REJECTED_SELF_LOOP, "REJECTED_SELF_LOOP",
                    "source and target ExerciseUnits map to the same KnowledgePoint",
                    sourceExercise.getId(), targetExercise.getId(), sourcePointId, targetPointId);
        }
        return state(EvidenceResolutionStatus.RESOLVED, null, null,
                sourceExercise.getId(), targetExercise.getId(), sourcePointId, targetPointId);
    }

    private KnowledgeRelationEvidence.ResolutionState unresolved(
            EvidenceResolutionStatus status,
            String code,
            String detail
    ) {
        return state(status, code, detail, null, null, null, null);
    }

    private KnowledgeRelationEvidence.ResolutionState state(
            EvidenceResolutionStatus status,
            String code,
            String detail,
            Long sourceExerciseUnitId,
            Long targetExerciseUnitId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId
    ) {
        return new KnowledgeRelationEvidence.ResolutionState(
                status, code, detail, sourceExerciseUnitId, targetExerciseUnitId,
                sourceKnowledgePointId, targetKnowledgePointId
        );
    }
}
