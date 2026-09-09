package com.smartlearning.recommendation.application;

import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.assessment.infrastructure.persistence.QuestionRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import com.smartlearning.mastery.application.MasteryReadService;
import com.smartlearning.mastery.domain.MasteryStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationFilterTest {

    @Test
    void excludesInvalidCandidatesButKeepsTopicLevelRecommendationWhenQuestionContentIsUnavailable() {
        KnowledgePointRepository pointRepository = mock(KnowledgePointRepository.class);
        ExerciseKnowledgeRepository mappingRepository = mock(ExerciseKnowledgeRepository.class);
        ExerciseUnitRepository exerciseRepository = mock(ExerciseUnitRepository.class);
        QuestionRepository questionRepository = mock(QuestionRepository.class);
        RecommendationFilter filter = new RecommendationFilter(
                pointRepository, mappingRepository, exerciseRepository, questionRepository
        );

        KnowledgePoint allowedPoint = point(1L, 10L, "ACTIVE");
        KnowledgePoint wrongGraphPoint = point(2L, 10L, "ACTIVE");
        KnowledgePoint inactivePoint = point(3L, 10L, "DISABLED");
        KnowledgePoint noQuestionPoint = point(4L, 10L, "ACTIVE");
        KnowledgePoint inactiveExercisePoint = point(5L, 10L, "ACTIVE");
        List<KnowledgePoint> points = List.of(
                allowedPoint, wrongGraphPoint, inactivePoint, noQuestionPoint, inactiveExercisePoint
        );
        when(pointRepository.findAllById(any())).thenReturn(points);

        List<ExerciseKnowledge> mappings = List.of(
                mapping(1L, 101L), mapping(2L, 102L), mapping(3L, 103L), mapping(4L, 104L), mapping(5L, 105L)
        );
        when(mappingRepository.findByKnowledgePointIdInOrderByKnowledgePointIdAscExerciseUnitIdAsc(any())).thenReturn(mappings);
        ExerciseUnit allowedExercise = exercise(101L, 10L, "EX-101");
        ExerciseUnit noQuestionExercise = exercise(104L, 10L, "EX-104");
        when(exerciseRepository.findByIdInAndStatusOrderByExerciseCodeAsc(any(), eq("ACTIVE")))
                .thenReturn(List.of(allowedExercise, noQuestionExercise));
        when(questionRepository.existsByExerciseUnitIdAndStatus(101L, "ACTIVE")).thenReturn(true);
        when(questionRepository.existsByExerciseUnitIdAndStatus(104L, "ACTIVE")).thenReturn(false);

        List<RecommendationFilter.PracticeCandidate> filtered = filter.filter(List.of(
                candidate(42L, 1L),
                candidate(99L, 2L),
                candidate(42L, 3L),
                candidate(42L, 4L),
                candidate(42L, 5L)
        ), 10L, 42L);

        assertThat(filtered).hasSize(2);
        assertThat(filtered.get(0).candidate().knowledgePointId()).isEqualTo(1L);
        assertThat(filtered.get(0).exerciseUnitId()).isEqualTo(101L);
        assertThat(filtered.get(1).candidate().knowledgePointId()).isEqualTo(4L);
        assertThat(filtered.get(1).exerciseUnitId()).isNull();
    }

    private KnowledgePoint point(long id, long courseId, String status) {
        KnowledgePoint point = mock(KnowledgePoint.class);
        when(point.getId()).thenReturn(id);
        when(point.getCourseId()).thenReturn(courseId);
        when(point.getStatus()).thenReturn(status);
        return point;
    }

    private ExerciseKnowledge mapping(long pointId, long exerciseId) {
        ExerciseKnowledge mapping = mock(ExerciseKnowledge.class);
        when(mapping.getKnowledgePointId()).thenReturn(pointId);
        when(mapping.getExerciseUnitId()).thenReturn(exerciseId);
        return mapping;
    }

    private ExerciseUnit exercise(long id, long courseId, String code) {
        ExerciseUnit exercise = mock(ExerciseUnit.class);
        when(exercise.getId()).thenReturn(id);
        when(exercise.getCourseId()).thenReturn(courseId);
        when(exercise.getExerciseCode()).thenReturn(code);
        when(exercise.getExerciseName()).thenReturn("Exercise " + id);
        return exercise;
    }

    private RecommendationCandidate candidate(long graphVersionId, long pointId) {
        MasteryReadService.MasteryState state = new MasteryReadService.MasteryState(
                MasteryStatus.OBSERVED,
                new BigDecimal("0.3333"),
                1,
                0,
                "RULE",
                "RULE_BETA_1_1_V1",
                Instant.parse("2026-09-08T00:00:00Z"),
                Instant.parse("2026-09-08T00:00:00Z")
        );
        return new RecommendationCandidate(
                graphVersionId, pointId, "KP-" + pointId, "Point " + pointId, state, false, 0, List.of()
        );
    }
}
