package com.smartlearning.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.release")
public record ReleaseProperties(
        boolean mode,
        String buildVersion,
        String buildTime
) {
}
