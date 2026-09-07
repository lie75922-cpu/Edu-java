package com.smartlearning.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_area")
public class KnowledgeArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "area_code", nullable = false, length = 96)
    private String areaCode;

    @Column(name = "area_name", nullable = false, length = 128)
    private String areaName;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(nullable = false, length = 20)
    private String status;

    protected KnowledgeArea() {
    }

    public KnowledgeArea(Long courseId, String areaCode, String areaName, String sourceType, String externalId, String status) {
        this.courseId = courseId;
        this.areaCode = areaCode;
        this.areaName = areaName;
        this.sourceType = sourceType;
        this.externalId = externalId;
        this.status = status;
    }

    public void update(Long courseId, String areaCode, String areaName, String sourceType, String externalId, String status) {
        this.courseId = courseId;
        this.areaCode = areaCode;
        this.areaName = areaName;
        this.sourceType = sourceType;
        this.externalId = externalId;
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

    public String getAreaCode() {
        return areaCode;
    }

    public String getAreaName() {
        return areaName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getStatus() {
        return status;
    }
}
