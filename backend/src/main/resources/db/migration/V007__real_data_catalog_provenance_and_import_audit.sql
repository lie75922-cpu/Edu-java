ALTER TABLE seed_import_run
    ADD COLUMN export_format_version VARCHAR(32) NOT NULL DEFAULT 'legacy-seed-v1' AFTER source_name,
    ADD COLUMN input_path VARCHAR(1024) NOT NULL DEFAULT 'not-recorded' AFTER export_format_version,
    ADD COLUMN input_size_bytes BIGINT NULL AFTER input_path,
    ADD COLUMN input_encoding VARCHAR(32) NOT NULL DEFAULT 'not-recorded' AFTER input_size_bytes,
    ADD COLUMN input_columns_json JSON NULL AFTER input_encoding,
    ADD COLUMN source_record_count INT NULL AFTER input_columns_json,
    ADD COLUMN quarantine_count INT NOT NULL DEFAULT 0 AFTER summary_json;

CREATE TABLE catalog_source_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    raw_value TEXT NOT NULL,
    display_name_zh VARCHAR(255) NOT NULL,
    display_mapping_status VARCHAR(32) NOT NULL,
    business_mapping_status VARCHAR(64) NOT NULL,
    source_metadata_row_number INT NULL,
    provenance_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_catalog_source_record_identity (course_id, entity_type, external_id),
    KEY idx_catalog_source_record_course_type (course_id, entity_type, id),
    CONSTRAINT fk_catalog_source_record_course FOREIGN KEY (course_id) REFERENCES course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
