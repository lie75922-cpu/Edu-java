package com.smartlearning.graph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_relation_evidence_import_run")
public class KnowledgeRelationEvidenceImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "graph_version_id", nullable = false)
    private Long graphVersionId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(nullable = false, length = 16)
    private String mode;

    @Column(name = "source_type", nullable = false, length = 48)
    private String sourceType;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "summary_json", nullable = false, columnDefinition = "JSON")
    private String summaryJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected KnowledgeRelationEvidenceImportRun() {
    }

    public KnowledgeRelationEvidenceImportRun(Long graphVersionId, Long courseId, Long requestedBy, String mode, String sourceType) {
        this.graphVersionId = graphVersionId;
        this.courseId = courseId;
        this.requestedBy = requestedBy;
        this.mode = mode;
        this.sourceType = sourceType;
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

    public Long getGraphVersionId() {
        return graphVersionId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public String getMode() {
        return mode;
    }

    public String getSourceType() {
        return sourceType;
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
