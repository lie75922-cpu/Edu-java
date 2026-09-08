package com.smartlearning.mastery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "student_knowledge_mastery_history")
public class StudentKnowledgeMasteryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    @Column(name = "answer_record_id", nullable = false)
    private Long answerRecordId;

    @Column(name = "previous_attempt_count", nullable = false)
    private int previousAttemptCount;

    @Column(name = "previous_correct_count", nullable = false)
    private int previousCorrectCount;

    @Column(name = "previous_score", precision = 5, scale = 4)
    private BigDecimal previousScore;

    @Column(name = "new_attempt_count", nullable = false)
    private int newAttemptCount;

    @Column(name = "new_correct_count", nullable = false)
    private int newCorrectCount;

    @Column(name = "new_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal newScore;

    @Column(name = "algorithm_version", nullable = false, length = 64)
    private String algorithmVersion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected StudentKnowledgeMasteryHistory() {
    }

    public StudentKnowledgeMasteryHistory(
            Long studentId,
            Long courseId,
            Long knowledgePointId,
            Long answerRecordId,
            MasteryProvider.CurrentMastery previous,
            MasteryProvider.MasteryUpdate next
    ) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.knowledgePointId = knowledgePointId;
        this.answerRecordId = answerRecordId;
        this.previousAttemptCount = previous.attemptCount();
        this.previousCorrectCount = previous.correctCount();
        this.previousScore = previous.masteryScore();
        this.newAttemptCount = next.attemptCount();
        this.newCorrectCount = next.correctCount();
        this.newScore = next.masteryScore();
        this.algorithmVersion = next.algorithmVersion();
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

    public Long getAnswerRecordId() {
        return answerRecordId;
    }

    public int getPreviousAttemptCount() {
        return previousAttemptCount;
    }

    public int getPreviousCorrectCount() {
        return previousCorrectCount;
    }

    public BigDecimal getPreviousScore() {
        return previousScore;
    }

    public int getNewAttemptCount() {
        return newAttemptCount;
    }

    public int getNewCorrectCount() {
        return newCorrectCount;
    }

    public BigDecimal getNewScore() {
        return newScore;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
