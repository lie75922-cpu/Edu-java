package com.smartlearning.mastery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "student_knowledge_mastery")
public class StudentKnowledgeMastery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "mastery_score", precision = 5, scale = 4)
    private BigDecimal masteryScore;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    @Column(name = "algorithm_version", nullable = false, length = 64)
    private String algorithmVersion;

    @Column(name = "last_answered_at")
    private Instant lastAnsweredAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected StudentKnowledgeMastery() {
    }

    public MasteryProvider.CurrentMastery current() {
        return new MasteryProvider.CurrentMastery(attemptCount, correctCount, masteryScore, lastAnsweredAt);
    }

    public void apply(MasteryProvider.MasteryUpdate update) {
        this.attemptCount = update.attemptCount();
        this.correctCount = update.correctCount();
        this.masteryScore = update.masteryScore();
        this.lastAnsweredAt = update.lastAnsweredAt();
        this.algorithmVersion = update.algorithmVersion();
        this.sourceType = update.sourceType();
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

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public BigDecimal getMasteryScore() {
        return masteryScore;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public Instant getLastAnsweredAt() {
        return lastAnsweredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
