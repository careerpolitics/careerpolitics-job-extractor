package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.ArticleGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    private static final int MAX_TAGS = 4;
    private static final int MAX_KEYWORDS = 10;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TrendingProperties properties;
    private final HeadlineMediaResolver headlineMediaResolver;

    public OpenRouterArticleGenerator(RestClient restClient,
                                      ObjectMapper objectMapper,
                                      TrendingProperties properties,
                                      HeadlineMediaResolver headlineMediaResolver) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.headlineMediaResolver = headlineMediaResolver;
    }

    @Override
    public GeneratedArticleDraft generate(String trend, String language, List<TrendHeadline> headlines) {
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
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.at("/choices/0/message/content").asText();
            JsonNode article = objectMapper.readTree(extractJsonPayload(content));
            String title = requiredText(article, "title");
            String markdown = requiredText(article, "markdown");
            List<String> tags = sanitizeTerms(article.path("tags"), MAX_TAGS);
            List<String> keywords = sanitizeTerms(article.path("keywords"), MAX_KEYWORDS);
            if (tags.isEmpty()) {
                throw new IllegalStateException("AI response did not include usable tags.");
            }
            if (keywords.isEmpty()) {
                throw new IllegalStateException("AI response did not include usable keywords.");
            }
            return new GeneratedArticleDraft(title, markdown, tags, keywords, "open-router");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse AI article response.", exception);
        }
    }

    private String buildPrompt(String trend, String language, List<TrendHeadline> headlines) {
        String sourcesText = buildSourcesText(headlines);
        String mediaText = buildMediaText(headlines);
        return String.format("""
                You are writing a polished article for CareerPolitics.com.

                Trend: %s
                Language: %s

                Write one complete article in valid Forem markdown.
                The output must be simple, clean, factual, and non-repetitive.

                Hard requirements:
                - Use only the supplied source material.
                - Do not mention AI, prompts, generation steps, or code.
                - Do not include promotional lines, subscription prompts, Telegram mentions, or marketing copy.
                - Do not include any extra fields beyond title, markdown, tags, and keywords.
                - If a fact is unclear or unconfirmed, say: "As of now, no official confirmation is available."
                - Do not invent facts.

                Article template:
                ## Overview
                ## Why This Is Trending
                ## Key Updates
                ## What This Means
                ## What Readers Should Watch Next
                ## FAQ

                Formatting rules:
                - Use ## headings only.
                - Use short paragraphs.
                - Use bullets only when they improve clarity.
                - Use at most one table, only if it adds value.
                - Use at most two details blocks, only if they add value.
                - Do not use CTA blocks.
                - Use media only when genuinely useful to the article.
                - Keep the markdown readable and publication-ready.

                Tags and keywords rules:
                - You must choose them yourself based on the article you write.
                - Return exactly 1 to 4 tags.
                - Tags must be concise, relevant, Forem-friendly, and non-duplicative.
                - Return 1 to 10 keywords.
                - Keywords must be relevant, concise, and non-duplicative.
                - Do not repeat the same wording across tags and keywords unless it is clearly necessary.

                Source material:
                %s

                Relevant media:
                %s

                Return ONLY valid JSON in this exact shape:
                {
                  "title": "Clear article title",
                  "markdown": "Full article in markdown",
                  "tags": ["tag-one", "tag-two"],
                  "keywords": ["keyword one", "keyword two"]
                }
                """, trend, language, sourcesText, mediaText);
    }

    List<String> sanitizeTerms(JsonNode node, int maxTerms) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String normalized = item.asText("").replaceAll("\\s+", " ").trim();
            if (normalized.isBlank()) {
                continue;
            }
            String uniquenessKey = normalized.toLowerCase(Locale.ROOT);
            boolean duplicate = values.stream()
                    .map(existing -> existing.toLowerCase(Locale.ROOT))
                    .anyMatch(uniquenessKey::equals);
            if (duplicate) {
                continue;
            }
            values.add(normalized);
            if (values.size() >= maxTerms) {
                break;
            }
        }
        return new ArrayList<>(values);
    }

    private String requiredText(JsonNode article, String fieldName) {
        String value = article.path(fieldName).asText("").trim();
        if (value.isBlank()) {
            throw new IllegalStateException("AI response did not include a usable " + fieldName + ".");
        }
        return value;
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
        String media = headlineMediaResolver.resolveAdditionalMedia(headlines, 3).stream()
                .map(mediaItem -> "- " + mediaItem.url())
                .reduce("", (left, right) -> left + right + "\n");
        return media.isBlank() ? "- No additional media supplied." : media;
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
