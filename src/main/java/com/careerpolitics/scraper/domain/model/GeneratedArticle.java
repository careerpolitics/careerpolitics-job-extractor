package com.careerpolitics.scraper.domain.model;

import java.util.List;

public record GeneratedArticle(
        String trend,
        String title,
        String markdown,
        List<String> tags,
        List<String> keywords,
        List<TrendHeadline> sources,
        boolean published,
        PublishingResult publishingResult,
        List<String> warnings,
        String generationStrategy
) {
}
