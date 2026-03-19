package com.careerpolitics.scraper.domain.model;

import java.util.List;

public record GeneratedArticleDraft(
        String title,
        String markdown,
        List<String> tags,
        List<String> keywords,
        String strategy
) {
}
