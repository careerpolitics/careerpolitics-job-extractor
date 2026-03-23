package com.careerpolitics.scraper.application;

import com.careerpolitics.scraper.domain.model.TrendTopic;
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
                return Set.of("openai-jobs", "fed-rate");
            }

            @Override
            public void save(TrendTopic trendTopic, boolean published) {
            }
        };

        TrendSelectionService service = new TrendSelectionService(historyStore, new TrendNormalizer());

        List<TrendTopic> result = service.pickFreshTrends(
                List.of(
                        new TrendTopic("AI Jobs", "ai-jobs", List.of("AI Jobs", "OpenAI jobs")),
                        new TrendTopic("AI Jobs", "ai-jobs", List.of("AI Jobs", "OpenAI jobs")),
                        new TrendTopic("NCAA", "ncaa", List.of("NCAA")),
                        new TrendTopic("Fed Rate", "fed-rate", List.of("Fed Rate")),
                        new TrendTopic("March Madness", "march-madness", List.of("March Madness"))
                ),
                3,
                24
        );

        assertEquals(List.of("NCAA", "March Madness"), result.stream().map(TrendTopic::name).toList());
    }
}
