package com.smartlearning.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "JSON")
    private String payloadJson;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(String eventType, String aggregateType, String aggregateId, String payloadJson) {
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payloadJson = payloadJson;
        this.status = "PENDING";
        this.retryCount = 0;
        this.version = 0;
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getStatus() {
        return status;
    }

    public void markDone() {
        this.status = "DONE";
        this.processedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = "FAILED";
        this.retryCount++;
        this.processedAt = Instant.now();
        this.lastError = error;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
