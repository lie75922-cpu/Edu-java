package com.smartlearning.assessment.api;

import com.smartlearning.assessment.application.QuestionService;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/questions")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TEACH_ADMIN', 'TEACHER')")
public class AdminQuestionController {

    private final QuestionService questionService;

    public AdminQuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public ApiResponse<List<QuestionApi.AdminQuestionResponse>> list(
            @RequestParam long exerciseUnitId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(questionService.listForTeaching(exerciseUnitId, CurrentUser.from(jwt)));
    }

    @GetMapping("/{questionId}")
    public ApiResponse<QuestionApi.AdminQuestionResponse> get(
            @PathVariable long questionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(questionService.getForTeaching(questionId, CurrentUser.from(jwt)));
    }

    @PostMapping
    public ApiResponse<QuestionApi.AdminQuestionResponse> create(
            @Valid @RequestBody QuestionApi.QuestionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(questionService.createForTeaching(request, CurrentUser.from(jwt)));
    }

    @PutMapping("/{questionId}")
    public ApiResponse<QuestionApi.AdminQuestionResponse> update(
            @PathVariable long questionId,
            @Valid @RequestBody QuestionApi.QuestionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(questionService.updateForTeaching(questionId, request, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/{questionId}")
    public ApiResponse<Void> disable(@PathVariable long questionId, @AuthenticationPrincipal Jwt jwt) {
        questionService.disableForTeaching(questionId, CurrentUser.from(jwt));
        return ApiResponse.ok(null);
    }
}
