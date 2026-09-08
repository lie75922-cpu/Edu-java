package com.smartlearning.infrastructure.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/** Redis is an optional cache; a failure is visible without disguising it as a critical database outage. */
@Component("redisRelease")
public class RedisReleaseHealthIndicator implements HealthIndicator {

    private static final Status DEGRADED = new Status("DEGRADED");
    private final RedisConnectionFactory connectionFactory;

    public RedisReleaseHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.ping();
            return Health.up().withDetail("dependency", "redis").withDetail("critical", false).build();
        } catch (Exception ignored) {
            return Health.status(DEGRADED).withDetail("dependency", "redis").withDetail("critical", false).build();
        }
    }
}
