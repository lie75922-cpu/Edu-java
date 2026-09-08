package com.smartlearning.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Rejects placeholder or known development credentials only for release-like startup.
 * Values are intentionally never included in exceptions or logs.
 */
@Component
public class ReleaseConfigurationValidator {

    private final ReleaseProperties releaseProperties;
    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;
    private final String mysqlPassword;
    private final String neo4jPassword;
    private final String mysqlRootPassword;

    public ReleaseConfigurationValidator(
            ReleaseProperties releaseProperties,
            JwtProperties jwtProperties,
            CorsProperties corsProperties,
            @Value("${spring.datasource.password:}") String mysqlPassword,
            @Value("${spring.neo4j.authentication.password:}") String neo4jPassword,
            @Value("${release.mysql-root-password:}") String mysqlRootPassword
    ) {
        this.releaseProperties = releaseProperties;
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        this.mysqlPassword = mysqlPassword;
        this.neo4jPassword = neo4jPassword;
        this.mysqlRootPassword = mysqlRootPassword;
    }

    @PostConstruct
    void validateReleaseConfiguration() {
        if (!releaseProperties.mode()) {
            return;
        }
        requirePrivateValue("JWT_SECRET", jwtProperties.secret(), 32);
        requirePrivateValue("MYSQL_PASSWORD", mysqlPassword, 12);
        requirePrivateValue("MYSQL_ROOT_PASSWORD", mysqlRootPassword, 12);
        requirePrivateValue("NEO4J_PASSWORD", neo4jPassword, 12);
        if (corsProperties.allowedOrigins() != null && corsProperties.allowedOrigins().contains("*")) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must not contain a wildcard in release mode");
        }
    }

    private void requirePrivateValue(String variable, String value, int minimumLength) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < minimumLength || isPlaceholderOrDevelopmentValue(normalized)) {
            throw new IllegalStateException(variable + " must be a non-placeholder private value in release mode");
        }
    }

    private boolean isPlaceholderOrDevelopmentValue(String normalized) {
        return normalized.startsWith("<")
                || normalized.contains("required-private")
                || normalized.contains("replace_with")
                || normalized.contains("change_me")
                || normalized.contains("dev_password")
                || normalized.contains("root_dev")
                || normalized.contains("neo4j_dev")
                || normalized.equals("password")
                || normalized.equals("secret");
    }
}
