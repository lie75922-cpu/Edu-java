package com.smartlearning.research;

import com.smartlearning.common.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResearchExerciseController.class)
@Import({ResearchExerciseCatalogService.class, ResearchCatalogExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "app.research.exercises.catalog-path=build/test-data/research/junyi_catalog_v2.json",
        "spring.security.user.name=user",
        "spring.security.user.password=password"
})
class ResearchExerciseControllerTest {

    private static final Path CATALOG_PATH = Path.of("build/test-data/research/junyi_catalog_v2.json");
    private static final String SOURCE_SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void writeCatalog() throws Exception {
        writeCatalog(List.of(
                item(1, "linear_equations", "Linear Equations", "algebra", "math", true, "one_step", List.of("one_step"), false),
                item(2, "triangle_area", "Triangle Area", "geometry", "math", true, "", List.of(), false),
                item(3, "linear_review", "Linear Review", "algebra", "math", false, "linear_equations", List.of("linear_equations"), false),
                item(4, "duplicate_exercise", "Duplicate One", "algebra", "math", true, "", List.of(), true),
                item(5, "duplicate_exercise", "Duplicate Two", "algebra", "math", true, "", List.of(), true)
        ));
    }

    @Test
    void researchExercisesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/research/exercises"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsDefaultPageWithoutMergingDuplicateExternalIds() throws Exception {
        mockMvc.perform(get("/api/v1/research/exercises")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items[3].recordNumber").value(4))
                .andExpect(jsonPath("$.data.items[4].recordNumber").value(5))
                .andExpect(jsonPath("$.data.items[4].duplicateExternalId").value(true));
    }

    @Test
    void filtersByTopicAreaAndQuery() throws Exception {
        mockMvc.perform(get("/api/v1/research/exercises")
                        .queryParam("topic", "algebra")
                        .queryParam("area", "math")
                        .queryParam("q", "review")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].externalId").value("linear_review"));
    }

    @Test
    void supportsPaginationAndSizeLimit() throws Exception {
        mockMvc.perform(get("/api/v1/research/exercises")
                        .queryParam("page", "1")
                        .queryParam("size", "2")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].recordNumber").value(3));

        mockMvc.perform(get("/api/v1/research/exercises")
                        .queryParam("size", "101")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/v1/research/exercises")
                        .queryParam("page", "-1")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void returnsBadRequestWhenPagingParameterTypeIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/research/exercises")
                        .queryParam("page", "abc")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void returnsExplicitErrorWhenCatalogIsMissing() throws Exception {
        Files.deleteIfExists(CATALOG_PATH);

        mockMvc.perform(get("/api/v1/research/exercises")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RESEARCH_CATALOG_NOT_FOUND"));
    }

    @Test
    void returnsExplicitErrorWhenCatalogProvenanceIsInvalid() throws Exception {
        Files.createDirectories(CATALOG_PATH.getParent());
        objectMapper.writeValue(CATALOG_PATH.toFile(), Map.of(
                "schemaVersion", 2,
                "provenance", Map.of(
                        "sourceType", "THIRD_PARTY_PROCESSED",
                        "sourceLabel", "mirror",
                        "sourceUri", "https://example.invalid",
                        "sourceFileName", "junyi_Exercise_table.csv",
                        "sourceSha256", "not-a-sha",
                        "acquiredAt", "2026-09-07T00:00:00Z",
                        "transformation", "projection"
                ),
                "items", List.of()
        ));

        mockMvc.perform(get("/api/v1/research/exercises")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("RESEARCH_CATALOG_INVALID_FORMAT"));
    }

    @Test
    void returnsExplicitErrorWhenRawAndParsedPrerequisiteDiverge() throws Exception {
        writeCatalog(List.of(item(1, "broken", "Broken", "algebra", "math", true, "p1,p2", List.of("p1"), false)));

        mockMvc.perform(get("/api/v1/research/exercises")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("RESEARCH_CATALOG_INVALID_FORMAT"));
    }

    @Test
    void reloadsCatalogWhenUnderlyingFileChanges() throws Exception {
        mockMvc.perform(get("/api/v1/research/exercises")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5));

        writeCatalog(List.of(item(1, "only_one", "Only One", "algebra", "math", true, "", List.of(), false)));

        mockMvc.perform(get("/api/v1/research/exercises")
                        .with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].externalId").value("only_one"));
    }

    private void writeCatalog(List<Map<String, Object>> items) throws Exception {
        Files.createDirectories(CATALOG_PATH.getParent());
        objectMapper.writeValue(CATALOG_PATH.toFile(), Map.of(
                "schemaVersion", 2,
                "provenance", Map.of(
                        "sourceType", "THIRD_PARTY_PROCESSED",
                        "sourceLabel", "Junyi metadata mirror under provenance review",
                        "sourceUri", "https://example.invalid/test-source",
                        "sourceFileName", "junyi_Exercise_table.csv",
                        "sourceSha256", SOURCE_SHA,
                        "acquiredAt", "2026-09-07T00:00:00Z",
                        "transformation", "Research catalog projection only"
                ),
                "items", items
        ));
    }

    private Map<String, Object> item(
            int recordNumber,
            String externalId,
            String displayName,
            String topic,
            String area,
            boolean live,
            String prerequisiteRaw,
            List<String> prerequisites,
            boolean duplicateExternalId
    ) {
        return Map.of(
                "recordNumber", recordNumber,
                "externalId", externalId,
                "displayName", displayName,
                "topic", topic,
                "area", area,
                "live", live,
                "prerequisiteRaw", prerequisiteRaw,
                "prerequisites", prerequisites,
                "duplicateExternalId", duplicateExternalId
        );
    }
}
