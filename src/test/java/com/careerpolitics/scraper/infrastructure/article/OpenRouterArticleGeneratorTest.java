package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouterArticleGeneratorTest {

    @Test
    void extractJsonPayloadStripsMarkdownCodeFences() {
        OpenRouterArticleGenerator generator = new OpenRouterArticleGenerator(
                RestClient.create(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties(),
                new TemplateArticleGenerator(new TrendNormalizer())
        );

        String payload = generator.extractJsonPayload("""
                ```json
                {
                  \"title\": \"Example\",
                  \"markdown\": \"Body\"
                }
                ```
                """);

        assertThat(payload).startsWith("{");
        assertThat(payload).endsWith("}");
        assertThat(payload).contains("\"title\": \"Example\"");
    }

    @Test
    void extractJsonPayloadKeepsBareJsonUntouched() {
        OpenRouterArticleGenerator generator = new OpenRouterArticleGenerator(
                RestClient.create(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties(),
                new TemplateArticleGenerator(new TrendNormalizer())
        );

        String payload = generator.extractJsonPayload("{\"title\":\"Example\",\"markdown\":\"Body\"}");

        assertThat(payload).isEqualTo("{\"title\":\"Example\",\"markdown\":\"Body\"}");
    }

    private TrendingProperties properties() {
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
