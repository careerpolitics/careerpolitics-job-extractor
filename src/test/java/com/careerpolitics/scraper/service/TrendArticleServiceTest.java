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
    void parseNewsRssDocument_shouldExtractHeadlineDetails() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper());

        String rss = """
                <rss><channel>
                  <item>
                    <title>UPSC announces exam calendar</title>
                    <link>https://example.com/upsc-calendar</link>
                    <source>Example News</source>
                    <pubDate>Sun, 16 Mar 2026 10:00:00 GMT</pubDate>
                    <description><![CDATA[Important updates for aspirants.]]></description>
                  </item>
                </channel></rss>
                """;

        Document doc = Jsoup.parse(rss, "", org.jsoup.parser.Parser.xmlParser());
        List<com.careerpolitics.scraper.model.response.TrendNewsItem> news = service.parseNewsRssDocument(doc, "UPSC", 3);

        assertEquals(1, news.size());
        assertEquals("UPSC", news.get(0).getTrend());
        assertEquals("UPSC announces exam calendar", news.get(0).getTitle());
        assertEquals("https://example.com/upsc-calendar", news.get(0).getLink());
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

}
