package com.smartlearning.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo-seed")
public record DemoSeedProperties(boolean enabled) {
}
