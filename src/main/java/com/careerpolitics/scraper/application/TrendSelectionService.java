package com.careerpolitics.scraper.application;

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

    public List<String> pickFreshTrends(List<String> discoveredTrends, int maxTrends, int cooldownHours) {
        if (discoveredTrends == null || discoveredTrends.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, String> uniqueBySlug = new LinkedHashMap<>();
        for (String trend : discoveredTrends) {
            String cleanedTrend = trendNormalizer.clean(trend);
            String slug = trendNormalizer.slug(cleanedTrend);
            if (!cleanedTrend.isBlank() && !slug.isBlank()) {
                uniqueBySlug.putIfAbsent(slug, cleanedTrend);
            }
        }

        if (cooldownHours <= 0) {
            return uniqueBySlug.values().stream().limit(Math.max(1, maxTrends)).toList();
        }

        Set<String> recentlyUsed = trendHistoryStore.findUsedSince(LocalDateTime.now().minusHours(cooldownHours));
        return uniqueBySlug.entrySet().stream()
                .filter(entry -> !recentlyUsed.contains(entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .limit(Math.max(1, maxTrends))
                .toList();
    }

    public void remember(String trend, boolean published) {
        trendHistoryStore.save(trend, published);
    }
}
