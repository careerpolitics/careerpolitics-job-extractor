package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.ArticleGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class OpenRouterArticleGenerator implements ArticleGenerator {

    private static final int MAX_TAGS = 8;
    private static final int MAX_KEYWORDS = 10;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TrendingProperties properties;
    private final TemplateArticleGenerator fallbackGenerator;
    private final HeadlineMediaResolver headlineMediaResolver;

    public OpenRouterArticleGenerator(RestClient restClient,
                                      ObjectMapper objectMapper,
                                      TrendingProperties properties,
                                      TemplateArticleGenerator fallbackGenerator,
                                      HeadlineMediaResolver headlineMediaResolver) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.fallbackGenerator = fallbackGenerator;
        this.headlineMediaResolver = headlineMediaResolver;
    }

    @Override
    public boolean supportsAi() {
        return true;
    }

    @Override
    public GeneratedArticleDraft generate(String trend, String language, List<TrendHeadline> headlines) {
        try {
            GeneratedArticleDraft fallbackDraft = fallbackGenerator.generate(trend, language, headlines);
            String body = restClient.post()
                    .uri(properties.generation().openRouterBaseUrl() + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.generation().openRouterApiKey())
                    .body(Map.of(
                            "model", properties.generation().openRouterModel(),
                            "messages", List.of(
                                    Map.of("role", "system", "content", "Return only valid JSON with title, markdown, tags, and keywords fields."),
                                    Map.of("role", "user", "content", buildPrompt(trend, language, headlines))
                            )
                    ))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            String content = root.at("/choices/0/message/content").asText();
            JsonNode article = objectMapper.readTree(extractJsonPayload(content));
            String title = article.path("title").asText(trend + " is trending: what matters now");
            String markdown = article.path("markdown").asText(fallbackDraft.markdown());
            List<String> tags = sanitizeTerms(article.path("tags"), MAX_TAGS, true);
            List<String> keywords = sanitizeTerms(article.path("keywords"), MAX_KEYWORDS, false);
            if (tags.isEmpty()) {
                tags = fallbackDraft.tags();
            }
            if (keywords.isEmpty()) {
                keywords = fallbackDraft.keywords();
            }
            return new GeneratedArticleDraft(title, markdown, tags, keywords, "open-router");
        } catch (Exception exception) {
            return fallbackGenerator.generate(trend, language, headlines);
        }
    }

    private String buildPrompt(String trend, String language, List<TrendHeadline> headlines) {
        String sourcesText = buildSourcesText(headlines);
        String mediaText = buildMediaText(headlines);
        return String.format("""
                You are a senior journalist writing a clean, useful article for CareerPolitics.com.

                TREND: %s
                Language: %s

                Write an original article that focuses on the most useful angle for readers based on the source material.
                Keep it factual, concise, and easy to scan.

                Requirements:
                - Use the provided article template structure.
                - Do not mention code, prompts, generation steps, or AI.
                - Do not include any extra metadata fields beyond title, markdown, tags, and keywords.
                - Do not include promotional lines, marketing copy, Telegram mentions, or calls to join/follow/subscribe.
                - Keep the article simple and clean.
                - Avoid repetition across sections.
                - If information is unclear, say: "As of now, no official confirmation is available."
                - Use only the provided source material. Do not invent facts.

                Article structure:
                ## Overview
                ## Why This Is Trending
                ## Key Updates
                ## What This Means
                ## What Readers Should Watch Next
                ## FAQ

                Formatting rules:
                - Start body sections with ## headings only.
                - Use short paragraphs and bullets where helpful.
                - Use at most one card block, up to two details blocks, and one table only if they add value.
                - Do not use CTA blocks.
                - Use media only when genuinely relevant.
                - Keep markdown valid for Forem.

                Tags and keywords rules:
                - Decide the most appropriate tags and keywords yourself from the article content.
                - Keep them relevant, concise, lowercase where appropriate, and properly formatted for Forem.
                - Do not repeat or closely duplicate tags or keywords.
                - Return up to 8 tags and up to 10 keywords.

                Provided sources:
                %s

                Relevant media:
                %s

                Return ONLY valid JSON in this shape:
                {
                  "title": "Clear article title",
                  "markdown": "Full article in markdown",
                  "tags": ["tag-one", "tag-two"],
                  "keywords": ["keyword one", "keyword two"]
                }
                """, trend, language, sourcesText, mediaText);
    }

    List<String> sanitizeTerms(JsonNode node, int maxTerms, boolean slugify) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (value == null) {
                continue;
            }
            String normalized = value.trim();
            if (normalized.isBlank()) {
                continue;
            }
            normalized = slugify ? toForemTag(normalized) : normalized.replaceAll("\\s+", " ");
            if (normalized.isBlank()) {
                continue;
            }
            String uniquenessKey = normalized.toLowerCase(Locale.ROOT);
            if (values.stream().map(existing -> existing.toLowerCase(Locale.ROOT)).anyMatch(uniquenessKey::equals)) {
                continue;
            }
            values.add(normalized);
            if (values.size() >= maxTerms) {
                break;
            }
        }
        return new ArrayList<>(values);
    }

    private String toForemTag(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.length() > 20) {
            normalized = normalized.substring(0, 20).replaceAll("-+$", "");
        }
        return normalized;
    }

    private String buildSourcesText(List<TrendHeadline> headlines) {
        StringBuilder builder = new StringBuilder();
        for (TrendHeadline headline : headlines) {
            builder.append("- Title: ").append(headline.title())
                    .append(" | Source: ").append(headline.source())
                    .append(" | Summary: ").append(safe(headline.summary()))
                    .append("\n");
            if (headline.articleDetails() != null) {
                builder.append("  Description: ").append(safe(headline.articleDetails().description())).append("\n");
                builder.append("  Content excerpt: ").append(safe(headline.articleDetails().content())).append("\n");
            }
        }
        return builder.toString().isBlank() ? "- No source details were available." : builder.toString();
    }

    private String buildMediaText(List<TrendHeadline> headlines) {
        return headlineMediaResolver.resolveAdditionalMedia(headlines, 3).stream()
                .map(media -> "- " + media.url())
                .reduce("", (left, right) -> left + right + "\n");
    }

    private String safe(String value) {
        return Objects.toString(value, "").replaceAll("\\s+", " ").trim();
    }

    String extractJsonPayload(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
