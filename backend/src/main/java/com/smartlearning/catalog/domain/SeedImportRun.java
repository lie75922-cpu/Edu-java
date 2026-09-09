package com.smartlearning.catalog.domain;

import com.smartlearning.catalog.api.SeedImportApi;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "seed_import_run")
public class SeedImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(name = "source_name", nullable = false, length = 64)
    private String sourceName;

    @Column(name = "export_format_version", nullable = false, length = 32)
    private String exportFormatVersion;

    @Column(name = "input_path", nullable = false, length = 1024)
    private String inputPath;

    @Column(name = "input_size_bytes")
    private Long inputSizeBytes;

    @Column(name = "input_encoding", nullable = false, length = 32)
    private String inputEncoding;

    @Column(name = "input_columns_json", nullable = false, columnDefinition = "JSON")
    private String inputColumnsJson;

    @Column(name = "source_record_count")
    private Integer sourceRecordCount;

    @Column(nullable = false, length = 48)
    private String status;

    @Column(name = "summary_json", nullable = false, columnDefinition = "JSON")
    private String summaryJson;

    @Column(name = "quarantine_count", nullable = false)
    private int quarantineCount;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected SeedImportRun() {
    }

    public SeedImportRun(
            Long requestedBy,
            String mode,
            String sourceName,
            SeedImportApi.SeedImportMetadata metadata,
            String inputColumnsJson
    ) {
        this.requestedBy = requestedBy;
        this.mode = mode;
        this.sourceName = sourceName;
        this.exportFormatVersion = metadata.exportFormatVersion();
        this.inputPath = metadata.inputPath();
        this.inputSizeBytes = metadata.inputSizeBytes();
        this.inputEncoding = metadata.inputEncoding();
        this.inputColumnsJson = inputColumnsJson;
        this.sourceRecordCount = metadata.sourceRecordCount();
        this.status = "RUNNING";
        this.summaryJson = "{}";
    }

    public void complete(String status, String summaryJson, int quarantineCount) {
        this.status = status;
        this.summaryJson = summaryJson;
        this.quarantineCount = quarantineCount;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public String getMode() {
        return mode;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getExportFormatVersion() {
        return exportFormatVersion;
    }

    public String getInputPath() {
        return inputPath;
    }

    public Long getInputSizeBytes() {
        return inputSizeBytes;
    }

    public String getInputEncoding() {
        return inputEncoding;
    }

    public String getInputColumnsJson() {
        return inputColumnsJson;
    }

    public Integer getSourceRecordCount() {
        return sourceRecordCount;
    }

    public String getStatus() {
        return status;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public int getQuarantineCount() {
        return quarantineCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
