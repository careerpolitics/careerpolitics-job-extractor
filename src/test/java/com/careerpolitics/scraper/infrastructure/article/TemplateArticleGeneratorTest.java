package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateArticleGeneratorTest {

    @Test
    void generateBuildsReadableMarkdownAndTags() {
        TemplateArticleGenerator generator = new TemplateArticleGenerator(new TrendNormalizer());

        GeneratedArticleDraft draft = generator.generate(
                "AI Jobs",
                "en-US",
                List.of(new TrendHeadline("AI Jobs", "Hiring expands", "https://example.com", "Reuters", null, "New hiring wave"))
        );

        assertTrue(draft.markdown().contains("# AI Jobs"));
        assertTrue(draft.tags().contains("trending"));
        assertTrue(draft.keywords().contains("AI Jobs"));
    }
}
