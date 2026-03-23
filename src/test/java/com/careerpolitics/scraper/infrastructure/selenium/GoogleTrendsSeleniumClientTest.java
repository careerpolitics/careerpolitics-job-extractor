package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.TrendDiscoveryCandidate;
import com.careerpolitics.scraper.domain.model.TrendTopic;
import com.careerpolitics.scraper.domain.port.TrendTopicCleaner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class GoogleTrendsSeleniumClientTest {

    @Test
    void parseReturnsUniqueVisibleTrendTerms() {
        GoogleTrendsSeleniumClient client = new GoogleTrendsSeleniumClient(
                null,
                properties(),
                new TrendNormalizer(),
                (candidates, maxTopics) -> candidates.stream()
                        .map(candidate -> new TrendTopic(candidate.title(), new TrendNormalizer().slug(candidate.title())))
                        .toList()
        );

        String html = """
                <html><body>
                  <div class='mZ3RIc'>March Madness</div>
                  <div class='mZ3RIc'>March Madness</div>
                  <div data-term='AI Jobs'></div>
                </body></html>
                """;

        assertEquals(List.of("March Madness", "AI Jobs"), client.parse(html, 5));
    }

    @Test
    void parseKeepsOnlyHeadlineTrendsWhenRowsContainBreakdownsAndUiNoise() {
        List<TrendDiscoveryCandidate> capturedCandidates = new ArrayList<>();
        TrendTopicCleaner cleaner = (candidates, maxTopics) -> {
            capturedCandidates.addAll(candidates);
            return List.of(
                    new TrendTopic("AI Hiring", "ai-hiring"),
                    new TrendTopic("Federal Reserve", "federal-reserve")
            );
        };
        GoogleTrendsSeleniumClient client = new GoogleTrendsSeleniumClient(null, properties(), new TrendNormalizer(), cleaner);

        String html = """
                <html><body>
                  <table>
                    <tbody>
                      <tr>
                        <td>1</td>
                        <td>
                          <div class='mZ3RIc'>AI Layoffs</div>
                          <div>trending_up</div>
                          <div>Active</div>
                          <div>5K+ searches</div>
                          <div>2 hours ago</div>
                          <div class='trend-breakdown'>
                            <a title='OpenAI jobs'>OpenAI jobs</a>
                            <a title='Anthropic hiring'>Anthropic hiring</a>
                          </div>
                        </td>
                      </tr>
                      <tr>
                        <td>2</td>
                        <td>
                          <div data-term='Federal Reserve'></div>
                          <div>10K+ searches</div>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </body></html>
                """;

        assertEquals(List.of("AI Hiring", "Federal Reserve"), client.parse(html, 5));
        assertEquals("AI Layoffs", capturedCandidates.get(0).title());
        assertIterableEquals(List.of("OpenAI jobs", "Anthropic hiring"), capturedCandidates.get(0).breakdowns());
        assertEquals("Federal Reserve", capturedCandidates.get(1).title());
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
