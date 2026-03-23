package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.TrendDiscoveryCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouterTrendTopicCleanerTest {

    @Test
    void fallbackTopicsUsesHeadlineWhenAiIsDisabled() {
        OpenRouterTrendTopicCleaner cleaner = new OpenRouterTrendTopicCleaner(
                RestClient.create(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                disabledProperties(),
                new TrendNormalizer()
        );

        var topics = cleaner.cleanTopics(List.of(
                new TrendDiscoveryCandidate("AI Hiring", List.of("OpenAI jobs", "Anthropic hiring"), "AI Hiring OpenAI jobs Anthropic hiring"),
                new TrendDiscoveryCandidate("Federal Reserve", List.of("Fed meeting"), "Federal Reserve Fed meeting")
        ), 5);

        assertThat(topics).extracting("name").containsExactly("AI Hiring", "Federal Reserve");
    }

    @Test
    void parseTopicsDeduplicatesAiTopics() throws Exception {
        OpenRouterTrendTopicCleaner cleaner = new OpenRouterTrendTopicCleaner(
                RestClient.create(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                enabledProperties(),
                new TrendNormalizer()
        );

        String response = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "```json\\n{\\n  \\"topics\\": [\\n    {\\"name\\": \\"AI Hiring\\", \\"keywords\\": [\\"AI Layoffs\\", \\"OpenAI jobs\\"]},\\n    {\\"name\\": \\"AI Hiring\\", \\"keywords\\": [\\"Anthropic hiring\\"]},\\n    {\\"name\\": \\"Federal Reserve\\", \\"keywords\\": [\\"Fed meeting\\"]}\\n  ]\\n}\\n```"
                      }
                    }
                  ]
                }
                """;

        var topics = cleaner.parseTopics(response, 5, List.of(
                new TrendDiscoveryCandidate("AI Layoffs", List.of("OpenAI jobs"), "AI Layoffs OpenAI jobs")
        ));

        assertThat(topics).extracting("name").containsExactly("AI Hiring", "Federal Reserve");
    }

    private TrendingProperties disabledProperties() {
        return new TrendingProperties(
                new TrendingProperties.Discovery("https://trends.google.com/trending", 5, List.of()),
                new TrendingProperties.News("https://www.google.com/search", 4),
                new TrendingProperties.Selenium(true, true, true, false, 45, 20, 2, 750, "http://selenium:4444/wd/hub", "", List.of(), Duration.ofSeconds(2)),
                new TrendingProperties.Generation(false, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "", Duration.ofSeconds(20)),
                new TrendingProperties.Publishing(false, "", "", null, Duration.ofSeconds(10)),
                new TrendingProperties.Scheduler(false, "0 0 */6 * * *", "US", "en-US", 3, 4, 24, false)
        );
    }

    private TrendingProperties enabledProperties() {
        return new TrendingProperties(
                new TrendingProperties.Discovery("https://trends.google.com/trending", 5, List.of()),
                new TrendingProperties.News("https://www.google.com/search", 4),
                new TrendingProperties.Selenium(true, true, true, false, 45, 20, 2, 750, "http://selenium:4444/wd/hub", "", List.of(), Duration.ofSeconds(2)),
                new TrendingProperties.Generation(true, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "token", Duration.ofSeconds(20)),
                new TrendingProperties.Publishing(false, "", "", null, Duration.ofSeconds(10)),
                new TrendingProperties.Scheduler(false, "0 0 */6 * * *", "US", "en-US", 3, 4, 24, false)
        );
    }
}
