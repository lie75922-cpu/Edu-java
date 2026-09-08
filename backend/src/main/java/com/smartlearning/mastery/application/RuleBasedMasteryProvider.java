package com.smartlearning.mastery.application;

import com.smartlearning.mastery.domain.MasteryProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Transparent Beta(1,1) rule fallback; this is intentionally not a cognitive-diagnosis model. */
@Component
public class RuleBasedMasteryProvider implements MasteryProvider {

    public static final String ALGORITHM_VERSION = "RULE_BETA_1_1_V1";
    private static final String SOURCE_TYPE = "RULE";

    @Override
    public String algorithmVersion() {
        return ALGORITHM_VERSION;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public MasteryUpdate update(CurrentMastery current, boolean correct, Instant answeredAt) {
        int nextAttemptCount = current.attemptCount() + 1;
        int nextCorrectCount = current.correctCount() + (correct ? 1 : 0);
        BigDecimal mastery = BigDecimal.valueOf(nextCorrectCount + 1L)
                .divide(BigDecimal.valueOf(nextAttemptCount + 2L), 4, RoundingMode.HALF_UP);
        return new MasteryUpdate(
                nextAttemptCount,
                nextCorrectCount,
                mastery,
                answeredAt,
                algorithmVersion(),
                sourceType()
        );
    }
}
