package com.smartlearning.catalog.application;

import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.catalog.api.SeedImportApi;
import com.smartlearning.catalog.domain.SeedImportConflict;
import com.smartlearning.catalog.domain.SeedImportRun;
import com.smartlearning.catalog.infrastructure.persistence.SeedImportConflictRepository;
import com.smartlearning.catalog.infrastructure.persistence.SeedImportRunRepository;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.domain.Course;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.knowledge.domain.KnowledgeArea;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgeAreaRepository;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
public class SeedImportService {

    private static final String SOURCE_NAME = "JUNYI_CATALOG";
    private static final String TOPIC_SOURCE = "JUNYI_TOPIC";

    private final CourseRepository courseRepository;
    private final KnowledgeAreaRepository areaRepository;
    private final KnowledgePointRepository pointRepository;
    private final ExerciseUnitRepository exerciseRepository;
    private final ExerciseKnowledgeRepository mappingRepository;
    private final SeedImportRunRepository runRepository;
    private final SeedImportConflictRepository conflictRepository;
    private final ObjectMapper objectMapper;

    public SeedImportService(
            CourseRepository courseRepository,
            KnowledgeAreaRepository areaRepository,
            KnowledgePointRepository pointRepository,
            ExerciseUnitRepository exerciseRepository,
            ExerciseKnowledgeRepository mappingRepository,
            SeedImportRunRepository runRepository,
            SeedImportConflictRepository conflictRepository,
            ObjectMapper objectMapper
    ) {
        this.courseRepository = courseRepository;
        this.areaRepository = areaRepository;
        this.pointRepository = pointRepository;
        this.exerciseRepository = exerciseRepository;
        this.mappingRepository = mappingRepository;
        this.runRepository = runRepository;
        this.conflictRepository = conflictRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SeedImportApi.SeedImportResult dryRun(SeedImportApi.SeedImportRequest request, long requestedBy) {
        return execute(request, requestedBy, false);
    }

    @Transactional
    public SeedImportApi.SeedImportResult apply(SeedImportApi.SeedImportRequest request, long requestedBy) {
        return execute(request, requestedBy, true);
    }

    public List<SeedImportApi.SeedConflictResponse> conflicts(long runId) {
        if (!runRepository.existsById(runId)) {
            throw new NotFoundException("seed import run does not exist");
        }
        return conflictRepository.findByImportRunIdOrderByIdAsc(runId).stream().map(this::toConflictResponse).toList();
    }

    private SeedImportApi.SeedImportResult execute(SeedImportApi.SeedImportRequest request, long requestedBy, boolean apply) {
        String mode = apply ? "APPLY" : "DRY_RUN";
        SeedImportRun run = runRepository.save(new SeedImportRun(requestedBy, mode, SOURCE_NAME));
        MutableSummary summary = new MutableSummary();
        List<ConflictDraft> conflicts = new ArrayList<>();

        Set<String> duplicateAreas = duplicates(request.areas(), SeedImportApi.SeedArea::externalId);
        Set<String> duplicateTopics = duplicates(request.topics(), SeedImportApi.SeedTopic::externalId);
        Set<String> duplicateExercises = duplicates(request.exercises(), SeedImportApi.SeedExercise::externalId);
        duplicateAreas.forEach(id -> conflicts.add(conflict("KNOWLEDGE_AREA", id, "DUPLICATE_SOURCE_EXTERNAL_ID", "duplicate area external ID in import input")));
        duplicateTopics.forEach(id -> conflicts.add(conflict("KNOWLEDGE_POINT", id, "DUPLICATE_SOURCE_EXTERNAL_ID", "duplicate topic external ID in import input")));
        duplicateExercises.forEach(id -> conflicts.add(conflict("EXERCISE_UNIT", id, "DUPLICATE_SOURCE_EXTERNAL_ID", "duplicate exercise external ID in import input")));

        Course course = courseRepository.findByCourseCode(request.courseCode()).orElse(null);
        if (course == null) {
            summary.createdCourses++;
            if (apply) {
                course = courseRepository.save(new Course(request.courseCode(), request.courseName(), "Imported Junyi catalog metadata", "ACTIVE"));
            }
        }
        Long courseId = course == null ? null : course.getId();

        Map<String, AreaResolution> areas = importAreas(request.areas(), duplicateAreas, courseId, apply, summary, conflicts);
        Map<String, PointResolution> points = importTopics(
                request.topics(), duplicateTopics, duplicateAreas, areas, courseId, apply, summary, conflicts
        );
        importExercises(request.exercises(), duplicateExercises, duplicateTopics, points, courseId, apply, summary, conflicts);

        List<SeedImportConflict> persistedConflicts = conflicts.stream()
                .map(draft -> conflictRepository.save(new SeedImportConflict(
                        run.getId(), draft.entityType(), draft.externalId(), draft.conflictType(), toJson(Map.of("message", draft.message()))
                )))
                .toList();
        summary.conflictCount = persistedConflicts.size();
        String status = summary.conflictCount == 0 ? (apply ? "COMPLETED" : "DRY_RUN_COMPLETED") : "COMPLETED_WITH_CONFLICTS";
        run.complete(status, toJson(summary.snapshot()));
        return new SeedImportApi.SeedImportResult(
                run.getId(), mode, status, summary.createdCourses, summary.createdAreas, summary.reusedAreas,
                summary.createdKnowledgePoints, summary.reusedKnowledgePoints, summary.createdExerciseUnits,
                summary.reusedExerciseUnits, summary.createdMappings, summary.unmappedExercises, summary.conflictCount,
                persistedConflicts.stream().map(this::toConflictResponse).toList()
        );
    }

    private Map<String, AreaResolution> importAreas(
            List<SeedImportApi.SeedArea> seedAreas,
            Set<String> duplicateIds,
            Long courseId,
            boolean apply,
            MutableSummary summary,
            List<ConflictDraft> conflicts
    ) {
        Map<String, AreaResolution> results = new HashMap<>();
        for (SeedImportApi.SeedArea seed : seedAreas) {
            String externalId = normalizedId(seed.externalId());
            if (duplicateIds.contains(externalId)) {
                continue;
            }
            List<KnowledgeArea> existing = courseId == null ? List.of()
                    : areaRepository.findByCourseIdAndSourceTypeAndExternalId(courseId, SOURCE_NAME, externalId);
            if (existing.size() > 1) {
                conflicts.add(conflict("KNOWLEDGE_AREA", externalId, "IDENTITY_CONFLICT", "multiple existing areas have this source external ID"));
                continue;
            }
            if (existing.size() == 1) {
                summary.reusedAreas++;
                results.put(externalId, new AreaResolution(existing.getFirst(), true));
                continue;
            }
            summary.createdAreas++;
            if (apply) {
                KnowledgeArea area = areaRepository.save(new KnowledgeArea(
                        courseId, nextAreaCode(courseId, externalId), seed.areaName(), SOURCE_NAME, externalId, "ACTIVE"
                ));
                results.put(externalId, new AreaResolution(area, true));
            } else {
                results.put(externalId, new AreaResolution(null, true));
            }
        }
        return results;
    }

    private Map<String, PointResolution> importTopics(
            List<SeedImportApi.SeedTopic> seedTopics,
            Set<String> duplicateIds,
            Set<String> duplicateAreaIds,
            Map<String, AreaResolution> areas,
            Long courseId,
            boolean apply,
            MutableSummary summary,
            List<ConflictDraft> conflicts
    ) {
        Map<String, PointResolution> results = new HashMap<>();
        for (SeedImportApi.SeedTopic seed : seedTopics) {
            String externalId = normalizedId(seed.externalId());
            if (duplicateIds.contains(externalId)) {
                continue;
            }
            List<KnowledgePoint> existing = courseId == null ? List.of()
                    : pointRepository.findByCourseIdAndSourceTypeAndExternalId(courseId, TOPIC_SOURCE, externalId);
            if (existing.size() > 1) {
                conflicts.add(conflict("KNOWLEDGE_POINT", externalId, "IDENTITY_CONFLICT", "multiple existing knowledge points have this source external ID"));
                continue;
            }
            AreaResolution area = resolveArea(seed.areaExternalId(), duplicateAreaIds, areas, courseId, conflicts);
            if (seed.areaExternalId() != null && !seed.areaExternalId().isBlank() && (area == null || !area.resolvable())) {
                conflicts.add(conflict("KNOWLEDGE_POINT", externalId, "UNMAPPED_AREA", "topic references an unavailable area external ID"));
            }
            if (existing.size() == 1) {
                summary.reusedKnowledgePoints++;
                results.put(externalId, new PointResolution(existing.getFirst(), true));
                continue;
            }
            summary.createdKnowledgePoints++;
            if (apply) {
                KnowledgePoint point = pointRepository.save(new KnowledgePoint(
                        courseId, area == null ? null : area.area().getId(), nextKnowledgeCode(courseId, externalId), seed.topicName(),
                        TOPIC_SOURCE, externalId, area == null ? "UNMAPPED" : "MAPPED", "ACTIVE"
                ));
                results.put(externalId, new PointResolution(point, true));
            } else {
                results.put(externalId, new PointResolution(null, true));
            }
        }
        return results;
    }

    private void importExercises(
            List<SeedImportApi.SeedExercise> seedExercises,
            Set<String> duplicateIds,
            Set<String> duplicateTopicIds,
            Map<String, PointResolution> points,
            Long courseId,
            boolean apply,
            MutableSummary summary,
            List<ConflictDraft> conflicts
    ) {
        for (SeedImportApi.SeedExercise seed : seedExercises) {
            String externalId = normalizedId(seed.externalId());
            if (duplicateIds.contains(externalId)) {
                continue;
            }
            List<ExerciseUnit> existing = courseId == null ? List.of()
                    : exerciseRepository.findByCourseIdAndSourceTypeAndExternalId(courseId, SOURCE_NAME, externalId);
            if (existing.size() > 1) {
                conflicts.add(conflict("EXERCISE_UNIT", externalId, "IDENTITY_CONFLICT", "multiple existing exercise units have this source external ID"));
                continue;
            }
            PointResolution point = resolvePoint(seed.topicExternalId(), duplicateTopicIds, points, courseId, conflicts);
            if (point == null || !point.resolvable()) {
                summary.unmappedExercises++;
                conflicts.add(conflict("EXERCISE_UNIT", externalId, "UNMAPPED_TOPIC", "exercise has no resolvable Topic mapping"));
            }
            ExerciseUnit exercise;
            if (existing.size() == 1) {
                exercise = existing.getFirst();
                summary.reusedExerciseUnits++;
            } else {
                summary.createdExerciseUnits++;
                exercise = apply ? exerciseRepository.save(new ExerciseUnit(
                        courseId, nextExerciseCode(courseId, externalId), seed.exerciseName(), SOURCE_NAME, externalId,
                        "RESOLVED", point == null ? "UNMAPPED" : "MAPPED", seed.difficulty(), "ACTIVE"
                )) : null;
            }
            if (point != null && point.resolvable()) {
                if (apply && exercise != null && point.point() != null) {
                    boolean mappingAlreadyExists = mappingRepository
                            .findByExerciseUnitIdAndKnowledgePointId(exercise.getId(), point.point().getId())
                            .isPresent();
                    if (!mappingAlreadyExists) {
                        mappingRepository.save(new ExerciseKnowledge(
                                exercise.getId(), point.point().getId(), TOPIC_SOURCE, BigDecimal.ONE, false
                        ));
                        exercise.setMappingStatus("MAPPED");
                        summary.createdMappings++;
                    }
                } else if (!apply) {
                    boolean mappingAlreadyExists = exercise != null && point.point() != null && mappingRepository
                            .findByExerciseUnitIdAndKnowledgePointId(exercise.getId(), point.point().getId())
                            .isPresent();
                    if (!mappingAlreadyExists) {
                        summary.createdMappings++;
                    }
                }
            }
        }
    }

    private String nextAreaCode(long courseId, String externalId) {
        return nextCode("JUNYI_AREA", externalId, candidate -> areaRepository.existsByCourseIdAndAreaCode(courseId, candidate));
    }

    private String nextKnowledgeCode(long courseId, String externalId) {
        return nextCode("JUNYI_TOPIC", externalId, candidate -> pointRepository.existsByCourseIdAndKnowledgeCode(courseId, candidate));
    }

    private String nextExerciseCode(long courseId, String externalId) {
        return nextCode("JUNYI_EXERCISE", externalId, candidate -> exerciseRepository.existsByCourseIdAndExerciseCode(courseId, candidate));
    }

    private String nextCode(String prefix, String externalId, Function<String, Boolean> exists) {
        String normalized = externalId.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("(^_+|_+$)", "").toUpperCase();
        String base = (prefix + "_" + (normalized.isBlank() ? "ITEM" : normalized));
        if (base.length() > 88) {
            base = base.substring(0, 88);
        }
        String candidate = base;
        int suffix = 2;
        while (exists.apply(candidate)) {
            candidate = base.substring(0, Math.min(base.length(), 91)) + "_" + suffix++;
        }
        return candidate;
    }

    private <T> Set<String> duplicates(Collection<T> items, Function<T, String> idExtractor) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (T item : items) {
            String id = normalizedId(idExtractor.apply(item));
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }
        return duplicates;
    }

    private String normalizedId(String id) {
        return id.trim();
    }

    private AreaResolution resolveArea(
            String externalId,
            Set<String> duplicateAreaIds,
            Map<String, AreaResolution> importedAreas,
            Long courseId,
            List<ConflictDraft> conflicts
    ) {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        String normalizedExternalId = normalizedId(externalId);
        if (duplicateAreaIds.contains(normalizedExternalId)) {
            return null;
        }
        AreaResolution imported = importedAreas.get(normalizedExternalId);
        if (imported != null || courseId == null) {
            return imported;
        }
        List<KnowledgeArea> existing = areaRepository
                .findByCourseIdAndSourceTypeAndExternalId(courseId, SOURCE_NAME, normalizedExternalId);
        if (existing.size() == 1) {
            return new AreaResolution(existing.getFirst(), true);
        }
        if (existing.size() > 1) {
            conflicts.add(conflict("KNOWLEDGE_AREA", normalizedExternalId, "IDENTITY_CONFLICT", "multiple existing areas have this source external ID"));
        }
        return null;
    }

    private PointResolution resolvePoint(
            String externalId,
            Set<String> duplicateTopicIds,
            Map<String, PointResolution> importedPoints,
            Long courseId,
            List<ConflictDraft> conflicts
    ) {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        String normalizedExternalId = normalizedId(externalId);
        if (duplicateTopicIds.contains(normalizedExternalId)) {
            return null;
        }
        PointResolution imported = importedPoints.get(normalizedExternalId);
        if (imported != null || courseId == null) {
            return imported;
        }
        List<KnowledgePoint> existing = pointRepository
                .findByCourseIdAndSourceTypeAndExternalId(courseId, TOPIC_SOURCE, normalizedExternalId);
        if (existing.size() == 1) {
            return new PointResolution(existing.getFirst(), true);
        }
        if (existing.size() > 1) {
            conflicts.add(conflict("KNOWLEDGE_POINT", normalizedExternalId, "IDENTITY_CONFLICT", "multiple existing knowledge points have this source external ID"));
        }
        return null;
    }

    private ConflictDraft conflict(String entityType, String externalId, String conflictType, String message) {
        return new ConflictDraft(entityType, externalId, conflictType, message);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("unable to serialize seed import audit data", ex);
        }
    }

    private SeedImportApi.SeedConflictResponse toConflictResponse(SeedImportConflict conflict) {
        return new SeedImportApi.SeedConflictResponse(
                conflict.getId(), conflict.getEntityType(), conflict.getExternalId(), conflict.getConflictType(),
                conflict.getDetailJson(), conflict.getCreatedAt()
        );
    }

    private record AreaResolution(KnowledgeArea area, boolean resolvable) {
    }

    private record PointResolution(KnowledgePoint point, boolean resolvable) {
    }

    private record ConflictDraft(String entityType, String externalId, String conflictType, String message) {
    }

    private static final class MutableSummary {
        private int createdCourses;
        private int createdAreas;
        private int reusedAreas;
        private int createdKnowledgePoints;
        private int reusedKnowledgePoints;
        private int createdExerciseUnits;
        private int reusedExerciseUnits;
        private int createdMappings;
        private int unmappedExercises;
        private int conflictCount;

        private SummarySnapshot snapshot() {
            return new SummarySnapshot(
                    createdCourses, createdAreas, reusedAreas, createdKnowledgePoints, reusedKnowledgePoints,
                    createdExerciseUnits, reusedExerciseUnits, createdMappings, unmappedExercises, conflictCount
            );
        }
    }

    private record SummarySnapshot(
            int createdCourses,
            int createdAreas,
            int reusedAreas,
            int createdKnowledgePoints,
            int reusedKnowledgePoints,
            int createdExerciseUnits,
            int reusedExerciseUnits,
            int createdMappings,
            int unmappedExercises,
            int conflictCount
    ) {
    }
}
