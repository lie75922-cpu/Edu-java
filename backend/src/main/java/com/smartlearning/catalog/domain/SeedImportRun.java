package com.smartlearning.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "seed_import_run")
public class SeedImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(name = "source_name", nullable = false, length = 64)
    private String sourceName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "summary_json", nullable = false, columnDefinition = "JSON")
    private String summaryJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected SeedImportRun() {
    }

    public SeedImportRun(Long requestedBy, String mode, String sourceName) {
        this.requestedBy = requestedBy;
        this.mode = mode;
        this.sourceName = sourceName;
        this.status = "RUNNING";
        this.summaryJson = "{}";
    }

    public void complete(String status, String summaryJson) {
        this.status = status;
        this.summaryJson = summaryJson;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public String getMode() {
        return mode;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getStatus() {
        return status;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
