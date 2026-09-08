package com.smartlearning.assessment.api;

import com.smartlearning.assessment.application.ExerciseUnitService;
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
@RequestMapping("/api/v1/admin/exercise-units")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TEACH_ADMIN', 'TEACHER')")
public class AdminExerciseUnitController {

    private final ExerciseUnitService exerciseUnitService;

    public AdminExerciseUnitController(ExerciseUnitService exerciseUnitService) {
        this.exerciseUnitService = exerciseUnitService;
    }

    @GetMapping
    public ApiResponse<List<ExerciseApi.ExerciseUnitResponse>> list(
            @RequestParam long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(exerciseUnitService.listForTeaching(courseId, CurrentUser.from(jwt)));
    }

    @PostMapping
    public ApiResponse<ExerciseApi.ExerciseUnitResponse> create(
            @Valid @RequestBody ExerciseApi.ExerciseUnitRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(exerciseUnitService.createForTeaching(request, CurrentUser.from(jwt)));
    }

    @PutMapping("/{exerciseUnitId}")
    public ApiResponse<ExerciseApi.ExerciseUnitResponse> update(
            @PathVariable long exerciseUnitId,
            @Valid @RequestBody ExerciseApi.ExerciseUnitRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(exerciseUnitService.updateForTeaching(exerciseUnitId, request, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/{exerciseUnitId}")
    public ApiResponse<Void> disable(@PathVariable long exerciseUnitId, @AuthenticationPrincipal Jwt jwt) {
        exerciseUnitService.disableForTeaching(exerciseUnitId, CurrentUser.from(jwt));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{exerciseUnitId}/knowledge-points")
    public ApiResponse<ExerciseApi.ExerciseUnitResponse> upsertMapping(
            @PathVariable long exerciseUnitId,
            @Valid @RequestBody ExerciseApi.MappingRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(exerciseUnitService.upsertMappingForTeaching(exerciseUnitId, request, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/{exerciseUnitId}/knowledge-points/{knowledgePointId}")
    public ApiResponse<Void> removeMapping(
            @PathVariable long exerciseUnitId,
            @PathVariable long knowledgePointId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        exerciseUnitService.removeMappingForTeaching(exerciseUnitId, knowledgePointId, CurrentUser.from(jwt));
        return ApiResponse.ok(null);
    }
}
