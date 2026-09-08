package com.smartlearning.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V006SchemaTest {

    @Test
    void v06AddsTeacherAssignmentsAndOnlyNewAnalyticsIndexes() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V006__teacher_course_assignment_and_analytics_indexes.sql"
        ));

        assertThat(migration).contains(
                "CREATE TABLE course_teacher_assignment",
                "teacher_id BIGINT NOT NULL",
                "course_id BIGINT NOT NULL",
                "assignment_role VARCHAR(20) NOT NULL",
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'",
                "assigned_by BIGINT NULL",
                "assigned_at DATETIME(3) NOT NULL",
                "UNIQUE KEY uk_course_teacher_assignment_teacher_course (teacher_id, course_id)",
                "idx_course_teacher_assignment_course_status",
                "idx_course_teacher_assignment_teacher_status",
                "idx_answer_record_course_answered_id",
                "idx_answer_record_course_question_answered"
        );
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V001__baseline.sql"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V002__v0_2_core_business_loop.sql"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V003__knowledge_relation_governance_and_versioned_graph.sql"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V004__rule_mastery_recommendation_and_learning_path.sql"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V005__evidence_reresolution_history_and_draft_reconciliation.sql"))).isTrue();
    }
}
