package com.smartlearning.recommendation.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class RecommendationRanker {

    public List<RankedRecommendation> rank(List<RecommendationFilter.PracticeCandidate> candidates) {
        Comparator<RecommendationFilter.PracticeCandidate> ordering = Comparator
                .comparing((RecommendationFilter.PracticeCandidate item) -> !item.candidate().unmetPrerequisite())
                .thenComparing(item -> masterySortValue(item.candidate()))
                .thenComparing(Comparator.comparingInt((RecommendationFilter.PracticeCandidate item) ->
                        item.candidate().recentErrorCount()).reversed())
                .thenComparing(item -> lastPracticeSortValue(item.candidate()))
                .thenComparingLong(item -> item.candidate().knowledgePointId())
                .thenComparing(RecommendationFilter.PracticeCandidate::exerciseUnitId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
        List<RecommendationFilter.PracticeCandidate> sorted = candidates.stream().sorted(ordering).toList();
        java.util.ArrayList<RankedRecommendation> ranked = new java.util.ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            ranked.add(new RankedRecommendation(index + 1, sorted.get(index)));
        }
        return List.copyOf(ranked);
    }

    private BigDecimal masterySortValue(RecommendationCandidate candidate) {
        return candidate.mastery().masteryScore() == null ? BigDecimal.ONE : candidate.mastery().masteryScore();
    }

    private Instant lastPracticeSortValue(RecommendationCandidate candidate) {
        return candidate.mastery().lastAnsweredAt() == null ? Instant.EPOCH : candidate.mastery().lastAnsweredAt();
    }

    public record RankedRecommendation(int rank, RecommendationFilter.PracticeCandidate practice) {
    }
}
