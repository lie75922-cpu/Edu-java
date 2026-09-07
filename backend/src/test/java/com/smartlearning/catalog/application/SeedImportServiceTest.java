package com.smartlearning.catalog.application;

import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.catalog.api.SeedImportApi;
import com.smartlearning.catalog.domain.SeedImportConflict;
import com.smartlearning.catalog.domain.SeedImportRun;
import com.smartlearning.catalog.infrastructure.persistence.SeedImportConflictRepository;
import com.smartlearning.catalog.infrastructure.persistence.SeedImportRunRepository;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgeAreaRepository;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedImportServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private KnowledgeAreaRepository areaRepository;
    @Mock
    private KnowledgePointRepository pointRepository;
    @Mock
    private ExerciseUnitRepository exerciseRepository;
    @Mock
    private ExerciseKnowledgeRepository mappingRepository;
    @Mock
    private SeedImportRunRepository runRepository;
    @Mock
    private SeedImportConflictRepository conflictRepository;

    @Test
    void duplicateResearchExerciseIdsAreQuarantinedInsteadOfMerged() {
        when(courseRepository.findByCourseCode("MATH-1")).thenReturn(Optional.empty());
        when(runRepository.save(any(SeedImportRun.class))).thenAnswer(invocation -> {
            SeedImportRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", 88L);
            return run;
        });
        when(conflictRepository.save(any(SeedImportConflict.class))).thenAnswer(invocation -> {
            SeedImportConflict conflict = invocation.getArgument(0);
            ReflectionTestUtils.setField(conflict, "id", 1L);
            return conflict;
        });
        SeedImportService service = new SeedImportService(
                courseRepository, areaRepository, pointRepository, exerciseRepository, mappingRepository,
                runRepository, conflictRepository, new ObjectMapper()
        );
        SeedImportApi.SeedImportRequest request = new SeedImportApi.SeedImportRequest(
                "MATH-1", "Mathematics", List.of(), List.of(), List.of(
                        new SeedImportApi.SeedExercise("same-exercise", "First", "", null),
                        new SeedImportApi.SeedExercise("same-exercise", "Second", "", null)
                )
        );

        SeedImportApi.SeedImportResult result = service.dryRun(request, 9L);

        assertThat(result.conflictCount()).isEqualTo(1);
        assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.entityType()).isEqualTo("EXERCISE_UNIT");
            assertThat(conflict.conflictType()).isEqualTo("DUPLICATE_SOURCE_EXTERNAL_ID");
        });
        verify(exerciseRepository, never()).save(any());
        verify(mappingRepository, never()).save(any());
    }

    @Test
    void catalogImporterHasNoPlatformUserOrAnswerRecordWriteDependency() {
        List<String> dependencyTypes = Arrays.stream(SeedImportService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .toList();
        List<String> requestFields = Arrays.stream(SeedImportApi.SeedImportRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertThat(dependencyTypes).noneMatch(name -> name.contains("PlatformUser") || name.contains("AnswerRecord"));
        assertThat(requestFields).doesNotContain("user", "student", "problemLog", "interactions");
    }
}
