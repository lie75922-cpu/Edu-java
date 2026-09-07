package com.smartlearning.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V002SchemaTest {

    @Test
    void v02MigrationDefinesCoreBusinessTablesAndIdempotencyConstraint() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V002__v0_2_core_business_loop.sql"));

        assertThat(migration).contains(
                "CREATE TABLE course", "CREATE TABLE knowledge_area", "CREATE TABLE knowledge_point",
                "CREATE TABLE exercise_unit", "CREATE TABLE exercise_knowledge", "CREATE TABLE question",
                "CREATE TABLE question_option", "CREATE TABLE course_enrollment", "CREATE TABLE answer_record",
                "CREATE TABLE seed_import_run", "CREATE TABLE seed_import_conflict",
                "UNIQUE KEY uk_answer_record_student_request (student_id, client_request_id)"
        );
    }
}
