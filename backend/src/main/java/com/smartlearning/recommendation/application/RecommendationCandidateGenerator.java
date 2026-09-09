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
import com.smartlearning.recommendation.domain.RecommendationReasonCode;
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

    public List<RecommendationCandidate> generate(long studentId, long courseId, Long graphVersionId) {
        List<KnowledgePoint> activePointList = knowledgePointRepository
                .findByCourseIdAndStatusOrderByKnowledgeCodeAsc(courseId, "ACTIVE");
        Map<Long, KnowledgePoint> activePoints = new LinkedHashMap<>();
        activePointList.forEach(point -> activePoints.put(point.getId(), point));

        List<StudentKnowledgeMastery> observed = masteryReadService.observedForStudentAndCourse(studentId, courseId);
        List<StudentKnowledgeMastery> weakObserved = observed.stream()
                .filter(mastery -> mastery.getMasteryScore() != null
                        && mastery.getMasteryScore().compareTo(policy.weakMasteryThreshold()) < 0)
                .filter(mastery -> activePoints.containsKey(mastery.getKnowledgePointId()))
                .toList();

        Map<Long, CandidateSeed> seeds = new LinkedHashMap<>();
        for (StudentKnowledgeMastery weak : weakObserved) {
            CandidateSeed seed = seeds.computeIfAbsent(weak.getKnowledgePointId(), CandidateSeed::new);
            seed.lowMastery = true;
            seed.defaultReason = RecommendationReasonCode.LOW_MASTERY;
            if (graphVersionId == null) {
                continue;
            }
            PublishedGraphStore.GraphSlice prerequisiteSlice = publishedGraphStore.prerequisites(
                    graphVersionId, weak.getKnowledgePointId()
            );
            for (PublishedGraphStore.ProjectionEdge edge : prerequisiteSlice.edges()) {
                if (edge.targetKnowledgePointId() == weak.getKnowledgePointId()) {
                    seeds.computeIfAbsent(edge.sourceKnowledgePointId(), CandidateSeed::new)
                            .unmetForKnowledgePointIds.add(weak.getKnowledgePointId());
                }
            }
        }

        // Conventional fallback: if there is no clear weak point, continue from the
        // least-mastered observed Topics rather than inventing weakness for UNKNOWN Topics.
        if (seeds.isEmpty() && !observed.isEmpty()) {
            observed.stream()
                    .filter(mastery -> activePoints.containsKey(mastery.getKnowledgePointId()))
                    .sorted(Comparator
                            .comparing(StudentKnowledgeMastery::getMasteryScore)
                            .thenComparing(StudentKnowledgeMastery::getLastAnsweredAt,
                                    Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparingLong(StudentKnowledgeMastery::getKnowledgePointId))
                    .limit(policy.maxRecommendations())
                    .forEach(mastery -> {
                        CandidateSeed seed = seeds.computeIfAbsent(
                                mastery.getKnowledgePointId(), CandidateSeed::new
                        );
                        seed.fallbackObserved = true;
                        seed.defaultReason = RecommendationReasonCode.TARGET_PRACTICE;
                    });
        }

        // Cold start: use stable course order only. UNKNOWN remains UNKNOWN and is
        // never assigned a fabricated 0.5 mastery/weakness value.
        if (seeds.isEmpty()) {
            activePointList.stream().limit(policy.maxRecommendations()).forEach(point -> {
                CandidateSeed seed = seeds.computeIfAbsent(point.getId(), CandidateSeed::new);
                seed.coldStart = true;
                seed.defaultReason = RecommendationReasonCode.TARGET_PRACTICE;
            });
        }
        if (seeds.isEmpty()) {
            return List.of();
        }

        Set<Long> candidatePointIds = new LinkedHashSet<>(seeds.keySet());
        Map<Long, MasteryReadService.MasteryState> states = masteryReadService.statesFor(
                studentId, courseId, candidatePointIds
        );
        seeds.entrySet().removeIf(entry -> {
            CandidateSeed seed = entry.getValue();
            if (!seed.unmetForKnowledgePointIds.isEmpty()) {
                return states.get(entry.getKey()).isMastered(policy.weakMasteryThreshold());
            }
            return !(seed.lowMastery || seed.fallbackObserved || seed.coldStart);
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
                    seed.unmetForKnowledgePointIds.stream().sorted().toList(),
                    seed.defaultReason
            ));
        }
        return candidates.stream().sorted(Comparator.comparingLong(RecommendationCandidate::knowledgePointId)).toList();
    }

    private Map<Long, Integer> recentErrorsByPoint(long studentId, long courseId, Collection<Long> pointIds) {
        Instant cutoff = Instant.now().minus(policy.recentErrorWindow());
        List<AnswerRecord> recentAnswers = answerRecordRepository
                .findByStudentIdAndCourseIdAndAnsweredAtAfterOrderByAnsweredAtDesc(studentId, courseId, cutoff);
        Map<Long, List<ExerciseKnowledge>> mappingsByExercise = new HashMap<>();
        for (ExerciseKnowledge mapping : exerciseKnowledgeRepository
                .findByKnowledgePointIdInOrderByKnowledgePointIdAscExerciseUnitIdAsc(pointIds)) {
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
        private boolean fallbackObserved;
        private boolean coldStart;
        private RecommendationReasonCode defaultReason = RecommendationReasonCode.TARGET_PRACTICE;
        private final Set<Long> unmetForKnowledgePointIds = new LinkedHashSet<>();

        private CandidateSeed(long ignored) {
        }
    }
}
