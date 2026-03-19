package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleTrendsSeleniumClientTest {

    @Test
    void parseReturnsUniqueVisibleTrendTerms() {
        GoogleTrendsSeleniumClient client = new GoogleTrendsSeleniumClient(null, properties(), new TrendNormalizer());

        String html = """
                <html><body>
                  <div class='mZ3RIc'>March Madness</div>
                  <div class='mZ3RIc'>March Madness</div>
                  <div data-term='AI Jobs'></div>
                </body></html>
                """;

        assertEquals(List.of("March Madness", "AI Jobs"), client.parse(html, 5));
    }

    private TrendingProperties properties() {
        return new TrendingProperties(
                new TrendingProperties.Discovery("https://trends.google.com/trending", 5, List.of()),
                new TrendingProperties.News("https://www.google.com/search", 4),
                new TrendingProperties.Selenium(true, true, true, false, 45, 20, 2, 750, "http://selenium:4444/wd/hub", List.of(), Duration.ofSeconds(2)),
                new TrendingProperties.Generation(false, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "", Duration.ofSeconds(20)),
                new TrendingProperties.Publishing(false, "", "", null, Duration.ofSeconds(10)),
                new TrendingProperties.Scheduler(false, "0 0 */6 * * *", "US", "en-US", 3, 4, 24, false)
        );
    }
}
