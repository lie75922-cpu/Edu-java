package com.smartlearning.mastery.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MasteryApi {

    private MasteryApi() {
    }

    public record MasteryResponse(
            Long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            String status,
            BigDecimal masteryScore,
            int attemptCount,
            int correctCount,
            String sourceType,
            String algorithmVersion,
            Instant lastAnsweredAt,
            Instant updatedAt
    ) {
    }

    public record MasteryHistoryResponse(
            Long historyId,
            Long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            Long answerRecordId,
            int previousAttemptCount,
            int previousCorrectCount,
            BigDecimal previousScore,
            int newAttemptCount,
            int newCorrectCount,
            BigDecimal newScore,
            String algorithmVersion,
            Instant createdAt
    ) {
    }

    public record CourseMasteryResponse(Long courseId, List<MasteryResponse> items) {
    }
}
