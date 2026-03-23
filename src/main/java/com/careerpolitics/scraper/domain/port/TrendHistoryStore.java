package com.careerpolitics.scraper.domain.port;

import com.careerpolitics.scraper.domain.model.TrendTopic;

import java.time.LocalDateTime;
import java.util.Set;

public interface TrendHistoryStore {
    Set<String> findUsedSince(LocalDateTime cutoff);

    void save(TrendTopic trendTopic, boolean published);
}
