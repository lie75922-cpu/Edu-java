package com.smartlearning.catalog.application;

import com.smartlearning.catalog.api.SeedImportApi;
import com.smartlearning.graph.api.GraphApi;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict reader for the frozen Foundation business_export contract. It is not an HTTP file-path
 * endpoint: an administrator supplies the resulting request objects to the existing audited import
 * APIs. This keeps local filesystem authority outside the product API surface.
 */
@Component
public class FoundationBusinessExportReader {

    private final ObjectMapper objectMapper;

    public FoundationBusinessExportReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FoundationBusinessExport read(Path exportDirectory, String courseCode, String courseName) {
        try {
            JsonNode manifest = objectMapper.readTree(Files.readString(requiredFile(exportDirectory, "manifest.json")));
            JsonNode input = manifest.path("input");
            SeedImportApi.SeedImportMetadata metadata = new SeedImportApi.SeedImportMetadata(
                    requiredText(manifest, "export_format_version"),
                    requiredText(input, "metadata_path"),
                    nullableLong(input, "size_bytes"),
                    requiredText(input, "encoding"),
                    stringList(input.path("columns")),
                    nullableInt(input, "row_count")
            );
            Map<String, String> topicByExercise = new HashMap<>();
            for (JsonNode mapping : jsonLines(exportDirectory, "exercise_topic_mapping.jsonl")) {
                topicByExercise.put(requiredText(mapping, "exercise_external_id"), nullableText(mapping, "topic_external_id"));
            }

            List<SeedImportApi.SeedArea> areas = new ArrayList<>();
            for (JsonNode area : jsonLines(exportDirectory, "areas.jsonl")) {
                areas.add(new SeedImportApi.SeedArea(
                        requiredText(area, "area_external_id"),
                        requiredText(area, "display_name_zh"),
                        requiredText(area, "raw_area"),
                        requiredText(area, "display_name_zh"),
                        requiredText(area, "display_mapping_status"),
                        requiredText(area, "business_mapping_status"),
                        objectMap(area.path("provenance"))
                ));
            }

            List<SeedImportApi.SeedTopic> topics = new ArrayList<>();
            for (JsonNode topic : jsonLines(exportDirectory, "topics.jsonl")) {
                topics.add(new SeedImportApi.SeedTopic(
                        requiredText(topic, "topic_external_id"),
                        requiredText(topic, "display_name_zh"),
                        nullableText(topic, "area_external_id"),
                        requiredText(topic, "raw_topic"),
                        requiredText(topic, "display_name_zh"),
                        requiredText(topic, "display_mapping_status"),
                        requiredText(topic, "business_mapping_status"),
                        objectMap(topic.path("provenance"))
                ));
            }

            List<SeedImportApi.SeedExercise> exercises = new ArrayList<>();
            for (JsonNode exercise : jsonLines(exportDirectory, "exercises.jsonl")) {
                String externalId = requiredText(exercise, "exercise_external_id");
                Map<String, Object> rawSourceFields = objectMap(exercise.path("raw_source_fields"));
                String rawName = stringValue(rawSourceFields.get("name"), externalId);
                String displayName = nullableText(exercise, "display_name_zh");
                exercises.add(new SeedImportApi.SeedExercise(
                        externalId,
                        stringValue(displayName, rawName),
                        topicByExercise.get(externalId),
                        decimalOrNull(rawSourceFields.get("difficulty")),
                        rawName,
                        stringValue(displayName, rawName),
                        requiredText(exercise, "display_mapping_status"),
                        requiredText(exercise, "business_mapping_status"),
                        nullableInt(exercise, "source_metadata_row_number"),
                        rawSourceFields,
                        objectMap(exercise.path("provenance"))
                ));
            }

            List<GraphApi.RawPrerequisiteEvidenceRequest> rawEvidence = new ArrayList<>();
            for (JsonNode evidence : jsonLines(exportDirectory, "prerequisite_raw_evidence.jsonl")) {
                rawEvidence.add(new GraphApi.RawPrerequisiteEvidenceRequest(
                        requiredText(evidence, "evidence_id"),
                        requiredText(evidence, "prerequisite_exercise_external_id"),
                        requiredText(evidence, "dependent_exercise_external_id"),
                        objectMap(evidence)
                ));
            }

            List<GraphApi.CandidateTopicRelationRequest> candidates = new ArrayList<>();
            for (JsonNode candidate : jsonLines(exportDirectory, "prerequisite_topic_candidates.jsonl")) {
                candidates.add(new GraphApi.CandidateTopicRelationRequest(
                        requiredText(candidate, "candidate_id"),
                        requiredText(candidate, "prerequisite_topic_external_id"),
                        requiredText(candidate, "dependent_topic_external_id"),
                        requiredText(candidate, "derivation_policy_version"),
                        requiredText(candidate, "candidate_status"),
                        requiredText(candidate, "published_graph_status"),
                        stringList(candidate.path("raw_evidence_ids"))
                ));
            }
            return new FoundationBusinessExport(
                    new SeedImportApi.SeedImportRequest(courseCode, courseName, areas, topics, exercises, metadata),
                    new GraphApi.EvidenceImportRequest(rawEvidence),
                    new GraphApi.CandidateRelationImportRequest(candidates)
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read the Foundation business export", exception);
        }
    }

    private List<JsonNode> jsonLines(Path exportDirectory, String fileName) throws IOException {
        List<JsonNode> nodes = new ArrayList<>();
        for (String line : Files.readAllLines(requiredFile(exportDirectory, fileName), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                nodes.add(objectMapper.readTree(line));
            }
        }
        return List.copyOf(nodes);
    }

    private Path requiredFile(Path exportDirectory, String fileName) {
        Path file = exportDirectory.resolve(fileName).normalize();
        if (!file.startsWith(exportDirectory.normalize()) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Foundation export is missing required artifact: " + fileName);
        }
        return file;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(objectMapper.convertValue(node, Map.class)));
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode entry : node) {
                values.add(entry.asText());
            }
        }
        return List.copyOf(values);
    }

    private String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Foundation export has a missing required field: " + field);
        }
        return value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    private BigDecimal decimalOrNull(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record FoundationBusinessExport(
            SeedImportApi.SeedImportRequest catalogRequest,
            GraphApi.EvidenceImportRequest rawEvidenceRequest,
            GraphApi.CandidateRelationImportRequest candidateRequest
    ) {
    }
}
