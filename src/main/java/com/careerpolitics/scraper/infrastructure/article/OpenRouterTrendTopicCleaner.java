package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.TrendTopic;
import com.careerpolitics.scraper.domain.port.TrendTopicCleaner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenRouterTrendTopicCleaner implements TrendTopicCleaner {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TrendingProperties properties;
    private final TrendNormalizer trendNormalizer;

    public OpenRouterTrendTopicCleaner(RestClient restClient,
                                       ObjectMapper objectMapper,
                                       TrendingProperties properties,
                                       TrendNormalizer trendNormalizer) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.trendNormalizer = trendNormalizer;
    }

    @Override
    public List<TrendTopic> cleanTopics(String tableHtml, int maxTopics) {
        if (tableHtml == null || tableHtml.isBlank() || !aiCleaningEnabled()) {
            return List.of();
        }

        int limit = Math.max(1, maxTopics);
        try {
            String body = restClient.post()
                    .uri(properties.generation().openRouterBaseUrl() + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.generation().openRouterApiKey())
                    .body(Map.of(
                            "model", properties.generation().openRouterModel(),
                            "messages", List.of(
                                    Map.of("role", "system", "content", "Return only valid JSON."),
                                    Map.of("role", "user", "content", buildPrompt(tableHtml, limit))
                            )
                    ))
                    .retrieve()
                    .body(String.class);
            return parseTopics(body, limit);
        } catch (Exception exception) {
            log.warn("AI trend topic cleaning failed for extracted table HTML: {}", exception.getMessage());
            return List.of();
        }
    }

    private boolean aiCleaningEnabled() {
        return properties.generation().openRouterEnabled()
                && properties.generation().openRouterApiKey() != null
                && !properties.generation().openRouterApiKey().isBlank();
    }

    private String buildPrompt(String tableHtml, int maxTopics) {
        return """
                Read the Google Trends HTML table below and return structured trend topics.

                Requirements:
                - Parse the table rows and their related breakdown keywords.
                - Merge related keywords under one common topic.
                - Remove UI noise, timestamps, search counts, and duplicate rows.
                - Keep topic names short, clear, and human-readable.

                Return valid JSON only in this format:
                {
                  "topics": [
                    {
                      "name": "Common Topic",
                      "keywords": ["keyword one", "keyword two"]
                    }
                  ]
                }

                Constraints:
                - Return at most %d topics.
                - Each keyword should belong to only one best topic.
                - Do not include explanations or markdown fences.

                HTML table:
                %s
                """.formatted(maxTopics, tableHtml);
    }

    List<TrendTopic> parseTopics(String responseBody, int maxTopics) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.at("/choices/0/message/content").asText();
        JsonNode payload = objectMapper.readTree(extractJsonPayload(content));

        LinkedHashMap<String, TrendTopic> topics = new LinkedHashMap<>();
        for (JsonNode topicNode : payload.path("topics")) {
            String name = trendNormalizer.clean(topicNode.path("name").asText(""));
            String slug = trendNormalizer.slug(name);
            if (name.isBlank() || slug.isBlank()) {
                continue;
            }
            topics.putIfAbsent(slug, new TrendTopic(name, slug));
            if (topics.size() >= maxTopics) {
                break;
            }
        }
        return new ArrayList<>(topics.values());
    }

    String extractJsonPayload(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }
}
