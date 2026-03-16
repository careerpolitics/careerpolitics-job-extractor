package com.careerpolitics.scraper.service;

import com.careerpolitics.scraper.repository.TrendArticleHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrendArticleServiceTest {

    @Test
    void extractTrendsFromDocument_shouldIgnoreNavigationLinksAndPreferTableRows() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper());

        String html = """
                <html>
                  <body>
                    <nav>
                      <a title='Go back'>Go back</a>
                      <a title='Home Link'>Home Link</a>
                      <a title='Explore Link'>Explore Link</a>
                      <a title='Trending now Link'>Trending now Link</a>
                      <a title='Year in Search Link'>Year in Search Link</a>
                    </nav>
                    <main>
                      <table>
                        <tbody>
                          <tr><td>1</td><td>UPSC Result 2026</td></tr>
                          <tr><td>2</td><td>JEE Main Counseling</td></tr>
                          <tr><td>3</td><td>NEET UG Cutoff</td></tr>
                        </tbody>
                      </table>
                    </main>
                  </body>
                </html>
                """;

        Document doc = Jsoup.parse(html);
        List<String> trends = service.extractTrendsFromDocument(doc, 5);

        assertEquals(3, trends.size());
        assertTrue(trends.contains("UPSC Result 2026"));
        assertTrue(trends.contains("JEE Main Counseling"));
        assertTrue(trends.contains("NEET UG Cutoff"));
        assertFalse(trends.stream().anyMatch(t -> t.toLowerCase().contains("link")));
    }


    @Test
    void extractTrendsFromDocument_shouldRemoveTrendMetadataNoise() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper());

        String html = """
                <html><body><main>
                  <table><tbody>
                    <tr><td>1</td><td>rpsc 500+ searches · timelapse Lasted 1 hr · 11h ago</td></tr>
                    <tr><td>2</td><td>banasthali vidyapith 500+ arrow_upward 50% 8 hours ago timelapse Lasted 1 hr</td></tr>
                  </tbody></table>
                </main></body></html>
                """;

        List<String> trends = service.extractTrendsFromDocument(Jsoup.parse(html), 5);

        assertEquals(2, trends.size());
        assertEquals("rpsc", trends.get(0).toLowerCase());
        assertEquals("banasthali vidyapith", trends.get(1).toLowerCase());
    }

    @Test
    void parseGoogleSearchNewsDocument_shouldExtractHeadlineDetails() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper());

        String html = """
                <html><body>
                  <div class='SoaBEf'>
                    <a href='https://news.google.com/articles/abc?url=https%3A%2F%2Fexample.com%2Fupsc-story'>Read</a>
                    <div class='n0jPhd'>UPSC announces exam calendar</div>
                    <div class='CEMjEf'><span>Example News</span></div>
                    <time>2 hours ago</time>
                  </div>
                </body></html>
                """;

        Document doc = Jsoup.parse(html, "https://news.google.com");
        List<com.careerpolitics.scraper.model.response.TrendNewsItem> news = service.parseGoogleSearchNewsDocument(doc, "UPSC", 3);

        assertEquals(1, news.size());
        assertEquals("UPSC", news.get(0).getTrend());
        assertEquals("UPSC announces exam calendar", news.get(0).getTitle());
        assertEquals("https://example.com/upsc-story", news.get(0).getLink());
        assertEquals("Example News", news.get(0).getSource());
    }

    @Test
    void normalizeTag_shouldConvertToAsciiSlug() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper());

        assertEquals("rrbclerk", service.normalizeTag("rrb clerk"));
        assertEquals("mainsresult", service.normalizeTag("mains result"));
        assertEquals("bankingjobs", service.normalizeTag("banking jobs"));
        assertEquals("upsc2026", service.normalizeTag("UPSC 2026"));
    }

    @Test
    void resolveOriginalNewsUrl_shouldPreferWrappedQueryUrl() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper());

        String wrapped = "https://news.google.com/rss/articles/CBMiX2h0dHBzOi8vbmV3cy5zaXRlL2FydGljbGXSAQA?oc=5&url=https%3A%2F%2Fpublisher.com%2Fstory%3Fid%3D1";
        String resolved = service.resolveOriginalNewsUrl(wrapped);

        assertEquals("https://publisher.com/story?id=1", resolved);
    }

    @Test
    void parseQueryParams_shouldDecodeValues() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper());

        var params = service.parseQueryParams("url=https%3A%2F%2Fexample.com%2Fa%3Fx%3D1&hl=en-US");
        assertEquals("https://example.com/a?x=1", params.get("url"));
        assertEquals("en-US", params.get("hl"));
    }


    @Test
    void selectNonRepeatingTrends_shouldPrioritizeFreshTrendsOverRecentlyUsedOnes() {
        TrendArticleHistoryRepository repo = mock(TrendArticleHistoryRepository.class);
        when(repo.findTrendSlugsUsedSince(any())).thenReturn(List.of("upsc", "neet"));
        when(repo.findLatestGeneratedAtByTrendSlug()).thenReturn(List.of(
                new Object[]{"upsc", LocalDateTime.now().minusHours(2)},
                new Object[]{"neet", LocalDateTime.now().minusHours(5)}
        ));

        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper(), repo);
        List<String> selected = service.selectNonRepeatingTrends(List.of("UPSC", "NDA", "NEET", "SSC"), 3, 24);

        assertEquals(List.of("NDA", "SSC", "NEET"), selected);
    }

    @Test
    void selectNonRepeatingTrends_shouldReturnUniqueTrendsWhenHistoryUnavailable() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper());

        List<String> selected = service.selectNonRepeatingTrends(List.of("UPSC", "UPSC", "NEET"), 5, 24);

        assertEquals(List.of("UPSC", "NEET"), selected);
    }
}
