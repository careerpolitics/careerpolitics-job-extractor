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
                
                Use relevant sections logically. Each section must add new information (no repetition).
                
                ## Overview
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
                
                Additional rules:
                • Prioritize sections that match search intent (jobs, dates, eligibility, salary)
                • Do NOT include empty or weak sections
                • Ensure each section is actionable and information-dense
                
                ---
                
                STEP 4 — FORMATTING (STRICT)
                
                • Use short paragraphs (2–3 lines max)
                • Use bullet points for clarity
                • Keep content highly scannable
                
                Structured formatting rules (VERY IMPORTANT):
                
                1. TABLE USAGE (MANDATORY LOGIC)
                • If the content includes structured data (dates, vacancies, salary, categories), use EXACTLY ONE table
                • Table must be simple (2–4 columns max)
                • Do NOT create multiple tables
                
                2. DETAILS BLOCK (CONTROLLED USAGE)
                • Use at most TWO details blocks
                • Use ONLY for:
                  - Long syllabus
                  - Detailed eligibility breakdown
                  - Extended FAQs (if needed)
                • Do NOT hide critical information inside details
                • Syntax: `{%% details Summary %%} ... {%% enddetails %%}`
                
                3. HIGHLIGHT BLOCK (IMPORTANT)
                • Use at most ONE highlight/card block
                • Use ONLY for critical updates such as:
                  - Last date
                  - Major change
                  - Important warning
                • Syntax: `{%% card %%} ... {%% endcard %%}`
                
                4. CALL TO ACTION (OPTIONAL)
                • If the source data includes an official URL (apply link, notification PDF), you may add ONE CTA at the end.
                • Use this format: `{%% cta URL %%} Click here to ... {%% endcta %%}`
                • Do NOT use CTAs for social media or promotional content.
                
                5. CONTENT DENSITY
                • Every paragraph must add new information
                • Avoid filler, repetition, or generic statements
                
                6. READABILITY
                • Maintain clear section separation
                • Avoid large text blocks
                • Ensure mobile-friendly formatting
                
                ---
                
                STEP 5 — ACCURACY (VERY IMPORTANT)
                • Use ONLY the provided source data
                • Do NOT invent facts
                • If something is unclear, write:
                  "As of now, no official confirmation is available."
                
                ---
                
                STEP 6 — SEO OPTIMIZATION (HIGH PRIORITY)
                
                • Identify primary search intent (e.g., "apply online", "last date", "eligibility", "salary")
                • Ensure the article directly answers these queries
                
                Featured snippet optimization:
                • Provide clear, direct answers in the first 1–2 lines of relevant sections
                • Use bullet points for list-type queries
                • Use table for structured queries
                
                Keyword usage:
                • Naturally include high-intent keywords:
                  apply online, last date, eligibility, syllabus, salary, notification
                • Avoid keyword stuffing
                
                Search behavior optimization:
                • Assume reader wants quick, actionable answers
                • Reduce scrolling effort by structuring content logically
                
                ---
                
                STEP 7 — FAQ GENERATION (MANDATORY)
                
                • Include 3 to 5 FAQs within the FAQ section
                • Questions must reflect real search queries:
                  - What is the last date?
                  - Who is eligible?
                  - What is the salary?
                  - How to apply?
                • Answers must be:
                  - Direct
                  - Fact-based
                  - 1–3 lines max
                • Do NOT repeat content unnecessarily
                • If data is missing, write:
                  "As of now, no official confirmation is available."
                • Formatting options (choose one that fits the article):
                  - Simple Q&A list: **Q:** ... **A:** ...
                  - Collapsible sections: `{%% details Question %%} Answer {%% enddetails %%}`
                
                ---
                
                STEP 8 — TITLE
                
                • Make it clear, specific, and SEO-friendly
                • Use numbers, salary, or dates when useful
                • Avoid vague or clickbait titles
                
                ---
                
                STEP 9 — DESCRIPTION
                
                • Write one concise, high-information summary
                • Include key elements such as role, dates, or opportunity
                • Keep it optimized for search preview (meta description)
                • Do NOT repeat the title
                
                ---
                
                STEP 10 — TAGS
                
                • Choose 4 tags
                • Tags must reflect:
                  - Exam or job name
                  - Category (government-jobs, results, admit-card, etc.)
                • Keep them concise and SEO-relevant
                • Avoid generic or duplicate tags
                
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
                  "tags": ["tag-one", "tag-two","tag-three","tag-four"]
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
