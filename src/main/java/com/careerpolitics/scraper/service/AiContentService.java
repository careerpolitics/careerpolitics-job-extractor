package com.careerpolitics.scraper.service;


import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AiContentService {

    private final WebClient webClient;

    public String enhanceDescription(String rawTitle, String rawDescription) {
        try {
            String prompt = "Improve and expand this government job post intro in 2-3 concise, SEO-friendly sentences. Use neutral tone, include key numbers if present. Title: '" +
                    safe(rawTitle) + "'. Intro: '" + safe(rawDescription) + "'";
            String encoded = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
            String url = "https://text.pollinations.ai/" + encoded;
            String response = webClient.get()
                    .uri(url)
                    .accept(MediaType.TEXT_PLAIN, MediaType.ALL)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorReturn(rawDescription)
                    .block();
            if (response == null || response.isBlank()) return rawDescription;
            return response.trim();
        } catch (Exception ex) {
            return rawDescription;
        }
    }

    private String safe(String input) {
        return input == null ? "" : input;
    }
}