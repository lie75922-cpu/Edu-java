package com.smartlearning.graph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_relation_evidence_conflict")
public class KnowledgeRelationEvidenceConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_run_id", nullable = false)
    private Long importRunId;

    @Column(name = "external_evidence_id", length = 128)
    private String externalEvidenceId;

    @Column(name = "source_external_id", length = 128)
    private String sourceExternalId;

    @Column(name = "target_external_id", length = 128)
    private String targetExternalId;

    @Column(name = "conflict_code", nullable = false, length = 64)
    private String conflictCode;

    @Column(name = "detail_json", nullable = false, columnDefinition = "JSON")
    private String detailJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected KnowledgeRelationEvidenceConflict() {
    }

    public KnowledgeRelationEvidenceConflict(
            Long importRunId,
            String externalEvidenceId,
            String sourceExternalId,
            String targetExternalId,
            String conflictCode,
            String detailJson
    ) {
        this.importRunId = importRunId;
        this.externalEvidenceId = externalEvidenceId;
        this.sourceExternalId = sourceExternalId;
        this.targetExternalId = targetExternalId;
        this.conflictCode = conflictCode;
        this.detailJson = detailJson;
    }

    public Long getId() {
        return id;
    }

    public Long getImportRunId() {
        return importRunId;
    }

    public String getExternalEvidenceId() {
        return externalEvidenceId;
    }

    public String getSourceExternalId() {
        return sourceExternalId;
    }

    public String getTargetExternalId() {
        return targetExternalId;
    }

    public String getConflictCode() {
        return conflictCode;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
