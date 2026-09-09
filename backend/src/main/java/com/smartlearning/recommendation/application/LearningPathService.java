package com.smartlearning.recommendation.application;

import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.assessment.infrastructure.persistence.QuestionRepository;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.graph.application.GraphQueryService;
import com.smartlearning.graph.application.PublishedGraphStore;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import com.smartlearning.mastery.application.MasteryQueryService;
import com.smartlearning.mastery.application.MasteryReadService;
import com.smartlearning.mastery.domain.MasteryStatus;
import com.smartlearning.recommendation.api.RecommendationApi;
import com.smartlearning.recommendation.domain.RecommendationReasonCode;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

@Service
public class LearningPathService {

    private final KnowledgePointRepository knowledgePointRepository;
    private final GraphQueryService graphQueryService;
    private final PublishedGraphStore publishedGraphStore;
    private final MasteryQueryService masteryQueryService;
    private final RecommendationRulePolicy policy;
    private final ExerciseKnowledgeRepository exerciseKnowledgeRepository;
    private final ExerciseUnitRepository exerciseUnitRepository;
    private final QuestionRepository questionRepository;

    public LearningPathService(
            KnowledgePointRepository knowledgePointRepository,
            GraphQueryService graphQueryService,
            PublishedGraphStore publishedGraphStore,
            MasteryQueryService masteryQueryService,
            RecommendationRulePolicy policy,
            ExerciseKnowledgeRepository exerciseKnowledgeRepository,
            ExerciseUnitRepository exerciseUnitRepository,
            QuestionRepository questionRepository
    ) {
        this.knowledgePointRepository = knowledgePointRepository;
        this.graphQueryService = graphQueryService;
        this.publishedGraphStore = publishedGraphStore;
        this.masteryQueryService = masteryQueryService;
        this.policy = policy;
        this.exerciseKnowledgeRepository = exerciseKnowledgeRepository;
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.questionRepository = questionRepository;
    }

    public RecommendationApi.LearningPathResponse learningPath(long targetKnowledgePointId, CurrentUser user) {
        KnowledgePoint target = knowledgePointRepository.findById(targetKnowledgePointId)
                .orElseThrow(() -> new NotFoundException("knowledge point does not exist"));
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new ConflictException("target knowledge point is not active");
        }
        Long graphVersionId = graphQueryService.activeGraphVersionIdOrNull(target.getCourseId(), user);
        if (graphVersionId == null) {
            return conventionalLearningPlan(target, user);
        }
        PublishedGraphStore.GraphSlice slice;
        try {
            slice = publishedGraphStore.prerequisiteSubgraph(graphVersionId, targetKnowledgePointId);
        } catch (Neo4jException ex) {
            return conventionalLearningPlan(target, user);
        }
        Map<Long, PublishedGraphStore.ProjectionNode> graphNodes = new LinkedHashMap<>();
        slice.nodes().forEach(node -> graphNodes.put(node.businessId(), node));
        if (!graphNodes.containsKey(targetKnowledgePointId)) {
            return conventionalLearningPlan(target, user);
        }
        Map<Long, MasteryReadService.MasteryState> mastery = masteryQueryService.masteryStates(
                user.id(), target.getCourseId(), graphNodes.keySet()
        );
        Set<Long> requiredNodeIds = graphNodes.keySet().stream()
                .filter(nodeId -> !mastery.get(nodeId).isMastered(policy.weakMasteryThreshold()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Long> orderedIds = topologicalOrder(requiredNodeIds, slice.edges(), targetKnowledgePointId);
        Map<Long, AvailableExercise> exercises = availableExercises(target.getCourseId(), orderedIds);
        List<RecommendationApi.LearningPathNodeResponse> nodes = new ArrayList<>();
        for (int index = 0; index < orderedIds.size(); index++) {
            long pointId = orderedIds.get(index);
            PublishedGraphStore.ProjectionNode point = graphNodes.get(pointId);
            MasteryReadService.MasteryState state = mastery.get(pointId);
            AvailableExercise exercise = exercises.get(pointId);
            nodes.add(new RecommendationApi.LearningPathNodeResponse(
                    index + 1, pointId, point.knowledgeCode(), point.knowledgeName(), state.status().name(),
                    state.masteryScore(), pointId == targetKnowledgePointId
                            ? RecommendationReasonCode.TARGET_PRACTICE.name()
                            : RecommendationReasonCode.UNMET_PREREQUISITE.name(),
                    exercise == null ? null : exercise.id(), exercise == null ? null : exercise.code(),
                    exercise == null ? null : exercise.name(), exercise != null
            ));
        }
        return new RecommendationApi.LearningPathResponse(targetKnowledgePointId, target.getCourseId(), graphVersionId, nodes);
    }

    /**
     * Graph-optional conventional plan. It never claims that weak Topics are
     * prerequisites: it simply schedules a few observed weak Topics before the
     * selected target and marks graphVersionId as null.
     */
    private RecommendationApi.LearningPathResponse conventionalLearningPlan(KnowledgePoint target, CurrentUser user) {
        List<KnowledgePoint> activePoints = knowledgePointRepository
                .findByCourseIdAndStatusOrderByKnowledgeCodeAsc(target.getCourseId(), "ACTIVE");
        Map<Long, KnowledgePoint> pointsById = new LinkedHashMap<>();
        activePoints.forEach(point -> pointsById.put(point.getId(), point));
        Map<Long, MasteryReadService.MasteryState> mastery = masteryQueryService.masteryStates(
                user.id(), target.getCourseId(), pointsById.keySet()
        );

        List<Long> orderedIds = activePoints.stream()
                .map(KnowledgePoint::getId)
                .filter(id -> id != target.getId())
                .filter(id -> mastery.get(id).status() == MasteryStatus.OBSERVED)
                .filter(id -> mastery.get(id).masteryScore() != null
                        && mastery.get(id).masteryScore().compareTo(policy.weakMasteryThreshold()) < 0)
                .sorted(Comparator
                        .comparing((Long id) -> mastery.get(id).masteryScore())
                        .thenComparingLong(Long::longValue))
                .limit(Math.max(0, policy.maxRecommendations() - 1L))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        orderedIds.remove(target.getId());
        orderedIds.add(target.getId());

        Map<Long, AvailableExercise> exercises = availableExercises(target.getCourseId(), orderedIds);
        List<RecommendationApi.LearningPathNodeResponse> nodes = new ArrayList<>();
        for (int index = 0; index < orderedIds.size(); index++) {
            long pointId = orderedIds.get(index);
            KnowledgePoint point = pointsById.get(pointId);
            if (point == null) {
                continue;
            }
            MasteryReadService.MasteryState state = mastery.get(pointId);
            AvailableExercise exercise = exercises.get(pointId);
            nodes.add(new RecommendationApi.LearningPathNodeResponse(
                    nodes.size() + 1,
                    pointId,
                    point.getKnowledgeCode(),
                    point.getKnowledgeName(),
                    state.status().name(),
                    state.masteryScore(),
                    pointId == target.getId()
                            ? RecommendationReasonCode.TARGET_PRACTICE.name()
                            : RecommendationReasonCode.LOW_MASTERY.name(),
                    exercise == null ? null : exercise.id(),
                    exercise == null ? null : exercise.code(),
                    exercise == null ? null : exercise.name(),
                    exercise != null
            ));
        }
        return new RecommendationApi.LearningPathResponse(target.getId(), target.getCourseId(), null, nodes);
    }

    private List<Long> topologicalOrder(
            Set<Long> nodeIds,
            Collection<PublishedGraphStore.ProjectionEdge> edges,
            long targetKnowledgePointId
    ) {
        Map<Long, Set<Long>> outgoing = new HashMap<>();
        Map<Long, Integer> indegree = new HashMap<>();
        nodeIds.forEach(id -> {
            outgoing.put(id, new HashSet<>());
            indegree.put(id, 0);
        });
        for (PublishedGraphStore.ProjectionEdge edge : edges) {
            if (nodeIds.contains(edge.sourceKnowledgePointId()) && nodeIds.contains(edge.targetKnowledgePointId())
                    && outgoing.get(edge.sourceKnowledgePointId()).add(edge.targetKnowledgePointId())) {
                indegree.merge(edge.targetKnowledgePointId(), 1, Integer::sum);
            }
        }
        PriorityQueue<Long> ready = new PriorityQueue<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });
        List<Long> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            long nodeId = ready.remove();
            ordered.add(nodeId);
            for (Long successor : outgoing.get(nodeId).stream().sorted().toList()) {
                int remaining = indegree.merge(successor, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(successor);
                }
            }
        }
        if (ordered.size() != nodeIds.size()) {
            throw new ConflictException("active published graph subgraph is not a DAG; learning path was not synthesized");
        }
        if (nodeIds.contains(targetKnowledgePointId)) {
            ordered.remove(Long.valueOf(targetKnowledgePointId));
            ordered.add(targetKnowledgePointId);
        }
        return List.copyOf(ordered);
    }

    private Map<Long, AvailableExercise> availableExercises(long courseId, Collection<Long> pointIds) {
        Map<Long, List<ExerciseKnowledge>> mappings = new HashMap<>();
        for (ExerciseKnowledge mapping : exerciseKnowledgeRepository.findByKnowledgePointIdInOrderByKnowledgePointIdAscExerciseUnitIdAsc(pointIds)) {
            mappings.computeIfAbsent(mapping.getKnowledgePointId(), ignored -> new ArrayList<>()).add(mapping);
        }
        List<Long> exerciseIds = mappings.values().stream().flatMap(List::stream)
                .map(ExerciseKnowledge::getExerciseUnitId).distinct().toList();
        Map<Long, ExerciseUnit> activeExercises = new HashMap<>();
        if (!exerciseIds.isEmpty()) {
            exerciseUnitRepository.findByIdInAndStatusOrderByExerciseCodeAsc(exerciseIds, "ACTIVE")
                    .stream().filter(exercise -> exercise.getCourseId().equals(courseId))
                    .forEach(exercise -> activeExercises.put(exercise.getId(), exercise));
        }
        Map<Long, AvailableExercise> result = new HashMap<>();
        for (Map.Entry<Long, List<ExerciseKnowledge>> entry : mappings.entrySet()) {
            activeExercises.values().stream()
                    .filter(exercise -> entry.getValue().stream().anyMatch(mapping -> mapping.getExerciseUnitId().equals(exercise.getId())))
                    .filter(exercise -> questionRepository.existsByExerciseUnitIdAndStatus(exercise.getId(), "ACTIVE"))
                    .min(Comparator.comparing(ExerciseUnit::getExerciseCode).thenComparing(ExerciseUnit::getId))
                    .ifPresent(exercise -> result.put(entry.getKey(), new AvailableExercise(
                            exercise.getId(), exercise.getExerciseCode(), exercise.getExerciseName()
                    )));
        }
        return result;
    }

    private record AvailableExercise(long id, String code, String name) {
    }
}
