package com.careerpolitics.scraper.infrastructure.persistence;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.domain.port.TrendHistoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Component
public class JpaTrendHistoryStore implements TrendHistoryStore {

    private final TrendRunHistoryRepository repository;
    private final TrendNormalizer trendNormalizer;

    public JpaTrendHistoryStore(TrendRunHistoryRepository repository, TrendNormalizer trendNormalizer) {
        this.repository = repository;
        this.trendNormalizer = trendNormalizer;
    }

    @Override
    public Set<String> findUsedSince(LocalDateTime cutoff) {
        return repository.findTrendSlugsUsedSince(cutoff);
    }

    @Override
    public void save(String trend, boolean published) {
        String cleanedTrend = trendNormalizer.clean(trend);
        if (cleanedTrend.isBlank()) {
            return;
        }

        try {
            TrendRunHistory history = new TrendRunHistory();
            history.setTrend(cleanedTrend);
            history.setTrendSlug(trendNormalizer.slug(cleanedTrend));
            history.setPublished(published);
            repository.save(history);
        } catch (Exception exception) {
            log.warn("Unable to persist trend history for '{}': {}", cleanedTrend, exception.getMessage());
        }
    }
}
