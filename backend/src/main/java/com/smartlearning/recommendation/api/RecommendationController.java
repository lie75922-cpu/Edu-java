package com.smartlearning.recommendation.api;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.recommendation.application.LearningPathService;
import com.smartlearning.recommendation.application.RecommendationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('STUDENT')")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final LearningPathService learningPathService;

    public RecommendationController(RecommendationService recommendationService, LearningPathService learningPathService) {
        this.recommendationService = recommendationService;
        this.learningPathService = learningPathService;
    }

    @PostMapping("/courses/{courseId}/recommendations")
    public ApiResponse<RecommendationApi.RecommendationSnapshotResponse> generate(
            @PathVariable long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(recommendationService.generate(courseId, CurrentUser.from(jwt)));
    }

    @GetMapping("/courses/{courseId}/recommendations/latest")
    public ApiResponse<RecommendationApi.RecommendationSnapshotResponse> latest(
            @PathVariable long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(recommendationService.latest(courseId, CurrentUser.from(jwt)));
    }

    @GetMapping("/knowledge-points/{targetKnowledgePointId}/learning-path")
    public ApiResponse<RecommendationApi.LearningPathResponse> learningPath(
            @PathVariable long targetKnowledgePointId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(learningPathService.learningPath(targetKnowledgePointId, CurrentUser.from(jwt)));
    }
}
