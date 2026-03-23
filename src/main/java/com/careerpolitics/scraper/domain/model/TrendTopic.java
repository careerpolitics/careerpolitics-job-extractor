package com.careerpolitics.scraper.domain.model;

import java.util.List;

public record TrendTopic(String name, String slug, List<String> keywords) {

    public TrendTopic(String name, String slug) {
        this(name, slug, List.of());
    }
}
