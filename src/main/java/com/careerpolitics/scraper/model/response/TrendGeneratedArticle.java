package com.careerpolitics.scraper.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TrendGeneratedArticle {
    private String trend;
    private String title;
    private String markdown;
    private List<String> tags;
    private List<String> keywords;
    private List<TrendNewsItem> sources;
    private List<TrendMediaItem> media;
    private String coverImage;
    private boolean published;
    private Map<String, Object> publishResponse;
    private List<String> errors;
}
