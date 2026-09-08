package com.smartlearning.knowledge.api;

import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.knowledge.application.KnowledgeService;
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
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TEACH_ADMIN', 'TEACHER')")
public class AdminKnowledgeController {

    private final KnowledgeService knowledgeService;

    public AdminKnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/knowledge-areas")
    public ApiResponse<List<KnowledgeApi.KnowledgeAreaResponse>> listAreas(
            @RequestParam long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(knowledgeService.listAreasForTeaching(courseId, CurrentUser.from(jwt)));
    }

    @PostMapping("/knowledge-areas")
    public ApiResponse<KnowledgeApi.KnowledgeAreaResponse> createArea(
            @Valid @RequestBody KnowledgeApi.KnowledgeAreaRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(knowledgeService.createAreaForTeaching(request, CurrentUser.from(jwt)));
    }

    @PutMapping("/knowledge-areas/{areaId}")
    public ApiResponse<KnowledgeApi.KnowledgeAreaResponse> updateArea(
            @PathVariable long areaId,
            @Valid @RequestBody KnowledgeApi.KnowledgeAreaRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(knowledgeService.updateAreaForTeaching(areaId, request, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/knowledge-areas/{areaId}")
    public ApiResponse<Void> disableArea(@PathVariable long areaId, @AuthenticationPrincipal Jwt jwt) {
        knowledgeService.disableAreaForTeaching(areaId, CurrentUser.from(jwt));
        return ApiResponse.ok(null);
    }

    @GetMapping("/knowledge-points")
    public ApiResponse<List<KnowledgeApi.KnowledgePointResponse>> listPoints(
            @RequestParam long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(knowledgeService.listPointsForTeaching(courseId, CurrentUser.from(jwt)));
    }

    @PostMapping("/knowledge-points")
    public ApiResponse<KnowledgeApi.KnowledgePointResponse> createPoint(
            @Valid @RequestBody KnowledgeApi.KnowledgePointRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(knowledgeService.createPointForTeaching(request, CurrentUser.from(jwt)));
    }

    @PutMapping("/knowledge-points/{pointId}")
    public ApiResponse<KnowledgeApi.KnowledgePointResponse> updatePoint(
            @PathVariable long pointId,
            @Valid @RequestBody KnowledgeApi.KnowledgePointRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(knowledgeService.updatePointForTeaching(pointId, request, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/knowledge-points/{pointId}")
    public ApiResponse<Void> disablePoint(@PathVariable long pointId, @AuthenticationPrincipal Jwt jwt) {
        knowledgeService.disablePointForTeaching(pointId, CurrentUser.from(jwt));
        return ApiResponse.ok(null);
    }
}
