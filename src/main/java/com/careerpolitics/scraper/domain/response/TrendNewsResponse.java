package com.careerpolitics.scraper.domain.response;

import com.careerpolitics.scraper.domain.model.TrendHeadline;

import java.util.List;

public record TrendNewsResponse(
        String trend,
        String geo,
        String language,
        int maxNewsPerTrend,
        List<TrendHeadline> news
) {
}
