package com.smartlearning.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(String username, String password, String nickname) {

    public boolean isConfigured() {
        return hasText(username) && hasText(password);
    }

    public boolean isPartiallyConfigured() {
        return hasText(username) != hasText(password);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
