package com.smartlearning.knowledge.api;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.knowledge.application.KnowledgeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses/{courseId}")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/knowledge-points")
    public ApiResponse<List<KnowledgeApi.KnowledgePointResponse>> listPoints(
            @PathVariable long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(knowledgeService.listActivePoints(courseId, CurrentUser.from(jwt)));
    }

    @GetMapping("/knowledge-areas")
    public ApiResponse<List<KnowledgeApi.KnowledgeAreaResponse>> listAreas(
            @PathVariable long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(knowledgeService.listActiveAreas(courseId, CurrentUser.from(jwt)));
    }
}
