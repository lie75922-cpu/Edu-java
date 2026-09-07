package com.smartlearning.course.api;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.course.application.CourseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ApiResponse<List<CourseApi.CourseResponse>> list() {
        return ApiResponse.ok(courseService.listActiveCourses());
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseApi.CourseResponse> get(
            @PathVariable long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(courseService.getCourse(courseId, CurrentUser.from(jwt)));
    }

    @PostMapping("/{courseId}/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<CourseApi.EnrollmentResponse> enroll(
            @PathVariable long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(courseService.enroll(courseId, CurrentUser.from(jwt)));
    }
}
