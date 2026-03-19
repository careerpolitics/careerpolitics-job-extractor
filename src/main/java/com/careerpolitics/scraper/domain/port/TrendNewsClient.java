package com.careerpolitics.scraper.domain.port;

import com.careerpolitics.scraper.domain.model.TrendHeadline;

import java.util.List;

public interface TrendNewsClient {
    List<TrendHeadline> discover(String trend, String geo, String language, int maxNewsPerTrend);
}
