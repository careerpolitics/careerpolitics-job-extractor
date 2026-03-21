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
                                Map.of("role", "system", "content", "Return only valid JSON with title, markdown, description, and tags fields."),
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
            String description = requiredText(article, "description");
            List<String> tags = sanitizeTerms(article.path("tags"), MAX_TAGS);
            if (tags.isEmpty()) {
                throw new IllegalStateException("AI response did not include usable tags.");
            }
            return new GeneratedArticleDraft(title, markdown, tags, description, "open-router");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse AI article response.", exception);
        }
    }

    private String buildPrompt(String trend, String language, List<TrendHeadline> headlines) {
        String sourcesText = buildSourcesText(headlines);
        String mediaText = buildMediaText(headlines);
        return String.format("""
                You are a senior investigative journalist and SEO strategist writing for CareerPolitics.com — a platform focused on government jobs, exams, and policy updates.

                OBJECTIVE:
                TREND: %s
                Language: %s

                Write a high-quality, human-like article that:
                • Solves real search intent (jobs, exams, results, dates)
                • Provides actionable insights for aspirants
                • Is clear, factual, and non-repetitive
                • Feels natural and not AI-generated

                Additional hard requirements:
                • Use only the provided source data
                • Do not invent facts
                • Do not mention AI, prompts, generation steps, or code
                • Do not include promotional lines, Telegram mentions, subscription prompts, or marketing copy
                • Do not include any extra fields beyond title, markdown, description, and tags
                • If something is unclear, write exactly: "As of now, no official confirmation is available."

                ---

                STEP 1 — CHOOSE A CLEAR ANGLE
                Pick ONE and stay consistent:
                • Recruitment / exam notification
                • Policy impact
                • Timeline change
                • Controversy
                • Opportunity for aspirants

                ---

                STEP 2 — WRITING RULES (STRICT)
                • No fluff or generic phrases
                • No repetition
                • No source-by-source narration
                • Use smooth, natural transitions
                • Write like a professional journalist

                ---

                STEP 3 — STRUCTURE (USE ONLY WHAT FITS)
                Use relevant sections logically:

                ## Overview
                ## Why This Is Trending
                ## Important Dates
                ## Vacancy Details
                ## Eligibility Criteria
                ## Salary / Pay Scale
                ## Selection Process
                ## Exam Pattern / Syllabus
                ## Timeline of Events
                ## Impact on Aspirants
                ## What Should Aspirants Do Now
                ## FAQ

                Do not force sections that are not relevant.

                ---

                STEP 4 — FORMATTING
                • Use short paragraphs (2–3 lines)
                • Use bullet points where helpful
                • Use at most ONE table (only if it adds value)
                • Use at most TWO details blocks (only if useful)
                • Keep markdown clean, readable, and publication-ready
                • Do not use CTA blocks

                Rich formatting and media instructions:
                • Use media only when it genuinely improves the article
                • If you use an image, use standard markdown image syntax with clear alt text
                • If you use an external media URL that suits embedding, use Forem embed syntax: {% embed URL %}
                • Never output raw HTML embeds
                • Prefer a clean article first; use rich elements only where they add clarity

                ---

                STEP 5 — ACCURACY (VERY IMPORTANT)
                • Use ONLY the provided source data
                • Do NOT invent facts
                • If something is unclear, write:
                  "As of now, no official confirmation is available."

                ---

                STEP 6 — SEO OPTIMIZATION
                • Identify the likely search intent
                • Title must match search intent clearly
                • Naturally include keywords such as:
                  apply online, last date, eligibility, syllabus, salary
                • Avoid keyword stuffing

                ---

                STEP 7 — TITLE
                • Make it clear, specific, and SEO-friendly
                • Use numbers, salary, or dates when useful
                • Avoid vague or clickbait titles

                ---

                STEP 8 — DESCRIPTION
                • Write one concise plain-text summary
                • Keep it informative and suitable for metadata
                • Do NOT repeat the title word-for-word

                ---

                STEP 9 — TAGS
                • Choose them yourself based on the article
                • Return 1 to 4 tags only
                • Tags must be relevant, concise, and non-duplicative
                • Follow Forem-style tagging (simple, lowercase preferred)

                ---

                PROVIDED DATA:

                News Sources:
                %s

                Media:
                %s

                ---

                OUTPUT FORMAT (STRICT)

                Return ONLY valid JSON in this exact structure:

                {
                  "title": "Clear and SEO-optimized headline",
                  "markdown": "Full article in valid Forem markdown",
                  "description": "Short plain-text summary",
                  "tags": ["tag-one", "tag-two"]
                }

                IMPORTANT:
                • Do not include any text outside JSON
                • Do not include code blocks
                • Ensure the JSON is valid and parseable
                """, trend, language, sourcesText, mediaText);
    }

    List<String> sanitizeTerms(JsonNode node, int maxTerms) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String normalized = sanitizeTag(item.asText(""));
            if (normalized.isBlank()) {
                continue;
            }
            values.add(normalized);
            if (values.size() >= maxTerms) {
                break;
            }
        }
        return new ArrayList<>(values);
    }

    private String sanitizeTag(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
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
