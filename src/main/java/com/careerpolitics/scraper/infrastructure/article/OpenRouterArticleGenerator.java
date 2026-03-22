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
                You are a senior investigative journalist and SEO strategist writing for CareerPolitics.com — focused on government jobs, exams, and policy updates.
                
                OBJECTIVE:
                TREND: %s
                Language: %s
                
                Generate a high-quality, human-like, SEO-optimized article.
                
                ---
                
                CORE GOALS:
                • Solve real user intent (apply, result, eligibility, dates)
                • Be factually accurate and non-repetitive
                • Be scannable and engaging
                • Avoid AI-like writing patterns
                
                ---
                
                HARD CONSTRAINTS:
                • Use ONLY provided data
                • Do NOT invent facts
                • Do NOT include promotions or marketing
                • Do NOT mention AI or generation
                • If unclear, write exactly:
                  "As of now, no official confirmation is available."
                
                ---
                
                STEP 1 — KEYWORD EXTRACTION (MANDATORY)
                
                From the trend, derive a PRIMARY keyword and 2–3 SECONDARY keywords.
                
                Examples:
                • "vmou admission 2026 apply online"
                • "last date vmou form"
                • "b.ed distance eligibility"
                
                You MUST:
                • Use PRIMARY keyword in title
                • Use it in first paragraph
                • Use it in at least one heading
                
                ---
                
                STEP 2 — INTENT LOCK
                
                Choose ONE:
                • Recruitment
                • Result
                • Admission
                • Policy update
                • Timeline change
                • Opportunity
                
                Do NOT mix intents.
                
                ---
                
                STEP 3 — STRONG SEO HOOK
                
                First paragraph MUST:
                • Include primary keyword
                • Include action (apply/check/download)
                • Include deadline (if available)
                
                Avoid generic openings.
                
                ---
                
                STEP 4 — ADVANCED DEDUP ENGINE
                
                • Merge duplicate facts across sources
                • Compress repeated information into one statement
                • Avoid rephrasing same idea again
                • Keep content tight and information-dense
                
                ---
                
                STEP 5 — AUTO STRUCTURE + TOC
                
                If article length > medium, ADD:
                
                ## Table Of Contents
                - [Overview](#overview)
                - [Important Dates](#important-dates)
                - [Eligibility Criteria](#eligibility-criteria)
                - [What Should Aspirants Do Now](#what-should-aspirants-do-now)
                
                Then structure using ONLY relevant sections:
                
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
                
                Rules:
                • Skip empty sections
                • Merge overlapping sections
                • Maintain logical flow
                
                ---
                
                STEP 6 — SMART MEDIA ENGINE
                
                1. If media exists:
                   • Include EXACTLY ONE embed:
                     {%% embed URL %%}
                
                2. Image logic:
                   • Use max 1–2 images
                   • Place only after headings
                   • Use descriptive alt text
                
                3. Do NOT:
                   • Spam media
                   • Use irrelevant embeds
                
                ---
                
                STEP 7 — DEV FORMATTING
                
                • Use ## and ### only
                • Short paragraphs (2–3 lines)
                • Use bullet points for clarity
                
                Blocks:
                • Use max 1 {%% card %%}
                • Use max 2 {%% details %%}
                • If details used → MUST include ## FAQ
                
                ---
                
                STEP 8 — ACTIONABLE VALUE (CRITICAL)
                
                “What Should Aspirants Do Now” MUST include:
                • 3–5 bullet actionable steps
                • Practical guidance (documents, deadlines, preparation)
                
                ---
                
                STEP 9 — SEO PRECISION
                
                Ensure:
                • Keywords appear naturally (no stuffing)
                • Title matches search intent
                • Content answers user query directly
                
                ---
                
                STEP 10 — TAG GENERATION
                
                Generate 1–4 tags:
                • lowercase
                • relevant
                • non-duplicate
                
                Examples:
                ["admission", "b-ed", "distance-education"]
                
                ---
                
                STEP 11 — DUAL TITLE GENERATION (A/B READY)
                
                Generate 2 titles internally:
                • One keyword-focused
                • One user-focused
                
                Return ONLY the best one.
                
                ---
                
                STEP 12 — CLEAN ENDING
                
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
                  "title": "SEO-optimized headline",
                  "markdown": "Full article in valid Forem markdown",
                  "description": "Concise summary with keywords",
                  "tags": ["tag-one", "tag-two"]
                }
                
                IMPORTANT:
                • No text outside JSON
                • No code blocks
                • JSON must be valid and parseable
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
