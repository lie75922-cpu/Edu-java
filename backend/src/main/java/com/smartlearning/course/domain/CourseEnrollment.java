package com.smartlearning.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "course_enrollment")
public class CourseEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "enroll_source", nullable = false, length = 32)
    private String enrollSource;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "enrolled_at", nullable = false, insertable = false, updatable = false)
    private Instant enrolledAt;

    protected CourseEnrollment() {
    }

    public CourseEnrollment(Long studentId, Long courseId, String enrollSource) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.enrollSource = enrollSource;
        this.status = "ACTIVE";
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getEnrollSource() {
        return enrollSource;
    }

    public String getStatus() {
        return status;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }
}
