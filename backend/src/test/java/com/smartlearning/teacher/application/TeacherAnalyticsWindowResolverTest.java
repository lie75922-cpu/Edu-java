package com.smartlearning.teacher.application;

import com.smartlearning.common.exception.BadRequestException;
import com.smartlearning.teacher.api.TeacherAnalyticsApi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeacherAnalyticsWindowResolverTest {

    private final Instant now = Instant.parse("2026-09-08T00:00:00Z");
    private final TeacherAnalyticsWindowResolver resolver = new TeacherAnalyticsWindowResolver(
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void defaultsToThePreviousThirtyDaysAndReturnsTheResolvedWindow() {
        TeacherAnalyticsApi.ResolvedWindow window = resolver.resolve(null, null);

        assertThat(window.from()).isEqualTo(Instant.parse("2026-08-09T00:00:00Z"));
        assertThat(window.to()).isEqualTo(now);
    }

    @Test
    void rejectsPartialOrOversizedWindows() {
        assertThatThrownBy(() -> resolver.resolve(now.minusSeconds(60), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("supplied together");
        assertThatThrownBy(() -> resolver.resolve(now.minusSeconds(366L * 24 * 60 * 60), now))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("365 days");
    }

    @Test
    void rejectsNonIncreasingWindows() {
        assertThatThrownBy(() -> resolver.resolve(now, now))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("before");
    }
}
