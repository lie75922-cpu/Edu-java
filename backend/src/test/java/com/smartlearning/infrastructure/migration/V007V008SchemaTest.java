package com.smartlearning.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V007V008SchemaTest {

    @Test
    void migrationsAddCatalogProvenanceAndCandidatePolicyWithoutRewritingEarlierVersions() throws Exception {
        String v007 = Files.readString(Path.of(
                "src/main/resources/db/migration/V007__real_data_catalog_provenance_and_import_audit.sql"
        ));
        String v008 = Files.readString(Path.of(
                "src/main/resources/db/migration/V008__candidate_input_policy_governance.sql"
        ));

        assertThat(v007).contains(
                "ALTER TABLE seed_import_run",
                "export_format_version",
                "input_path",
                "input_columns_json",
                "quarantine_count",
                "CREATE TABLE catalog_source_record",
                "raw_value TEXT NOT NULL",
                "display_name_zh",
                "provenance_json",
                "uk_catalog_source_record_identity"
        );
        assertThat(v008).contains("candidate_input_id", "candidate_policy_version", "candidate_status", "published_graph_status");
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V001__baseline.sql"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V006__teacher_course_assignment_and_analytics_indexes.sql"))).isTrue();
    }
}
