package com.smartlearning.course.api;

import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.course.application.CourseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/courses")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TEACHER')")
public class AdminCourseController {

    private final CourseService courseService;

    public AdminCourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public ApiResponse<CourseApi.CourseResponse> create(@Valid @RequestBody CourseApi.CourseRequest request) {
        return ApiResponse.ok(courseService.create(request));
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseApi.CourseResponse> update(
            @PathVariable long courseId,
            @Valid @RequestBody CourseApi.CourseRequest request
    ) {
        return ApiResponse.ok(courseService.update(courseId, request));
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> disable(@PathVariable long courseId) {
        courseService.disable(courseId);
        return ApiResponse.ok(null);
    }
}
