package com.smartlearning.graph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "knowledge_relation")
public class KnowledgeRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "graph_version_id", nullable = false)
    private Long graphVersionId;

    @Column(name = "source_knowledge_point_id", nullable = false)
    private Long sourceKnowledgePointId;

    @Column(name = "target_knowledge_point_id", nullable = false)
    private Long targetKnowledgePointId;

    @Column(name = "relation_type", nullable = false, length = 32)
    private String relationType;

    @Column(name = "relation_source", nullable = false, length = 32)
    private String relationSource;

    @Column(name = "candidate_input_id", length = 128)
    private String candidateInputId;

    @Column(name = "candidate_policy_version", length = 128)
    private String candidatePolicyVersion;

    @Column(name = "candidate_status", length = 64)
    private String candidateStatus;

    @Column(name = "published_graph_status", length = 64)
    private String publishedGraphStatus;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 32)
    private RelationReviewStatus reviewStatus;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected KnowledgeRelation() {
    }

    public KnowledgeRelation(
            Long graphVersionId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId,
            String relationType,
            String relationSource,
            BigDecimal confidence,
            int evidenceCount,
            RelationReviewStatus reviewStatus,
            Long createdBy
    ) {
        this(
                graphVersionId, sourceKnowledgePointId, targetKnowledgePointId, relationType, relationSource,
                null, null, null, null, confidence, evidenceCount, reviewStatus, createdBy
        );
    }

    public KnowledgeRelation(
            Long graphVersionId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId,
            String relationType,
            String relationSource,
            String candidateInputId,
            String candidatePolicyVersion,
            String candidateStatus,
            String publishedGraphStatus,
            BigDecimal confidence,
            int evidenceCount,
            RelationReviewStatus reviewStatus,
            Long createdBy
    ) {
        this.graphVersionId = graphVersionId;
        this.sourceKnowledgePointId = sourceKnowledgePointId;
        this.targetKnowledgePointId = targetKnowledgePointId;
        this.relationType = relationType;
        this.relationSource = relationSource;
        this.candidateInputId = candidateInputId;
        this.candidatePolicyVersion = candidatePolicyVersion;
        this.candidateStatus = candidateStatus;
        this.publishedGraphStatus = publishedGraphStatus;
        this.confidence = confidence;
        this.evidenceCount = evidenceCount;
        this.reviewStatus = reviewStatus;
        this.createdBy = createdBy;
    }

    public void review(RelationReviewStatus status) {
        this.reviewStatus = status;
    }

    public void incrementEvidenceCount() {
        evidenceCount++;
    }

    /** Keeps the denormalized count aligned with the actual per-relation Evidence links. */
    public void reconcileEvidenceCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("evidence count cannot be negative");
        }
        evidenceCount = count;
        if (count == 0 && "EVIDENCE".equals(relationSource)) {
            reviewStatus = RelationReviewStatus.REJECTED;
        }
    }

    public KnowledgeRelation copyForGraphVersion(long targetGraphVersionId, Long copiedBy) {
        return new KnowledgeRelation(
                targetGraphVersionId,
                sourceKnowledgePointId,
                targetKnowledgePointId,
                relationType,
                relationSource,
                candidateInputId,
                candidatePolicyVersion,
                candidateStatus,
                publishedGraphStatus,
                confidence,
                evidenceCount,
                reviewStatus,
                copiedBy
        );
    }

    public Long getId() {
        return id;
    }

    public Long getGraphVersionId() {
        return graphVersionId;
    }

    public Long getSourceKnowledgePointId() {
        return sourceKnowledgePointId;
    }

    public Long getTargetKnowledgePointId() {
        return targetKnowledgePointId;
    }

    public String getRelationType() {
        return relationType;
    }

    public String getRelationSource() {
        return relationSource;
    }

    public String getCandidateInputId() {
        return candidateInputId;
    }

    public String getCandidatePolicyVersion() {
        return candidatePolicyVersion;
    }

    public String getCandidateStatus() {
        return candidateStatus;
    }

    public String getPublishedGraphStatus() {
        return publishedGraphStatus;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public RelationReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
