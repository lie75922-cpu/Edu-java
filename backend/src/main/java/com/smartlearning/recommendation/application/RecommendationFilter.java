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
import java.util.Objects;

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
            Long activeGraphVersionId
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgePoint> pointsById = new HashMap<>();
        knowledgePointRepository.findAllById(candidates.stream().map(RecommendationCandidate::knowledgePointId).toList())
                .forEach(point -> pointsById.put(point.getId(), point));
        List<RecommendationCandidate> currentCandidates = candidates.stream()
                .filter(candidate -> Objects.equals(candidate.graphVersionId(), activeGraphVersionId))
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
            List<ExerciseUnit> mappedActiveExercises = mappingsByPoint
                    .getOrDefault(candidate.knowledgePointId(), List.of())
                    .stream()
                    .map(ExerciseKnowledge::getExerciseUnitId)
                    .map(activeExercisesById::get)
                    .filter(Objects::nonNull)
                    .filter(exercise -> exercise.getCourseId().equals(courseId))
                    .sorted(Comparator.comparing(ExerciseUnit::getExerciseCode).thenComparing(ExerciseUnit::getId))
                    .toList();

            // Prefer one actually answerable exercise. If the imported catalog has no
            // legal Question content, keep a Topic-level study recommendation instead
            // of fabricating a question or dropping the recommendation completely.
            ExerciseUnit answerable = mappedActiveExercises.stream()
                    .filter(exercise -> questionRepository.existsByExerciseUnitIdAndStatus(exercise.getId(), "ACTIVE"))
                    .findFirst()
                    .orElse(null);
            if (answerable != null) {
                filtered.add(new PracticeCandidate(
                        candidate,
                        answerable.getId(),
                        answerable.getExerciseCode(),
                        answerable.getExerciseName()
                ));
            } else {
                filtered.add(new PracticeCandidate(candidate, null, null, null));
            }
        }
        return filtered.stream().sorted(Comparator
                .comparingLong((PracticeCandidate candidate) -> candidate.candidate().knowledgePointId())
                .thenComparing(PracticeCandidate::exerciseUnitId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private boolean isActiveCoursePoint(KnowledgePoint point, long courseId) {
        return point != null && point.getCourseId().equals(courseId) && "ACTIVE".equals(point.getStatus());
    }

    public record PracticeCandidate(
            RecommendationCandidate candidate,
            Long exerciseUnitId,
            String exerciseCode,
            String exerciseName
    ) {
    }
}
