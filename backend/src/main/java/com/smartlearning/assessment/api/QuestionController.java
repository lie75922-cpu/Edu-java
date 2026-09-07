package com.smartlearning.assessment.api;

import com.smartlearning.assessment.application.QuestionService;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.learning.api.AnswerApi;
import com.smartlearning.learning.application.AnswerSubmissionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class QuestionController {

    private final QuestionService questionService;
    private final AnswerSubmissionService answerSubmissionService;

    public QuestionController(QuestionService questionService, AnswerSubmissionService answerSubmissionService) {
        this.questionService = questionService;
        this.answerSubmissionService = answerSubmissionService;
    }

    @GetMapping("/exercise-units/{exerciseUnitId}/questions/next")
    public ApiResponse<QuestionApi.QuestionResponse> nextQuestion(
            @PathVariable long exerciseUnitId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(questionService.nextQuestion(exerciseUnitId, CurrentUser.from(jwt)));
    }

    @PostMapping("/questions/{questionId}/answers")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<AnswerApi.AnswerResultResponse> submitAnswer(
            @PathVariable long questionId,
            @Valid @RequestBody AnswerApi.AnswerSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(answerSubmissionService.submit(questionId, request, CurrentUser.from(jwt)));
    }
}
