package com.careerpolitics.scraper.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TrendArticleResponse {
    private List<String> trends;
    private List<TrendNewsItem> news;
    private List<TrendGeneratedArticle> articles;

    // Backward-compatible single-article fields (mirrors first generated article when present)
    private String generatedTitle;
    private String generatedMarkdown;
    private boolean published;
    private Map<String, Object> publishResponse;
    private List<String> errors;
}
