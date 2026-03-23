package com.careerpolitics.scraper.domain.port;

import com.careerpolitics.scraper.domain.model.TrendTopic;

import java.util.List;

public interface TrendDiscoveryClient {
    List<TrendTopic> discover(String geo, String language, int maxTrends);
}
