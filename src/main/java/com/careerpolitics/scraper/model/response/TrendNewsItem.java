package com.careerpolitics.scraper.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrendNewsItem {
    private String trend;
    private String title;
    private String link;
    private String source;
    private String publishedAt;
    private String snippet;
}
