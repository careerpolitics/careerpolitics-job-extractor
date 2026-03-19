package com.careerpolitics.scraper.domain.model;

public record TrendHeadline(
        String trend,
        String title,
        String link,
        String source,
        String publishedAt,
        String summary
) {
}
