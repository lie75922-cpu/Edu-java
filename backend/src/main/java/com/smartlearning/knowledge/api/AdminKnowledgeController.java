package com.smartlearning.knowledge.api;

import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.knowledge.application.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TEACHER')")
public class AdminKnowledgeController {

    private final KnowledgeService knowledgeService;

    public AdminKnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/knowledge-areas")
    public ApiResponse<List<KnowledgeApi.KnowledgeAreaResponse>> listAreas(@RequestParam long courseId) {
        return ApiResponse.ok(knowledgeService.listAreasForAdmin(courseId));
    }

    @PostMapping("/knowledge-areas")
    public ApiResponse<KnowledgeApi.KnowledgeAreaResponse> createArea(
            @Valid @RequestBody KnowledgeApi.KnowledgeAreaRequest request
    ) {
        return ApiResponse.ok(knowledgeService.createArea(request));
    }

    @PutMapping("/knowledge-areas/{areaId}")
    public ApiResponse<KnowledgeApi.KnowledgeAreaResponse> updateArea(
            @PathVariable long areaId,
            @Valid @RequestBody KnowledgeApi.KnowledgeAreaRequest request
    ) {
        return ApiResponse.ok(knowledgeService.updateArea(areaId, request));
    }

    @DeleteMapping("/knowledge-areas/{areaId}")
    public ApiResponse<Void> disableArea(@PathVariable long areaId) {
        knowledgeService.disableArea(areaId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/knowledge-points")
    public ApiResponse<List<KnowledgeApi.KnowledgePointResponse>> listPoints(@RequestParam long courseId) {
        return ApiResponse.ok(knowledgeService.listPointsForAdmin(courseId));
    }

    @PostMapping("/knowledge-points")
    public ApiResponse<KnowledgeApi.KnowledgePointResponse> createPoint(
            @Valid @RequestBody KnowledgeApi.KnowledgePointRequest request
    ) {
        return ApiResponse.ok(knowledgeService.createPoint(request));
    }

    @PutMapping("/knowledge-points/{pointId}")
    public ApiResponse<KnowledgeApi.KnowledgePointResponse> updatePoint(
            @PathVariable long pointId,
            @Valid @RequestBody KnowledgeApi.KnowledgePointRequest request
    ) {
        return ApiResponse.ok(knowledgeService.updatePoint(pointId, request));
    }

    @DeleteMapping("/knowledge-points/{pointId}")
    public ApiResponse<Void> disablePoint(@PathVariable long pointId) {
        knowledgeService.disablePoint(pointId);
        return ApiResponse.ok(null);
    }
}
