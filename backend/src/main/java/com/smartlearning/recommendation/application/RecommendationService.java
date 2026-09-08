package com.smartlearning.recommendation.application;

import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.graph.application.GraphQueryService;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import com.smartlearning.mastery.domain.MasteryProvider;
import com.smartlearning.mastery.domain.MasteryStatus;
import com.smartlearning.recommendation.api.RecommendationApi;
import com.smartlearning.recommendation.domain.RecommendationItem;
import com.smartlearning.recommendation.domain.RecommendationSnapshot;
import com.smartlearning.recommendation.infrastructure.persistence.RecommendationItemRepository;
import com.smartlearning.recommendation.infrastructure.persistence.RecommendationSnapshotRepository;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {

    private final CourseRepository courseRepository;
    private final CourseAccessService courseAccessService;
    private final GraphQueryService graphQueryService;
    private final RecommendationCandidateGenerator candidateGenerator;
    private final RecommendationFilter recommendationFilter;
    private final RecommendationRanker recommendationRanker;
    private final RecommendationExplanationBuilder explanationBuilder;
    private final RecommendationRulePolicy policy;
    private final RecommendationSnapshotRepository snapshotRepository;
    private final RecommendationItemRepository itemRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final ExerciseUnitRepository exerciseUnitRepository;
    private final MasteryProvider masteryProvider;

    public RecommendationService(
            CourseRepository courseRepository,
            CourseAccessService courseAccessService,
            GraphQueryService graphQueryService,
            RecommendationCandidateGenerator candidateGenerator,
            RecommendationFilter recommendationFilter,
            RecommendationRanker recommendationRanker,
            RecommendationExplanationBuilder explanationBuilder,
            RecommendationRulePolicy policy,
            RecommendationSnapshotRepository snapshotRepository,
            RecommendationItemRepository itemRepository,
            KnowledgePointRepository knowledgePointRepository,
            ExerciseUnitRepository exerciseUnitRepository,
            MasteryProvider masteryProvider
    ) {
        this.courseRepository = courseRepository;
        this.courseAccessService = courseAccessService;
        this.graphQueryService = graphQueryService;
        this.candidateGenerator = candidateGenerator;
        this.recommendationFilter = recommendationFilter;
        this.recommendationRanker = recommendationRanker;
        this.explanationBuilder = explanationBuilder;
        this.policy = policy;
        this.snapshotRepository = snapshotRepository;
        this.itemRepository = itemRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.masteryProvider = masteryProvider;
    }

    @Transactional
    public RecommendationApi.RecommendationSnapshotResponse generate(long courseId, CurrentUser user) {
        requireCourseAccess(courseId, user);
        long graphVersionId = graphQueryService.activeGraphVersionId(courseId, user);
        List<RecommendationCandidate> candidates;
        try {
            candidates = candidateGenerator.generate(user.id(), courseId, graphVersionId);
        } catch (Neo4jException ex) {
            throw new ConflictException("active published graph is unavailable; recommendations were not synthesized");
        }
        List<RecommendationFilter.PracticeCandidate> available = recommendationFilter.filter(
                candidates, courseId, graphVersionId
        );
        List<RecommendationRanker.RankedRecommendation> ranked = recommendationRanker.rank(available);

        RecommendationSnapshot snapshot = snapshotRepository.save(new RecommendationSnapshot(
                user.id(), courseId, graphVersionId, masteryProvider.algorithmVersion(), policy.ruleVersion()
        ));
        List<RecommendationItem> items = ranked.stream().map(ranking -> {
            RecommendationExplanationBuilder.Explanation explanation = explanationBuilder.build(ranking);
            RecommendationCandidate candidate = ranking.practice().candidate();
            return itemRepository.save(new RecommendationItem(
                    snapshot.getId(), ranking.rank(), candidate.knowledgePointId(), ranking.practice().exerciseUnitId(),
                    candidate.mastery().masteryScore(), explanation.reasonCode(), explanation.evidenceJson()
            ));
        }).toList();
        return toSnapshotResponse(snapshot, items);
    }

    public RecommendationApi.RecommendationSnapshotResponse latest(long courseId, CurrentUser user) {
        requireCourseAccess(courseId, user);
        RecommendationSnapshot snapshot = snapshotRepository
                .findFirstByStudentIdAndCourseIdOrderByGeneratedAtDescIdDesc(user.id(), courseId)
                .orElseThrow(() -> new NotFoundException("no recommendation snapshot exists for this student and course"));
        return toSnapshotResponse(snapshot, itemRepository.findByRecommendationSnapshotIdOrderByRankNoAsc(snapshot.getId()));
    }

    private void requireCourseAccess(long courseId, CurrentUser user) {
        if (!courseRepository.existsById(courseId)) {
            throw new NotFoundException("course does not exist");
        }
        courseAccessService.requireCourseAccess(courseId, user);
    }

    private RecommendationApi.RecommendationSnapshotResponse toSnapshotResponse(
            RecommendationSnapshot snapshot,
            List<RecommendationItem> items
    ) {
        Map<Long, KnowledgePoint> points = new HashMap<>();
        knowledgePointRepository.findAllById(items.stream().map(RecommendationItem::getKnowledgePointId).toList())
                .forEach(point -> points.put(point.getId(), point));
        Map<Long, ExerciseUnit> exercises = new HashMap<>();
        exerciseUnitRepository.findAllById(items.stream().map(RecommendationItem::getExerciseUnitId)
                        .filter(java.util.Objects::nonNull).toList())
                .forEach(exercise -> exercises.put(exercise.getId(), exercise));
        List<RecommendationApi.RecommendationItemResponse> responses = items.stream().map(item -> {
            KnowledgePoint point = points.get(item.getKnowledgePointId());
            ExerciseUnit exercise = item.getExerciseUnitId() == null ? null : exercises.get(item.getExerciseUnitId());
            return new RecommendationApi.RecommendationItemResponse(
                    item.getId(), item.getRankNo(), item.getKnowledgePointId(),
                    point == null ? null : point.getKnowledgeCode(), point == null ? null : point.getKnowledgeName(),
                    item.getExerciseUnitId(), exercise == null ? null : exercise.getExerciseCode(),
                    exercise == null ? null : exercise.getExerciseName(), item.getMasteryScore(),
                    item.getMasteryScore() == null ? MasteryStatus.UNKNOWN.name() : MasteryStatus.OBSERVED.name(),
                    item.getReasonCode().name(), item.getExplanationJson()
            );
        }).toList();
        return new RecommendationApi.RecommendationSnapshotResponse(
                snapshot.getId(), snapshot.getStudentId(), snapshot.getCourseId(), snapshot.getGraphVersionId(),
                snapshot.getMasteryAlgorithmVersion(), snapshot.getRecommendationRuleVersion(), snapshot.getGeneratedAt(), responses
        );
    }
}
