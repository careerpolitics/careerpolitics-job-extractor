package com.careerpolitics.scraper.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request for generating detailed jobs and education articles for each trend")
public class TrendArticleRequest {

    @Schema(description = "Country/region code for Google Trends", example = "IN", defaultValue = "IN")
    private String geo = "IN";

    @Schema(description = "Language locale for trend/news discovery and article generation output", example = "en-US", defaultValue = "en-US")
    private String language = "en-US";

    @Min(1)
    @Max(30)
    @Schema(description = "Maximum number of trend topics to include", example = "5", minimum = "1", maximum = "30", defaultValue = "5")
    private int maxTrends = 5;

    @Min(1)
    @Max(10)
    @Schema(description = "Maximum number of news headlines collected for each trend", example = "3", minimum = "1", maximum = "10", defaultValue = "3")
    private int maxNewsPerTrend = 3;


    @Min(1)
    @Max(720)
    @Schema(description = "Do not repeat same trend within this cooldown window (hours)", example = "24", minimum = "1", maximum = "720", defaultValue = "48")
    private int trendCooldownHours = 48;

    @Schema(description = "Whether article should be published at CareerPolitics article API", example = "false", defaultValue = "false")
    private Boolean publish = false;

    @Schema(description = "Fallback trend keywords used if Google Trends is unavailable")
    private List<String> fallbackTrends;

    @JsonAlias("CAREERPOLITICS_ARTICLE_API_TOKEN")
    @Schema(description = "Article API token used only for this API request when publish=true. Cron jobs continue using configured properties.", example = "cp_live_xxx")
    private String articleApiToken;

    @JsonProperty("organization_id")
    @Schema(description = "Optional organization id to include while publishing. API callers can override it per request; automated jobs use configured properties when present.", example = "42")
    private Long organizationId;

    public boolean shouldPublish() {
        return Boolean.TRUE.equals(publish);
    }
}
