package com.smartlearning.course.api;

import com.smartlearning.course.domain.TeacherAssignmentRole;
import com.smartlearning.course.domain.TeacherAssignmentStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class TeacherAssignmentApi {

    private TeacherAssignmentApi() {
    }

    public record CreateAssignmentRequest(
            @NotNull Long teacherId,
            @NotNull TeacherAssignmentRole assignmentRole
    ) {
    }

    public record UpdateAssignmentRequest(
            @NotNull TeacherAssignmentRole assignmentRole,
            @NotNull TeacherAssignmentStatus status
    ) {
    }

    public record AssignmentResponse(
            Long id,
            Long teacherId,
            String username,
            String displayName,
            Long courseId,
            String assignmentRole,
            String status,
            Long assignedBy,
            Instant assignedAt
    ) {
    }
}
