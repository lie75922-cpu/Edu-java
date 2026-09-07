package com.smartlearning.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "exercise_unit")
public class ExerciseUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "exercise_code", nullable = false, length = 96)
    private String exerciseCode;

    @Column(name = "exercise_name", nullable = false, length = 255)
    private String exerciseName;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "identity_status", nullable = false, length = 32)
    private String identityStatus;

    @Column(name = "mapping_status", nullable = false, length = 20)
    private String mappingStatus;

    @Column(precision = 5, scale = 2)
    private BigDecimal difficulty;

    @Column(nullable = false, length = 20)
    private String status;

    protected ExerciseUnit() {
    }

    public ExerciseUnit(
            Long courseId,
            String exerciseCode,
            String exerciseName,
            String sourceType,
            String externalId,
            String identityStatus,
            String mappingStatus,
            BigDecimal difficulty,
            String status
    ) {
        this.courseId = courseId;
        this.exerciseCode = exerciseCode;
        this.exerciseName = exerciseName;
        this.sourceType = sourceType;
        this.externalId = externalId;
        this.identityStatus = identityStatus;
        this.mappingStatus = mappingStatus;
        this.difficulty = difficulty;
        this.status = status;
    }

    public void update(
            Long courseId,
            String exerciseCode,
            String exerciseName,
            String sourceType,
            String externalId,
            String identityStatus,
            String mappingStatus,
            BigDecimal difficulty,
            String status
    ) {
        this.courseId = courseId;
        this.exerciseCode = exerciseCode;
        this.exerciseName = exerciseName;
        this.sourceType = sourceType;
        this.externalId = externalId;
        this.identityStatus = identityStatus;
        this.mappingStatus = mappingStatus;
        this.difficulty = difficulty;
        this.status = status;
    }

    public void setMappingStatus(String mappingStatus) {
        this.mappingStatus = mappingStatus;
    }

    public void disable() {
        this.status = "DISABLED";
    }

    public Long getId() {
        return id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getExerciseCode() {
        return exerciseCode;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getIdentityStatus() {
        return identityStatus;
    }

    public String getMappingStatus() {
        return mappingStatus;
    }

    public BigDecimal getDifficulty() {
        return difficulty;
    }

    public String getStatus() {
        return status;
    }
}
