package com.careerpolitics.scraper.service;

import com.careerpolitics.scraper.model.TrendArticleHistory;
import com.careerpolitics.scraper.repository.TrendArticleHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class TrendDiversityService {

    private final TrendArticleHistoryRepository trendArticleHistoryRepository;

    public TrendDiversityService(TrendArticleHistoryRepository trendArticleHistoryRepository) {
        this.trendArticleHistoryRepository = trendArticleHistoryRepository;
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
        if (cooldownHours <= 0) {
            return orderedUnique.stream().limit(Math.max(1, maxTrends)).toList();
        }

        LocalDateTime cutoff = LocalDateTime.now().minusHours(cooldownHours);
        Set<String> recentlyUsed = new HashSet<>(trendArticleHistoryRepository.findTrendSlugsUsedSince(cutoff));

        List<String> fresh = orderedUnique.stream()
                .filter(trend -> !recentlyUsed.contains(slug(trend)))
                .limit(Math.max(1, maxTrends))
                .toList();

        int skipped = orderedUnique.size() - fresh.size();
        if (skipped > 0) {
            log.info("Skipped {} recently processed trends inside cooldown window ({} hours)", skipped, cooldownHours);
        }

        return fresh;
    }

    void recordTrendHistory(String trend, boolean published) {
        if (trend == null || trend.isBlank()) {
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
