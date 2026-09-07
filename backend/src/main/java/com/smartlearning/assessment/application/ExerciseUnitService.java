package com.smartlearning.assessment.application;

import com.smartlearning.assessment.api.ExerciseApi;
import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.knowledge.application.KnowledgeService;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExerciseUnitService {

    private final ExerciseUnitRepository exerciseRepository;
    private final ExerciseKnowledgeRepository mappingRepository;
    private final CourseService courseService;
    private final CourseAccessService courseAccessService;
    private final KnowledgeService knowledgeService;

    public ExerciseUnitService(
            ExerciseUnitRepository exerciseRepository,
            ExerciseKnowledgeRepository mappingRepository,
            CourseService courseService,
            CourseAccessService courseAccessService,
            KnowledgeService knowledgeService
    ) {
        this.exerciseRepository = exerciseRepository;
        this.mappingRepository = mappingRepository;
        this.courseService = courseService;
        this.courseAccessService = courseAccessService;
        this.knowledgeService = knowledgeService;
    }

    public List<ExerciseApi.ExerciseUnitResponse> listActive(long courseId, Long knowledgePointId, CurrentUser user) {
        courseService.requireCourse(courseId);
        courseAccessService.requireCourseAccess(courseId, user);
        if (knowledgePointId == null) {
            return exerciseRepository.findByCourseIdAndStatusOrderByExerciseCodeAsc(courseId, "ACTIVE").stream()
                    .map(this::toResponse)
                    .toList();
        }
        KnowledgePoint point = knowledgeService.requirePoint(knowledgePointId);
        if (!point.getCourseId().equals(courseId)) {
            throw new ConflictException("knowledge point does not belong to this course");
        }
        return exerciseRepository.findByCourseIdAndStatusOrderByExerciseCodeAsc(courseId, "ACTIVE").stream()
                .filter(exercise -> mappingRepository.findByExerciseUnitIdOrderByIdAsc(exercise.getId()).stream()
                        .anyMatch(mapping -> mapping.getKnowledgePointId().equals(knowledgePointId)))
                .map(this::toResponse)
                .toList();
    }

    public List<ExerciseApi.ExerciseUnitResponse> listForAdmin(long courseId) {
        courseService.requireCourse(courseId);
        return exerciseRepository.findByCourseIdAndStatusOrderByExerciseCodeAsc(courseId, "ACTIVE").stream()
                .map(this::toResponse)
                .toList();
    }

    public ExerciseApi.ExerciseUnitResponse get(long exerciseUnitId, CurrentUser user) {
        ExerciseUnit exercise = requireExercise(exerciseUnitId);
        courseAccessService.requireCourseAccess(exercise.getCourseId(), user);
        return toResponse(exercise);
    }

    @Transactional
    public ExerciseApi.ExerciseUnitResponse create(ExerciseApi.ExerciseUnitRequest request) {
        courseService.requireCourse(request.courseId());
        if (exerciseRepository.existsByCourseIdAndExerciseCode(request.courseId(), request.exerciseCode())) {
            throw new ConflictException("exercise code is already in use for this course");
        }
        return toResponse(exerciseRepository.save(new ExerciseUnit(
                request.courseId(), request.exerciseCode(), request.exerciseName(), request.sourceType(), request.externalId(),
                request.identityStatus(), request.mappingStatus(), request.difficulty(), request.status()
        )));
    }

    @Transactional
    public ExerciseApi.ExerciseUnitResponse update(long exerciseUnitId, ExerciseApi.ExerciseUnitRequest request) {
        courseService.requireCourse(request.courseId());
        ExerciseUnit exercise = requireExercise(exerciseUnitId);
        if ((!exercise.getCourseId().equals(request.courseId()) || !exercise.getExerciseCode().equals(request.exerciseCode()))
                && exerciseRepository.existsByCourseIdAndExerciseCode(request.courseId(), request.exerciseCode())) {
            throw new ConflictException("exercise code is already in use for this course");
        }
        exercise.update(
                request.courseId(), request.exerciseCode(), request.exerciseName(), request.sourceType(), request.externalId(),
                request.identityStatus(), request.mappingStatus(), request.difficulty(), request.status()
        );
        return toResponse(exercise);
    }

    @Transactional
    public void disable(long exerciseUnitId) {
        requireExercise(exerciseUnitId).disable();
    }

    @Transactional
    public ExerciseApi.ExerciseUnitResponse upsertMapping(long exerciseUnitId, ExerciseApi.MappingRequest request) {
        ExerciseUnit exercise = requireExercise(exerciseUnitId);
        KnowledgePoint point = knowledgeService.requirePoint(request.knowledgePointId());
        if (!exercise.getCourseId().equals(point.getCourseId())) {
            throw new ConflictException("exercise unit and knowledge point must belong to the same course");
        }
        ExerciseKnowledge mapping = mappingRepository.findByExerciseUnitIdAndKnowledgePointId(exerciseUnitId, request.knowledgePointId())
                .orElseGet(() -> new ExerciseKnowledge(
                        exerciseUnitId, request.knowledgePointId(), request.mappingSource(), request.confidence(), request.verified()
                ));
        mapping.update(request.mappingSource(), request.confidence(), request.verified());
        mappingRepository.save(mapping);
        exercise.setMappingStatus("MAPPED");
        return toResponse(exercise);
    }

    @Transactional
    public void removeMapping(long exerciseUnitId, long knowledgePointId) {
        ExerciseKnowledge mapping = mappingRepository.findByExerciseUnitIdAndKnowledgePointId(exerciseUnitId, knowledgePointId)
                .orElseThrow(() -> new NotFoundException("exercise/knowledge mapping does not exist"));
        mappingRepository.delete(mapping);
        ExerciseUnit exercise = requireExercise(exerciseUnitId);
        if (mappingRepository.countByExerciseUnitId(exerciseUnitId) == 0) {
            exercise.setMappingStatus("UNMAPPED");
        }
    }

    public ExerciseUnit requireExercise(long exerciseUnitId) {
        return exerciseRepository.findById(exerciseUnitId)
                .orElseThrow(() -> new NotFoundException("exercise unit does not exist"));
    }

    private ExerciseApi.ExerciseUnitResponse toResponse(ExerciseUnit exercise) {
        List<ExerciseApi.KnowledgeMappingResponse> mappings = mappingRepository
                .findByExerciseUnitIdOrderByIdAsc(exercise.getId())
                .stream()
                .map(mapping -> toMappingResponse(mapping))
                .toList();
        return new ExerciseApi.ExerciseUnitResponse(
                exercise.getId(), exercise.getCourseId(), exercise.getExerciseCode(), exercise.getExerciseName(),
                exercise.getSourceType(), exercise.getExternalId(), exercise.getIdentityStatus(), exercise.getMappingStatus(),
                exercise.getDifficulty(), exercise.getStatus(), mappings
        );
    }

    private ExerciseApi.KnowledgeMappingResponse toMappingResponse(ExerciseKnowledge mapping) {
        KnowledgePoint point = knowledgeService.requirePoint(mapping.getKnowledgePointId());
        return new ExerciseApi.KnowledgeMappingResponse(
                mapping.getId(), point.getId(), point.getKnowledgeCode(), point.getKnowledgeName(), mapping.getMappingSource(),
                mapping.getConfidence(), mapping.isVerified()
        );
    }
}
