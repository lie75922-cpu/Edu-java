package com.smartlearning.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V003SchemaTest {

    @Test
    void v03MigrationDefinesGovernedEvidenceAndVersionedGraphTables() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V003__knowledge_relation_governance_and_versioned_graph.sql"));

        assertThat(migration).contains(
                "CREATE TABLE knowledge_relation_evidence",
                "CREATE TABLE graph_version",
                "CREATE TABLE knowledge_relation",
                "CREATE TABLE knowledge_relation_evidence_link",
                "CREATE TABLE graph_validation_issue",
                "CREATE TABLE knowledge_relation_evidence_import_run",
                "CREATE TABLE knowledge_relation_evidence_conflict",
                "active_graph_version_id",
                "UNIQUE KEY uk_graph_version_course_number (course_id, version_no)",
                "UNIQUE KEY uk_knowledge_relation_version_pair_type",
                "UNIQUE KEY uk_relation_evidence_link (relation_id, evidence_id)"
        );
    }
}
