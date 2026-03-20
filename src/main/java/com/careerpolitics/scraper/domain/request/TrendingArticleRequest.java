package com.careerpolitics.scraper.domain.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
@Schema(name = "TrendingArticleRequest", description = "Request for generating and optionally publishing trending articles.")
public class TrendingArticleRequest {

    @Schema(description = "Google Trends geography.", example = "IN", defaultValue = "IN")
    private String geo = "IN";

    @Schema(description = "Language used for discovery and article generation.", example = "en-IN", defaultValue = "en-IN")
    private String language = "en-IN";

    @Min(1)
    @Max(24)
    @Schema(description = "Maximum number of trends to process.", example = "5", defaultValue = "5")
    private int maxTrends = 5;

    @Min(1)
    @Max(20)
    @Schema(description = "Maximum number of news items to collect per trend.", example = "5", defaultValue = "5")
    private int maxNewsPerTrend = 5;

    @Min(1)
    @Max(720)
    @Schema(description = "Cooldown window in hours before a successfully published trend can be used again.", example = "48", defaultValue = "48")
    private int trendCooldownHours = 48;

    @Schema(description = "Whether generated articles should be published to the article API.", example = "false", defaultValue = "false")
    private Boolean publish = false;

    @JsonAlias("fallbackTrends")
    @Schema(description = "Optional request-scoped trend list that overrides live Selenium discovery for this call.", example = "[\"some-trends\"]")
    private List<String> requestedTrends;

    @JsonAlias("CAREERPOLITICS_ARTICLE_API_TOKEN")
    @Schema(description = "API token used when publish=true.", example = "token_key")
    private String articleApiToken;

    @JsonProperty("organization_id")
    @Schema(description = "Organization identifier used by the article API.", example = "0")
    private Long organizationId;

    public boolean shouldPublish() {
        return Boolean.TRUE.equals(publish);
    }
}
