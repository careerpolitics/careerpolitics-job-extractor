package com.careerpolitics.scraper.domain.model;

import java.util.List;

public record TrendDiscoveryCandidate(String title, List<String> breakdowns, String rawText) {
}
