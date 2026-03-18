package com.careerpolitics.scraper.service;

import com.careerpolitics.scraper.model.response.TrendMediaItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrendArticleServiceTest {

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
    void attachMarkdownImage_shouldPrependSecondaryImage() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper());

        String markdown = "# Heading\n\nBody copy";

        String prepared = service.attachMarkdownImage(markdown, "https://cdn.example.com/body.jpg");

        assertEquals("![Trend image](https://cdn.example.com/body.jpg)\n\n# Heading\n\nBody copy", prepared);
    }

    @Test
    void pickMarkdownImage_shouldSkipCoverImageAndUseNextImage() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper());

        var mediaItems = List.of(
                TrendMediaItem.builder().type("image").url("https://cdn.example.com/cover.jpg").build(),
                TrendMediaItem.builder().type("image").url("https://cdn.example.com/body.jpg").build(),
                TrendMediaItem.builder().type("video").url("https://cdn.example.com/video.mp4").build()
        );

        String markdownImage = service.pickMarkdownImage(mediaItems, "https://cdn.example.com/cover.jpg");

        assertEquals("https://cdn.example.com/body.jpg", markdownImage);
    }

    @Test
    void parseQueryParams_shouldDecodeValues() {
        TrendArticleService service = new TrendArticleService(new ObjectMapper(), new SeleniumTrendScraper());

        var params = service.parseQueryParams("url=https%3A%2F%2Fexample.com%2Fa%3Fx%3D1&hl=en-US");
        assertEquals("https://example.com/a?x=1", params.get("url"));
        assertEquals("en-US", params.get("hl"));
    }

}
