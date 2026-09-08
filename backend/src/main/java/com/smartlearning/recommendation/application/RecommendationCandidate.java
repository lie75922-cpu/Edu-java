package com.smartlearning.recommendation.application;

import com.smartlearning.mastery.application.MasteryReadService;

import java.util.List;

public record RecommendationCandidate(
        long graphVersionId,
        long knowledgePointId,
        String knowledgeCode,
        String knowledgeName,
        MasteryReadService.MasteryState mastery,
        boolean unmetPrerequisite,
        int recentErrorCount,
        List<Long> prerequisiteForKnowledgePointIds
) {
    public RecommendationCandidate {
        prerequisiteForKnowledgePointIds = List.copyOf(prerequisiteForKnowledgePointIds);
    }
}
