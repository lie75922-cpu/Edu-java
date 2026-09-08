package com.smartlearning.graph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_relation_evidence_link")
public class KnowledgeRelationEvidenceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "relation_id", nullable = false)
    private Long relationId;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected KnowledgeRelationEvidenceLink() {
    }

    public KnowledgeRelationEvidenceLink(Long relationId, Long evidenceId) {
        this.relationId = relationId;
        this.evidenceId = evidenceId;
    }

    public Long getId() {
        return id;
    }

    public Long getRelationId() {
        return relationId;
    }

    public Long getEvidenceId() {
        return evidenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
