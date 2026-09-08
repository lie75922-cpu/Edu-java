CREATE TABLE knowledge_relation_evidence_resolution_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evidence_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    old_resolution_status VARCHAR(48) NOT NULL,
    new_resolution_status VARCHAR(48) NOT NULL,
    old_source_exercise_unit_id BIGINT NULL,
    old_target_exercise_unit_id BIGINT NULL,
    old_source_knowledge_point_id BIGINT NULL,
    old_target_knowledge_point_id BIGINT NULL,
    new_source_exercise_unit_id BIGINT NULL,
    new_target_exercise_unit_id BIGINT NULL,
    new_source_knowledge_point_id BIGINT NULL,
    new_target_knowledge_point_id BIGINT NULL,
    old_conflict_code VARCHAR(64) NULL,
    new_conflict_code VARCHAR(64) NULL,
    old_resolution_detail TEXT NULL,
    new_resolution_detail TEXT NULL,
    operator_id BIGINT NULL,
    trigger_type VARCHAR(48) NOT NULL,
    before_relation_ids_json JSON NOT NULL,
    after_relation_ids_json JSON NOT NULL,
    reconciliation_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_relation_evidence_resolution_history_evidence (evidence_id, id),
    KEY idx_relation_evidence_resolution_history_draft (graph_version_id, id),
    CONSTRAINT fk_relation_evidence_resolution_history_evidence
        FOREIGN KEY (evidence_id) REFERENCES knowledge_relation_evidence(id),
    CONSTRAINT fk_relation_evidence_resolution_history_version
        FOREIGN KEY (graph_version_id) REFERENCES graph_version(id),
    CONSTRAINT fk_relation_evidence_resolution_history_operator
        FOREIGN KEY (operator_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
