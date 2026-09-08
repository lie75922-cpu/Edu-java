package com.smartlearning.mastery.application;

import com.smartlearning.mastery.domain.MasteryProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedMasteryProviderTest {

    private final RuleBasedMasteryProvider provider = new RuleBasedMasteryProvider();

    @Test
    void firstCorrectIsTwoThirdsUnderFixedBetaOneOneRule() {
        MasteryProvider.MasteryUpdate update = provider.update(empty(), true, Instant.parse("2026-09-08T00:00:00Z"));

        assertThat(update.attemptCount()).isEqualTo(1);
        assertThat(update.correctCount()).isEqualTo(1);
        assertThat(update.masteryScore()).isEqualByComparingTo("0.6667");
        assertThat(update.algorithmVersion()).isEqualTo("RULE_BETA_1_1_V1");
        assertThat(update.sourceType()).isEqualTo("RULE");
    }

    @Test
    void firstWrongIsOneThirdUnderFixedBetaOneOneRule() {
        MasteryProvider.MasteryUpdate update = provider.update(empty(), false, Instant.parse("2026-09-08T00:00:00Z"));

        assertThat(update.attemptCount()).isEqualTo(1);
        assertThat(update.correctCount()).isZero();
        assertThat(update.masteryScore()).isEqualByComparingTo("0.3333");
    }

    @Test
    void repeatedUpdatesUseActualAccumulatedCounts() {
        MasteryProvider.MasteryUpdate first = provider.update(empty(), true, Instant.parse("2026-09-08T00:00:00Z"));
        MasteryProvider.MasteryUpdate second = provider.update(
                new MasteryProvider.CurrentMastery(
                        first.attemptCount(), first.correctCount(), first.masteryScore(), first.lastAnsweredAt()
                ),
                false,
                Instant.parse("2026-09-08T00:01:00Z")
        );

        assertThat(second.attemptCount()).isEqualTo(2);
        assertThat(second.correctCount()).isEqualTo(1);
        assertThat(second.masteryScore()).isEqualByComparingTo("0.5000");
    }

    private MasteryProvider.CurrentMastery empty() {
        return new MasteryProvider.CurrentMastery(0, 0, null, null);
    }
}
