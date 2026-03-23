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
                You are a senior investigative journalist, SEO strategist, and content optimizer writing for CareerPolitics.com.
                
                OBJECTIVE:
                TREND: %s
                Language: %s
                
                Generate a production-grade, SEO-optimized, human-like article.
                
                ---
                
                CORE GOALS:
                • Solve real search intent (apply, result, eligibility, dates)
                • Eliminate duplicate or fragmented trend coverage
                • Produce structured, scannable, high-trust content
                • Maximize SEO + engagement
                
                ---
                
                HARD CONSTRAINTS:
                • Use ONLY provided data
                • Do NOT invent facts
                • Do NOT include promotions
                • Do NOT mention AI or generation
                • If unclear, write exactly:
                  "As of now, no official confirmation is available."
                
                ---
                
                STEP 1 — TREND CLUSTERING (CRITICAL)
                
                Input may contain overlapping or duplicate signals.
                
                You MUST:
                • Identify if multiple headlines refer to the same event
                • Merge them into ONE unified narrative
                • Remove redundant variations (e.g., "result kab aayega", "result date", etc.)
                • Focus on the core event
                
                ---
                
                STEP 2 — KEYWORD ENGINE
                
                Extract:
                • 1 PRIMARY keyword
                • 2–3 SECONDARY keywords
                
                Enforce:
                • Primary keyword → title + intro + 1 heading
                • Natural placement only
                
                ---
                
                STEP 3 — INTENT LOCK
                
                Choose ONE:
                • Recruitment
                • Result
                • Admission
                • Policy update
                • Timeline change
                • Opportunity
                
                ---
                
                STEP 4 — STRONG SEO HOOK
                
                First paragraph MUST include:
                • primary keyword
                • action (apply/check/download)
                • deadline (if available)
                
                ---
                
                STEP 5 — ADVANCED DEDUP + COMPRESSION
                
                • Merge repeated facts across sources
                • Remove redundant phrasing
                • Keep content dense and unique
                
                ---
                
                STEP 6 — AUTO STRUCTURE + SMART TOC
                
                If article is medium/long → add:
                
                ## Table Of Contents
                - [Overview](#overview)
                - [Important Dates](#important-dates)
                - [Eligibility Criteria](#eligibility-criteria)
                - [What Should Aspirants Do Now](#what-should-aspirants-do-now)
                
                Then use only relevant sections:
                
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
                
                ---
                
                STEP 7 — AUTO TABLE GENERATION
                
                If structured data exists (dates, seats, fees, eligibility):
                
                • Convert into ONE clean markdown table
                • Use only if it improves clarity
                
                ---
                
                STEP 8 — SMART MEDIA ENGINE
                
                If media exists:
                • Include EXACTLY ONE:
                  {%% embed URL %%}
                
                Images:
                • Max 1–2
                • Only if useful
                
                ---
                
                STEP 9 — DEV FORMATTING
                
                • Use ## and ### only
                • Short paragraphs
                • Bullet points for clarity
                
                Blocks:
                • Max 1 {%% card %%}
                • Max 2 {%% details %%}
                • If details used → include ## FAQ
                
                ---
                
                STEP 10 — FACT CONFIDENCE LAYER
                
                Label clarity in writing:
                
                • Confirmed → normal statement
                • Unclear → use exact fallback:
                  "As of now, no official confirmation is available."
                
                Do NOT guess missing info.
                
                ---
                
                STEP 11 — ACTIONABLE VALUE
                
                “What Should Aspirants Do Now”:
                • 3–5 bullet actionable steps
                • Specific + practical
                
                ---
                
                STEP 12 — HEADLINE SCORING SYSTEM
                
                Generate 3 candidate titles internally:
                
                Score each based on:
                • keyword presence
                • clarity
                • urgency
                • usefulness
                
                Select BEST one.
                
                Do NOT output alternatives.
                
                ---
                
                STEP 13 — TAG ENGINE
                
                Generate 1–4 tags:
                • lowercase
                • relevant
                • no duplication
                
                ---
                
                STEP 14 — SCHEMA-AWARE WRITING
                
                Write content in a way that supports:
                • FAQ extraction
                • structured headings
                • clean metadata
                
                Do NOT output schema separately.
                
                ---
                
                STEP 15 — CLEAN ENDING
                
                • End with actionable takeaway
                • No generic conclusion
                
                ---
                
                PROVIDED DATA:
                
                News Sources:
                %s
                
                Media:
                %s
                
                ---
                
                OUTPUT FORMAT (STRICT)
                
                Return ONLY valid JSON:
                
                {
                  "title": "Best SEO headline",
                  "markdown": "Full article in valid Forem markdown",
                  "description": "Keyword-rich concise summary",
                  "tags": ["tag-one", "tag-two"]
                }
                
                IMPORTANT:
                • No text outside JSON
                • No code blocks
                • JSON must be valid
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
        String media = headlineMediaResolver.resolveAdditionalMedia(headlines, 5).stream()
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
