package com.smartlearning.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exercise_unit_id", nullable = false)
    private Long exerciseUnitId;

    @Column(name = "question_type", nullable = false, length = 32)
    private String questionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    @Column(name = "answer_json", nullable = false, columnDefinition = "JSON")
    private String answerJson;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(precision = 5, scale = 2)
    private BigDecimal difficulty;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_by")
    private Long createdBy;

    protected Question() {
    }

    public Question(
            Long exerciseUnitId,
            String questionType,
            String stem,
            String answerJson,
            String explanation,
            BigDecimal difficulty,
            String status,
            Long createdBy
    ) {
        this.exerciseUnitId = exerciseUnitId;
        this.questionType = questionType;
        this.stem = stem;
        this.answerJson = answerJson;
        this.explanation = explanation;
        this.difficulty = difficulty;
        this.status = status;
        this.createdBy = createdBy;
    }

    public void update(
            String questionType,
            String stem,
            String answerJson,
            String explanation,
            BigDecimal difficulty,
            String status
    ) {
        this.questionType = questionType;
        this.stem = stem;
        this.answerJson = answerJson;
        this.explanation = explanation;
        this.difficulty = difficulty;
        this.status = status;
    }

    public void disable() {
        this.status = "DISABLED";
    }

    public Long getId() {
        return id;
    }

    public Long getExerciseUnitId() {
        return exerciseUnitId;
    }

    public String getQuestionType() {
        return questionType;
    }

    public String getStem() {
        return stem;
    }

    public String getAnswerJson() {
        return answerJson;
    }

    public String getExplanation() {
        return explanation;
    }

    public BigDecimal getDifficulty() {
        return difficulty;
    }

    public String getStatus() {
        return status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
