package com.smartlearning.course.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CourseApi {

    private CourseApi() {
    }

    public record CourseRequest(
            @NotBlank @Size(max = 64) String courseCode,
            @NotBlank @Size(max = 128) String courseName,
            String description,
            @NotBlank @Size(max = 20) String status
    ) {
    }

    public record CourseResponse(Long id, String courseCode, String courseName, String description, String status) {
    }

    public record EnrollmentResponse(Long id, Long courseId, String status, Instant enrolledAt) {
    }
}
