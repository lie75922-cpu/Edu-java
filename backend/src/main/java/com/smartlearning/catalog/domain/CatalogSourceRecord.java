package com.smartlearning.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Audit-side mapping for a business directory identity. Business display fields remain on their
 * own entities; raw source values and provenance never replace them or enter Question content.
 */
@Entity
@Table(name = "catalog_source_record")
public class CatalogSourceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Column(name = "raw_value", nullable = false, columnDefinition = "TEXT")
    private String rawValue;

    @Column(name = "display_name_zh", nullable = false, length = 255)
    private String displayNameZh;

    @Column(name = "display_mapping_status", nullable = false, length = 32)
    private String displayMappingStatus;

    @Column(name = "business_mapping_status", nullable = false, length = 64)
    private String businessMappingStatus;

    @Column(name = "source_metadata_row_number")
    private Integer sourceMetadataRowNumber;

    @Column(name = "provenance_json", nullable = false, columnDefinition = "JSON")
    private String provenanceJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected CatalogSourceRecord() {
    }

    public CatalogSourceRecord(
            Long courseId,
            String entityType,
            String externalId,
            String rawValue,
            String displayNameZh,
            String displayMappingStatus,
            String businessMappingStatus,
            Integer sourceMetadataRowNumber,
            String provenanceJson
    ) {
        this.courseId = courseId;
        this.entityType = entityType;
        this.externalId = externalId;
        this.rawValue = rawValue;
        this.displayNameZh = displayNameZh;
        this.displayMappingStatus = displayMappingStatus;
        this.businessMappingStatus = businessMappingStatus;
        this.sourceMetadataRowNumber = sourceMetadataRowNumber;
        this.provenanceJson = provenanceJson;
    }

    /** Raw value is insert-only. Only source-derived display and provenance may be refreshed. */
    public void refreshDerived(
            String displayNameZh,
            String displayMappingStatus,
            String businessMappingStatus,
            Integer sourceMetadataRowNumber,
            String provenanceJson
    ) {
        this.displayNameZh = displayNameZh;
        this.displayMappingStatus = displayMappingStatus;
        this.businessMappingStatus = businessMappingStatus;
        this.sourceMetadataRowNumber = sourceMetadataRowNumber;
        this.provenanceJson = provenanceJson;
    }

    public Long getId() {
        return id;
    }

    public String getRawValue() {
        return rawValue;
    }

    public String getDisplayNameZh() {
        return displayNameZh;
    }

    public String getDisplayMappingStatus() {
        return displayMappingStatus;
    }

    public String getBusinessMappingStatus() {
        return businessMappingStatus;
    }

    public Integer getSourceMetadataRowNumber() {
        return sourceMetadataRowNumber;
    }

    public String getProvenanceJson() {
        return provenanceJson;
    }
}
