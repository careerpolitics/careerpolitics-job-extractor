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
        Generation generation,
        Publishing publishing,
        Scheduler scheduler
) {

    public record Discovery(
            @NotBlank String googleTrendsRssUrl,
            @Min(1) @Max(50) int defaultMaxTrends,
            List<String> fallbackTrends
    ) {
    }

    public record News(
            @NotBlank String googleNewsRssUrl,
            @Min(1) @Max(20) int defaultMaxNewsPerTrend,
            Duration connectTimeout,
            Duration readTimeout
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
