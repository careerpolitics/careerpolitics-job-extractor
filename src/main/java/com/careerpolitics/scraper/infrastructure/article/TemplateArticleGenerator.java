package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.ArticleGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class TemplateArticleGenerator implements ArticleGenerator {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);

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
        List<String> tags = buildTags(trend, headlines);
        List<String> keywords = buildKeywords(trend, headlines);
        String markdown = buildMarkdown(trend, language, headlines);
        return new GeneratedArticleDraft(title, markdown, tags, keywords, "template");
    }

    private String buildMarkdown(String trend, String language, List<TrendHeadline> headlines) {
        StringBuilder builder = new StringBuilder();
        builder.append("## Table Of Contents\n");
        builder.append("* [Overview](#overview)\n");
        builder.append("* [Why This Is Trending](#why-this-is-trending)\n");
        builder.append("* [Key Updates](#key-updates)\n");
        builder.append("* [What This Means](#what-this-means)\n");
        builder.append("* [What Readers Should Watch Next](#what-readers-should-watch-next)\n");
        builder.append("* [FAQ](#faq)\n\n");

        builder.append("## Overview\n\n");
        builder.append("**").append(trend).append("** is drawing attention in Google Trends for **")
                .append(language)
                .append("**. This summary brings together the most useful points from the available reporting without repeating the same details.\n\n");

        builder.append("{% card %}\n");
        builder.append("**Topic:** ").append(trend).append("\n\n");
        builder.append("**Language:** ").append(language).append("\n\n");
        builder.append("**Source count:** ").append(headlines.size()).append("\n");
        builder.append("{% endcard %}\n\n");

        builder.append("## Why This Is Trending\n\n");
        if (headlines.isEmpty()) {
            builder.append("As of now, no official confirmation is available. Review trusted sources before acting on the trend.\n\n");
        } else {
            builder.append("Recent coverage suggests a few clear reasons this topic is gaining attention:\n\n");
            for (TrendHeadline headline : headlines.stream().limit(3).toList()) {
                String context = headline.articleDetails() != null && headline.articleDetails().description() != null
                        ? headline.articleDetails().description()
                        : headline.summary();
                builder.append("- **")
                        .append(headline.title())
                        .append("** from ")
                        .append(headline.source() == null ? "an unnamed source" : headline.source())
                        .append(". ")
                        .append(context == null || context.isBlank() ? "The available summary is limited." : context)
                        .append("\n");
            }
            builder.append("\n");
        }

        builder.append("## Key Updates\n\n");
        builder.append("| Item | Details |\n");
        builder.append("| --- | --- |\n");
        builder.append("| Trend | ").append(trend).append(" |\n");
        builder.append("| Language | ").append(language).append(" |\n");
        builder.append("| Sources reviewed | ").append(headlines.size()).append(" |\n");
        builder.append("| Current guidance | Verify official notices before making decisions |\n\n");

        builder.append("## What This Means\n\n");
        if (headlines.isEmpty()) {
            builder.append("With limited reporting available, the safest approach is to wait for clearer updates from official or highly reliable sources.\n\n");
        } else {
            builder.append("The current reporting points to these practical takeaways:\n\n");
            for (TrendHeadline headline : headlines.stream().limit(2).toList()) {
                builder.append("{% details ").append(headline.title()).append(" %}\n");
                builder.append("Source: ").append(headline.source() == null ? "Unknown source" : headline.source()).append("\n\n");
                builder.append("Summary: ").append(headline.summary() == null || headline.summary().isBlank() ? "No concise summary was available." : headline.summary()).append("\n\n");
                if (headline.articleDetails() != null && headline.articleDetails().content() != null && !headline.articleDetails().content().isBlank()) {
                    builder.append(headline.articleDetails().content()).append("\n\n");
                }
                builder.append("Reference: ").append(headline.link()).append("\n");
                builder.append("{% enddetails %}\n\n");
            }
        }

        builder.append("## What Readers Should Watch Next\n\n");
        builder.append("- Watch for official statements, notices, or timelines tied to **").append(trend).append("**.\n");
        builder.append("- Compare updates across reliable sources before taking action.\n");
        builder.append("- Note any changes in dates, eligibility, results, or policy impact.\n\n");

        List<String> mediaUrls = headlineMediaResolver.resolveAdditionalMedia(headlines, 2).stream()
                .map(HeadlineMediaResolver.ResolvedMedia::url)
                .toList();
        if (!mediaUrls.isEmpty()) {
            builder.append("## Related Media\n\n");
            for (String mediaUrl : mediaUrls) {
                builder.append(renderMediaBlock(trend, mediaUrl)).append("\n\n");
            }
        }

        builder.append("## FAQ\n\n");
        builder.append("{% details What is the main takeaway? %}\n");
        builder.append("Follow verified updates and focus on the next official development related to ").append(trend).append(".\n");
        builder.append("{% enddetails %}\n\n");
        builder.append("{% details Is the trend fully confirmed? %}\n");
        builder.append("Not always. Treat fast-moving topics carefully until the key details are confirmed.\n");
        builder.append("{% enddetails %}\n");
        return builder.toString();
    }

    private String renderMediaBlock(String trend, String mediaUrl) {
        String lower = mediaUrl.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif")) {
            return "![Supporting visual for " + trend + "](" + mediaUrl + ")";
        }
        return "{% embed " + mediaUrl + " %}";
    }

    private List<String> buildTags(String trend, List<TrendHeadline> headlines) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        collectTagCandidates(tags, trend, 3);
        headlines.stream().limit(3).forEach(headline -> {
            collectTagCandidates(tags, headline.title(), 1);
            collectTagCandidates(tags, headline.source(), 1);
        });
        return new ArrayList<>(tags).stream().limit(8).toList();
    }

    private List<String> buildKeywords(String trend, List<TrendHeadline> headlines) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addKeyword(keywords, trendNormalizer.clean(trend));
        headlines.stream().limit(4).forEach(headline -> {
            addKeyword(keywords, headline.title());
            if (headline.articleDetails() != null) {
                addKeyword(keywords, headline.articleDetails().description());
            }
        });
        return new ArrayList<>(keywords).stream().limit(10).toList();
    }

    private void collectTagCandidates(LinkedHashSet<String> tags, String text, int maxFromText) {
        if (text == null || text.isBlank()) {
            return;
        }
        int added = 0;
        for (String part : SPLIT_PATTERN.split(text.toLowerCase(Locale.ROOT))) {
            String candidate = normalizeTag(part);
            if (candidate.isBlank()) {
                continue;
            }
            if (tags.add(candidate)) {
                added++;
            }
            if (added >= maxFromText || tags.size() >= 8) {
                return;
            }
        }
        String slug = normalizeTag(trendNormalizer.slug(text));
        if (!slug.isBlank()) {
            tags.add(slug);
        }
    }

    private String normalizeTag(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.length() > 20) {
            normalized = normalized.substring(0, 20).replaceAll("-+$", "");
        }
        if (normalized.length() < 2 || List.of("the", "and", "for", "with", "from", "this", "that", "news").contains(normalized)) {
            return "";
        }
        return normalized;
    }

    private void addKeyword(LinkedHashSet<String> keywords, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return;
        }
        boolean alreadyPresent = keywords.stream()
                .anyMatch(existing -> existing.equalsIgnoreCase(normalized));
        if (!alreadyPresent) {
            keywords.add(normalized);
        }
    }
}
