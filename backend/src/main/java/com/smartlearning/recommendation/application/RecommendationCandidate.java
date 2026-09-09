package com.smartlearning.recommendation.application;

import com.smartlearning.mastery.application.MasteryReadService;
import com.smartlearning.recommendation.domain.RecommendationReasonCode;

import java.util.List;

public record RecommendationCandidate(
        Long graphVersionId,
        long knowledgePointId,
        String knowledgeCode,
        String knowledgeName,
        MasteryReadService.MasteryState mastery,
        boolean unmetPrerequisite,
        int recentErrorCount,
        List<Long> prerequisiteForKnowledgePointIds,
        RecommendationReasonCode defaultReason
) {
    public RecommendationCandidate {
        prerequisiteForKnowledgePointIds = List.copyOf(prerequisiteForKnowledgePointIds);
        defaultReason = defaultReason == null ? RecommendationReasonCode.TARGET_PRACTICE : defaultReason;
    }

    public RecommendationCandidate(
            Long graphVersionId,
            long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            MasteryReadService.MasteryState mastery,
            boolean unmetPrerequisite,
            int recentErrorCount,
            List<Long> prerequisiteForKnowledgePointIds
    ) {
        this(
                graphVersionId,
                knowledgePointId,
                knowledgeCode,
                knowledgeName,
                mastery,
                unmetPrerequisite,
                recentErrorCount,
                prerequisiteForKnowledgePointIds,
                RecommendationReasonCode.TARGET_PRACTICE
        );
    }
}
