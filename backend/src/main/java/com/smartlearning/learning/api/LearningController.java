package com.smartlearning.learning.api;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.learning.application.AnswerSubmissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/learning")
@PreAuthorize("hasRole('STUDENT')")
public class LearningController {

    private final AnswerSubmissionService answerSubmissionService;

    public LearningController(AnswerSubmissionService answerSubmissionService) {
        this.answerSubmissionService = answerSubmissionService;
    }

    @GetMapping("/answer-history")
    public ApiResponse<List<AnswerApi.AnswerHistoryResponse>> answerHistory(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(answerSubmissionService.history(CurrentUser.from(jwt)));
    }
}
