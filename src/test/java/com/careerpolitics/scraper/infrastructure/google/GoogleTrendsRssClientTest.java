package com.careerpolitics.scraper.infrastructure.google;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleTrendsRssClientTest {

    @Test
    void parseReturnsUniqueTrendTitles() {
        GoogleTrendsRssClient client = new GoogleTrendsRssClient(
                RestClient.builder().build(),
                properties(),
                new TrendNormalizer()
        );

        String xml = """
                <rss><channel>
                  <item><title>March Madness</title></item>
                  <item><title>March Madness</title></item>
                  <item><title>AI Jobs</title></item>
                </channel></rss>
                """;

        assertEquals(List.of("March Madness", "AI Jobs"), client.parse(xml, 5));
    }

    private TrendingProperties properties() {
        return new TrendingProperties(
                new TrendingProperties.Discovery("https://trends.google.com/trending/rss", 5, List.of()),
                new TrendingProperties.News("https://news.google.com/rss/search", 4, Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new TrendingProperties.Generation(false, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "", Duration.ofSeconds(20)),
                new TrendingProperties.Publishing(false, "", "", null, Duration.ofSeconds(10)),
                new TrendingProperties.Scheduler(false, "0 0 */6 * * *", "US", "en-US", 3, 4, 24, false)
        );
    }
}
