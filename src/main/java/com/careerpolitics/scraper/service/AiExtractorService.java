package com.careerpolitics.scraper.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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

    private static final int MAX_CONTENT_CHARS = 2000;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AiContentService aiContentService;

    public Map<String, Object> extractJobData(String url, String title, String html) {
        try {
            String contentSnippet = extractRelevantText(html, MAX_CONTENT_CHARS);
            String instruction = "You are an information extraction system. Extract and ENHANCE concise structured job details as strict JSON with these keys: title (string), department (string), vacancies (int), applicationLink (string), notificationLink (string), description (string, improved), importantDates (object of name->date string), eligibilityCriteria (array of strings), examPattern (string), applicationFee (string), selectionProcess (string). If unknown, use 'N/A' or [] or {} accordingly. Return ONLY JSON, no prose.";
            String prompt = instruction + "\nURL: " + url + "\nTITLE: " + title + "\nCONTENT: " + contentSnippet;
            String response = aiContentService.generate(prompt);
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

    private String extractRelevantText(String html, int maxLen) {
        try {
            Document doc = Jsoup.parse(html);
            Element article = doc.selectFirst("article");
            String text;
            if (article != null) {
                text = article.text();
            } else {
                Element main = doc.selectFirst("main, #main, .main, #content, .content");
                text = (main != null) ? main.text() : doc.body().text();
            }
            text = text.replaceAll("\\s+", " ").trim();
            if (text.length() > maxLen) {
                return text.substring(0, maxLen);
            }
            return text;
        } catch (Exception ex) {
            String plain = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (plain.length() > maxLen) return plain.substring(0, maxLen);
            return plain;
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