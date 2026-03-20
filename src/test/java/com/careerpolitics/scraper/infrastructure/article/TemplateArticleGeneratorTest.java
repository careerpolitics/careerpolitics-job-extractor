package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.domain.model.ArticleDetails;
import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateArticleGeneratorTest {

    @Test
    void generateBuildsReadableMarkdownAndTags() {
        TemplateArticleGenerator generator = new TemplateArticleGenerator(new TrendNormalizer(), new HeadlineMediaResolver());

        GeneratedArticleDraft draft = generator.generate(
                "AI Jobs",
                "en-US",
                List.of(new TrendHeadline("AI Jobs", "Hiring expands", "https://example.com", "Reuters", null, "New hiring wave",
                        new ArticleDetails("Hiring wave expands", "Detailed article content", List.of(
                                "https://example.com/cover.jpg",
                                "https://example.com/video.mp4"
                        ), "image")))
        );

        assertTrue(draft.markdown().contains("# AI Jobs"));
        assertTrue(draft.tags().contains("trending"));
        assertTrue(draft.keywords().contains("AI Jobs"));
        assertTrue(draft.markdown().contains("https://example.com/video.mp4"));
        assertFalse(draft.markdown().contains("https://example.com/cover.jpg"));
        assertTrue(draft.markdown().contains("## Table Of Contents"));
        assertTrue(draft.markdown().contains("{% card %}"));
        assertTrue(draft.markdown().contains("{% cta https://careerpolitics.com %}"));
        assertTrue(draft.markdown().contains("{% details"));
    }
}
