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

/** Immutable audit entry for one actual resolution transition in a targeted editable Draft. */
@Entity
@Table(name = "knowledge_relation_evidence_resolution_history")
public class KnowledgeRelationEvidenceResolutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evidence_id", nullable = false, updatable = false)
    private Long evidenceId;

    @Column(name = "graph_version_id", nullable = false, updatable = false)
    private Long graphVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_resolution_status", nullable = false, length = 48, updatable = false)
    private EvidenceResolutionStatus oldResolutionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_resolution_status", nullable = false, length = 48, updatable = false)
    private EvidenceResolutionStatus newResolutionStatus;

    @Column(name = "old_source_exercise_unit_id", updatable = false)
    private Long oldSourceExerciseUnitId;

    @Column(name = "old_target_exercise_unit_id", updatable = false)
    private Long oldTargetExerciseUnitId;

    @Column(name = "old_source_knowledge_point_id", updatable = false)
    private Long oldSourceKnowledgePointId;

    @Column(name = "old_target_knowledge_point_id", updatable = false)
    private Long oldTargetKnowledgePointId;

    @Column(name = "new_source_exercise_unit_id", updatable = false)
    private Long newSourceExerciseUnitId;

    @Column(name = "new_target_exercise_unit_id", updatable = false)
    private Long newTargetExerciseUnitId;

    @Column(name = "new_source_knowledge_point_id", updatable = false)
    private Long newSourceKnowledgePointId;

    @Column(name = "new_target_knowledge_point_id", updatable = false)
    private Long newTargetKnowledgePointId;

    @Column(name = "old_conflict_code", length = 64, updatable = false)
    private String oldConflictCode;

    @Column(name = "new_conflict_code", length = 64, updatable = false)
    private String newConflictCode;

    @Column(name = "old_resolution_detail", columnDefinition = "TEXT", updatable = false)
    private String oldResolutionDetail;

    @Column(name = "new_resolution_detail", columnDefinition = "TEXT", updatable = false)
    private String newResolutionDetail;

    @Column(name = "operator_id", updatable = false)
    private Long operatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 48, updatable = false)
    private EvidenceReresolutionTrigger triggerType;

    @Column(name = "before_relation_ids_json", nullable = false, columnDefinition = "JSON", updatable = false)
    private String beforeRelationIdsJson;

    @Column(name = "after_relation_ids_json", nullable = false, columnDefinition = "JSON", updatable = false)
    private String afterRelationIdsJson;

    @Column(name = "reconciliation_json", nullable = false, columnDefinition = "JSON", updatable = false)
    private String reconciliationJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected KnowledgeRelationEvidenceResolutionHistory() {
    }

    public KnowledgeRelationEvidenceResolutionHistory(
            Long evidenceId,
            Long graphVersionId,
            KnowledgeRelationEvidence.ResolutionState before,
            KnowledgeRelationEvidence.ResolutionState after,
            Long operatorId,
            EvidenceReresolutionTrigger triggerType,
            String beforeRelationIdsJson,
            String afterRelationIdsJson,
            String reconciliationJson
    ) {
        this.evidenceId = evidenceId;
        this.graphVersionId = graphVersionId;
        this.oldResolutionStatus = before.status();
        this.newResolutionStatus = after.status();
        this.oldSourceExerciseUnitId = before.sourceExerciseUnitId();
        this.oldTargetExerciseUnitId = before.targetExerciseUnitId();
        this.oldSourceKnowledgePointId = before.sourceKnowledgePointId();
        this.oldTargetKnowledgePointId = before.targetKnowledgePointId();
        this.newSourceExerciseUnitId = after.sourceExerciseUnitId();
        this.newTargetExerciseUnitId = after.targetExerciseUnitId();
        this.newSourceKnowledgePointId = after.sourceKnowledgePointId();
        this.newTargetKnowledgePointId = after.targetKnowledgePointId();
        this.oldConflictCode = before.conflictCode();
        this.newConflictCode = after.conflictCode();
        this.oldResolutionDetail = before.detail();
        this.newResolutionDetail = after.detail();
        this.operatorId = operatorId;
        this.triggerType = triggerType;
        this.beforeRelationIdsJson = beforeRelationIdsJson;
        this.afterRelationIdsJson = afterRelationIdsJson;
        this.reconciliationJson = reconciliationJson;
    }

    public Long getId() { return id; }
    public Long getEvidenceId() { return evidenceId; }
    public Long getGraphVersionId() { return graphVersionId; }
    public EvidenceResolutionStatus getOldResolutionStatus() { return oldResolutionStatus; }
    public EvidenceResolutionStatus getNewResolutionStatus() { return newResolutionStatus; }
    public Long getOldSourceExerciseUnitId() { return oldSourceExerciseUnitId; }
    public Long getOldTargetExerciseUnitId() { return oldTargetExerciseUnitId; }
    public Long getOldSourceKnowledgePointId() { return oldSourceKnowledgePointId; }
    public Long getOldTargetKnowledgePointId() { return oldTargetKnowledgePointId; }
    public Long getNewSourceExerciseUnitId() { return newSourceExerciseUnitId; }
    public Long getNewTargetExerciseUnitId() { return newTargetExerciseUnitId; }
    public Long getNewSourceKnowledgePointId() { return newSourceKnowledgePointId; }
    public Long getNewTargetKnowledgePointId() { return newTargetKnowledgePointId; }
    public String getOldConflictCode() { return oldConflictCode; }
    public String getNewConflictCode() { return newConflictCode; }
    public String getOldResolutionDetail() { return oldResolutionDetail; }
    public String getNewResolutionDetail() { return newResolutionDetail; }
    public Long getOperatorId() { return operatorId; }
    public EvidenceReresolutionTrigger getTriggerType() { return triggerType; }
    public String getBeforeRelationIdsJson() { return beforeRelationIdsJson; }
    public String getAfterRelationIdsJson() { return afterRelationIdsJson; }
    public String getReconciliationJson() { return reconciliationJson; }
    public Instant getCreatedAt() { return createdAt; }
}
