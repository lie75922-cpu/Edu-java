package com.smartlearning.teacher.application;

import com.smartlearning.common.exception.BadRequestException;
import com.smartlearning.teacher.api.TeacherAnalyticsApi;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class TeacherAnalyticsWindowResolver {

    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);
    private static final Duration MAXIMUM_WINDOW = Duration.ofDays(365);

    private final Clock clock;

    public TeacherAnalyticsWindowResolver() {
        this(Clock.systemUTC());
    }

    public TeacherAnalyticsWindowResolver(Clock clock) {
        this.clock = clock;
    }

    public TeacherAnalyticsApi.ResolvedWindow resolve(Instant from, Instant to) {
        if ((from == null) != (to == null)) {
            throw new BadRequestException("from and to must be supplied together");
        }
        Instant resolvedTo = to == null ? clock.instant() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(DEFAULT_WINDOW) : from;
        if (!resolvedFrom.isBefore(resolvedTo)) {
            throw new BadRequestException("from must be before to");
        }
        if (Duration.between(resolvedFrom, resolvedTo).compareTo(MAXIMUM_WINDOW) > 0) {
            throw new BadRequestException("analytics window must not exceed 365 days");
        }
        return new TeacherAnalyticsApi.ResolvedWindow(resolvedFrom, resolvedTo);
    }
}
