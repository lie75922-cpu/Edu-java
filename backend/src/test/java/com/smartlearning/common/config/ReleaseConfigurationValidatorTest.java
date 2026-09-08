package com.smartlearning.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseConfigurationValidatorTest {

    @Test
    void releaseModeRejectsPlaceholderValuesWithoutEchoingThem() {
        ReleaseConfigurationValidator validator = validator(
                true,
                "<required-private-value-at-least-32-bytes>",
                "local_mysql_password_value",
                "local_neo4j_password_value",
                "local_mysql_root_value",
                "http://localhost:8081"
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, validator::validateReleaseConfiguration);

        org.junit.jupiter.api.Assertions.assertEquals(
                "JWT_SECRET must be a non-placeholder private value in release mode",
                exception.getMessage()
        );
    }

    @Test
    void releaseModeRejectsWildcardCors() {
        ReleaseConfigurationValidator validator = validator(
                true,
                "local_jwt_private_value_more_than_thirty_two_bytes",
                "local_mysql_password_value",
                "local_neo4j_password_value",
                "local_mysql_root_value",
                "http://localhost:8081,*"
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, validator::validateReleaseConfiguration);

        org.junit.jupiter.api.Assertions.assertEquals(
                "CORS_ALLOWED_ORIGINS must not contain a wildcard in release mode",
                exception.getMessage()
        );
    }

    @Test
    void releaseModeAcceptsPrivateNonPlaceholderConfiguration() {
        assertDoesNotThrow(() -> validator(
                true,
                "local_jwt_private_value_more_than_thirty_two_bytes",
                "local_mysql_password_value",
                "local_neo4j_password_value",
                "local_mysql_root_value",
                "http://localhost:8081"
        ).validateReleaseConfiguration());
    }

    @Test
    void nonReleaseModeDoesNotApplyReleaseOnlyRules() {
        assertDoesNotThrow(() -> validator(
                false,
                "dev_password",
                "dev_password",
                "neo4j_dev_password",
                "root_dev_password",
                "*"
        ).validateReleaseConfiguration());
    }

    private ReleaseConfigurationValidator validator(
            boolean releaseMode,
            String jwtSecret,
            String mysqlPassword,
            String neo4jPassword,
            String mysqlRootPassword,
            String allowedOrigins
    ) {
        return new ReleaseConfigurationValidator(
                new ReleaseProperties(releaseMode, "0.7.0-rc", "test"),
                new JwtProperties(jwtSecret, "https://edu-java.local", 3600),
                new CorsProperties(allowedOrigins),
                mysqlPassword,
                neo4jPassword,
                mysqlRootPassword
        );
    }
}
