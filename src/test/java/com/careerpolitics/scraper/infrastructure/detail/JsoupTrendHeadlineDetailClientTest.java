package com.careerpolitics.scraper.infrastructure.detail;

import com.careerpolitics.scraper.domain.model.ArticleDetails;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupTrendHeadlineDetailClientTest {

    @Test
    void extractDetailsReadsDescriptionContentAndYoutubeMedia() {
        JsoupTrendHeadlineDetailClient client = new JsoupTrendHeadlineDetailClient();
        TrendHeadline headline = new TrendHeadline(
                "AI Jobs",
                "Hiring expands",
                "https://example.com/story",
                "Reuters",
                null,
                "Short summary",
                new ArticleDetails(null, null, null, null)
        );

        String html = """
                <html><head>
                  <meta property='og:description' content='Detailed description from article'>
                </head><body>
                  <article>
                    <p>This is the first detailed paragraph for the article and it contains enough information to keep.</p>
                    <p>This is the second detailed paragraph that should also be included in the extracted content.</p>
                  </article>
                  <iframe src='https://www.youtube.com/embed/demo'></iframe>
                </body></html>
                """;

        ArticleDetails details = client.extractDetails(Jsoup.parse(html, headline.link()), headline);

        assertThat(details.description()).isEqualTo("Detailed description from article");
        assertThat(details.content()).contains("first detailed paragraph");
        assertThat(details.mediaUrl()).isEqualTo("https://www.youtube.com/embed/demo");
        assertThat(details.mediaType()).isEqualTo("youtube");
    }
}
