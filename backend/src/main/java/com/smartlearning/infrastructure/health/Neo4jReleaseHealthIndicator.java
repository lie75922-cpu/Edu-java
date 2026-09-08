package com.smartlearning.infrastructure.health;

import org.neo4j.driver.Driver;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Neo4j is a required Published Graph projection dependency for release readiness. */
@Component("neo4jRelease")
public class Neo4jReleaseHealthIndicator implements HealthIndicator {

    private final Driver driver;

    public Neo4jReleaseHealthIndicator(Driver driver) {
        this.driver = driver;
    }

    @Override
    public Health health() {
        try {
            driver.verifyConnectivity();
            return Health.up().withDetail("dependency", "neo4j").build();
        } catch (Exception ignored) {
            return Health.down().withDetail("dependency", "neo4j").build();
        }
    }
}
