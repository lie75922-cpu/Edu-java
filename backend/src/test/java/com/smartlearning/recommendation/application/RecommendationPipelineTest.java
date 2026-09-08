package com.smartlearning.recommendation.application;

import com.smartlearning.mastery.application.MasteryReadService;
import com.smartlearning.mastery.domain.MasteryStatus;
import com.smartlearning.recommendation.domain.RecommendationReasonCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationPipelineTest {

    private final RecommendationRanker ranker = new RecommendationRanker();

    @Test
    void unmetPrerequisiteOutranksLowerMasteryNonPrerequisite() {
        RecommendationFilter.PracticeCandidate prerequisite = practice(2L, 20L, "0.6667", true, 0, "2026-09-01T00:00:00Z");
        RecommendationFilter.PracticeCandidate lowerMastery = practice(1L, 10L, "0.1000", false, 5, "2026-08-01T00:00:00Z");

        List<RecommendationRanker.RankedRecommendation> ranked = ranker.rank(List.of(lowerMastery, prerequisite));

        assertThat(ranked).extracting(item -> item.practice().candidate().knowledgePointId()).containsExactly(2L, 1L);
    }

    @Test
    void lowerMasteryThenRecentErrorsThenStableBusinessIdsDetermineOrder() {
        RecommendationFilter.PracticeCandidate lower = practice(3L, 30L, "0.3000", false, 0, "2026-09-01T00:00:00Z");
        RecommendationFilter.PracticeCandidate higher = practice(2L, 20L, "0.5000", false, 9, "2026-08-01T00:00:00Z");
        RecommendationFilter.PracticeCandidate stableSecond = practice(5L, 50L, "0.4000", false, 1, "2026-09-01T00:00:00Z");
        RecommendationFilter.PracticeCandidate stableFirst = practice(4L, 40L, "0.4000", false, 1, "2026-09-01T00:00:00Z");

        List<RecommendationRanker.RankedRecommendation> ranked = ranker.rank(List.of(higher, stableSecond, lower, stableFirst));

        assertThat(ranked).extracting(item -> item.practice().candidate().knowledgePointId())
                .containsExactly(3L, 4L, 5L, 2L);
    }

    @Test
    void explanationCarriesTheSameStructuredFactorsUsedForRanking() {
        RecommendationFilter.PracticeCandidate candidate = practice(7L, 70L, "0.3333", true, 2, "2026-09-01T00:00:00Z");
        RecommendationRanker.RankedRecommendation ranked = ranker.rank(List.of(candidate)).getFirst();
        RecommendationExplanationBuilder builder = new RecommendationExplanationBuilder(
                new RecommendationRulePolicy(), new ObjectMapper()
        );

        RecommendationExplanationBuilder.Explanation explanation = builder.build(ranked);

        assertThat(explanation.reasonCode()).isEqualTo(RecommendationReasonCode.UNMET_PREREQUISITE);
        assertThat(explanation.evidenceJson())
                .contains("\"rank\":1", "\"unmetPrerequisite\":true", "\"recentErrorCount\":2", "\"stableTieBreak\"");
    }

    private RecommendationFilter.PracticeCandidate practice(
            long knowledgePointId,
            long exerciseUnitId,
            String mastery,
            boolean unmetPrerequisite,
            int recentErrors,
            String lastAnsweredAt
    ) {
        MasteryReadService.MasteryState state = new MasteryReadService.MasteryState(
                MasteryStatus.OBSERVED,
                new BigDecimal(mastery),
                2,
                1,
                "RULE",
                "RULE_BETA_1_1_V1",
                Instant.parse(lastAnsweredAt),
                Instant.parse(lastAnsweredAt)
        );
        RecommendationCandidate candidate = new RecommendationCandidate(
                42L,
                knowledgePointId,
                "KP-" + knowledgePointId,
                "Point " + knowledgePointId,
                state,
                unmetPrerequisite,
                recentErrors,
                unmetPrerequisite ? List.of(99L) : List.of()
        );
        return new RecommendationFilter.PracticeCandidate(candidate, exerciseUnitId, "EX-" + exerciseUnitId, "Exercise " + exerciseUnitId);
    }
}
