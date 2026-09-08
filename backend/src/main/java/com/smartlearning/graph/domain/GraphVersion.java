package com.smartlearning.graph.domain;

import com.smartlearning.common.exception.ConflictException;
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
@Table(name = "graph_version")
public class GraphVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GraphVersionStatus status;

    @Column(length = 500)
    private String description;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    protected GraphVersion() {
    }

    public GraphVersion(Long courseId, int versionNo, String description, Long createdBy) {
        this.courseId = courseId;
        this.versionNo = versionNo;
        this.status = GraphVersionStatus.DRAFT;
        this.description = description;
        this.createdBy = createdBy;
    }

    public void reopenForEditing() {
        if (status != GraphVersionStatus.DRAFT && status != GraphVersionStatus.VALIDATION_FAILED) {
            throw new ConflictException("only a draft or validation-failed graph version can be edited");
        }
        status = GraphVersionStatus.DRAFT;
        failureReason = null;
    }

    public void beginValidation() {
        if (status != GraphVersionStatus.DRAFT && status != GraphVersionStatus.VALIDATION_FAILED && status != GraphVersionStatus.READY) {
            throw new ConflictException("graph version is not available for validation");
        }
        status = GraphVersionStatus.VALIDATING;
        failureReason = null;
    }

    public void markReady() {
        requireStatus(GraphVersionStatus.VALIDATING, "only a validating graph version can become ready");
        status = GraphVersionStatus.READY;
        validatedAt = Instant.now();
        failureReason = null;
    }

    public void markValidationFailed(String reason) {
        requireStatus(GraphVersionStatus.VALIDATING, "only a validating graph version can fail validation");
        status = GraphVersionStatus.VALIDATION_FAILED;
        validatedAt = Instant.now();
        failureReason = reason;
    }

    public void beginPublishing() {
        requireStatus(GraphVersionStatus.READY, "only a ready graph version can be published");
        status = GraphVersionStatus.PUBLISHING;
        failureReason = null;
    }

    public void retryProjection() {
        requireStatus(GraphVersionStatus.PROJECTION_FAILED, "only a projection-failed graph version can retry projection");
        status = GraphVersionStatus.PUBLISHING;
        failureReason = null;
    }

    public void markPublished() {
        requireStatus(GraphVersionStatus.PUBLISHING, "only a publishing graph version can become published");
        status = GraphVersionStatus.PUBLISHED;
        publishedAt = Instant.now();
        failureReason = null;
    }

    public void markProjectionFailed(String reason) {
        requireStatus(GraphVersionStatus.PUBLISHING, "only a publishing graph version can fail projection");
        status = GraphVersionStatus.PROJECTION_FAILED;
        failureReason = reason;
    }

    public void archive() {
        requireStatus(GraphVersionStatus.PUBLISHED, "only a published graph version can be archived");
        status = GraphVersionStatus.ARCHIVED;
    }

    private void requireStatus(GraphVersionStatus expected, String message) {
        if (status != expected) {
            throw new ConflictException(message);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public GraphVersionStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
