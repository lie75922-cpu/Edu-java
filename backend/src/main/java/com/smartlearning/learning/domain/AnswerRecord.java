package com.smartlearning.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "answer_record")
public class AnswerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "exercise_unit_id", nullable = false)
    private Long exerciseUnitId;

    @Column(name = "submitted_answer_json", nullable = false, columnDefinition = "JSON")
    private String submittedAnswerJson;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "client_request_id", nullable = false, length = 128)
    private String clientRequestId;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    protected AnswerRecord() {
    }

    public AnswerRecord(
            Long studentId,
            Long courseId,
            Long questionId,
            Long exerciseUnitId,
            String submittedAnswerJson,
            boolean correct,
            int attemptNo,
            Long durationMs,
            String clientRequestId,
            Instant answeredAt
    ) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.questionId = questionId;
        this.exerciseUnitId = exerciseUnitId;
        this.submittedAnswerJson = submittedAnswerJson;
        this.correct = correct;
        this.attemptNo = attemptNo;
        this.durationMs = durationMs;
        this.clientRequestId = clientRequestId;
        this.answeredAt = answeredAt;
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

    public Long getQuestionId() {
        return questionId;
    }

    public Long getExerciseUnitId() {
        return exerciseUnitId;
    }

    public String getSubmittedAnswerJson() {
        return submittedAnswerJson;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
