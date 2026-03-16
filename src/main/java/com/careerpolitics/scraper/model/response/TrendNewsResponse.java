package com.careerpolitics.scraper.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TrendNewsResponse {
    private String trend;
    private String geo;
    private String language;
    private int maxNewsPerTrend;
    private List<TrendNewsItem> news;
}
