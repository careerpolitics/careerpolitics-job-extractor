package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.ArticleGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class TemplateArticleGenerator implements ArticleGenerator {

    private final TrendNormalizer trendNormalizer;

    public TemplateArticleGenerator(TrendNormalizer trendNormalizer) {
        this.trendNormalizer = trendNormalizer;
    }

    @Override
    public boolean supportsAi() {
        return false;
    }

    @Override
    public GeneratedArticleDraft generate(String trend, String language, List<TrendHeadline> headlines) {
        String title = trend + " is trending: what matters now";
        List<String> tags = buildTags(trend);
        List<String> keywords = buildKeywords(trend, headlines);
        String markdown = buildMarkdown(trend, language, headlines);
        return new GeneratedArticleDraft(title, markdown, tags, keywords, "template");
    }

    private String buildMarkdown(String trend, String language, List<TrendHeadline> headlines) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(trend).append("\n\n");
        builder.append("## Trend summary\n\n");
        builder.append(trend).append(" is currently showing strong momentum in Google Trends for language ")
                .append(language)
                .append(". This lightweight article is generated from live Selenium-collected trend and headline data so it remains aligned with the current browser workflow.\n\n");

        builder.append("## Why it is trending\n\n");
        if (headlines.isEmpty()) {
            builder.append("No recent headlines were available at generation time. Review the trend manually before publishing externally.\n\n");
        } else {
            builder.append("Recent headlines indicate the following discussion points:\n\n");
            for (TrendHeadline headline : headlines) {
                String context = headline.articleDetails() != null && headline.articleDetails().description() != null
                        ? headline.articleDetails().description()
                        : headline.summary();
                builder.append("- **")
                        .append(headline.title())
                        .append("** — ")
                        .append(headline.source() == null ? "Unknown source" : headline.source())
                        .append(". ")
                        .append(context == null ? "" : context)
                        .append("\n");
            }
            builder.append("\n");
        }

        builder.append("## Source links\n\n");
        if (headlines.isEmpty()) {
            builder.append("- No source links were available.\n");
        } else {
            for (TrendHeadline headline : headlines) {
                builder.append("- [")
                        .append(headline.title())
                        .append("](")
                        .append(headline.link())
                        .append(")\n");
            }
        }
        return builder.toString();
    }

    private List<String> buildTags(String trend) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add("trending");
        tags.add("news");
        String slug = trendNormalizer.slug(trend).replace("-", "");
        if (!slug.isBlank()) {
            tags.add(slug.length() > 20 ? slug.substring(0, 20) : slug);
        }
        return List.copyOf(tags);
    }

    private List<String> buildKeywords(String trend, List<TrendHeadline> headlines) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        keywords.add(trendNormalizer.clean(trend));
        headlines.stream().limit(3).map(TrendHeadline::title).forEach(keywords::add);
        return new ArrayList<>(keywords);
    }
}
