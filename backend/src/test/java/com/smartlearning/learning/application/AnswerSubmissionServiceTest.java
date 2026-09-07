package com.smartlearning.learning.application;

import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.assessment.application.QuestionService;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.domain.Question;
import com.smartlearning.assessment.domain.QuestionOption;
import com.smartlearning.assessment.infrastructure.persistence.QuestionOptionRepository;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.ForbiddenOperationException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.learning.api.AnswerApi;
import com.smartlearning.learning.domain.AnswerRecord;
import com.smartlearning.learning.infrastructure.persistence.AnswerRecordRepository;
import com.smartlearning.outbox.application.OutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerSubmissionServiceTest {

    @Mock
    private AnswerRecordRepository answerRecordRepository;
    @Mock
    private QuestionService questionService;
    @Mock
    private QuestionOptionRepository optionRepository;
    @Mock
    private ExerciseUnitService exerciseUnitService;
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private OutboxService outboxService;

    @Test
    void correctAnswerPersistsRecordAndOutboxEvent() {
        Question question = question(31L, "ACTIVE");
        ExerciseUnit exercise = exercise(12L, "ACTIVE");
        when(answerRecordRepository.findByStudentIdAndClientRequestId(5L, "request-1")).thenReturn(Optional.empty());
        when(questionService.requireQuestion(31L)).thenReturn(question);
        when(exerciseUnitService.requireExercise(12L)).thenReturn(exercise);
        when(optionRepository.findByQuestionIdOrderBySortOrderAsc(31L)).thenReturn(List.of(option(31L, "A"), option(31L, "B")));
        when(questionService.answerOptionKeys(question)).thenReturn(List.of("A"));
        when(answerRecordRepository.countByStudentIdAndQuestionId(5L, 31L)).thenReturn(0L);
        when(answerRecordRepository.save(any(AnswerRecord.class))).thenAnswer(invocation -> {
            AnswerRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 101L);
            return record;
        });
        AnswerSubmissionService service = service();

        AnswerApi.AnswerResultResponse result = service.submit(31L, request("A", "request-1"), student());

        ArgumentCaptor<AnswerRecord> recordCaptor = ArgumentCaptor.forClass(AnswerRecord.class);
        verify(answerRecordRepository).save(recordCaptor.capture());
        verify(outboxService).enqueueMasteryUpdate(101L, 5L, 3L, 31L, 12L, true);
        assertThat(recordCaptor.getValue().isCorrect()).isTrue();
        assertThat(result.correct()).isTrue();
        assertThat(result.idempotentReplay()).isFalse();
    }

    @Test
    void duplicateClientRequestIsReplayedWithoutSecondWriteOrOutboxEvent() {
        AnswerRecord existing = new AnswerRecord(5L, 3L, 31L, 12L, "[\"A\"]", true, 1, 10L, "request-1", java.time.Instant.now());
        ReflectionTestUtils.setField(existing, "id", 101L);
        Question question = question(31L, "ACTIVE");
        when(answerRecordRepository.findByStudentIdAndClientRequestId(5L, "request-1")).thenReturn(Optional.of(existing));
        when(questionService.requireQuestion(31L)).thenReturn(question);
        when(questionService.answerOptionKeys(question)).thenReturn(List.of("A"));
        AnswerSubmissionService service = service();

        AnswerApi.AnswerResultResponse result = service.submit(31L, request("A", "request-1"), student());

        assertThat(result.idempotentReplay()).isTrue();
        verify(answerRecordRepository, never()).save(any());
        verify(outboxService, never()).enqueueMasteryUpdate(any(Long.class), any(Long.class), any(Long.class), any(Long.class), any(Long.class), any(Boolean.class));
    }

    @Test
    void clientRequestIdCannotBeReusedForAnotherQuestion() {
        AnswerRecord existing = new AnswerRecord(5L, 3L, 31L, 12L, "[\"A\"]", true, 1, 10L, "request-1", java.time.Instant.now());
        ReflectionTestUtils.setField(existing, "id", 101L);
        when(answerRecordRepository.findByStudentIdAndClientRequestId(5L, "request-1")).thenReturn(Optional.of(existing));
        AnswerSubmissionService service = service();

        assertThatThrownBy(() -> service.submit(99L, request("A", "request-1"), student()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("another question");

        verify(questionService, never()).requireQuestion(anyLong());
        verify(answerRecordRepository, never()).save(any());
        verify(outboxService, never()).enqueueMasteryUpdate(any(Long.class), any(Long.class), any(Long.class), any(Long.class), any(Long.class), any(Boolean.class));
    }

    @Test
    void disabledQuestionIsRejectedBeforeAnyWrite() {
        when(answerRecordRepository.findByStudentIdAndClientRequestId(5L, "request-1")).thenReturn(Optional.empty());
        when(questionService.requireQuestion(31L)).thenReturn(question(31L, "DISABLED"));
        AnswerSubmissionService service = service();

        assertThatThrownBy(() -> service.submit(31L, request("A", "request-1"), student()))
                .isInstanceOf(ConflictException.class);
        verify(answerRecordRepository, never()).save(any());
        verify(outboxService, never()).enqueueMasteryUpdate(any(Long.class), any(Long.class), any(Long.class), any(Long.class), any(Long.class), any(Boolean.class));
    }

    @Test
    void missingQuestionIsRejectedBeforeAnyWrite() {
        when(answerRecordRepository.findByStudentIdAndClientRequestId(5L, "request-1")).thenReturn(Optional.empty());
        when(questionService.requireQuestion(31L)).thenThrow(new NotFoundException("question does not exist"));
        AnswerSubmissionService service = service();

        assertThatThrownBy(() -> service.submit(31L, request("A", "request-1"), student()))
                .isInstanceOf(NotFoundException.class);
        verify(answerRecordRepository, never()).save(any());
        verify(outboxService, never()).enqueueMasteryUpdate(any(Long.class), any(Long.class), any(Long.class), any(Long.class), any(Long.class), any(Boolean.class));
    }

    @Test
    void unenrolledStudentCannotCreateAnswerRecord() {
        Question question = question(31L, "ACTIVE");
        ExerciseUnit exercise = exercise(12L, "ACTIVE");
        when(answerRecordRepository.findByStudentIdAndClientRequestId(5L, "request-1")).thenReturn(Optional.empty());
        when(questionService.requireQuestion(31L)).thenReturn(question);
        when(exerciseUnitService.requireExercise(12L)).thenReturn(exercise);
        doThrow(new ForbiddenOperationException("not enrolled")).when(courseAccessService).requireCourseAccess(3L, student());
        AnswerSubmissionService service = service();

        assertThatThrownBy(() -> service.submit(31L, request("A", "request-1"), student()))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(answerRecordRepository, never()).save(any());
    }

    private AnswerSubmissionService service() {
        return new AnswerSubmissionService(
                answerRecordRepository, questionService, optionRepository, exerciseUnitService, courseAccessService, outboxService,
                new ObjectMapper()
        );
    }

    private CurrentUser student() {
        return new CurrentUser(5L, "learner", Set.of("STUDENT"));
    }

    private AnswerApi.AnswerSubmissionRequest request(String selectedKey, String requestId) {
        return new AnswerApi.AnswerSubmissionRequest(List.of(selectedKey), 10L, requestId);
    }

    private Question question(long id, String status) {
        Question question = new Question(12L, "SINGLE_CHOICE", "Stem", "[\"A\"]", "Explanation", null, status, 1L);
        ReflectionTestUtils.setField(question, "id", id);
        return question;
    }

    private ExerciseUnit exercise(long id, String status) {
        ExerciseUnit exercise = new ExerciseUnit(3L, "EX-1", "Exercise", "PLATFORM", null, "RESOLVED", "MAPPED", null, status);
        ReflectionTestUtils.setField(exercise, "id", id);
        return exercise;
    }

    private QuestionOption option(long questionId, String key) {
        return new QuestionOption(questionId, key, key + " option", "A".equals(key) ? 1 : 2);
    }
}
