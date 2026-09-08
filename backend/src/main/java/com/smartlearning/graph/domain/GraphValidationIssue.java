package com.smartlearning.graph.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "graph_validation_issue")
public class GraphValidationIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "graph_version_id", nullable = false)
    private Long graphVersionId;

    @Column(name = "relation_id")
    private Long relationId;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(name = "issue_code", nullable = false, length = 64)
    private String issueCode;

    @Column(name = "node_ids_json", nullable = false, columnDefinition = "JSON")
    private String nodeIdsJson;

    @Column(name = "detail_json", nullable = false, columnDefinition = "JSON")
    private String detailJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected GraphValidationIssue() {
    }

    public GraphValidationIssue(
            Long graphVersionId,
            Long relationId,
            String severity,
            String issueCode,
            String nodeIdsJson,
            String detailJson
    ) {
        this.graphVersionId = graphVersionId;
        this.relationId = relationId;
        this.severity = severity;
        this.issueCode = issueCode;
        this.nodeIdsJson = nodeIdsJson;
        this.detailJson = detailJson;
    }

    public Long getId() {
        return id;
    }

    public Long getGraphVersionId() {
        return graphVersionId;
    }

    public Long getRelationId() {
        return relationId;
    }

    public String getSeverity() {
        return severity;
    }

    public String getIssueCode() {
        return issueCode;
    }

    public String getNodeIdsJson() {
        return nodeIdsJson;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
