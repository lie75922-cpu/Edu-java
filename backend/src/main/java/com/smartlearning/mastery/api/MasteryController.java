package com.smartlearning.mastery.api;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.mastery.application.MasteryQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('STUDENT')")
public class MasteryController {

    private final MasteryQueryService masteryQueryService;

    public MasteryController(MasteryQueryService masteryQueryService) {
        this.masteryQueryService = masteryQueryService;
    }

    @GetMapping("/courses/{courseId}/mastery")
    public ApiResponse<MasteryApi.CourseMasteryResponse> courseMastery(
            @PathVariable long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(masteryQueryService.courseMastery(courseId, CurrentUser.from(jwt)));
    }

    @GetMapping("/knowledge-points/{knowledgePointId}/mastery")
    public ApiResponse<MasteryApi.MasteryResponse> pointMastery(
            @PathVariable long knowledgePointId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(masteryQueryService.pointMastery(knowledgePointId, CurrentUser.from(jwt)));
    }

    @GetMapping("/courses/{courseId}/mastery/history")
    public ApiResponse<List<MasteryApi.MasteryHistoryResponse>> history(
            @PathVariable long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(masteryQueryService.history(courseId, CurrentUser.from(jwt)));
    }
}
