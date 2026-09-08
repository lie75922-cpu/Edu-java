package com.smartlearning.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V005SchemaTest {

    @Test
    void v05MigrationDefinesResolutionHistoryWithoutRewritingPriorMigrations() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V005__evidence_reresolution_history_and_draft_reconciliation.sql"
        ));

        assertThat(migration).contains(
                "CREATE TABLE knowledge_relation_evidence_resolution_history",
                "evidence_id BIGINT NOT NULL",
                "graph_version_id BIGINT NOT NULL",
                "old_resolution_status",
                "new_resolution_status",
                "operator_id BIGINT NULL",
                "trigger_type VARCHAR(48) NOT NULL",
                "before_relation_ids_json JSON NOT NULL",
                "after_relation_ids_json JSON NOT NULL",
                "reconciliation_json JSON NOT NULL"
        );
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V001__baseline.sql"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V002__v0_2_core_business_loop.sql"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V003__knowledge_relation_governance_and_versioned_graph.sql"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V004__rule_mastery_recommendation_and_learning_path.sql"))).isTrue();
    }
}
