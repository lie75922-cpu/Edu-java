ALTER TABLE course
    ADD COLUMN active_graph_version_id BIGINT NULL AFTER status;

CREATE TABLE graph_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    description VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    validated_at DATETIME(3) NULL,
    published_at DATETIME(3) NULL,
    failure_reason TEXT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_graph_version_course_number (course_id, version_no),
    KEY idx_graph_version_course_status (course_id, status),
    CONSTRAINT fk_graph_version_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_graph_version_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE course
    ADD KEY idx_course_active_graph_version (active_graph_version_id),
    ADD CONSTRAINT fk_course_active_graph_version
        FOREIGN KEY (active_graph_version_id) REFERENCES graph_version(id);

CREATE TABLE knowledge_relation_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    source_type VARCHAR(48) NOT NULL,
    external_evidence_id VARCHAR(128) NOT NULL,
    source_external_id VARCHAR(128) NOT NULL,
    target_external_id VARCHAR(128) NOT NULL,
    source_exercise_unit_id BIGINT NULL,
    target_exercise_unit_id BIGINT NULL,
    source_knowledge_point_id BIGINT NULL,
    target_knowledge_point_id BIGINT NULL,
    raw_payload_json JSON NOT NULL,
    resolution_status VARCHAR(48) NOT NULL,
    conflict_code VARCHAR(64) NULL,
    resolution_detail TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_relation_evidence_source_record (course_id, source_type, external_evidence_id),
    KEY idx_relation_evidence_course_status (course_id, resolution_status, id),
    KEY idx_relation_evidence_source_external (course_id, source_external_id),
    KEY idx_relation_evidence_target_external (course_id, target_external_id),
    CONSTRAINT fk_relation_evidence_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_relation_evidence_source_exercise FOREIGN KEY (source_exercise_unit_id) REFERENCES exercise_unit(id),
    CONSTRAINT fk_relation_evidence_target_exercise FOREIGN KEY (target_exercise_unit_id) REFERENCES exercise_unit(id),
    CONSTRAINT fk_relation_evidence_source_point FOREIGN KEY (source_knowledge_point_id) REFERENCES knowledge_point(id),
    CONSTRAINT fk_relation_evidence_target_point FOREIGN KEY (target_knowledge_point_id) REFERENCES knowledge_point(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_relation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    graph_version_id BIGINT NOT NULL,
    source_knowledge_point_id BIGINT NOT NULL,
    target_knowledge_point_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    relation_source VARCHAR(32) NOT NULL,
    confidence DECIMAL(5,4) NULL,
    evidence_count INT NOT NULL DEFAULT 0,
    review_status VARCHAR(32) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_relation_version_pair_type
        (graph_version_id, source_knowledge_point_id, target_knowledge_point_id, relation_type),
    KEY idx_knowledge_relation_version_review (graph_version_id, review_status, id),
    KEY idx_knowledge_relation_source (source_knowledge_point_id),
    KEY idx_knowledge_relation_target (target_knowledge_point_id),
    CONSTRAINT fk_knowledge_relation_version FOREIGN KEY (graph_version_id) REFERENCES graph_version(id),
    CONSTRAINT fk_knowledge_relation_source_point FOREIGN KEY (source_knowledge_point_id) REFERENCES knowledge_point(id),
    CONSTRAINT fk_knowledge_relation_target_point FOREIGN KEY (target_knowledge_point_id) REFERENCES knowledge_point(id),
    CONSTRAINT fk_knowledge_relation_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_relation_evidence_link (
    id BIGINT NOT NULL AUTO_INCREMENT,
    relation_id BIGINT NOT NULL,
    evidence_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_relation_evidence_link (relation_id, evidence_id),
    KEY idx_relation_evidence_link_evidence (evidence_id),
    CONSTRAINT fk_relation_evidence_link_relation FOREIGN KEY (relation_id) REFERENCES knowledge_relation(id) ON DELETE CASCADE,
    CONSTRAINT fk_relation_evidence_link_evidence FOREIGN KEY (evidence_id) REFERENCES knowledge_relation_evidence(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE graph_validation_issue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    graph_version_id BIGINT NOT NULL,
    relation_id BIGINT NULL,
    severity VARCHAR(16) NOT NULL,
    issue_code VARCHAR(64) NOT NULL,
    node_ids_json JSON NOT NULL,
    detail_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_graph_validation_issue_version (graph_version_id, severity, id),
    CONSTRAINT fk_graph_validation_issue_version FOREIGN KEY (graph_version_id) REFERENCES graph_version(id) ON DELETE CASCADE,
    CONSTRAINT fk_graph_validation_issue_relation FOREIGN KEY (relation_id) REFERENCES knowledge_relation(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_relation_evidence_import_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    graph_version_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    requested_by BIGINT NULL,
    mode VARCHAR(16) NOT NULL,
    source_type VARCHAR(48) NOT NULL,
    status VARCHAR(32) NOT NULL,
    summary_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_relation_evidence_import_course (course_id, id),
    CONSTRAINT fk_relation_evidence_import_version FOREIGN KEY (graph_version_id) REFERENCES graph_version(id),
    CONSTRAINT fk_relation_evidence_import_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_relation_evidence_import_requested_by FOREIGN KEY (requested_by) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_relation_evidence_conflict (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_run_id BIGINT NOT NULL,
    external_evidence_id VARCHAR(128) NULL,
    source_external_id VARCHAR(128) NULL,
    target_external_id VARCHAR(128) NULL,
    conflict_code VARCHAR(64) NOT NULL,
    detail_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_relation_evidence_conflict_run (import_run_id, id),
    CONSTRAINT fk_relation_evidence_conflict_run FOREIGN KEY (import_run_id)
        REFERENCES knowledge_relation_evidence_import_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
