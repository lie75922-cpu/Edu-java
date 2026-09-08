package com.smartlearning.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V004SchemaTest {

    @Test
    void v04MigrationDefinesAuditableRuleMasteryAndRecommendationTables() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V004__rule_mastery_recommendation_and_learning_path.sql"
        ));

        assertThat(migration).contains(
                "CREATE TABLE student_knowledge_mastery",
                "CREATE TABLE mastery_processed_answer",
                "CREATE TABLE student_knowledge_mastery_history",
                "CREATE TABLE recommendation_snapshot",
                "CREATE TABLE recommendation_item",
                "UNIQUE KEY uk_student_knowledge_mastery_student_point (student_id, knowledge_point_id)",
                "PRIMARY KEY (answer_record_id)",
                "UNIQUE KEY uk_mastery_history_answer_point (answer_record_id, knowledge_point_id)",
                "mastery_algorithm_version",
                "recommendation_rule_version",
                "explanation_json JSON NOT NULL"
        );
    }
}
