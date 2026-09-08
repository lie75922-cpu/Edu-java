package com.smartlearning.recommendation.application;

import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.assessment.infrastructure.persistence.QuestionRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RecommendationFilter {

    private final KnowledgePointRepository knowledgePointRepository;
    private final ExerciseKnowledgeRepository exerciseKnowledgeRepository;
    private final ExerciseUnitRepository exerciseUnitRepository;
    private final QuestionRepository questionRepository;

    public RecommendationFilter(
            KnowledgePointRepository knowledgePointRepository,
            ExerciseKnowledgeRepository exerciseKnowledgeRepository,
            ExerciseUnitRepository exerciseUnitRepository,
            QuestionRepository questionRepository
    ) {
        this.knowledgePointRepository = knowledgePointRepository;
        this.exerciseKnowledgeRepository = exerciseKnowledgeRepository;
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.questionRepository = questionRepository;
    }

    public List<PracticeCandidate> filter(
            Collection<RecommendationCandidate> candidates,
            long courseId,
            long activeGraphVersionId
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgePoint> pointsById = new HashMap<>();
        knowledgePointRepository.findAllById(candidates.stream().map(RecommendationCandidate::knowledgePointId).toList())
                .forEach(point -> pointsById.put(point.getId(), point));
        List<RecommendationCandidate> currentCandidates = candidates.stream()
                .filter(candidate -> candidate.graphVersionId() == activeGraphVersionId)
                .filter(candidate -> isActiveCoursePoint(pointsById.get(candidate.knowledgePointId()), courseId))
                .toList();
        if (currentCandidates.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ExerciseKnowledge>> mappingsByPoint = new HashMap<>();
        for (ExerciseKnowledge mapping : exerciseKnowledgeRepository.findByKnowledgePointIdInOrderByKnowledgePointIdAscExerciseUnitIdAsc(
                currentCandidates.stream().map(RecommendationCandidate::knowledgePointId).toList())) {
            mappingsByPoint.computeIfAbsent(mapping.getKnowledgePointId(), ignored -> new ArrayList<>()).add(mapping);
        }
        List<Long> exerciseIds = mappingsByPoint.values().stream().flatMap(List::stream)
                .map(ExerciseKnowledge::getExerciseUnitId).distinct().toList();
        Map<Long, ExerciseUnit> activeExercisesById = new HashMap<>();
        if (!exerciseIds.isEmpty()) {
            exerciseUnitRepository.findByIdInAndStatusOrderByExerciseCodeAsc(exerciseIds, "ACTIVE")
                    .forEach(exercise -> activeExercisesById.put(exercise.getId(), exercise));
        }

        List<PracticeCandidate> filtered = new ArrayList<>();
        for (RecommendationCandidate candidate : currentCandidates) {
            for (ExerciseKnowledge mapping : mappingsByPoint.getOrDefault(candidate.knowledgePointId(), List.of())) {
                ExerciseUnit exercise = activeExercisesById.get(mapping.getExerciseUnitId());
                if (exercise == null || !exercise.getCourseId().equals(courseId)) {
                    continue;
                }
                if (!questionRepository.existsByExerciseUnitIdAndStatus(exercise.getId(), "ACTIVE")) {
                    continue;
                }
                filtered.add(new PracticeCandidate(candidate, exercise.getId(), exercise.getExerciseCode(), exercise.getExerciseName()));
            }
        }
        return filtered.stream().sorted(Comparator
                .comparingLong((PracticeCandidate candidate) -> candidate.candidate().knowledgePointId())
                .thenComparingLong(PracticeCandidate::exerciseUnitId))
                .toList();
    }

    private boolean isActiveCoursePoint(KnowledgePoint point, long courseId) {
        return point != null && point.getCourseId().equals(courseId) && "ACTIVE".equals(point.getStatus());
    }

    public record PracticeCandidate(
            RecommendationCandidate candidate,
            long exerciseUnitId,
            String exerciseCode,
            String exerciseName
    ) {
    }
}
