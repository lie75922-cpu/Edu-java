package com.smartlearning.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "exercise_knowledge")
public class ExerciseKnowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exercise_unit_id", nullable = false)
    private Long exerciseUnitId;

    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    @Column(name = "mapping_source", nullable = false, length = 32)
    private String mappingSource;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(nullable = false)
    private boolean verified;

    protected ExerciseKnowledge() {
    }

    public ExerciseKnowledge(
            Long exerciseUnitId,
            Long knowledgePointId,
            String mappingSource,
            BigDecimal confidence,
            boolean verified
    ) {
        this.exerciseUnitId = exerciseUnitId;
        this.knowledgePointId = knowledgePointId;
        this.mappingSource = mappingSource;
        this.confidence = confidence;
        this.verified = verified;
    }

    public void update(String mappingSource, BigDecimal confidence, boolean verified) {
        this.mappingSource = mappingSource;
        this.confidence = confidence;
        this.verified = verified;
    }

    public Long getId() {
        return id;
    }

    public Long getExerciseUnitId() {
        return exerciseUnitId;
    }

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public String getMappingSource() {
        return mappingSource;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public boolean isVerified() {
        return verified;
    }
}
