package com.careerpolitics.scraper.infrastructure.persistence;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.domain.model.TrendTopic;
import com.careerpolitics.scraper.domain.port.TrendHistoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
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
    public void save(TrendTopic trendTopic, boolean published) {
        if (trendTopic == null) {
            return;
        }

        try {
            for (String keyword : uniqueKeywords(trendTopic)) {
                TrendRunHistory history = new TrendRunHistory();
                history.setTrend(keyword);
                history.setTrendSlug(trendNormalizer.slug(keyword));
                history.setPublished(published);
                repository.save(history);
            }
        } catch (Exception exception) {
            log.warn("Unable to persist trend history for '{}': {}", trendTopic.name(), exception.getMessage());
        }
    }

    private List<String> uniqueKeywords(TrendTopic trendTopic) {
        LinkedHashMap<String, String> keywords = new LinkedHashMap<>();
        String cleanedName = trendNormalizer.clean(trendTopic.name());
        String nameSlug = trendNormalizer.slug(cleanedName);
        if (!cleanedName.isBlank() && !nameSlug.isBlank()) {
            keywords.putIfAbsent(nameSlug, cleanedName);
        }
        if (trendTopic.keywords() != null) {
            for (String keyword : trendTopic.keywords()) {
                String cleanedKeyword = trendNormalizer.clean(keyword);
                String keywordSlug = trendNormalizer.slug(cleanedKeyword);
                if (!cleanedKeyword.isBlank() && !keywordSlug.isBlank()) {
                    keywords.putIfAbsent(keywordSlug, cleanedKeyword);
                }
            }
        }
        return List.copyOf(keywords.values());
    }
}
