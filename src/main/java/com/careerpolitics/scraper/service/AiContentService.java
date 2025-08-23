package com.careerpolitics.scraper.service;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiContentService {

    private final WebClient webClient;

    @Value("${AI_CONTENT_PROVIDER:}")
    private String providerOverride;

    @Value("${GOOGLE_API_KEY:}")
    private String googleApiKey;

    @Value("${OPENAI_API_KEY:}")
    private String openAiApiKey;

    @Value("${OPENROUTER_API_KEY:}")
    private String openRouterApiKey;

    public String enhanceDescription(String rawTitle, String rawDescription) {
        String prompt = "Improve and expand this government job post intro in 2-3 concise, SEO-friendly sentences. Use neutral tone, include key numbers if present. Title: '" +
                safe(rawTitle) + "'. Intro: '" + safe(rawDescription) + "'";
        try {
            // Select provider by override or available keys
            String provider = resolveProvider();
            switch (provider) {
                case "gemini":
                    return callGemini(prompt);
                case "openai":
                    return callOpenAi(prompt);
                case "openrouter":
                    return callOpenRouter(prompt);
                default:
                    return callPollinationsFallback(prompt);
            }
        } catch (Exception ex) {
            return rawDescription;
        }
    }

    private String resolveProvider() {
        if (providerOverride != null && !providerOverride.isBlank()) {
            String p = providerOverride.trim().toLowerCase();
            if (p.equals("gemini") || p.equals("openai") || p.equals("openrouter")) return p;
        }
        if (googleApiKey != null && !googleApiKey.isBlank()) return "gemini";
        if (openAiApiKey != null && !openAiApiKey.isBlank()) return "openai";
        if (openRouterApiKey != null && !openRouterApiKey.isBlank()) return "openrouter";
        return "pollinations";
    }

    private String callGemini(String prompt) {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + googleApiKey;
        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        return webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> extractGeminiText(resp, prompt))
                .onErrorReturn(prompt)
                .blockOptional()
                .orElse(prompt);
    }

    private String extractGeminiText(Map<?, ?> resp, String fallback) {
        try {
            List<?> candidates = (List<?>) resp.get("candidates");
            if (candidates == null || candidates.isEmpty()) return fallback;
            Map<?, ?> first = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) first.get("content");
            List<?> parts = (List<?>) content.get("parts");
            if (parts == null || parts.isEmpty()) return fallback;
            Map<?, ?> part = (Map<?, ?>) parts.get(0);
            Object text = part.get("text");
            return text == null ? fallback : text.toString().trim();
        } catch (Exception e) {
            return fallback;
        }
    }

    private String callOpenAi(String prompt) {
        String endpoint = "https://api.openai.com/v1/chat/completions";
        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "You are a helpful assistant that enhances job post descriptions."),
                Map.of("role", "user", "content", prompt)
        ));
        return webClient.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractOpenAiText)
                .onErrorReturn(prompt)
                .blockOptional()
                .orElse(prompt);
    }

    private String extractOpenAiText(Map<?, ?> resp) {
        try {
            List<?> choices = (List<?>) resp.get("choices");
            Map<?, ?> first = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) first.get("message");
            Object content = message.get("content");
            return content == null ? "" : content.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String callOpenRouter(String prompt) {
        String endpoint = "https://openrouter.ai/api/v1/chat/completions";
        Map<String, Object> body = new HashMap<>();
        body.put("model", "openai/gpt-4o-mini");
        body.put("messages", List.of(
                Map.of("role", "system", "content", "You are a helpful assistant that enhances job post descriptions."),
                Map.of("role", "user", "content", prompt)
        ));
        return webClient.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractOpenAiText)
                .onErrorReturn(prompt)
                .blockOptional()
                .orElse(prompt);
    }

    private String callPollinationsFallback(String prompt) {
        try {
            String encoded = URLEncoder.encode(truncate(prompt, 1500), StandardCharsets.UTF_8);
            String url = "https://text.pollinations.ai/" + encoded;
            String response = webClient.get()
                    .uri(url)
                    .accept(MediaType.TEXT_PLAIN, MediaType.ALL)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorReturn(prompt)
                    .block();
            if (response == null || response.isBlank()) return prompt;
            return response.trim();
        } catch (Exception ex) {
            return prompt;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    private String safe(String input) {
        return input == null ? "" : input;
    }
}