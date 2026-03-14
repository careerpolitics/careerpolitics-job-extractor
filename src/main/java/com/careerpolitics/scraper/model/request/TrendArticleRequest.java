package com.careerpolitics.scraper.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class TrendArticleRequest {

    private String geo = "IN";
    private String language = "en-US";

    @Min(1)
    @Max(30)
    private int maxTrends = 5;

    @Min(1)
    @Max(10)
    private int maxNewsPerTrend = 3;

    private boolean publish = true;

    /**
     * Optional fallback keywords used when Google Trends is unavailable.
     */
    private List<String> fallbackTrends;
}
