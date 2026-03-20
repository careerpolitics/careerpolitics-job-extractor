package com.careerpolitics.scraper.domain.port;

import com.careerpolitics.scraper.domain.model.TrendHeadline;

import java.util.List;

public interface TrendHeadlineDetailClient {
    List<TrendHeadline> enrich(List<TrendHeadline> headlines);
}
