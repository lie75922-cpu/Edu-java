package com.smartlearning.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SeedImportApi {

    private SeedImportApi() {
    }

    public record SeedArea(
            @NotBlank @Size(max = 128) String externalId,
            @NotBlank @Size(max = 128) String areaName,
            @Size(max = 128) String rawArea,
            @Size(max = 128) String displayNameZh,
            @Size(max = 32) String displayMappingStatus,
            @Size(max = 64) String businessMappingStatus,
            Map<String, Object> provenance
    ) {
        public SeedArea {
            rawArea = blankToDefault(rawArea, areaName);
            displayNameZh = blankToDefault(displayNameZh, areaName);
            displayMappingStatus = blankToDefault(displayMappingStatus, "UNREVIEWED");
            businessMappingStatus = blankToDefault(businessMappingStatus, "ELIGIBLE_FOR_IMPORT");
            provenance = immutableMap(provenance);
        }

        public SeedArea(String externalId, String areaName) {
            this(externalId, areaName, areaName, areaName, "UNREVIEWED", "ELIGIBLE_FOR_IMPORT", Map.of());
        }
    }

    public record SeedTopic(
            @NotBlank @Size(max = 128) String externalId,
            @NotBlank @Size(max = 128) String topicName,
            @Size(max = 128) String areaExternalId,
            @Size(max = 128) String rawTopic,
            @Size(max = 128) String displayNameZh,
            @Size(max = 32) String displayMappingStatus,
            @Size(max = 64) String businessMappingStatus,
            Map<String, Object> provenance
    ) {
        public SeedTopic {
            rawTopic = blankToDefault(rawTopic, topicName);
            displayNameZh = blankToDefault(displayNameZh, topicName);
            displayMappingStatus = blankToDefault(displayMappingStatus, "UNREVIEWED");
            businessMappingStatus = blankToDefault(businessMappingStatus, "ELIGIBLE_FOR_IMPORT");
            provenance = immutableMap(provenance);
        }

        public SeedTopic(String externalId, String topicName, String areaExternalId) {
            this(externalId, topicName, areaExternalId, topicName, topicName,
                    "UNREVIEWED", "ELIGIBLE_FOR_IMPORT", Map.of());
        }
    }

    public record SeedExercise(
            @NotBlank @Size(max = 128) String externalId,
            @NotBlank @Size(max = 255) String exerciseName,
            @Size(max = 128) String topicExternalId,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal difficulty,
            @Size(max = 255) String rawExerciseName,
            @Size(max = 255) String displayNameZh,
            @Size(max = 32) String displayMappingStatus,
            @Size(max = 64) String businessMappingStatus,
            Integer sourceMetadataRowNumber,
            Map<String, Object> rawSourceFields,
            Map<String, Object> provenance
    ) {
        public SeedExercise {
            rawExerciseName = blankToDefault(rawExerciseName, exerciseName);
            displayNameZh = blankToDefault(displayNameZh, exerciseName);
            displayMappingStatus = blankToDefault(displayMappingStatus, "UNREVIEWED");
            businessMappingStatus = blankToDefault(businessMappingStatus, "ELIGIBLE_FOR_IMPORT");
            rawSourceFields = immutableMap(rawSourceFields);
            provenance = immutableMap(provenance);
        }

        public SeedExercise(String externalId, String exerciseName, String topicExternalId, BigDecimal difficulty) {
            this(externalId, exerciseName, topicExternalId, difficulty, exerciseName, exerciseName,
                    "UNREVIEWED", "ELIGIBLE_FOR_IMPORT", null, Map.of(), Map.of());
        }
    }

    /**
     * Explicit input identity replaces content digests: the format version, local source metadata and
     * declared input structure are retained with every import run for a human-auditable replay boundary.
     */
    public record SeedImportMetadata(
            @NotBlank @Size(max = 32) String exportFormatVersion,
            @NotBlank @Size(max = 1_024) String inputPath,
            Long inputSizeBytes,
            @NotBlank @Size(max = 32) String inputEncoding,
            @Size(max = 128) List<@NotBlank @Size(max = 128) String> inputColumns,
            Integer sourceRecordCount
    ) {
        public SeedImportMetadata {
            inputColumns = inputColumns == null ? List.of() : List.copyOf(inputColumns);
        }

        public static SeedImportMetadata legacy() {
            return new SeedImportMetadata("legacy-seed-v1", "not-recorded", null, "not-recorded", List.of(), null);
        }
    }

    public record SeedImportRequest(
            @NotBlank @Size(max = 64) String courseCode,
            @NotBlank @Size(max = 128) String courseName,
            @Size(max = 4_000) List<@Valid SeedArea> areas,
            @Size(max = 4_000) List<@Valid SeedTopic> topics,
            @Size(max = 8_000) List<@Valid SeedExercise> exercises,
            @Valid SeedImportMetadata metadata
    ) {
        public SeedImportRequest {
            areas = areas == null ? List.of() : List.copyOf(areas);
            topics = topics == null ? List.of() : List.copyOf(topics);
            exercises = exercises == null ? List.of() : List.copyOf(exercises);
            metadata = metadata == null ? SeedImportMetadata.legacy() : metadata;
        }

        public SeedImportRequest(
                String courseCode,
                String courseName,
                List<SeedArea> areas,
                List<SeedTopic> topics,
                List<SeedExercise> exercises
        ) {
            this(courseCode, courseName, areas, topics, exercises, SeedImportMetadata.legacy());
        }
    }

    public record SeedConflictResponse(
            Long id,
            String entityType,
            String externalId,
            String conflictType,
            String detailJson,
            Instant createdAt
    ) {
    }

    public record SeedImportResult(
            Long importRunId,
            String mode,
            String status,
            int createdCourses,
            int createdAreas,
            int reusedAreas,
            int updatedAreas,
            int createdKnowledgePoints,
            int reusedKnowledgePoints,
            int updatedKnowledgePoints,
            int createdExerciseUnits,
            int reusedExerciseUnits,
            int updatedExerciseUnits,
            int createdMappings,
            int unmappedExercises,
            int quarantinedRecords,
            int createdProvenanceRecords,
            int updatedDisplayRecords,
            int conflictCount,
            SeedImportMetadata metadata,
            List<SeedConflictResponse> conflicts
    ) {
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        return value == null || value.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
