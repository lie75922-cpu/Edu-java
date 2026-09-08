package com.smartlearning.recommendation.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RecommendationApi {

    private RecommendationApi() {
    }

    public record RecommendationItemResponse(
            Long id,
            int rank,
            Long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            Long exerciseUnitId,
            String exerciseCode,
            String exerciseName,
            BigDecimal masteryScore,
            String masteryStatus,
            String reasonCode,
            String explanationJson
    ) {
    }

    public record RecommendationSnapshotResponse(
            Long id,
            Long studentId,
            Long courseId,
            Long graphVersionId,
            String masteryAlgorithmVersion,
            String recommendationRuleVersion,
            Instant generatedAt,
            List<RecommendationItemResponse> items
    ) {
    }

    public record LearningPathNodeResponse(
            int order,
            Long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            String masteryStatus,
            BigDecimal masteryScore,
            String reasonCode,
            Long exerciseUnitId,
            String exerciseCode,
            String exerciseName,
            boolean hasAvailableExercise
    ) {
    }

    public record LearningPathResponse(
            Long targetKnowledgePointId,
            Long courseId,
            Long graphVersionId,
            List<LearningPathNodeResponse> nodes
    ) {
    }
}
