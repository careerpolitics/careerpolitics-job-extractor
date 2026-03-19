package com.careerpolitics.scraper.domain.port;

import java.util.List;

public interface TrendDiscoveryClient {
    List<String> discover(String geo, String language, int maxTrends);
}
