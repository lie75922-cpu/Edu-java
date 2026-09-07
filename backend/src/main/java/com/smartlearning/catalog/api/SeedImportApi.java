package com.smartlearning.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SeedImportApi {

    private SeedImportApi() {
    }

    public record SeedArea(
            @NotBlank @Size(max = 128) String externalId,
            @NotBlank @Size(max = 128) String areaName
    ) {
    }

    public record SeedTopic(
            @NotBlank @Size(max = 128) String externalId,
            @NotBlank @Size(max = 128) String topicName,
            @Size(max = 128) String areaExternalId
    ) {
    }

    public record SeedExercise(
            @NotBlank @Size(max = 128) String externalId,
            @NotBlank @Size(max = 255) String exerciseName,
            @Size(max = 128) String topicExternalId,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal difficulty
    ) {
    }

    public record SeedImportRequest(
            @NotBlank @Size(max = 64) String courseCode,
            @NotBlank @Size(max = 128) String courseName,
            @Size(max = 4_000) List<@Valid SeedArea> areas,
            @Size(max = 4_000) List<@Valid SeedTopic> topics,
            @Size(max = 8_000) List<@Valid SeedExercise> exercises
    ) {
        public SeedImportRequest {
            areas = areas == null ? List.of() : List.copyOf(areas);
            topics = topics == null ? List.of() : List.copyOf(topics);
            exercises = exercises == null ? List.of() : List.copyOf(exercises);
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
            int createdKnowledgePoints,
            int reusedKnowledgePoints,
            int createdExerciseUnits,
            int reusedExerciseUnits,
            int createdMappings,
            int unmappedExercises,
            int conflictCount,
            List<SeedConflictResponse> conflicts
    ) {
    }
}
