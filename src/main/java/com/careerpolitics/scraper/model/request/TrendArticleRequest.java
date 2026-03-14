package com.careerpolitics.scraper.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request for generating a jobs and education trend article")
public class TrendArticleRequest {

    @Schema(description = "Country/region code for Google Trends", example = "IN", defaultValue = "IN")
    private String geo = "IN";

    @Schema(description = "Language locale for Trends discovery", example = "en-US", defaultValue = "en-US")
    private String language = "en-US";

    @Min(1)
    @Max(30)
    @Schema(description = "Maximum number of trend topics to include", example = "5", minimum = "1", maximum = "30", defaultValue = "5")
    private int maxTrends = 5;

    @Min(1)
    @Max(10)
    @Schema(description = "Maximum number of news headlines collected for each trend", example = "3", minimum = "1", maximum = "10", defaultValue = "3")
    private int maxNewsPerTrend = 3;

    @Schema(description = "Whether to publish to CareerPolitics article API after generation", example = "true", defaultValue = "true")
    private boolean publish = true;

    @Schema(description = "Fallback trend keywords used if Google Trends is unavailable")
    private List<String> fallbackTrends;
}
