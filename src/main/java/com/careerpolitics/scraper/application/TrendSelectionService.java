package com.careerpolitics.scraper.application;

import com.careerpolitics.scraper.domain.model.TrendTopic;
import com.careerpolitics.scraper.domain.port.TrendHistoryStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Service
public class TrendSelectionService {

    private final TrendHistoryStore trendHistoryStore;
    private final TrendNormalizer trendNormalizer;

    public TrendSelectionService(TrendHistoryStore trendHistoryStore, TrendNormalizer trendNormalizer) {
        this.trendHistoryStore = trendHistoryStore;
        this.trendNormalizer = trendNormalizer;
    }

    public List<TrendTopic> pickFreshTrends(List<TrendTopic> discoveredTrends, int maxTrends, int cooldownHours) {
        if (discoveredTrends == null || discoveredTrends.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, TrendTopic> uniqueBySlug = new LinkedHashMap<>();
        for (TrendTopic trend : discoveredTrends) {
            TrendTopic normalizedTopic = normalize(trend);
            if (!normalizedTopic.name().isBlank() && !normalizedTopic.slug().isBlank()) {
                uniqueBySlug.putIfAbsent(normalizedTopic.slug(), normalizedTopic);
            }
        }

        if (cooldownHours <= 0) {
            return uniqueBySlug.values().stream().limit(Math.max(1, maxTrends)).toList();
        }

        Set<String> recentlyUsed = trendHistoryStore.findUsedSince(LocalDateTime.now().minusHours(cooldownHours));
        return uniqueBySlug.values().stream()
                .filter(topic -> isFresh(topic, recentlyUsed))
                .limit(Math.max(1, maxTrends))
                .toList();
    }

    public void remember(TrendTopic trendTopic, boolean published) {
        trendHistoryStore.save(normalize(trendTopic), published);
    }

    private TrendTopic normalize(TrendTopic trendTopic) {
        if (trendTopic == null) {
            return new TrendTopic("", "", List.of());
        }
        String name = trendNormalizer.clean(trendTopic.name());
        String slug = trendNormalizer.slug(name);
        LinkedHashMap<String, String> keywords = new LinkedHashMap<>();
        if (!name.isBlank() && !slug.isBlank()) {
            keywords.putIfAbsent(slug, name);
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
        return new TrendTopic(name, slug, List.copyOf(keywords.values()));
    }

    private boolean isFresh(TrendTopic topic, Set<String> recentlyUsed) {
        if (recentlyUsed.contains(topic.slug())) {
            return false;
        }
        return topic.keywords() == null || topic.keywords().stream()
                .map(trendNormalizer::slug)
                .noneMatch(recentlyUsed::contains);
    }
}
