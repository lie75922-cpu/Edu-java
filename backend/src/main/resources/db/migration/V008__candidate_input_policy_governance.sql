ALTER TABLE knowledge_relation
    ADD COLUMN candidate_input_id VARCHAR(128) NULL AFTER relation_source,
    ADD COLUMN candidate_policy_version VARCHAR(128) NULL AFTER candidate_input_id,
    ADD COLUMN candidate_status VARCHAR(64) NULL AFTER candidate_policy_version,
    ADD COLUMN published_graph_status VARCHAR(64) NULL AFTER candidate_status;
