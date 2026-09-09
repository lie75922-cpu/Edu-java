package com.smartlearning.catalog.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FoundationBusinessExportReaderTest {

    @Test
    void readsFoundationContractFixtureWithoutTreatingMetadataAsQuestionContent() {
        FoundationBusinessExportReader reader = new FoundationBusinessExportReader(new ObjectMapper());

        FoundationBusinessExportReader.FoundationBusinessExport export = reader.read(
                Path.of("src/test/resources/real-data-v1/business_export_fixture"), "JUNYI-MATH", "数学"
        );

        assertThat(export.catalogRequest().metadata().exportFormatVersion()).isEqualTo("1.0.0");
        assertThat(export.catalogRequest().areas()).singleElement().satisfies(area -> {
            assertThat(area.rawArea()).isEqualTo("fixture-raw-area-1");
            assertThat(area.displayNameZh()).isEqualTo("示例领域");
        });
        assertThat(export.catalogRequest().exercises()).singleElement().satisfies(exercise -> {
            assertThat(exercise.topicExternalId()).isEqualTo("fixture-topic:1");
            assertThat(exercise.rawSourceFields()).doesNotContainKeys("question", "answer", "student", "interaction");
        });
        assertThat(export.rawEvidenceRequest().evidence()).hasSize(1);
        assertThat(export.candidateRequest().candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.derivationPolicyVersion()).isEqualTo("fixture-topic-projection-v1");
            assertThat(candidate.candidateStatus()).isEqualTo("REVIEW_REQUIRED_NOT_PUBLISHED");
            assertThat(candidate.publishedGraphStatus()).isEqualTo("NOT_PUBLISHED");
            assertThat(candidate.rawEvidenceIds()).containsExactly("fixture-evidence-1");
        });
    }
}
