package com.careerpolitics.scraper.infrastructure.google;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleNewsRssClientTest {

    @Test
    void parseExtractsHeadlinesAndResolvesRedirectLinks() {
        GoogleNewsRssClient client = new GoogleNewsRssClient(RestClient.builder().build(), properties(), new TrendNormalizer());

        String xml = """
                <rss><channel>
                  <item>
                    <title>Market rally continues</title>
                    <link>https://news.google.com/rss/articles/abc?url=https%3A%2F%2Fexample.com%2Fstory</link>
                    <source>Reuters</source>
                    <pubDate>Fri, 01 Mar 2024 10:00:00 GMT</pubDate>
                    <description>&lt;p&gt;Stocks are rising.&lt;/p&gt;</description>
                  </item>
                </channel></rss>
                """;

        List<TrendHeadline> headlines = client.parse(xml, "Markets", 3);

        assertEquals(1, headlines.size());
        assertEquals("https://example.com/story", headlines.get(0).link());
        assertEquals("Reuters", headlines.get(0).source());
        assertEquals("Market rally continues", headlines.get(0).title());
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
