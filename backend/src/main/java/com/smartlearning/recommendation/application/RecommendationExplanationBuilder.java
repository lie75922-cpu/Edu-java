package com.smartlearning.recommendation.application;

import com.smartlearning.recommendation.domain.RecommendationReasonCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RecommendationExplanationBuilder {

    private final RecommendationRulePolicy policy;
    private final ObjectMapper objectMapper;

    public RecommendationExplanationBuilder(RecommendationRulePolicy policy, ObjectMapper objectMapper) {
        this.policy = policy;
        this.objectMapper = objectMapper;
    }

    public Explanation build(RecommendationRanker.RankedRecommendation ranked) {
        RecommendationCandidate candidate = ranked.practice().candidate();
        RecommendationReasonCode reasonCode = reasonFor(candidate);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("ruleVersion", policy.ruleVersion());
        evidence.put("graphVersionId", candidate.graphVersionId());
        evidence.put("rank", ranked.rank());
        evidence.put("unmetPrerequisite", candidate.unmetPrerequisite());
        evidence.put("prerequisiteForKnowledgePointIds", candidate.prerequisiteForKnowledgePointIds());
        evidence.put("masteryStatus", candidate.mastery().status().name());
        evidence.put("masteryScore", candidate.mastery().masteryScore());
        evidence.put("attemptCount", candidate.mastery().attemptCount());
        evidence.put("correctCount", candidate.mastery().correctCount());
        evidence.put("recentErrorCount", candidate.recentErrorCount());
        evidence.put("lastAnsweredAt", candidate.mastery().lastAnsweredAt());
        evidence.put("stableTieBreak", Map.of(
                "knowledgePointId", candidate.knowledgePointId(),
                "exerciseUnitId", ranked.practice().exerciseUnitId()
        ));
        String message = messageFor(reasonCode, candidate);
        evidence.put("message", message);
        try {
            return new Explanation(reasonCode, message, objectMapper.writeValueAsString(evidence));
        } catch (Exception ex) {
            throw new IllegalStateException("unable to serialize recommendation explanation", ex);
        }
    }

    private RecommendationReasonCode reasonFor(RecommendationCandidate candidate) {
        if (candidate.unmetPrerequisite()) {
            return RecommendationReasonCode.UNMET_PREREQUISITE;
        }
        if (candidate.recentErrorCount() > 0) {
            return RecommendationReasonCode.RECENT_ERRORS;
        }
        if (candidate.mastery().masteryScore() != null
                && candidate.mastery().lastAnsweredAt() != null
                && candidate.mastery().lastAnsweredAt().isBefore(Instant.now().minus(policy.reviewDueAfter()))) {
            return RecommendationReasonCode.REVIEW_DUE;
        }
        if (candidate.mastery().masteryScore() != null) {
            return RecommendationReasonCode.LOW_MASTERY;
        }
        return RecommendationReasonCode.TARGET_PRACTICE;
    }

    private String messageFor(RecommendationReasonCode reasonCode, RecommendationCandidate candidate) {
        return switch (reasonCode) {
            case UNMET_PREREQUISITE -> candidate.knowledgeName() + " 是当前薄弱知识的未掌握前置知识，因此优先练习。";
            case RECENT_ERRORS -> candidate.knowledgeName() + " 近期错误次数为 " + candidate.recentErrorCount()
                    + "，当前规则掌握度为 " + candidate.mastery().masteryScore() + "。";
            case REVIEW_DUE -> candidate.knowledgeName() + " 当前规则掌握度为 " + candidate.mastery().masteryScore()
                    + "，且较久未练习，建议复习。";
            case LOW_MASTERY -> candidate.knowledgeName() + " 当前规则掌握度为 " + candidate.mastery().masteryScore() + "，低于 0.70。";
            case TARGET_PRACTICE -> candidate.knowledgeName() + " 可作为目标知识点练习。";
        };
    }

    public record Explanation(RecommendationReasonCode reasonCode, String message, String evidenceJson) {
    }
}
