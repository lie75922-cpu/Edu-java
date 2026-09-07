package com.smartlearning.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_point")
public class KnowledgePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "knowledge_code", nullable = false, length = 96)
    private String knowledgeCode;

    @Column(name = "knowledge_name", nullable = false, length = 128)
    private String knowledgeName;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "mapping_status", nullable = false, length = 20)
    private String mappingStatus;

    @Column(nullable = false, length = 20)
    private String status;

    protected KnowledgePoint() {
    }

    public KnowledgePoint(
            Long courseId,
            Long areaId,
            String knowledgeCode,
            String knowledgeName,
            String sourceType,
            String externalId,
            String mappingStatus,
            String status
    ) {
        this.courseId = courseId;
        this.areaId = areaId;
        this.knowledgeCode = knowledgeCode;
        this.knowledgeName = knowledgeName;
        this.sourceType = sourceType;
        this.externalId = externalId;
        this.mappingStatus = mappingStatus;
        this.status = status;
    }

    public void update(
            Long courseId,
            Long areaId,
            String knowledgeCode,
            String knowledgeName,
            String sourceType,
            String externalId,
            String mappingStatus,
            String status
    ) {
        this.courseId = courseId;
        this.areaId = areaId;
        this.knowledgeCode = knowledgeCode;
        this.knowledgeName = knowledgeName;
        this.sourceType = sourceType;
        this.externalId = externalId;
        this.mappingStatus = mappingStatus;
        this.status = status;
    }

    public void disable() {
        this.status = "DISABLED";
    }

    public Long getId() {
        return id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getAreaId() {
        return areaId;
    }

    public String getKnowledgeCode() {
        return knowledgeCode;
    }

    public String getKnowledgeName() {
        return knowledgeName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getMappingStatus() {
        return mappingStatus;
    }

    public String getStatus() {
        return status;
    }
}
