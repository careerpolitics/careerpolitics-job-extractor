package com.careerpolitics.scraper.service;

import com.careerpolitics.scraper.model.request.TrendArticleRequest;
import com.careerpolitics.scraper.model.response.TrendArticleResponse;
import com.careerpolitics.scraper.model.response.TrendNewsItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrendArticleService {

    private static final Pattern CLEANER_PATTERN = Pattern.compile("\\s+");

    private final ObjectMapper objectMapper;

    @Value("${careerpolitics.content.google-trends-url:https://trends.google.com/trending}")
    private String googleTrendsUrl;

    @Value("${careerpolitics.content.google-news-rss-url:https://news.google.com/rss/search}")
    private String googleNewsRssUrl;

    @Value("${careerpolitics.content.ai.base-url:https://api.openai.com/v1}")
    private String aiBaseUrl;

    @Value("${careerpolitics.content.ai.model:gpt-4o-mini}")
    private String aiModel;

    @Value("${careerpolitics.content.ai.api-key:}")
    private String aiApiKey;

    @Value("${careerpolitics.content.article-api.url:}")
    private String articleApiUrl;

    @Value("${careerpolitics.content.article-api.token:}")
    private String articleApiToken;

    public TrendArticleResponse createAndOptionallyPublish(TrendArticleRequest request) {
        List<String> trends = fetchGoogleTrends(request.getGeo(), request.getLanguage(), request.getMaxTrends());
        if (trends.isEmpty() && request.getFallbackTrends() != null) {
            trends = request.getFallbackTrends().stream().filter(Objects::nonNull).map(String::trim)
                    .filter(s -> !s.isBlank()).limit(request.getMaxTrends()).toList();
        }

        if (trends.isEmpty()) {
            throw new IllegalStateException("Could not discover trends from Google Trends and no fallback trends were provided");
        }

        List<TrendNewsItem> newsItems = collectNews(trends, request.getMaxNewsPerTrend());
        Map<String, String> article = generateArticleWithAi(trends, newsItems);

        Map<String, Object> publishResponse = null;
        boolean published = false;
        if (request.isPublish()) {
            publishResponse = publishToCareerPolitics(article.get("title"), article.get("markdown"), trends, newsItems);
            published = true;
        }

        return TrendArticleResponse.builder()
                .trends(trends)
                .news(newsItems)
                .generatedTitle(article.get("title"))
                .generatedMarkdown(article.get("markdown"))
                .published(published)
                .publishResponse(publishResponse)
                .build();
    }

    List<String> fetchGoogleTrends(String geo, String language, int maxTrends) {
        List<String> trends = new ArrayList<>();

        try {
            String url = googleTrendsUrl + "?geo=" + urlEncode(geo) + "&hl=" + urlEncode(language)
                    + "&category=9&status=active";

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();

            Elements candidates = doc.select("a[title], div[title], [data-term], [aria-label]");
            for (Element element : candidates) {
                String value = Optional.ofNullable(element.attr("data-term")).filter(v -> !v.isBlank())
                        .orElseGet(() -> Optional.ofNullable(element.attr("title")).filter(v -> !v.isBlank())
                                .orElse(element.attr("aria-label")));

                String cleaned = clean(value);
                if (cleaned.length() > 2 && cleaned.length() < 120 && !trends.contains(cleaned)) {
                    trends.add(cleaned);
                }
                if (trends.size() >= maxTrends) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to scrape Google Trends: {}", ex.getMessage());
        }

        return trends.stream().limit(maxTrends).toList();
    }

    List<TrendNewsItem> collectNews(List<String> trends, int maxNewsPerTrend) {
        List<TrendNewsItem> newsItems = new ArrayList<>();

        for (String trend : trends) {
            try {
                String rssUrl = googleNewsRssUrl + "?q=" + urlEncode(trend + " jobs education India") + "&hl=en-IN&gl=IN&ceid=IN:en";
                Document doc = Jsoup.connect(rssUrl)
                        .userAgent("Mozilla/5.0")
                        .timeout(15000)
                        .get();

                Elements items = doc.select("item");
                int count = 0;
                for (Element item : items) {
                    if (count >= maxNewsPerTrend) {
                        break;
                    }
                    TrendNewsItem news = TrendNewsItem.builder()
                            .trend(trend)
                            .title(clean(item.selectFirst("title") != null ? item.selectFirst("title").text() : ""))
                            .link(item.selectFirst("link") != null ? item.selectFirst("link").text() : "")
                            .source(item.selectFirst("source") != null ? item.selectFirst("source").text() : "Google News")
                            .publishedAt(item.selectFirst("pubDate") != null ? item.selectFirst("pubDate").text() : "")
                            .snippet(clean(item.selectFirst("description") != null ? Jsoup.parse(item.selectFirst("description").text()).text() : ""))
                            .build();

                    if (!news.getTitle().isBlank()) {
                        newsItems.add(news);
                        count++;
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to collect news for trend {}: {}", trend, ex.getMessage());
            }
        }

        return newsItems;
    }

    Map<String, String> generateArticleWithAi(List<String> trends, List<TrendNewsItem> newsItems) {
        if (aiApiKey == null || aiApiKey.isBlank()) {
            throw new IllegalStateException("AI API key is missing. Set careerpolitics.content.ai.api-key");
        }

        String newsContext = newsItems.stream()
                .map(item -> String.format("- Trend: %s | Headline: %s | Source: %s | Snippet: %s",
                        item.getTrend(), item.getTitle(), item.getSource(), item.getSnippet()))
                .collect(Collectors.joining("\n"));

        String prompt = "You are a content strategist for CareerPolitics. Write a Forem-ready markdown article focused on trending topics in jobs and education in India. " +
                "Use a practical, data-informed tone. Include: intro, trend analysis, opportunities for job seekers/students, and a conclusion with actionable advice. " +
                "Also include a section `## Sources` with bullet links to the mentioned headlines. " +
                "Return strict JSON with keys: title, markdown.\n\n" +
                "Date: " + LocalDate.now() + "\n" +
                "Trends: " + String.join(", ", trends) + "\n" +
                "News:\n" + newsContext;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiModel);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", "You generate high-quality SEO-friendly career articles."),
                Map.of("role", "user", "content", prompt)
        ));

        WebClient client = WebClient.builder()
                .baseUrl(aiBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        String raw = client.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode article = objectMapper.readTree(content);
            Map<String, String> result = new HashMap<>();
            result.put("title", article.path("title").asText("Weekly Trends: Jobs & Education in India"));
            result.put("markdown", article.path("markdown").asText(""));
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse AI response", ex);
        }
    }

    Map<String, Object> publishToCareerPolitics(String title, String markdown, List<String> trends, List<TrendNewsItem> newsItems) {
        if (articleApiUrl == null || articleApiUrl.isBlank()) {
            throw new IllegalStateException("Article API URL is missing. Set careerpolitics.content.article-api.url");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("article", Map.of(
                "title", title,
                "body_markdown", markdown,
                "published", true,
                "tags", List.of("jobs", "education", "india", "trending")
        ));
        payload.put("meta", Map.of(
                "source", "google-trends",
                "trends", trends,
                "headlines", newsItems.stream().map(TrendNewsItem::getTitle).toList()
        ));

        WebClient.Builder builder = WebClient.builder();
        if (articleApiToken != null && !articleApiToken.isBlank()) {
            builder.defaultHeader("api-key", articleApiToken);
        }

        String response = builder.build().post()
                .uri(articleApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readValue(response, Map.class);
        } catch (Exception ex) {
            return Map.of("raw", response);
        }
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return CLEANER_PATTERN.matcher(value.replace('\u00A0', ' ').trim()).replaceAll(" ");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
