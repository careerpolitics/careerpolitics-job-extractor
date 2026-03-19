package com.careerpolitics.scraper.domain.port;

import java.time.LocalDateTime;
import java.util.Set;

public interface TrendHistoryStore {
    Set<String> findUsedSince(LocalDateTime cutoff);

    void save(String trend, boolean published);
}
