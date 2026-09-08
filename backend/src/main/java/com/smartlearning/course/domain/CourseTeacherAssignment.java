package com.smartlearning.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "course_teacher_assignment")
public class CourseTeacherAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 20)
    private TeacherAssignmentRole assignmentRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeacherAssignmentStatus status;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected CourseTeacherAssignment() {
    }

    public CourseTeacherAssignment(
            Long teacherId,
            Long courseId,
            TeacherAssignmentRole assignmentRole,
            Long assignedBy
    ) {
        this.teacherId = teacherId;
        this.courseId = courseId;
        this.assignmentRole = assignmentRole;
        this.status = TeacherAssignmentStatus.ACTIVE;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now();
    }

    public void update(TeacherAssignmentRole assignmentRole, TeacherAssignmentStatus status, Long assignedBy) {
        this.assignmentRole = assignmentRole;
        this.status = status;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now();
    }

    public void deactivate(Long assignedBy) {
        this.status = TeacherAssignmentStatus.INACTIVE;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public TeacherAssignmentRole getAssignmentRole() {
        return assignmentRole;
    }

    public TeacherAssignmentStatus getStatus() {
        return status;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
