package com.smartlearning.assessment.api;

import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exercise-units")
public class ExerciseUnitController {

    private final ExerciseUnitService exerciseUnitService;

    public ExerciseUnitController(ExerciseUnitService exerciseUnitService) {
        this.exerciseUnitService = exerciseUnitService;
    }

    @GetMapping
    public ApiResponse<List<ExerciseApi.ExerciseUnitResponse>> list(
            @RequestParam long courseId,
            @RequestParam(required = false) Long knowledgePointId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(exerciseUnitService.listActive(courseId, knowledgePointId, CurrentUser.from(jwt)));
    }

    @GetMapping("/{exerciseUnitId}")
    public ApiResponse<ExerciseApi.ExerciseUnitResponse> get(
            @PathVariable long exerciseUnitId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(exerciseUnitService.get(exerciseUnitId, CurrentUser.from(jwt)));
    }
}
