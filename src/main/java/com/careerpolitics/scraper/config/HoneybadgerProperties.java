package com.careerpolitics.scraper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "careerpolitics.observability.honeybadger")
public record HoneybadgerProperties(
        boolean enabled,
        String apiKey,
        String endpoint,
        String environment,
        String serviceName,
        Duration timeout,
        Logging logging
) {

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public record Logging(
            boolean enabled,
            String minimumLevel
    ) {
    }
}
