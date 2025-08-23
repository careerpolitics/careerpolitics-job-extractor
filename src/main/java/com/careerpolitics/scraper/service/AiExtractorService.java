package com.careerpolitics.scraper.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiExtractorService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public Map<String, Object> extractJobData(String url, String title, String html) {
        try {
            String instruction = "Extract structured job details as compact JSON with fields: title, department, vacancies (int), applicationLink, notificationLink, description, importantDates(object map name->date), eligibilityCriteria(array), examPattern, applicationFee, selectionProcess. Use only values present or reasonable N/A.";
            String prompt = instruction + "\nURL: " + url + "\nTITLE: " + title + "\nHTML: " + html;
            String encoded = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
            String endpoint = "https://text.pollinations.ai/" + encoded;
            String response = webClient.get()
                    .uri(endpoint)
                    .accept(MediaType.TEXT_PLAIN, MediaType.ALL)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null || response.isBlank()) return Map.of();
            String candidate = findJsonBlock(response);
            if (candidate == null) return Map.of();
            JsonNode node = objectMapper.readTree(candidate);
            Map<String, Object> map = new HashMap<>();
            node.fields().forEachRemaining(e -> map.put(e.getKey(), objectMapper.convertValue(e.getValue(), Object.class)));
            return map;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String findJsonBlock(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }
}