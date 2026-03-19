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

    public OpenRouterArticleGenerator(RestClient restClient,
                                      ObjectMapper objectMapper,
                                      TrendingProperties properties,
                                      TemplateArticleGenerator fallbackGenerator) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.fallbackGenerator = fallbackGenerator;
    }

    @Override
    public boolean supportsAi() {
        return true;
    }

    @Override
    public GeneratedArticleDraft generate(String trend, String language, List<TrendHeadline> headlines) {
        try {
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
            JsonNode article = objectMapper.readTree(content);
            String title = article.path("title").asText(trend + " is trending: what matters now");
            String markdown = article.path("markdown").asText(fallbackGenerator.generate(trend, language, headlines).markdown());
            List<String> tags = List.copyOf(new LinkedHashSet<>(fallbackGenerator.generate(trend, language, headlines).tags()));
            List<String> keywords = List.copyOf(new LinkedHashSet<>(fallbackGenerator.generate(trend, language, headlines).keywords()));
            return new GeneratedArticleDraft(title, markdown, tags, keywords, "open-router");
        } catch (Exception exception) {
            return fallbackGenerator.generate(trend, language, headlines);
        }
    }

    private String buildPrompt(String trend, String language, List<TrendHeadline> headlines) {
        StringBuilder builder = new StringBuilder();
        builder.append("Create a concise article in ").append(language).append(" about trend: ").append(trend).append(". ");
        builder.append("Use these headlines:\n");
        for (TrendHeadline headline : headlines) {
            builder.append("- ").append(headline.title()).append(" | ").append(headline.source()).append("\n");
        }
        return builder.toString();
    }
}
