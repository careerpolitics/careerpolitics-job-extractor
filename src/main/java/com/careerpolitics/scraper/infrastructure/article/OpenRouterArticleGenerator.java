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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class OpenRouterArticleGenerator implements ArticleGenerator {

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
                                    Map.of("role", "system", "content", "Return only JSON with title and markdown fields."),
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
            List<String> tags = List.copyOf(new LinkedHashSet<>(fallbackDraft.tags()));
            List<String> keywords = List.copyOf(new LinkedHashSet<>(fallbackDraft.keywords()));
            return new GeneratedArticleDraft(title, markdown, tags, keywords, "open-router");
        } catch (Exception exception) {
            return fallbackGenerator.generate(trend, language, headlines);
        }
    }

    private String buildPrompt(String trend, String language, List<TrendHeadline> headlines) {
        String coverImage = headlineMediaResolver.resolveCoverMediaUrl(headlines);
        String sourcesText = buildSourcesText(headlines);
        String mediaText = buildMediaText(headlines);
        return String.format("""
                You are a senior investigative journalist and SEO strategist writing for CareerPolitics.com — a platform focused on government jobs, exams, and policy updates.

                Your job is to create original, human-like, high-authority content optimized for:
                • Google ranking
                • Click-through rate (CTR)
                • Dwell time
                • Aspirant usefulness

                ---

                ## OBJECTIVE

                TREND: %s
                Language: %s

                Write a high-quality article that:
                • Solves real search intent (jobs, exams, results, dates)
                • Provides actionable insights
                • Avoids generic AI writing

                Use sources as input, but merge them into one cohesive story.

                ---

                ## STEP 1 — CHOOSE ANGLE (MANDATORY)

                Pick ONE:
                • Recruitment / exam notification
                • Policy impact
                • Timeline change
                • Controversy
                • Opportunity for aspirants

                Stick to it throughout.

                ---

                ## STEP 2 — ANTI-AI RULES

                STRICT:
                • No source-by-source summary
                • No repetitive phrases ("In today's world", etc.)
                • No fluff
                • Use natural transitions
                • Write like a journalist, not AI

                ---

                ## STEP 3 — FOREM FEATURES (MANDATORY USAGE)

                You MUST include:

                1. At least 1 CARD block
                {%% card %%}
                📅 Important Date
                💰 Salary / Key Info
                📢 Critical Update
                {%% endcard %%}

                2. At least 2 CTA blocks
                (after intro + before conclusion)
                {%% cta https://careerpolitics.com %%}
                Join our Telegram for job alerts
                {%% endcta %%}

                3. At least 2 DETAILS blocks
                (for FAQ / syllabus / eligibility)

                4. At least 1 TABLE
                (for dates / salary / vacancies)

                5. Optional embeds
                Use raw media URLs (YouTube / GIF / image / video) if relevant.
                Use one strong visual as the cover image and consider other relevant media in markdown only when it genuinely helps the article.

                ---

                ## STEP 4 — SEO STRUCTURE (VERY IMPORTANT)

                Include relevant sections (adapt based on topic):

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

                If not applicable, skip logically.

                ---

                ## STEP 5 — WRITING STYLE

                • Short paragraphs (2–3 lines)
                • Bullet points for clarity
                • Bold key terms
                • No long text blocks

                ---

                ## STEP 6 — ACCURACY

                • Use only provided data
                • If unclear:
                  "As of now, no official confirmation is available."
                • Do NOT invent facts

                ---

                ## STEP 7 — SEO RULES

                • Identify primary search query
                • Title must directly match it
                • Include keywords naturally:
                  - apply online
                  - last date
                  - eligibility
                  - syllabus
                  - salary

                ---

                ## STEP 8 — TITLE OPTIMIZATION

                Title should:
                • Include numbers / salary / dates
                • Be clear and specific
                • Target search intent

                Example:
                "SSC CGL 2026 Notification Out – Apply Online, Salary ₹44,900+"

                ---

                ## PROVIDED DATA

                Cover Image: %s

                News Sources:
                %s

                Media:
                %s

                ---

                ## OUTPUT FORMAT (STRICT)

                Return ONLY valid JSON:

                {
                  "title": "SEO optimized headline",
                  "markdown": "Full article in markdown with Forem liquid tags",
                  "tags": ["max 8"],
                  "keywords": ["max 10"]
                }

                No extra text outside JSON.
                """, trend, language, coverImage, sourcesText, mediaText);
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
        return builder.toString();
    }

    private String buildMediaText(List<TrendHeadline> headlines) {
        StringBuilder builder = new StringBuilder();
        for (HeadlineMediaResolver.ResolvedMedia media : headlineMediaResolver.resolveAdditionalMedia(headlines, 6)) {
            builder.append("- ").append(safe(media.type()))
                    .append(": ").append(safe(media.url())).append("\n");
        }
        return builder.isEmpty() ? "n/a" : builder.toString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }

    String extractJsonPayload(String content) {
        if (content == null) {
            return "{}";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            if (firstLineBreak >= 0) {
                trimmed = trimmed.substring(firstLineBreak + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }
}
