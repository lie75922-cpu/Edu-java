package com.smartlearning.graph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_relation_evidence")
public class KnowledgeRelationEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "source_type", nullable = false, length = 48)
    private String sourceType;

    @Column(name = "external_evidence_id", nullable = false, length = 128)
    private String externalEvidenceId;

    @Column(name = "source_external_id", nullable = false, length = 128)
    private String sourceExternalId;

    @Column(name = "target_external_id", nullable = false, length = 128)
    private String targetExternalId;

    @Column(name = "source_exercise_unit_id")
    private Long sourceExerciseUnitId;

    @Column(name = "target_exercise_unit_id")
    private Long targetExerciseUnitId;

    @Column(name = "source_knowledge_point_id")
    private Long sourceKnowledgePointId;

    @Column(name = "target_knowledge_point_id")
    private Long targetKnowledgePointId;

    @Column(name = "raw_payload_json", nullable = false, columnDefinition = "JSON")
    private String rawPayloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false, length = 48)
    private EvidenceResolutionStatus resolutionStatus;

    @Column(name = "conflict_code", length = 64)
    private String conflictCode;

    @Column(name = "resolution_detail", columnDefinition = "TEXT")
    private String resolutionDetail;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected KnowledgeRelationEvidence() {
    }

    public KnowledgeRelationEvidence(
            Long courseId,
            String sourceType,
            String externalEvidenceId,
            String sourceExternalId,
            String targetExternalId,
            Long sourceExerciseUnitId,
            Long targetExerciseUnitId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId,
            String rawPayloadJson,
            EvidenceResolutionStatus resolutionStatus,
            String conflictCode,
            String resolutionDetail
    ) {
        this.courseId = courseId;
        this.sourceType = sourceType;
        this.externalEvidenceId = externalEvidenceId;
        this.sourceExternalId = sourceExternalId;
        this.targetExternalId = targetExternalId;
        this.sourceExerciseUnitId = sourceExerciseUnitId;
        this.targetExerciseUnitId = targetExerciseUnitId;
        this.sourceKnowledgePointId = sourceKnowledgePointId;
        this.targetKnowledgePointId = targetKnowledgePointId;
        this.rawPayloadJson = rawPayloadJson;
        this.resolutionStatus = resolutionStatus;
        this.conflictCode = conflictCode;
        this.resolutionDetail = resolutionDetail;
    }

    public boolean isResolved() {
        return resolutionStatus == EvidenceResolutionStatus.RESOLVED;
    }

    public Long getId() {
        return id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getSourceType() {
        return sourceType;
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

    public Long getSourceExerciseUnitId() {
        return sourceExerciseUnitId;
    }

    public Long getTargetExerciseUnitId() {
        return targetExerciseUnitId;
    }

    public Long getSourceKnowledgePointId() {
        return sourceKnowledgePointId;
    }

    public Long getTargetKnowledgePointId() {
        return targetKnowledgePointId;
    }

    public String getRawPayloadJson() {
        return rawPayloadJson;
    }

    public EvidenceResolutionStatus getResolutionStatus() {
        return resolutionStatus;
    }

    public String getConflictCode() {
        return conflictCode;
    }

    public String getResolutionDetail() {
        return resolutionDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
