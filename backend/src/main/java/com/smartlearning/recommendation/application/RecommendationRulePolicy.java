package com.smartlearning.recommendation.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

/** Central, versioned recommendation policy; no per-student or per-fixture exceptions are allowed. */
@Component
public class RecommendationRulePolicy {

    public static final String RULE_VERSION = "REC_RULE_V1";
    public static final BigDecimal WEAK_MASTERY_THRESHOLD = new BigDecimal("0.70");
    private static final Duration RECENT_ERROR_WINDOW = Duration.ofDays(30);
    private static final Duration REVIEW_DUE_AFTER = Duration.ofDays(7);
    private static final int MAX_RECOMMENDATIONS = 5;

    public String ruleVersion() {
        return RULE_VERSION;
    }

    public BigDecimal weakMasteryThreshold() {
        return WEAK_MASTERY_THRESHOLD;
    }

    public Duration recentErrorWindow() {
        return RECENT_ERROR_WINDOW;
    }

    public Duration reviewDueAfter() {
        return REVIEW_DUE_AFTER;
    }

    public int maxRecommendations() {
        return MAX_RECOMMENDATIONS;
    }
}
