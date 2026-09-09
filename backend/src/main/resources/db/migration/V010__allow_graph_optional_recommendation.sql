ALTER TABLE recommendation_snapshot
    DROP FOREIGN KEY fk_recommendation_snapshot_graph_version;

ALTER TABLE recommendation_snapshot
    MODIFY COLUMN graph_version_id BIGINT NULL;

ALTER TABLE recommendation_snapshot
    ADD CONSTRAINT fk_recommendation_snapshot_graph_version
        FOREIGN KEY (graph_version_id) REFERENCES graph_version(id);
