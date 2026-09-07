package com.smartlearning.assessment.api;

import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.common.api.ApiResponse;
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
@RequestMapping("/api/v1/admin/exercise-units")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TEACHER')")
public class AdminExerciseUnitController {

    private final ExerciseUnitService exerciseUnitService;

    public AdminExerciseUnitController(ExerciseUnitService exerciseUnitService) {
        this.exerciseUnitService = exerciseUnitService;
    }

    @GetMapping
    public ApiResponse<List<ExerciseApi.ExerciseUnitResponse>> list(@RequestParam long courseId) {
        return ApiResponse.ok(exerciseUnitService.listForAdmin(courseId));
    }

    @PostMapping
    public ApiResponse<ExerciseApi.ExerciseUnitResponse> create(
            @Valid @RequestBody ExerciseApi.ExerciseUnitRequest request
    ) {
        return ApiResponse.ok(exerciseUnitService.create(request));
    }

    @PutMapping("/{exerciseUnitId}")
    public ApiResponse<ExerciseApi.ExerciseUnitResponse> update(
            @PathVariable long exerciseUnitId,
            @Valid @RequestBody ExerciseApi.ExerciseUnitRequest request
    ) {
        return ApiResponse.ok(exerciseUnitService.update(exerciseUnitId, request));
    }

    @DeleteMapping("/{exerciseUnitId}")
    public ApiResponse<Void> disable(@PathVariable long exerciseUnitId) {
        exerciseUnitService.disable(exerciseUnitId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{exerciseUnitId}/knowledge-points")
    public ApiResponse<ExerciseApi.ExerciseUnitResponse> upsertMapping(
            @PathVariable long exerciseUnitId,
            @Valid @RequestBody ExerciseApi.MappingRequest request
    ) {
        return ApiResponse.ok(exerciseUnitService.upsertMapping(exerciseUnitId, request));
    }

    @DeleteMapping("/{exerciseUnitId}/knowledge-points/{knowledgePointId}")
    public ApiResponse<Void> removeMapping(@PathVariable long exerciseUnitId, @PathVariable long knowledgePointId) {
        exerciseUnitService.removeMapping(exerciseUnitId, knowledgePointId);
        return ApiResponse.ok(null);
    }
}
