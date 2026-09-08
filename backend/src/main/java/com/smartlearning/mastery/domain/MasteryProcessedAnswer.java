package com.smartlearning.mastery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "mastery_processed_answer")
public class MasteryProcessedAnswer {

    @Id
    @Column(name = "answer_record_id")
    private Long answerRecordId;

    @Column(name = "algorithm_version", nullable = false, length = 64)
    private String algorithmVersion;

    @Column(name = "processed_at", nullable = false, insertable = false, updatable = false)
    private Instant processedAt;

    protected MasteryProcessedAnswer() {
    }

    public Long getAnswerRecordId() {
        return answerRecordId;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
