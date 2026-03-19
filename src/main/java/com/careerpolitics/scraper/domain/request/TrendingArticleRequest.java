package com.careerpolitics.scraper.domain.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class TrendingArticleRequest {

    private String geo = "IN";

    private String language = "en-IN";

    @Min(1)
    @Max(24)
    private int maxTrends = 5;

    @Min(1)
    @Max(20)
    private int maxNewsPerTrend = 4;

    @Min(1)
    @Max(720)
    private int trendCooldownHours = 48;

    private Boolean publish = false;

    private List<String> fallbackTrends;

    @JsonAlias("CAREERPOLITICS_ARTICLE_API_TOKEN")
    private String articleApiToken;

    @JsonProperty("organization_id")
    private Long organizationId;

    public boolean shouldPublish() {
        return Boolean.TRUE.equals(publish);
    }
}
