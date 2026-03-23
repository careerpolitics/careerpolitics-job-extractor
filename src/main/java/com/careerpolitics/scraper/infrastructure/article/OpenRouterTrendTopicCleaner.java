package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.TrendDiscoveryCandidate;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
public class OpenRouterTrendTopicCleaner implements TrendTopicCleaner {

    private static final Pattern WORD_SPLITTER = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "from", "into", "about", "after", "before", "over",
            "under", "new", "latest", "today", "live", "update", "updates", "breaking", "now",
            "near", "amid", "row", "issue", "issues", "search", "searches"
    );

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
    public List<TrendTopic> cleanTopics(List<TrendDiscoveryCandidate> candidates, int maxTopics) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int limit = Math.max(1, maxTopics);
        if (!aiCleaningEnabled()) {
            return fallbackTopics(candidates, limit);
        }

        try {
            String body = restClient.post()
                    .uri(properties.generation().openRouterBaseUrl() + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.generation().openRouterApiKey())
                    .body(Map.of(
                            "model", properties.generation().openRouterModel(),
                            "messages", List.of(
                                    Map.of("role", "system", "content", "Return only valid JSON."),
                                    Map.of("role", "user", "content", buildPrompt(candidates, limit))
                            )
                    ))
                    .retrieve()
                    .body(String.class);
            return parseTopics(body, limit, candidates);
        } catch (Exception exception) {
            log.warn("AI trend topic cleaning failed. Falling back to heuristic normalization: {}", exception.getMessage());
            return fallbackTopics(candidates, limit);
        }
    }

    private boolean aiCleaningEnabled() {
        return properties.generation().openRouterEnabled()
                && properties.generation().openRouterApiKey() != null
                && !properties.generation().openRouterApiKey().isBlank();
    }

    private String buildPrompt(List<TrendDiscoveryCandidate> candidates, int maxTopics) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < candidates.size(); index++) {
            TrendDiscoveryCandidate candidate = candidates.get(index);
            builder.append(index + 1)
                    .append(". headline: ")
                    .append(safe(candidate.title()))
                    .append(" | breakdowns: ")
                    .append(candidate.breakdowns() == null || candidate.breakdowns().isEmpty()
                            ? "[]"
                            : candidate.breakdowns().toString())
                    .append(" | raw: ")
                    .append(safe(candidate.rawText()))
                    .append("\n");
        }

        return """
                You clean Google Trends trend rows into canonical topics.

                Goals:
                - Read each row's headline, breakdown keywords, and raw text.
                - Infer a short common topic label for the row.
                - Merge rows or keywords that refer to the same event/entity/story into one common topic.
                - Keep topic names concise, human-readable, and publication-safe.
                - Remove UI noise, counts, timestamps, and filler words.
                - Prefer 2 to 6 words.
                - Use title case.

                Return valid JSON only in this format:
                {
                  "topics": [
                    {
                      "name": "Common Topic",
                      "keywords": ["headline or keyword", "another keyword"]
                    }
                  ]
                }

                Constraints:
                - Return at most %d topics.
                - Every keyword should map under one best common topic only.
                - Do not return duplicate or near-duplicate topics.
                - Do not include explanations.

                Input rows:
                %s
                """.formatted(maxTopics, builder);
    }

    List<TrendTopic> parseTopics(String responseBody, int maxTopics, List<TrendDiscoveryCandidate> candidates) throws JsonProcessingException {
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

        if (topics.isEmpty()) {
            return fallbackTopics(candidates, maxTopics);
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

    List<TrendTopic> fallbackTopics(List<TrendDiscoveryCandidate> candidates, int maxTopics) {
        LinkedHashMap<String, TrendTopic> topics = new LinkedHashMap<>();
        for (TrendDiscoveryCandidate candidate : candidates) {
            String label = heuristicTopicLabel(candidate);
            String slug = trendNormalizer.slug(label);
            if (label.isBlank() || slug.isBlank()) {
                continue;
            }
            topics.putIfAbsent(slug, new TrendTopic(label, slug));
            if (topics.size() >= maxTopics) {
                break;
            }
        }
        return new ArrayList<>(topics.values());
    }

    private String heuristicTopicLabel(TrendDiscoveryCandidate candidate) {
        String title = trendNormalizer.clean(candidate.title());
        if (!title.isBlank()) {
            return title;
        }

        Map<String, Integer> tokenCounts = new LinkedHashMap<>();
        List<String> allSignals = new ArrayList<>();
        if (candidate.breakdowns() != null) {
            allSignals.addAll(candidate.breakdowns());
        }
        allSignals.add(candidate.rawText());

        for (String signal : allSignals) {
            for (String token : tokenize(signal)) {
                tokenCounts.merge(token, 1, Integer::sum);
            }
        }

        List<String> selected = tokenCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        if (!selected.isEmpty()) {
            return selected.stream()
                    .map(this::titleCase)
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
        }

        if (candidate.breakdowns() != null && !candidate.breakdowns().isEmpty()) {
            return trendNormalizer.clean(candidate.breakdowns().get(0));
        }
        return trendNormalizer.clean(candidate.rawText());
    }

    private List<String> tokenize(String value) {
        String cleaned = trendNormalizer.slug(value).replace('-', ' ');
        if (cleaned.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : WORD_SPLITTER.split(cleaned.toLowerCase(Locale.ROOT))) {
            if (token.isBlank() || token.length() < 2 || STOPWORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return new ArrayList<>(tokens);
    }

    private String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
