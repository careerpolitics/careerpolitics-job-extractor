package com.careerpolitics.scraper.application;

import com.careerpolitics.scraper.domain.port.TrendHistoryStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrendSelectionServiceTest {

    @Test
    void pickFreshTrendsSkipsRecentlyUsedSlugsAndDeduplicatesInput() {
        TrendHistoryStore historyStore = new TrendHistoryStore() {
            @Override
            public Set<String> findUsedSince(LocalDateTime cutoff) {
                return Set.of("ai-jobs", "fed-rate");
            }

            @Override
            public void save(String trend, boolean published) {
            }
        };

        TrendSelectionService service = new TrendSelectionService(historyStore, new TrendNormalizer());

        List<String> result = service.pickFreshTrends(
                List.of("AI Jobs", "AI Jobs", "NCAA", "Fed Rate", "March Madness"),
                3,
                24
        );

        assertEquals(List.of("NCAA", "March Madness"), result);
    }
}
