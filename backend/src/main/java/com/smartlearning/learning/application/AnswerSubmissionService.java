package com.smartlearning.learning.application;

import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.assessment.application.QuestionService;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.domain.Question;
import com.smartlearning.assessment.domain.QuestionOption;
import com.smartlearning.assessment.infrastructure.persistence.QuestionOptionRepository;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.BadRequestException;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.learning.api.AnswerApi;
import com.smartlearning.learning.domain.AnswerRecord;
import com.smartlearning.learning.infrastructure.persistence.AnswerRecordRepository;
import com.smartlearning.outbox.application.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AnswerSubmissionService {

    private final AnswerRecordRepository answerRecordRepository;
    private final QuestionService questionService;
    private final QuestionOptionRepository optionRepository;
    private final ExerciseUnitService exerciseUnitService;
    private final CourseAccessService courseAccessService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public AnswerSubmissionService(
            AnswerRecordRepository answerRecordRepository,
            QuestionService questionService,
            QuestionOptionRepository optionRepository,
            ExerciseUnitService exerciseUnitService,
            CourseAccessService courseAccessService,
            OutboxService outboxService,
            ObjectMapper objectMapper
    ) {
        this.answerRecordRepository = answerRecordRepository;
        this.questionService = questionService;
        this.optionRepository = optionRepository;
        this.exerciseUnitService = exerciseUnitService;
        this.courseAccessService = courseAccessService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnswerApi.AnswerResultResponse submit(
            long questionId,
            AnswerApi.AnswerSubmissionRequest request,
            CurrentUser user
    ) {
        AnswerRecord existing = answerRecordRepository.findByStudentIdAndClientRequestId(user.id(), request.clientRequestId())
                .orElse(null);
        if (existing != null) {
            return resultForExisting(existing);
        }

        Question question = questionService.requireQuestion(questionId);
        if (!"ACTIVE".equals(question.getStatus())) {
            throw new ConflictException("question is disabled");
        }
        ExerciseUnit exercise = exerciseUnitService.requireExercise(question.getExerciseUnitId());
        if (!"ACTIVE".equals(exercise.getStatus())) {
            throw new ConflictException("exercise unit is disabled");
        }
        courseAccessService.requireCourseAccess(exercise.getCourseId(), user);

        List<String> submittedKeys = normalizeSubmission(request.selectedOptionKeys(), question);
        List<String> correctKeys = questionService.answerOptionKeys(question);
        boolean correct = new HashSet<>(submittedKeys).equals(new HashSet<>(correctKeys));
        int attemptNo = Math.toIntExact(answerRecordRepository.countByStudentIdAndQuestionId(user.id(), questionId) + 1);
        AnswerRecord record = answerRecordRepository.save(new AnswerRecord(
                user.id(), exercise.getCourseId(), questionId, exercise.getId(), serialize(submittedKeys), correct, attemptNo,
                request.durationMs(), request.clientRequestId(), Instant.now()
        ));
        outboxService.enqueueMasteryUpdate(
                record.getId(), user.id(), exercise.getCourseId(), questionId, exercise.getId(), correct
        );
        return new AnswerApi.AnswerResultResponse(
                record.getId(), questionId, correct, attemptNo, correctKeys, question.getExplanation(), false
        );
    }

    public List<AnswerApi.AnswerHistoryResponse> history(CurrentUser user) {
        return answerRecordRepository.findByStudentIdOrderByAnsweredAtDesc(user.id()).stream()
                .map(record -> new AnswerApi.AnswerHistoryResponse(
                        record.getId(), record.getCourseId(), record.getQuestionId(), record.getExerciseUnitId(),
                        record.isCorrect(), record.getAttemptNo(), record.getDurationMs(), record.getAnsweredAt()
                ))
                .toList();
    }

    private AnswerApi.AnswerResultResponse resultForExisting(AnswerRecord record) {
        Question question = questionService.requireQuestion(record.getQuestionId());
        return new AnswerApi.AnswerResultResponse(
                record.getId(), record.getQuestionId(), record.isCorrect(), record.getAttemptNo(),
                questionService.answerOptionKeys(question), question.getExplanation(), true
        );
    }

    private List<String> normalizeSubmission(List<String> submittedKeys, Question question) {
        List<String> normalized = submittedKeys.stream().map(String::trim).sorted().toList();
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new BadRequestException("selected options must not contain duplicates");
        }
        Set<String> allowedKeys = optionRepository.findByQuestionIdOrderBySortOrderAsc(question.getId()).stream()
                .map(QuestionOption::getOptionKey)
                .collect(java.util.stream.Collectors.toSet());
        if (!allowedKeys.containsAll(normalized)) {
            throw new BadRequestException("selected options do not belong to this question");
        }
        if (("SINGLE_CHOICE".equals(question.getQuestionType()) || "TRUE_FALSE".equals(question.getQuestionType()))
                && normalized.size() != 1) {
            throw new BadRequestException("this question requires exactly one selected option");
        }
        return normalized;
    }

    private String serialize(List<String> submittedKeys) {
        try {
            return objectMapper.writeValueAsString(submittedKeys);
        } catch (Exception ex) {
            throw new IllegalStateException("unable to serialize submitted answer", ex);
        }
    }
}
