package com.careerpolitics.scraper.service;

import com.careerpolitics.scraper.repository.TrendArticleHistoryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrendDiversityServiceTest {

    @Test
    void selectNonRepeatingTrends_shouldPrioritizeFreshTrendsOverRecentlyUsedOnes() {
        TrendArticleHistoryRepository repo = mock(TrendArticleHistoryRepository.class);
        when(repo.findTrendSlugsUsedSince(any())).thenReturn(List.of("upsc", "neet"));
        when(repo.findLatestGeneratedAtByTrendSlug()).thenReturn(List.of(
                new Object[]{"upsc", LocalDateTime.now().minusHours(2)},
                new Object[]{"neet", LocalDateTime.now().minusHours(5)}
        ));

        TrendDiversityService service = new TrendDiversityService(repo);
        List<String> selected = service.selectNonRepeatingTrends(
                List.of("UPSC", "NDA", "NEET", "SSC"),
                3,
                24
        );

        assertEquals(List.of("NDA", "SSC", "NEET"), selected);
    }

    @Test
    void selectNonRepeatingTrends_shouldReturnUniqueTrendsWhenHistoryUnavailable() {
        TrendDiversityService service = new TrendDiversityService();

        List<String> selected = service.selectNonRepeatingTrends(
                List.of("UPSC", "UPSC", "NEET"),
                5,
                24
        );

        assertEquals(List.of("UPSC", "NEET"), selected);
    }
}
