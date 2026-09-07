package com.smartlearning.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "seed_import_conflict")
public class SeedImportConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_run_id", nullable = false)
    private Long importRunId;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "conflict_type", nullable = false, length = 64)
    private String conflictType;

    @Column(name = "detail_json", nullable = false, columnDefinition = "JSON")
    private String detailJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected SeedImportConflict() {
    }

    public SeedImportConflict(Long importRunId, String entityType, String externalId, String conflictType, String detailJson) {
        this.importRunId = importRunId;
        this.entityType = entityType;
        this.externalId = externalId;
        this.conflictType = conflictType;
        this.detailJson = detailJson;
    }

    public Long getId() {
        return id;
    }

    public Long getImportRunId() {
        return importRunId;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getConflictType() {
        return conflictType;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
