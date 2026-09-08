package com.smartlearning.mastery.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The only mastery abstraction available to business code. Implementations may be rule based today
 * or model backed in a separately approved future phase.
 */
public interface MasteryProvider {

    String algorithmVersion();

    String sourceType();

    MasteryUpdate update(CurrentMastery current, boolean correct, Instant answeredAt);

    record CurrentMastery(int attemptCount, int correctCount, BigDecimal masteryScore, Instant lastAnsweredAt) {
    }

    record MasteryUpdate(
            int attemptCount,
            int correctCount,
            BigDecimal masteryScore,
            Instant lastAnsweredAt,
            String algorithmVersion,
            String sourceType
    ) {
    }
}
