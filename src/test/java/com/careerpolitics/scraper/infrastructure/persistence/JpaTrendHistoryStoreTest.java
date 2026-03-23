package com.careerpolitics.scraper.infrastructure.persistence;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.domain.model.TrendTopic;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class JpaTrendHistoryStoreTest {

    @Test
    void savePersistsTopicAndGatheredKeywordsForCooldown() {
        TrendRunHistoryRepository repository = Mockito.mock(TrendRunHistoryRepository.class);
        JpaTrendHistoryStore store = new JpaTrendHistoryStore(repository, new TrendNormalizer());

        store.save(new TrendTopic("AI Hiring", "ai-hiring", List.of("AI Hiring", "OpenAI jobs", "Anthropic hiring")), true);

        ArgumentCaptor<TrendRunHistory> captor = ArgumentCaptor.forClass(TrendRunHistory.class);
        verify(repository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting("trendSlug")
                .containsExactly("ai-hiring", "openai-jobs", "anthropic-hiring");
    }
}
