package com.careerpolitics.scraper.domain.response;

import java.util.List;

public record TrendDiscoveryResponse(
        String geo,
        String language,
        int maxTrends,
        List<String> trends
) {
}
