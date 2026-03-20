package com.careerpolitics.scraper.domain.model;

public record ArticleDetails(
        String description,
        String content,
        String mediaUrl,
        String mediaType
) {
}
