package com.careerpolitics.scraper.domain.model;

import java.util.List;

public record ArticleDetails(
        String description,
        String content,
        List<String> mediaUrls,
        String mediaType
) {
}
