package com.smartlearning.assessment.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class ExerciseApi {

    private ExerciseApi() {
    }

    public record ExerciseUnitRequest(
            @NotNull Long courseId,
            @NotBlank @Size(max = 96) String exerciseCode,
            @NotBlank @Size(max = 255) String exerciseName,
            @NotBlank @Size(max = 32) String sourceType,
            @Size(max = 128) String externalId,
            @NotBlank @Size(max = 32) String identityStatus,
            @NotBlank @Size(max = 20) String mappingStatus,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal difficulty,
            @NotBlank @Size(max = 20) String status
    ) {
    }

    public record MappingRequest(
            @NotNull Long knowledgePointId,
            @NotBlank @Size(max = 32) String mappingSource,
            @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal confidence,
            boolean verified
    ) {
    }

    public record KnowledgeMappingResponse(
            Long id,
            Long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            String mappingSource,
            BigDecimal confidence,
            boolean verified
    ) {
    }

    public record ExerciseUnitResponse(
            Long id,
            Long courseId,
            String exerciseCode,
            String exerciseName,
            String sourceType,
            String externalId,
            String identityStatus,
            String mappingStatus,
            BigDecimal difficulty,
            String status,
            List<KnowledgeMappingResponse> knowledgePoints
    ) {
    }
}
