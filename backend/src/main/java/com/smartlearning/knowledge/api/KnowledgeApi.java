package com.smartlearning.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class KnowledgeApi {

    private KnowledgeApi() {
    }

    public record KnowledgeAreaRequest(
            @NotNull Long courseId,
            @NotBlank @Size(max = 96) String areaCode,
            @NotBlank @Size(max = 128) String areaName,
            @NotBlank @Size(max = 32) String sourceType,
            @Size(max = 128) String externalId,
            @NotBlank @Size(max = 20) String status
    ) {
    }

    public record KnowledgePointRequest(
            @NotNull Long courseId,
            Long areaId,
            @NotBlank @Size(max = 96) String knowledgeCode,
            @NotBlank @Size(max = 128) String knowledgeName,
            @NotBlank @Size(max = 32) String sourceType,
            @Size(max = 128) String externalId,
            @NotBlank @Size(max = 20) String mappingStatus,
            @NotBlank @Size(max = 20) String status
    ) {
    }

    public record KnowledgeAreaResponse(
            Long id,
            Long courseId,
            String areaCode,
            String areaName,
            String sourceType,
            String externalId,
            String status
    ) {
    }

    public record KnowledgePointResponse(
            Long id,
            Long courseId,
            Long areaId,
            String knowledgeCode,
            String knowledgeName,
            String sourceType,
            String externalId,
            String mappingStatus,
            String status
    ) {
    }
}
