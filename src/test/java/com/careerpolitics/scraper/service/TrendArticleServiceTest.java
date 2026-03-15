package com.careerpolitics.scraper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrendArticleServiceTest {

    @Test
    void extractTrendsFromDocument_shouldIgnoreNavigationLinksAndPreferTableRows() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper());

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
    void parseGoogleTrendsApiPayload_shouldParseXssiJsonPayload() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper());
        String payload = ")]}'\n" +
                "{\"default\":{\"trendingStories\":[" +
                "{\"title\":{\"query\":\"SBI PO Notification\"}}," +
                "{\"title\":{\"query\":\"Go back\"}}]," +
                "\"trendingSearchesDays\":[{\"trendingSearches\":[" +
                "{\"title\":{\"query\":\"NEET UG 2026\"}}]}]}}";

        List<String> trends = service.parseGoogleTrendsApiPayload(payload, 5);

        assertEquals(2, trends.size());
        assertEquals("SBI PO Notification", trends.get(0));
        assertEquals("NEET UG 2026", trends.get(1));
    }
    @Test
    void parseGoogleSearchNewsDocument_shouldExtractHeadlineDetails() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper());

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
        TrendArticleService service = new TrendArticleService(new ObjectMapper());

        assertEquals("rrbclerk", service.normalizeTag("rrb clerk"));
        assertEquals("mainsresult", service.normalizeTag("mains result"));
        assertEquals("bankingjobs", service.normalizeTag("banking jobs"));
        assertEquals("upsc2026", service.normalizeTag("UPSC 2026"));
    }

    @Test
    void resolveOriginalNewsUrl_shouldPreferWrappedQueryUrl() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper());

        String wrapped = "https://news.google.com/rss/articles/CBMiX2h0dHBzOi8vbmV3cy5zaXRlL2FydGljbGXSAQA?oc=5&url=https%3A%2F%2Fpublisher.com%2Fstory%3Fid%3D1";
        String resolved = service.resolveOriginalNewsUrl(wrapped);

        assertEquals("https://publisher.com/story?id=1", resolved);
    }

    @Test
    void parseQueryParams_shouldDecodeValues() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper());

        var params = service.parseQueryParams("url=https%3A%2F%2Fexample.com%2Fa%3Fx%3D1&hl=en-US");
        assertEquals("https://example.com/a?x=1", params.get("url"));
        assertEquals("en-US", params.get("hl"));
    }

}
