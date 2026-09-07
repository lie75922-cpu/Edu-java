package com.smartlearning.assessment.application;

import com.smartlearning.assessment.api.ExerciseApi;
import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.knowledge.application.KnowledgeService;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseUnitServiceTest {

    @Mock
    private ExerciseUnitRepository exerciseRepository;
    @Mock
    private ExerciseKnowledgeRepository mappingRepository;
    @Mock
    private CourseService courseService;
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private KnowledgeService knowledgeService;

    @Test
    void mappingUsesDedicatedManyToManyRecordAndMarksExerciseMapped() {
        ExerciseUnit exercise = new ExerciseUnit(3L, "EX-1", "Exercise", "PLATFORM", null, "RESOLVED", "UNMAPPED", null, "ACTIVE");
        KnowledgePoint point = new KnowledgePoint(3L, null, "KP-1", "Knowledge", "PLATFORM", null, "MAPPED", "ACTIVE");
        ReflectionTestUtils.setField(exercise, "id", 12L);
        ReflectionTestUtils.setField(point, "id", 9L);
        ExerciseKnowledge mapping = new ExerciseKnowledge(12L, 9L, "MANUAL", new BigDecimal("0.9000"), true);
        ReflectionTestUtils.setField(mapping, "id", 20L);
        when(exerciseRepository.findById(12L)).thenReturn(Optional.of(exercise));
        when(knowledgeService.requirePoint(9L)).thenReturn(point);
        when(mappingRepository.findByExerciseUnitIdAndKnowledgePointId(12L, 9L)).thenReturn(Optional.empty());
        when(mappingRepository.save(any(ExerciseKnowledge.class))).thenReturn(mapping);
        when(mappingRepository.findByExerciseUnitIdOrderByIdAsc(12L)).thenReturn(List.of(mapping));
        ExerciseUnitService service = new ExerciseUnitService(
                exerciseRepository, mappingRepository, courseService, courseAccessService, knowledgeService
        );

        ExerciseApi.ExerciseUnitResponse response = service.upsertMapping(
                12L, new ExerciseApi.MappingRequest(9L, "MANUAL", new BigDecimal("0.9000"), true)
        );

        verify(mappingRepository).save(any(ExerciseKnowledge.class));
        assertThat(exercise.getMappingStatus()).isEqualTo("MAPPED");
        assertThat(response.knowledgePoints()).singleElement().satisfies(item -> {
            assertThat(item.knowledgePointId()).isEqualTo(9L);
            assertThat(item.mappingSource()).isEqualTo("MANUAL");
        });
    }
}
