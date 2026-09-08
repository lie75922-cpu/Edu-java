package com.smartlearning.recommendation.application;

import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.graph.application.PublishedGraphStore;
import com.smartlearning.learning.domain.AnswerRecord;
import com.smartlearning.learning.infrastructure.persistence.AnswerRecordRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import com.smartlearning.mastery.application.MasteryReadService;
import com.smartlearning.mastery.domain.StudentKnowledgeMastery;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RecommendationCandidateGenerator {

    private final MasteryReadService masteryReadService;
    private final KnowledgePointRepository knowledgePointRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final ExerciseKnowledgeRepository exerciseKnowledgeRepository;
    private final PublishedGraphStore publishedGraphStore;
    private final RecommendationRulePolicy policy;

    public RecommendationCandidateGenerator(
            MasteryReadService masteryReadService,
            KnowledgePointRepository knowledgePointRepository,
            AnswerRecordRepository answerRecordRepository,
            ExerciseKnowledgeRepository exerciseKnowledgeRepository,
            PublishedGraphStore publishedGraphStore,
            RecommendationRulePolicy policy
    ) {
        this.masteryReadService = masteryReadService;
        this.knowledgePointRepository = knowledgePointRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.exerciseKnowledgeRepository = exerciseKnowledgeRepository;
        this.publishedGraphStore = publishedGraphStore;
        this.policy = policy;
    }

    public List<RecommendationCandidate> generate(long studentId, long courseId, long graphVersionId) {
        Map<Long, KnowledgePoint> activePoints = knowledgePointRepository
                .findByCourseIdAndStatusOrderByKnowledgeCodeAsc(courseId, "ACTIVE")
                .stream().collect(java.util.stream.Collectors.toMap(KnowledgePoint::getId, point -> point));
        List<StudentKnowledgeMastery> observed = masteryReadService.observedForStudentAndCourse(studentId, courseId);
        List<StudentKnowledgeMastery> weakObserved = observed.stream()
                .filter(mastery -> mastery.getMasteryScore() != null
                        && mastery.getMasteryScore().compareTo(policy.weakMasteryThreshold()) < 0)
                .filter(mastery -> activePoints.containsKey(mastery.getKnowledgePointId()))
                .toList();

        Map<Long, CandidateSeed> seeds = new LinkedHashMap<>();
        for (StudentKnowledgeMastery weak : weakObserved) {
            seeds.computeIfAbsent(weak.getKnowledgePointId(), CandidateSeed::new).lowMastery = true;
            PublishedGraphStore.GraphSlice prerequisiteSlice = publishedGraphStore.prerequisites(graphVersionId, weak.getKnowledgePointId());
            for (PublishedGraphStore.ProjectionEdge edge : prerequisiteSlice.edges()) {
                if (edge.targetKnowledgePointId() == weak.getKnowledgePointId()) {
                    seeds.computeIfAbsent(edge.sourceKnowledgePointId(), CandidateSeed::new)
                            .unmetForKnowledgePointIds.add(weak.getKnowledgePointId());
                }
            }
        }
        if (seeds.isEmpty()) {
            return List.of();
        }

        Set<Long> candidatePointIds = new LinkedHashSet<>(seeds.keySet());
        Map<Long, MasteryReadService.MasteryState> states = masteryReadService.statesFor(studentId, courseId, candidatePointIds);
        seeds.entrySet().removeIf(entry -> {
            CandidateSeed seed = entry.getValue();
            if (!seed.unmetForKnowledgePointIds.isEmpty()) {
                return states.get(entry.getKey()).isMastered(policy.weakMasteryThreshold());
            }
            return !seed.lowMastery;
        });
        if (seeds.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> errorsByPoint = recentErrorsByPoint(studentId, courseId, seeds.keySet());
        List<RecommendationCandidate> candidates = new ArrayList<>();
        for (Map.Entry<Long, CandidateSeed> entry : seeds.entrySet()) {
            KnowledgePoint point = activePoints.get(entry.getKey());
            if (point == null) {
                continue;
            }
            CandidateSeed seed = entry.getValue();
            candidates.add(new RecommendationCandidate(
                    graphVersionId,
                    point.getId(),
                    point.getKnowledgeCode(),
                    point.getKnowledgeName(),
                    states.get(point.getId()),
                    !seed.unmetForKnowledgePointIds.isEmpty(),
                    errorsByPoint.getOrDefault(point.getId(), 0),
                    seed.unmetForKnowledgePointIds.stream().sorted().toList()
            ));
        }
        return candidates.stream().sorted(Comparator.comparingLong(RecommendationCandidate::knowledgePointId)).toList();
    }

    private Map<Long, Integer> recentErrorsByPoint(long studentId, long courseId, Collection<Long> pointIds) {
        Instant cutoff = Instant.now().minus(policy.recentErrorWindow());
        List<AnswerRecord> recentAnswers = answerRecordRepository
                .findByStudentIdAndCourseIdAndAnsweredAtAfterOrderByAnsweredAtDesc(studentId, courseId, cutoff);
        Map<Long, List<ExerciseKnowledge>> mappingsByExercise = new HashMap<>();
        for (ExerciseKnowledge mapping : exerciseKnowledgeRepository.findByKnowledgePointIdInOrderByKnowledgePointIdAscExerciseUnitIdAsc(pointIds)) {
            mappingsByExercise.computeIfAbsent(mapping.getExerciseUnitId(), ignored -> new ArrayList<>()).add(mapping);
        }
        Map<Long, Integer> errors = new HashMap<>();
        for (AnswerRecord answer : recentAnswers) {
            if (answer.isCorrect()) {
                continue;
            }
            for (ExerciseKnowledge mapping : mappingsByExercise.getOrDefault(answer.getExerciseUnitId(), List.of())) {
                errors.merge(mapping.getKnowledgePointId(), 1, Integer::sum);
            }
        }
        return errors;
    }

    private static final class CandidateSeed {
        private boolean lowMastery;
        private final Set<Long> unmetForKnowledgePointIds = new LinkedHashSet<>();

        private CandidateSeed(long ignored) {
        }
    }
}
