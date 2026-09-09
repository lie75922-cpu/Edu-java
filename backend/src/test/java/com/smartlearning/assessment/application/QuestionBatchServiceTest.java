package com.smartlearning.assessment.application;

import com.smartlearning.assessment.api.QuestionApi;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.domain.Question;
import com.smartlearning.assessment.infrastructure.persistence.QuestionOptionRepository;
import com.smartlearning.assessment.infrastructure.persistence.QuestionRepository;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.course.application.CourseAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionBatchServiceTest {

    @Test
    void createsEveryQuestionAndChecksTeachingAccessForEveryTargetCourse() {
        QuestionRepository questionRepository = mock(QuestionRepository.class);
        QuestionOptionRepository optionRepository = mock(QuestionOptionRepository.class);
        ExerciseUnitService exerciseUnitService = mock(ExerciseUnitService.class);
        CourseAccessService accessService = mock(CourseAccessService.class);
        QuestionService service = new QuestionService(
                questionRepository, optionRepository, exerciseUnitService, accessService, new ObjectMapper()
        );

        ExerciseUnit exerciseA = exercise(101L, 7L);
        ExerciseUnit exerciseB = exercise(202L, 8L);
        when(exerciseUnitService.requireExercise(101L)).thenReturn(exerciseA);
        when(exerciseUnitService.requireExercise(202L)).thenReturn(exerciseB);
        when(optionRepository.findByQuestionIdOrderBySortOrderAsc(anyLong())).thenReturn(List.of());

        AtomicLong ids = new AtomicLong(1000L);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            ReflectionTestUtils.setField(question, "id", ids.incrementAndGet());
            return question;
        });

        CurrentUser teacher = new CurrentUser(55L, "teacher", Set.of("TEACHER"));
        QuestionApi.QuestionBatchResponse response = service.createBatchForTeaching(
                new QuestionApi.QuestionBatchRequest(List.of(
                        request(101L, "第一道批量题"),
                        request(202L, "第二道批量题")
                )),
                teacher
        );

        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(response.items()).extracting(QuestionApi.AdminQuestionResponse::stem)
                .containsExactly("第一道批量题", "第二道批量题");
        verify(accessService).requireTeachingAccess(7L, teacher);
        verify(accessService).requireTeachingAccess(8L, teacher);
        verify(questionRepository, times(2)).save(any(Question.class));
    }

    private QuestionApi.QuestionRequest request(long exerciseId, String stem) {
        return new QuestionApi.QuestionRequest(
                exerciseId,
                "SINGLE_CHOICE",
                stem,
                List.of("A"),
                "授权内容测试解析",
                new BigDecimal("30"),
                "ACTIVE",
                List.of(
                        new QuestionApi.OptionRequest("A", "正确选项", 1),
                        new QuestionApi.OptionRequest("B", "干扰选项", 2)
                )
        );
    }

    private ExerciseUnit exercise(long id, long courseId) {
        ExerciseUnit exercise = mock(ExerciseUnit.class);
        when(exercise.getId()).thenReturn(id);
        when(exercise.getCourseId()).thenReturn(courseId);
        return exercise;
    }
}
