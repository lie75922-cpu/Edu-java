package com.smartlearning.recommendation.application;

import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.graph.application.PublishedGraphStore;
import com.smartlearning.learning.infrastructure.persistence.AnswerRecordRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import com.smartlearning.mastery.application.MasteryReadService;
import com.smartlearning.mastery.domain.MasteryStatus;
import com.smartlearning.mastery.domain.StudentKnowledgeMastery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecommendationCandidateGeneratorTest {

    @Test
    void coldStartUsesStableCourseOrderWithoutInventingMasteryOrCallingGraph() {
        MasteryReadService masteryReadService = mock(MasteryReadService.class);
        KnowledgePointRepository pointRepository = mock(KnowledgePointRepository.class);
        AnswerRecordRepository answerRepository = mock(AnswerRecordRepository.class);
        ExerciseKnowledgeRepository mappingRepository = mock(ExerciseKnowledgeRepository.class);
        PublishedGraphStore graphStore = mock(PublishedGraphStore.class);
        RecommendationCandidateGenerator generator = new RecommendationCandidateGenerator(
                masteryReadService,
                pointRepository,
                answerRepository,
                mappingRepository,
                graphStore,
                new RecommendationRulePolicy()
        );

        KnowledgePoint first = point(11L, "A", "First");
        KnowledgePoint second = point(12L, "B", "Second");
        when(pointRepository.findByCourseIdAndStatusOrderByKnowledgeCodeAsc(7L, "ACTIVE"))
                .thenReturn(List.of(first, second));
        when(masteryReadService.observedForStudentAndCourse(3L, 7L)).thenReturn(List.of());
        when(masteryReadService.statesFor(anyLong(), anyLong(), any())).thenReturn(Map.of(
                11L, MasteryReadService.MasteryState.unknown(),
                12L, MasteryReadService.MasteryState.unknown()
        ));
        when(answerRepository.findByStudentIdAndCourseIdAndAnsweredAtAfterOrderByAnsweredAtDesc(anyLong(), anyLong(), any()))
                .thenReturn(List.of());
        when(mappingRepository.findByKnowledgePointIdInOrderByKnowledgePointIdAscExerciseUnitIdAsc(any()))
                .thenReturn(List.of());

        List<RecommendationCandidate> candidates = generator.generate(3L, 7L, null);

        assertThat(candidates).extracting(RecommendationCandidate::knowledgePointId).containsExactly(11L, 12L);
        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.graphVersionId()).isNull();
            assertThat(candidate.mastery().status()).isEqualTo(MasteryStatus.UNKNOWN);
            assertThat(candidate.mastery().masteryScore()).isNull();
            assertThat(candidate.unmetPrerequisite()).isFalse();
        });
        verifyNoInteractions(graphStore);
    }

    @Test
    void noGraphRecommendsObservedWeakTopicWithoutAddingUnknownTopics() {
        MasteryReadService masteryReadService = mock(MasteryReadService.class);
        KnowledgePointRepository pointRepository = mock(KnowledgePointRepository.class);
        AnswerRecordRepository answerRepository = mock(AnswerRecordRepository.class);
        ExerciseKnowledgeRepository mappingRepository = mock(ExerciseKnowledgeRepository.class);
        PublishedGraphStore graphStore = mock(PublishedGraphStore.class);
        RecommendationCandidateGenerator generator = new RecommendationCandidateGenerator(
                masteryReadService,
                pointRepository,
                answerRepository,
                mappingRepository,
                graphStore,
                new RecommendationRulePolicy()
        );

        KnowledgePoint weakPoint = point(21L, "A", "Weak");
        KnowledgePoint strongPoint = point(22L, "B", "Strong");
        when(pointRepository.findByCourseIdAndStatusOrderByKnowledgeCodeAsc(8L, "ACTIVE"))
                .thenReturn(List.of(weakPoint, strongPoint));
        StudentKnowledgeMastery weak = mastery(21L, "0.4000");
        StudentKnowledgeMastery strong = mastery(22L, "0.9000");
        when(masteryReadService.observedForStudentAndCourse(4L, 8L)).thenReturn(List.of(weak, strong));
        when(masteryReadService.statesFor(anyLong(), anyLong(), any())).thenReturn(Map.of(
                21L, observedState("0.4000"),
                22L, observedState("0.9000")
        ));
        when(answerRepository.findByStudentIdAndCourseIdAndAnsweredAtAfterOrderByAnsweredAtDesc(anyLong(), anyLong(), any()))
                .thenReturn(List.of());
        when(mappingRepository.findByKnowledgePointIdInOrderByKnowledgePointIdAscExerciseUnitIdAsc(any()))
                .thenReturn(List.of());

        List<RecommendationCandidate> candidates = generator.generate(4L, 8L, null);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().knowledgePointId()).isEqualTo(21L);
        assertThat(candidates.getFirst().mastery().masteryScore()).isEqualByComparingTo("0.4000");
        verifyNoInteractions(graphStore);
    }

    private KnowledgePoint point(long id, String code, String name) {
        KnowledgePoint point = mock(KnowledgePoint.class);
        when(point.getId()).thenReturn(id);
        when(point.getKnowledgeCode()).thenReturn(code);
        when(point.getKnowledgeName()).thenReturn(name);
        return point;
    }

    private StudentKnowledgeMastery mastery(long pointId, String score) {
        StudentKnowledgeMastery mastery = mock(StudentKnowledgeMastery.class);
        when(mastery.getKnowledgePointId()).thenReturn(pointId);
        when(mastery.getMasteryScore()).thenReturn(new BigDecimal(score));
        when(mastery.getLastAnsweredAt()).thenReturn(Instant.parse("2026-09-01T00:00:00Z"));
        return mastery;
    }

    private MasteryReadService.MasteryState observedState(String score) {
        return new MasteryReadService.MasteryState(
                MasteryStatus.OBSERVED,
                new BigDecimal(score),
                2,
                1,
                "RULE",
                "RULE_BETA_1_1_V1",
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z")
        );
    }
}
