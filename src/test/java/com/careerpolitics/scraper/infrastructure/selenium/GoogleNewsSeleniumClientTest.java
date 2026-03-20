package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleNewsSeleniumClientTest {

    @Test
    void parseExtractsHeadlinesAndResolvesWrappedLinks() {
        GoogleNewsSeleniumClient client = new GoogleNewsSeleniumClient(null, properties(), new TrendNormalizer());

        String html = """
                <html><body>
                  <div class='SoaBEf'>
                    <a href='https://news.google.com/articles/abc?url=https%3A%2F%2Fexample.com%2Fstory'>Read</a>
                    <img src='https://example.com/story.jpg' />
                    <div class='n0jPhd'>Market rally continues</div>
                    <div class='CEMjEf'><span>Reuters</span></div>
                    <time>2 hours ago</time>
                    <div class='GI74Re'>Stocks keep rising</div>
                  </div>
                </body></html>
                """;

        List<TrendHeadline> headlines = client.parse(html, "https://www.google.com/search", "Markets", 3);

        assertEquals(1, headlines.size());
        assertEquals("https://example.com/story", headlines.get(0).link());
        assertEquals("Reuters", headlines.get(0).source());
        assertEquals("Market rally continues", headlines.get(0).title());
        assertEquals("https://example.com/story.jpg", headlines.get(0).articleDetails().mediaUrl());
        assertEquals("image", headlines.get(0).articleDetails().mediaType());
    }

    private TrendingProperties properties() {
        return new TrendingProperties(
                new TrendingProperties.Discovery("https://trends.google.com/trending", 5, List.of()),
                new TrendingProperties.News("https://www.google.com/search", 4),
                new TrendingProperties.Selenium(true, true, true, false, 45, 20, 2, 750, "http://selenium:4444/wd/hub", "", List.of(), Duration.ofSeconds(2)),
                new TrendingProperties.Generation(false, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "", Duration.ofSeconds(20)),
                new TrendingProperties.Publishing(false, "", "", null, Duration.ofSeconds(10)),
                new TrendingProperties.Scheduler(false, "0 0 */6 * * *", "US", "en-US", 3, 4, 24, false)
        );
    }
}
