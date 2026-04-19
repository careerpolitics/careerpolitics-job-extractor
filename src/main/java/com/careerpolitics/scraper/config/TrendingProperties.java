package com.careerpolitics.scraper.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "careerpolitics.trending")
public record TrendingProperties(
        Discovery discovery,
        News news,
        Selenium selenium,
        Generation generation,
        Publishing publishing,
        Scheduler scheduler
) {

    public record Discovery(
            @NotBlank String googleTrendsUrl,
            @Min(1) @Max(50) int defaultMaxTrends,
            List<String> fallbackTrends
    ) {
    }

    public record News(
            @NotBlank String googleSearchUrl,
            @NotBlank String googleNewsRssUrl,
            boolean rssEnabled,
            @Min(1) @Max(20) int defaultMaxNewsPerTrend
    ) {
    }

    public record Selenium(
            boolean enabled,
            boolean newsEnabled,
            boolean headless,
            boolean manualVerificationWaitEnabled,
            @Min(10) @Max(300) int manualVerificationMaxWaitSeconds,
            @Min(5) @Max(120) int timeoutSeconds,
            @Min(1) @Max(5) int maxAttempts,
            @Min(100) @Max(5000) int interactionDelayMs,
            String remoteUrl,
            String userAgent,
            List<String> proxyPool,
            Duration sessionRetryBackoff
    ) {
    }

    public record Generation(
            boolean openRouterEnabled,
            @NotBlank String openRouterBaseUrl,
            @NotBlank String openRouterModel,
            String openRouterApiKey,
            Duration timeout
    ) {
    }

    public record Publishing(
            boolean enabled,
            String articleApiUrl,
            String articleApiToken,
            Long organizationId,
            Duration timeout
    ) {
    }

    public record Scheduler(
            boolean enabled,
            @NotBlank String cron,
            @NotBlank String geo,
            @NotBlank String language,
            @Min(1) @Max(24) int maxTrends,
            @Min(1) @Max(20) int maxNewsPerTrend,
            @Min(1) @Max(720) int trendCooldownHours,
            boolean publish
    ) {
    }
}
