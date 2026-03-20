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
    private final HeadlineMediaResolver headlineMediaResolver;

    public TemplateArticleGenerator(TrendNormalizer trendNormalizer, HeadlineMediaResolver headlineMediaResolver) {
        this.trendNormalizer = trendNormalizer;
        this.headlineMediaResolver = headlineMediaResolver;
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
        builder.append("## Table Of Contents\n");
        builder.append("* [Overview](#overview)\n");
        builder.append("* [Why This Is Trending](#why-this-is-trending)\n");
        builder.append("* [Important Snapshot](#important-snapshot)\n");
        builder.append("* [Source Highlights](#source-highlights)\n");
        builder.append("* [What Aspirants Should Do Now](#what-aspirants-should-do-now)\n");
        builder.append("* [FAQ](#faq)\n\n");

        builder.append("## Overview\n\n");
        builder.append(trend).append(" is currently showing strong momentum in Google Trends for language ")
                .append(language)
                .append(". This article is generated from live Selenium-collected trend, source, and media data to give aspirants a quick, actionable briefing.\n\n");

        builder.append("{% card %}\n");
        builder.append("**Trending Topic:** ").append(trend).append("\n\n");
        builder.append("**Language Context:** ").append(language).append("\n\n");
        builder.append("**Current Signal:** ").append(headlines.isEmpty() ? "Source coverage is limited right now." : "Multiple live headlines are discussing this topic.").append("\n");
        builder.append("{% endcard %}\n\n");

        builder.append("{% cta https://careerpolitics.com %}\n");
        builder.append("Track CareerPolitics for more job alerts, exam updates, and policy explainers.\n");
        builder.append("{% endcta %}\n\n");

        builder.append("## Why This Is Trending\n\n");
        if (headlines.isEmpty()) {
            builder.append("As of now, no official confirmation is available from the discovered sources. Review the trend manually before making decisions.\n\n");
        } else {
            builder.append("Recent source coverage points to the following discussion angles:\n\n");
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

        builder.append("## Important Snapshot\n\n");
        builder.append("| Item | Details |\n");
        builder.append("| --- | --- |\n");
        builder.append("| Trend | ").append(trend).append(" |\n");
        builder.append("| Language | ").append(language).append(" |\n");
        builder.append("| Source Count | ").append(headlines.size()).append(" |\n");
        builder.append("| Action | Follow official notices before taking action |\n\n");

        builder.append("## Source Highlights\n\n");
        if (headlines.isEmpty()) {
            builder.append("{% details Source update %}\n");
            builder.append("No headline details were available at generation time.\n");
            builder.append("{% enddetails %}\n\n");
        } else {
            int index = 1;
            for (TrendHeadline headline : headlines.stream().limit(3).toList()) {
                builder.append("{% details Source ").append(index++).append(": ").append(headline.title()).append(" %}\n");
                builder.append("Source: ").append(headline.source() == null ? "Unknown source" : headline.source()).append("\n\n");
                builder.append("Summary: ").append(headline.summary() == null ? "n/a" : headline.summary()).append("\n\n");
                if (headline.articleDetails() != null && headline.articleDetails().content() != null && !headline.articleDetails().content().isBlank()) {
                    builder.append(headline.articleDetails().content()).append("\n\n");
                }
                builder.append("Reference: ").append(headline.link()).append("\n");
                builder.append("{% enddetails %}\n\n");
            }
        }

        builder.append("## What Aspirants Should Do Now\n\n");
        builder.append("- Check whether the trend has a **job**, **exam**, **result**, or **policy** implication.\n");
        builder.append("- Verify dates, eligibility, and official notices from authoritative sources.\n");
        builder.append("- Keep a note of application windows, result timelines, and clarification updates.\n\n");

        builder.append("{% cta https://careerpolitics.com %}\n");
        builder.append("Visit CareerPolitics for the latest recruitment and exam updates.\n");
        builder.append("{% endcta %}\n\n");

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

        List<String> mediaUrls = headlineMediaResolver.resolveAdditionalMedia(headlines, 2).stream()
                .map(HeadlineMediaResolver.ResolvedMedia::url)
                .toList();
        if (!mediaUrls.isEmpty()) {
            builder.append("\n## Related media\n\n");
            for (String mediaUrl : mediaUrls) {
                builder.append(renderMediaBlock(trend, mediaUrl)).append("\n\n");
            }
        }

        builder.append("## FAQ\n\n");
        builder.append("{% details What is the key takeaway for aspirants? %}\n");
        builder.append("Focus on verified updates, official timelines, and actionable next steps related to ").append(trend).append(".\n");
        builder.append("{% enddetails %}\n\n");
        builder.append("{% details Is every trending topic officially confirmed? %}\n");
        builder.append("No. If official clarity is missing, treat the development as evolving and watch for confirmations.\n");
        builder.append("{% enddetails %}\n");
        return builder.toString();
    }

    private String renderMediaBlock(String trend, String mediaUrl) {
        String lower = mediaUrl.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif")) {
            return "![Supporting visual for " + trend + "](" + mediaUrl + ")\n<figcaption>Supporting media related to " + trend + ".</figcaption>";
        }
        return "{% embed " + mediaUrl + " %}";
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
