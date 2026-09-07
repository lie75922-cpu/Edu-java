package com.smartlearning.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_code", nullable = false, unique = true, length = 64)
    private String courseCode;

    @Column(name = "course_name", nullable = false, length = 128)
    private String courseName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Course() {
    }

    public Course(String courseCode, String courseName, String description, String status) {
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.description = description;
        this.status = status;
    }

    public void update(String courseCode, String courseName, String description, String status) {
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.description = description;
        this.status = status;
    }

    public void disable() {
        this.status = "DISABLED";
    }

    public Long getId() {
        return id;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }
}
