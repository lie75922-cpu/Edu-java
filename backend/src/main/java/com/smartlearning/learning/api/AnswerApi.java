package com.smartlearning.learning.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AnswerApi {

    private AnswerApi() {
    }

    public record AnswerSubmissionRequest(
            @NotEmpty List<@NotBlank @Size(max = 16) String> selectedOptionKeys,
            @Min(0) Long durationMs,
            @NotBlank @Size(max = 128) String clientRequestId
    ) {
    }

    public record AnswerResultResponse(
            Long answerRecordId,
            Long questionId,
            boolean correct,
            int attemptNo,
            List<String> correctOptionKeys,
            String explanation,
            boolean idempotentReplay
    ) {
    }

    public record AnswerHistoryResponse(
            Long answerRecordId,
            Long courseId,
            Long questionId,
            Long exerciseUnitId,
            boolean correct,
            int attemptNo,
            Long durationMs,
            Instant answeredAt
    ) {
    }
}
