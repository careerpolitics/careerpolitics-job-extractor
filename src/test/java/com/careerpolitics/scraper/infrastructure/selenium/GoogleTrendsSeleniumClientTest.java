package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.TrendTopic;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleTrendsSeleniumClientTest {

    @Test
    void parseExtractsTrendTableHtmlAndDelegatesToAiCleaner() {
        StringBuilder capturedTableHtml = new StringBuilder();
        GoogleTrendsSeleniumClient client = new GoogleTrendsSeleniumClient(
                null,
                properties(),
                (tableHtml, maxTopics) -> {
                    capturedTableHtml.append(tableHtml);
                    return List.of(
                            new TrendTopic("AI Hiring", "ai-hiring"),
                            new TrendTopic("Federal Reserve", "federal-reserve")
                    );
                }
        );

        String html = """
                <html><body>
                  <div>ignored wrapper</div>
                  <table>
                    <tbody>
                      <tr>
                        <td>1</td>
                        <td>
                          <div class='mZ3RIc'>AI Layoffs</div>
                          <div class='trend-breakdown'>
                            <a title='OpenAI jobs'>OpenAI jobs</a>
                            <a title='Anthropic hiring'>Anthropic hiring</a>
                          </div>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </body></html>
                """;

        assertEquals(List.of("AI Hiring", "Federal Reserve"), client.parse(html, 5));
        assertTrue(capturedTableHtml.toString().contains("<table>"));
        assertTrue(capturedTableHtml.toString().contains("OpenAI jobs"));
        assertTrue(capturedTableHtml.toString().contains("Anthropic hiring"));
    }

    @Test
    void parseReturnsEmptyWhenNoTrendTableExists() {
        GoogleTrendsSeleniumClient client = new GoogleTrendsSeleniumClient(
                null,
                properties(),
                (tableHtml, maxTopics) -> List.of(new TrendTopic("Unexpected", "unexpected"))
        );

        assertEquals(List.of(), client.parse("<html><body><div>No table</div></body></html>", 5));
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
