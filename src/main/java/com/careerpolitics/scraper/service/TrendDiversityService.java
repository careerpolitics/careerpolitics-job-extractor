package com.careerpolitics.scraper.service;

import com.careerpolitics.scraper.model.TrendArticleHistory;
import com.careerpolitics.scraper.repository.TrendArticleHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class TrendDiversityService {

    private final TrendArticleHistoryRepository trendArticleHistoryRepository;

    public TrendDiversityService(TrendArticleHistoryRepository trendArticleHistoryRepository) {
        this.trendArticleHistoryRepository = trendArticleHistoryRepository;
    }

    TrendDiversityService() {
        this.trendArticleHistoryRepository = null;
    }

    List<String> selectNonRepeatingTrends(List<String> trends, int maxTrends, int cooldownHours) {
        if (trends == null || trends.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, String> uniqueBySlug = new LinkedHashMap<>();
        for (String trend : trends) {
            String cleanedTrend = clean(trend);
            if (cleanedTrend.isBlank()) {
                continue;
            }
            uniqueBySlug.putIfAbsent(slug(cleanedTrend), cleanedTrend);
        }

        List<String> orderedUnique = new ArrayList<>(uniqueBySlug.values());
        if (trendArticleHistoryRepository == null || cooldownHours <= 0) {
            return orderedUnique.stream().limit(Math.max(1, maxTrends)).toList();
        }

        LocalDateTime cutoff = LocalDateTime.now().minusHours(cooldownHours);
        Set<String> recentlyUsed = new HashSet<>(trendArticleHistoryRepository.findTrendSlugsUsedSince(cutoff));
        Map<String, LocalDateTime> latestUseBySlug = new HashMap<>();
        for (Object[] row : trendArticleHistoryRepository.findLatestGeneratedAtByTrendSlug()) {
            if (row.length >= 2 && row[0] != null && row[1] instanceof LocalDateTime time) {
                latestUseBySlug.put(String.valueOf(row[0]), time);
            }
        }

        List<String> fresh = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (String trend : orderedUnique) {
            if (recentlyUsed.contains(slug(trend))) {
                stale.add(trend);
            } else {
                fresh.add(trend);
            }
        }

        stale.sort(Comparator.comparing(t -> latestUseBySlug.getOrDefault(slug(t), LocalDateTime.MIN)));

        List<String> selected = new ArrayList<>(fresh);
        for (String trend : stale) {
            if (selected.size() >= maxTrends) {
                break;
            }
            selected.add(trend);
        }

        return selected.stream().limit(Math.max(1, maxTrends)).toList();
    }

    void recordTrendHistory(String trend, boolean published) {
        if (trendArticleHistoryRepository == null || trend == null || trend.isBlank()) {
            return;
        }

        try {
            TrendArticleHistory history = new TrendArticleHistory();
            history.setTrend(clean(trend));
            history.setTrendSlug(slug(trend));
            history.setPublished(published);
            trendArticleHistoryRepository.save(history);
        } catch (Exception ex) {
            log.warn("Failed to save trend history for trend={}: {}", trend, ex.getMessage());
        }
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String slug(String value) {
        return clean(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
