package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.domain.model.ArticleDetails;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeadlineMediaResolverTest {

    @Test
    void resolveAdditionalMediaExcludesChosenCoverMedia() {
        HeadlineMediaResolver resolver = new HeadlineMediaResolver();
        List<TrendHeadline> headlines = List.of(
                new TrendHeadline(
                        "AI Jobs",
                        "Headline 1",
                        "https://example.com/1",
                        "Reuters",
                        null,
                        "summary",
                        new ArticleDetails("desc", "content", List.of(
                                "https://example.com/cover.jpg",
                                "https://example.com/video.mp4",
                                "https://example.com/clip.gif"
                        ), "image")
                )
        );

        assertThat(resolver.resolveCoverMediaUrl(headlines)).isEqualTo("https://example.com/cover.jpg");
        assertThat(resolver.resolveAdditionalMedia(headlines, 5))
                .extracting(HeadlineMediaResolver.ResolvedMedia::url)
                .containsExactly("https://example.com/video.mp4", "https://example.com/clip.gif");
    }
}
