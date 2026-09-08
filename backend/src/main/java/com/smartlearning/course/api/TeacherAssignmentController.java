package com.smartlearning.course.api;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.course.application.TeacherAssignmentService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/courses/{courseId}/teachers")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TEACH_ADMIN')")
public class TeacherAssignmentController {

    private final TeacherAssignmentService teacherAssignmentService;

    public TeacherAssignmentController(TeacherAssignmentService teacherAssignmentService) {
        this.teacherAssignmentService = teacherAssignmentService;
    }

    @GetMapping
    public ApiResponse<List<TeacherAssignmentApi.AssignmentResponse>> list(
            @PathVariable long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(teacherAssignmentService.list(courseId, CurrentUser.from(jwt)));
    }

    @PostMapping
    public ApiResponse<TeacherAssignmentApi.AssignmentResponse> assign(
            @PathVariable long courseId,
            @Valid @RequestBody TeacherAssignmentApi.CreateAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(teacherAssignmentService.assign(courseId, request, CurrentUser.from(jwt)));
    }

    @PutMapping("/{teacherId}")
    public ApiResponse<TeacherAssignmentApi.AssignmentResponse> update(
            @PathVariable long courseId,
            @PathVariable long teacherId,
            @Valid @RequestBody TeacherAssignmentApi.UpdateAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(teacherAssignmentService.update(courseId, teacherId, request, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/{teacherId}")
    public ApiResponse<Void> deactivate(
            @PathVariable long courseId,
            @PathVariable long teacherId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        teacherAssignmentService.deactivate(courseId, teacherId, CurrentUser.from(jwt));
        return ApiResponse.ok(null);
    }
}
