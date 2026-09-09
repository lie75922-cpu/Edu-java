package com.smartlearning.assessment.application;

import com.smartlearning.assessment.api.QuestionApi;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.domain.Question;
import com.smartlearning.assessment.domain.QuestionOption;
import com.smartlearning.assessment.infrastructure.persistence.QuestionOptionRepository;
import com.smartlearning.assessment.infrastructure.persistence.QuestionRepository;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.BadRequestException;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.application.CourseAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class QuestionService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final ExerciseUnitService exerciseUnitService;
    private final CourseAccessService courseAccessService;
    private final ObjectMapper objectMapper;

    public QuestionService(
            QuestionRepository questionRepository,
            QuestionOptionRepository optionRepository,
            ExerciseUnitService exerciseUnitService,
            CourseAccessService courseAccessService,
            ObjectMapper objectMapper
    ) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.exerciseUnitService = exerciseUnitService;
        this.courseAccessService = courseAccessService;
        this.objectMapper = objectMapper;
    }

    public QuestionApi.QuestionResponse nextQuestion(long exerciseUnitId, CurrentUser user) {
        ExerciseUnit exercise = exerciseUnitService.requireExercise(exerciseUnitId);
        courseAccessService.requireCourseAccess(exercise.getCourseId(), user);
        if (!"ACTIVE".equals(exercise.getStatus())) {
            throw new ConflictException("exercise unit is disabled");
        }
        Question question = questionRepository.findFirstByExerciseUnitIdAndStatusOrderByIdAsc(exerciseUnitId, "ACTIVE")
                .orElseThrow(() -> new NotFoundException("no active question exists for this exercise unit"));
        return toStudentResponse(question);
    }

    public Question requireQuestion(long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("question does not exist"));
    }

    public List<QuestionApi.AdminQuestionResponse> listForAdmin(long exerciseUnitId) {
        exerciseUnitService.requireExercise(exerciseUnitId);
        return questionRepository.findByExerciseUnitIdOrderByIdAsc(exerciseUnitId).stream().map(this::toAdminResponse).toList();
    }

    public List<QuestionApi.AdminQuestionResponse> listForTeaching(long exerciseUnitId, CurrentUser user) {
        courseAccessService.requireTeachingAccess(exerciseUnitService.requireExercise(exerciseUnitId).getCourseId(), user);
        return listForAdmin(exerciseUnitId);
    }

    public QuestionApi.AdminQuestionResponse getForAdmin(long questionId) {
        return toAdminResponse(requireQuestion(questionId));
    }

    public QuestionApi.AdminQuestionResponse getForTeaching(long questionId, CurrentUser user) {
        Question question = requireQuestion(questionId);
        courseAccessService.requireTeachingAccess(exerciseUnitService.requireExercise(question.getExerciseUnitId()).getCourseId(), user);
        return toAdminResponse(question);
    }

    @Transactional
    public QuestionApi.AdminQuestionResponse createForTeaching(QuestionApi.QuestionRequest request, CurrentUser user) {
        courseAccessService.requireTeachingAccess(exerciseUnitService.requireExercise(request.exerciseUnitId()).getCourseId(), user);
        return create(request, user.id());
    }

    /**
     * Creates one bounded batch atomically. Every item goes through the same teaching-access and semantic validation
     * as a single create. Any runtime validation/authorization failure rolls the entire transaction back.
     */
    @Transactional
    public QuestionApi.QuestionBatchResponse createBatchForTeaching(
            QuestionApi.QuestionBatchRequest request,
            CurrentUser user
    ) {
        List<QuestionApi.AdminQuestionResponse> created = new ArrayList<>();
        for (QuestionApi.QuestionRequest question : request.questions()) {
            courseAccessService.requireTeachingAccess(
                    exerciseUnitService.requireExercise(question.exerciseUnitId()).getCourseId(), user
            );
            created.add(create(question, user.id()));
        }
        return new QuestionApi.QuestionBatchResponse(created.size(), created);
    }

    @Transactional
    public QuestionApi.AdminQuestionResponse updateForTeaching(
            long questionId,
            QuestionApi.QuestionRequest request,
            CurrentUser user
    ) {
        Question question = requireQuestion(questionId);
        courseAccessService.requireTeachingAccess(exerciseUnitService.requireExercise(question.getExerciseUnitId()).getCourseId(), user);
        courseAccessService.requireTeachingAccess(exerciseUnitService.requireExercise(request.exerciseUnitId()).getCourseId(), user);
        return update(questionId, request);
    }

    @Transactional
    public void disableForTeaching(long questionId, CurrentUser user) {
        Question question = requireQuestion(questionId);
        courseAccessService.requireTeachingAccess(exerciseUnitService.requireExercise(question.getExerciseUnitId()).getCourseId(), user);
        disable(questionId);
    }

    @Transactional
    public QuestionApi.AdminQuestionResponse create(QuestionApi.QuestionRequest request, long createdBy) {
        ExerciseUnit exercise = exerciseUnitService.requireExercise(request.exerciseUnitId());
        ValidationResult validation = validateQuestion(request);
        Question question = questionRepository.save(new Question(
                exercise.getId(), request.questionType(), request.stem(), validation.answerJson(), request.explanation(),
                request.difficulty(), request.status(), createdBy
        ));
        replaceOptions(question.getId(), validation.options());
        return toAdminResponse(question);
    }

    @Transactional
    public QuestionApi.AdminQuestionResponse update(long questionId, QuestionApi.QuestionRequest request) {
        Question question = requireQuestion(questionId);
        if (!question.getExerciseUnitId().equals(request.exerciseUnitId())) {
            throw new ConflictException("a question with answer history cannot be moved to another exercise unit");
        }
        ValidationResult validation = validateQuestion(request);
        question.update(
                request.questionType(), request.stem(), validation.answerJson(), request.explanation(), request.difficulty(), request.status()
        );
        replaceOptions(questionId, validation.options());
        return toAdminResponse(question);
    }

    @Transactional
    public void disable(long questionId) {
        requireQuestion(questionId).disable();
    }

    public List<String> answerOptionKeys(Question question) {
        try {
            String[] values = objectMapper.readValue(question.getAnswerJson(), String[].class);
            return normalizeKeys(List.of(values));
        } catch (Exception ex) {
            throw new IllegalStateException("question answer data is invalid", ex);
        }
    }

    public List<QuestionApi.OptionResponse> optionsFor(long questionId) {
        return optionRepository.findByQuestionIdOrderBySortOrderAsc(questionId).stream().map(this::toOptionResponse).toList();
    }

    private ValidationResult validateQuestion(QuestionApi.QuestionRequest request) {
        if (!SUPPORTED_TYPES.contains(request.questionType())) {
            throw new BadRequestException("unsupported question type");
        }
        if (request.options().size() < 2) {
            throw new BadRequestException("a question must contain at least two options");
        }
        Set<String> optionKeys = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (QuestionApi.OptionRequest option : request.options()) {
            if (!optionKeys.add(option.optionKey())) {
                throw new BadRequestException("option keys must be unique");
            }
            if (!sortOrders.add(option.sortOrder())) {
                throw new BadRequestException("option sort orders must be unique");
            }
        }
        List<String> answerKeys = normalizeKeys(request.answerOptionKeys());
        if (!optionKeys.containsAll(answerKeys)) {
            throw new BadRequestException("answer option keys must belong to the supplied options");
        }
        if ("SINGLE_CHOICE".equals(request.questionType()) && answerKeys.size() != 1) {
            throw new BadRequestException("single choice questions require exactly one answer option");
        }
        if ("TRUE_FALSE".equals(request.questionType())
                && (!optionKeys.equals(Set.of("TRUE", "FALSE")) || answerKeys.size() != 1)) {
            throw new BadRequestException("true/false questions require TRUE and FALSE options with one answer");
        }
        try {
            String answerJson = objectMapper.writeValueAsString(answerKeys);
            List<QuestionApi.OptionRequest> sortedOptions = new ArrayList<>(request.options());
            sortedOptions.sort(Comparator.comparingInt(QuestionApi.OptionRequest::sortOrder));
            return new ValidationResult(answerJson, List.copyOf(sortedOptions));
        } catch (Exception ex) {
            throw new IllegalStateException("unable to serialize question answer", ex);
        }
    }

    private List<String> normalizeKeys(List<String> keys) {
        List<String> normalized = keys.stream().map(String::trim).sorted().toList();
        if (normalized.isEmpty() || new HashSet<>(normalized).size() != normalized.size()) {
            throw new BadRequestException("answer options must be non-empty and non-duplicated");
        }
        return normalized;
    }

    private void replaceOptions(long questionId, List<QuestionApi.OptionRequest> options) {
        optionRepository.deleteAll(optionRepository.findByQuestionIdOrderBySortOrderAsc(questionId));
        optionRepository.flush();
        optionRepository.saveAll(options.stream()
                .map(option -> new QuestionOption(questionId, option.optionKey(), option.optionText(), option.sortOrder()))
                .toList());
    }

    private QuestionApi.QuestionResponse toStudentResponse(Question question) {
        return new QuestionApi.QuestionResponse(
                question.getId(), question.getExerciseUnitId(), question.getQuestionType(), question.getStem(), question.getDifficulty(),
                optionsFor(question.getId())
        );
    }

    private QuestionApi.AdminQuestionResponse toAdminResponse(Question question) {
        return new QuestionApi.AdminQuestionResponse(
                question.getId(), question.getExerciseUnitId(), question.getQuestionType(), question.getStem(), answerOptionKeys(question),
                question.getExplanation(), question.getDifficulty(), question.getStatus(), optionsFor(question.getId())
        );
    }

    private QuestionApi.OptionResponse toOptionResponse(QuestionOption option) {
        return new QuestionApi.OptionResponse(option.getId(), option.getOptionKey(), option.getOptionText(), option.getSortOrder());
    }

    private record ValidationResult(String answerJson, List<QuestionApi.OptionRequest> options) {
    }
}
