package com.smartlearning.catalog.application;

import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.catalog.api.SeedImportApi;
import com.smartlearning.catalog.domain.CatalogSourceRecord;
import com.smartlearning.catalog.domain.SeedImportConflict;
import com.smartlearning.catalog.domain.SeedImportRun;
import com.smartlearning.catalog.infrastructure.persistence.SeedImportConflictRepository;
import com.smartlearning.catalog.infrastructure.persistence.SeedImportRunRepository;
import com.smartlearning.catalog.infrastructure.persistence.CatalogSourceRecordRepository;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.course.domain.Course;
import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.knowledge.domain.KnowledgeArea;
import com.smartlearning.knowledge.domain.KnowledgePoint;
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
import java.nio.file.Path;
import java.util.Map;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

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
    @Mock
    private CatalogSourceRecordRepository sourceRecordRepository;

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
                runRepository, conflictRepository, sourceRecordRepository, new ObjectMapper()
        );
        SeedImportApi.SeedImportRequest request = new SeedImportApi.SeedImportRequest(
                "MATH-1", "Mathematics", List.of(), List.of(), List.of(
                        new SeedImportApi.SeedExercise("same-exercise", "First", "", null),
                        new SeedImportApi.SeedExercise("same-exercise", "Second", "", null)
                )
        );

        SeedImportApi.SeedImportResult result = service.dryRun(request, 9L);

        assertThat(result.status()).isEqualTo("DRY_RUN_COMPLETED_WITH_CONFLICTS");
        assertThat(result.conflictCount()).isEqualTo(1);
        assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.entityType()).isEqualTo("EXERCISE_UNIT");
            assertThat(conflict.conflictType()).isEqualTo("DUPLICATE_SOURCE_EXTERNAL_ID");
        });
        verify(exerciseRepository, never()).save(any());
        verify(mappingRepository, never()).save(any());
    }

    @Test
    void dryRunPreflightsDatabaseEquivalentExternalIdsWithDifferentRawValues() {
        when(courseRepository.findByCourseCode("MATH-1")).thenReturn(Optional.empty());
        when(runRepository.save(any(SeedImportRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 88L));
        when(conflictRepository.save(any(SeedImportConflict.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
        SeedImportApi.SeedImportRequest request = new SeedImportApi.SeedImportRequest(
                "MATH-1", "Mathematics",
                List.of(new SeedImportApi.SeedArea("area-1", "Area")),
                List.of(new SeedImportApi.SeedTopic("topic-1", "Topic", "area-1")),
                List.of(
                        new SeedImportApi.SeedExercise("Case-Identity", "First", "topic-1", null, "raw-first", "First", "UNREVIEWED", "ELIGIBLE_FOR_IMPORT", null, Map.of(), Map.of()),
                        new SeedImportApi.SeedExercise("case-identity", "Second", "topic-1", null, "raw-second", "Second", "UNREVIEWED", "ELIGIBLE_FOR_IMPORT", null, Map.of(), Map.of())
                )
        );

        SeedImportApi.SeedImportResult result = service().dryRun(request, 9L);

        assertThat(result.status()).isEqualTo("DRY_RUN_COMPLETED_WITH_CONFLICTS");
        assertThat(result.createdExerciseUnits()).isEqualTo(1);
        assertThat(result.conflictCount()).isEqualTo(1);
        assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.entityType()).isEqualTo("EXERCISE_UNIT");
            assertThat(conflict.conflictType()).isEqualTo("RAW_VALUE_CONFLICT");
        });
        verify(exerciseRepository, never()).save(any());
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

    @Test
    void foundationFixtureDryRunPersistsOnlyAuditAndNeverBusinessEntities() {
        when(courseRepository.findByCourseCode("JUNYI-MATH")).thenReturn(Optional.empty());
        stubSavedRun();
        SeedImportService service = service();
        SeedImportApi.SeedImportRequest request = foundationFixtureRequest();

        SeedImportApi.SeedImportResult result = service.dryRun(request, 9L);

        assertThat(result.mode()).isEqualTo("DRY_RUN");
        assertThat(result.createdCourses()).isEqualTo(1);
        assertThat(result.createdAreas()).isEqualTo(1);
        assertThat(result.createdKnowledgePoints()).isEqualTo(1);
        assertThat(result.createdExerciseUnits()).isEqualTo(1);
        assertThat(result.createdMappings()).isEqualTo(1);
        assertThat(result.metadata().exportFormatVersion()).isEqualTo("1.0.0");
        verify(courseRepository, never()).save(any());
        verify(areaRepository, never()).save(any());
        verify(pointRepository, never()).save(any());
        verify(exerciseRepository, never()).save(any());
        verify(mappingRepository, never()).save(any());
        verify(sourceRecordRepository, never()).save(any());
    }

    @Test
    void foundationFixtureApplyCreatesTheBusinessProjectionAndAuditProvenance() {
        Course course = new Course("JUNYI-MATH", "数学", "fixture", "ACTIVE");
        setId(course, 1L);
        when(courseRepository.findByCourseCode("JUNYI-MATH")).thenReturn(Optional.empty());
        when(courseRepository.save(any(Course.class))).thenReturn(course);
        when(areaRepository.save(any(KnowledgeArea.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 2L));
        when(pointRepository.save(any(KnowledgePoint.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 3L));
        when(exerciseRepository.save(any(ExerciseUnit.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 4L));
        when(mappingRepository.findByExerciseUnitIdAndKnowledgePointId(4L, 3L)).thenReturn(Optional.empty());
        when(sourceRecordRepository.findByCourseIdAndEntityTypeAndExternalId(any(), any(), any())).thenReturn(Optional.empty());
        stubSavedRun();

        SeedImportApi.SeedImportResult result = service().apply(foundationFixtureRequest(), 9L);

        assertThat(result.createdAreas()).isEqualTo(1);
        assertThat(result.createdKnowledgePoints()).isEqualTo(1);
        assertThat(result.createdExerciseUnits()).isEqualTo(1);
        assertThat(result.createdMappings()).isEqualTo(1);
        assertThat(result.createdProvenanceRecords()).isEqualTo(3);
        verify(sourceRecordRepository, times(3)).save(any(CatalogSourceRecord.class));
        verify(mappingRepository).save(any(ExerciseKnowledge.class));
    }

    @Test
    void unchangedRawIdentityAllowsDisplayOnlyUpdateWithoutNewBusinessIdentity() {
        Course course = new Course("JUNYI-MATH", "数学", "fixture", "ACTIVE");
        setId(course, 1L);
        KnowledgeArea area = new KnowledgeArea(1L, "JUNYI_AREA_FIXTURE", "旧领域", "JUNYI_CATALOG", "fixture-area:1", "ACTIVE");
        setId(area, 2L);
        KnowledgePoint point = new KnowledgePoint(1L, 2L, "JUNYI_TOPIC_FIXTURE", "旧主题", "JUNYI_TOPIC", "fixture-topic:1", "MAPPED", "ACTIVE");
        setId(point, 3L);
        ExerciseUnit exercise = new ExerciseUnit(1L, "JUNYI_EXERCISE_FIXTURE", "旧练习", "JUNYI_CATALOG", "fixture-exercise-1", "RESOLVED", "MAPPED", null, "ACTIVE");
        setId(exercise, 4L);
        Map<String, CatalogSourceRecord> sources = Map.of(
                "KNOWLEDGE_AREA", new CatalogSourceRecord(1L, "KNOWLEDGE_AREA", "fixture-area:1", "fixture-raw-area-1", "旧领域", "REVIEWED", "ELIGIBLE_FOR_IMPORT", null, "{}"),
                "KNOWLEDGE_POINT", new CatalogSourceRecord(1L, "KNOWLEDGE_POINT", "fixture-topic:1", "fixture-raw-topic-1", "旧主题", "REVIEWED", "ELIGIBLE_FOR_IMPORT", null, "{}"),
                "EXERCISE_UNIT", new CatalogSourceRecord(1L, "EXERCISE_UNIT", "fixture-exercise-1", "fixture-raw-exercise-1", "旧练习", "UNREVIEWED", "ELIGIBLE_FOR_IMPORT", 1, "{}")
        );
        when(courseRepository.findByCourseCode("JUNYI-MATH")).thenReturn(Optional.of(course));
        when(areaRepository.findByCourseIdAndSourceTypeAndExternalId(1L, "JUNYI_CATALOG", "fixture-area:1")).thenReturn(List.of(area));
        when(pointRepository.findByCourseIdAndSourceTypeAndExternalId(1L, "JUNYI_TOPIC", "fixture-topic:1")).thenReturn(List.of(point));
        when(exerciseRepository.findByCourseIdAndSourceTypeAndExternalId(1L, "JUNYI_CATALOG", "fixture-exercise-1")).thenReturn(List.of(exercise));
        when(mappingRepository.findByExerciseUnitIdAndKnowledgePointId(4L, 3L)).thenReturn(Optional.of(org.mockito.Mockito.mock(ExerciseKnowledge.class)));
        when(sourceRecordRepository.findByCourseIdAndEntityTypeAndExternalId(any(), any(), any()))
                .thenAnswer(invocation -> Optional.of(sources.get(invocation.getArgument(1))));
        stubSavedRun();

        SeedImportApi.SeedImportResult result = service().apply(foundationFixtureRequest(), 9L);

        assertThat(result.createdAreas()).isZero();
        assertThat(result.createdKnowledgePoints()).isZero();
        assertThat(result.createdExerciseUnits()).isZero();
        assertThat(result.updatedAreas()).isEqualTo(1);
        assertThat(result.updatedKnowledgePoints()).isEqualTo(1);
        assertThat(result.updatedExerciseUnits()).isEqualTo(1);
        assertThat(area.getExternalId()).isEqualTo("fixture-area:1");
        assertThat(area.getAreaName()).isEqualTo("示例领域");
        assertThat(point.getKnowledgeName()).isEqualTo("示例主题");
        assertThat(exercise.getExerciseName()).isEqualTo("示例练习");
        verify(sourceRecordRepository, times(3)).save(any(CatalogSourceRecord.class));
        verify(courseRepository, never()).save(any());
        verify(areaRepository, never()).save(any());
        verify(pointRepository, never()).save(any());
        verify(exerciseRepository, never()).save(any());
    }

    private SeedImportService service() {
        return new SeedImportService(
                courseRepository, areaRepository, pointRepository, exerciseRepository, mappingRepository,
                runRepository, conflictRepository, sourceRecordRepository, new ObjectMapper()
        );
    }

    private SeedImportApi.SeedImportRequest foundationFixtureRequest() {
        return new FoundationBusinessExportReader(new ObjectMapper()).read(
                Path.of("src/test/resources/real-data-v1/business_export_fixture"), "JUNYI-MATH", "数学"
        ).catalogRequest();
    }

    private void stubSavedRun() {
        when(runRepository.save(any(SeedImportRun.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 88L));
    }

    private <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private void setId(Object entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
