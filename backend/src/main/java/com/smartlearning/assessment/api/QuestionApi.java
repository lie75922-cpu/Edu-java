package com.smartlearning.assessment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class QuestionApi {

    private QuestionApi() {
    }

    public record OptionRequest(
            @NotBlank @Size(max = 16) String optionKey,
            @NotBlank String optionText,
            @NotNull Integer sortOrder
    ) {
    }

    public record QuestionRequest(
            @NotNull Long exerciseUnitId,
            @NotBlank @Size(max = 32) String questionType,
            @NotBlank String stem,
            @NotEmpty List<@NotBlank @Size(max = 16) String> answerOptionKeys,
            String explanation,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal difficulty,
            @NotBlank @Size(max = 20) String status,
            @NotEmpty List<@Valid OptionRequest> options
    ) {
    }

    /**
     * Transactional batch creation for teacher-owned/licensed content.
     * The batch is intentionally bounded so that a bad payload cannot become an unreviewable bulk write.
     */
    public record QuestionBatchRequest(
            @NotEmpty @Size(max = 200) List<@Valid QuestionRequest> questions
    ) {
        public QuestionBatchRequest {
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }

    public record OptionResponse(Long id, String optionKey, String optionText, int sortOrder) {
    }

    public record QuestionResponse(
            Long id,
            Long exerciseUnitId,
            String questionType,
            String stem,
            BigDecimal difficulty,
            List<OptionResponse> options
    ) {
    }

    public record AdminQuestionResponse(
            Long id,
            Long exerciseUnitId,
            String questionType,
            String stem,
            List<String> answerOptionKeys,
            String explanation,
            BigDecimal difficulty,
            String status,
            List<OptionResponse> options
    ) {
    }

    public record QuestionBatchResponse(
            int createdCount,
            List<AdminQuestionResponse> items
    ) {
        public QuestionBatchResponse {
            items = List.copyOf(items);
        }
    }
}
