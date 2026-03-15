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
    private static final List<String> INVALID_TREND_TOKENS = List.of(
            "go back", "home link", "explore link", "trending now link", "year in search link",
            "home", "explore", "trending now", "year in search", "google trends"
    );

    private final ObjectMapper objectMapper;

    @Value("${careerpolitics.content.google-trends-url:https://trends.google.com/trending}")
    private String googleTrendsUrl;

    @Value("${careerpolitics.content.google-news-rss-url:https://news.google.com/rss/search}")
    private String googleNewsRssUrl;

    @Value("${careerpolitics.content.google-trends-api-url:https://trends.google.com/trends/api}")
    private String googleTrendsApiUrl;

    @Value("${careerpolitics.content.ai.google.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String googleAiBaseUrl;

    @Value("${careerpolitics.content.ai.google.model:gemini-1.5-flash}")
    private String googleAiModel;

    @Value("${careerpolitics.content.ai.google.api-key:}")
    private String googleAiApiKey;

    @Value("${careerpolitics.content.article-api.url:}")
    private String articleApiUrl;

    @Value("${careerpolitics.content.article-api.token:}")
    private String articleApiToken;

    public TrendArticleResponse createAndOptionallyPublish(TrendArticleRequest request) {
        List<String> trends = fetchGoogleTrends(request.getGeo(), request.getLanguage(), request.getMaxTrends());

        if (trends.isEmpty() && request.getFallbackTrends() != null) {
            trends = request.getFallbackTrends().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .limit(request.getMaxTrends())
                    .toList();
        }

        if (trends.isEmpty()) {
            throw new IllegalStateException("Could not scrape trending topics from Google Trends and no fallbackTrends were provided");
        }

        List<TrendNewsItem> newsItems = gatherDetailsForTrends(trends, request.getMaxNewsPerTrend(), request.getGeo(), request.getLanguage());
        Map<String, String> article = generateArticleWithGoogleAi(trends, newsItems);

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
        String url = googleTrendsUrl + "?geo=" + urlEncode(geo) + "&hl=" + urlEncode(language) + "&category=9&status=active";

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .get();

                List<String> trends = extractTrendsFromDocument(doc, maxTrends);
                if (!trends.isEmpty()) {
                    return trends;
                }

                if (attempt < 3) {
                    Thread.sleep(1200);
                }
            } catch (Exception ex) {
                log.warn("Failed to scrape Google Trends page (attempt {}): {}", attempt, ex.getMessage());
            }
        }

        List<String> apiTrends = fetchTrendsFromApiEndpoints(geo, language, maxTrends);
        if (!apiTrends.isEmpty()) {
            log.info("Using Google Trends API fallback results, count={}", apiTrends.size());
            return apiTrends;
        }

        return List.of();
    }

    List<String> fetchTrendsFromApiEndpoints(String geo, String language, int maxTrends) {
        LinkedHashSet<String> trends = new LinkedHashSet<>();

        List<String> endpoints = List.of(
                googleTrendsApiUrl + "/realtimetrends?hl=" + urlEncode(language) + "&tz=-330&cat=all&fi=0&fs=0&geo=" + urlEncode(geo) + "&ri=300&rs=20&sort=0",
                googleTrendsApiUrl + "/dailytrends?hl=" + urlEncode(language) + "&tz=-330&geo=" + urlEncode(geo) + "&ns=15"
        );

        for (String endpoint : endpoints) {
            try {
                String raw = Jsoup.connect(endpoint)
                        .ignoreContentType(true)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .execute()
                        .body();

                List<String> parsed = parseGoogleTrendsApiPayload(raw, maxTrends);
                for (String trend : parsed) {
                    addIfValidTrend(trend, trends, maxTrends);
                    if (trends.size() >= maxTrends) {
                        return new ArrayList<>(trends);
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed trends API fallback call {}: {}", endpoint, ex.getMessage());
            }
        }

        return new ArrayList<>(trends);
    }

    List<String> parseGoogleTrendsApiPayload(String raw, int maxTrends) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        String sanitized = raw.startsWith(")]}'") ? raw.substring(4) : raw;

        LinkedHashSet<String> trends = new LinkedHashSet<>();
        try {
            JsonNode root = objectMapper.readTree(sanitized).path("default");

            JsonNode realtimeStories = root.path("trendingStories");
            if (realtimeStories.isArray()) {
                for (JsonNode story : realtimeStories) {
                    String query = clean(story.path("title").path("query").asText());
                    addIfValidTrend(query, trends, maxTrends);
                    if (trends.size() >= maxTrends) {
                        return new ArrayList<>(trends);
                    }
                }
            }

            JsonNode dailyDays = root.path("trendingSearchesDays");
            if (dailyDays.isArray()) {
                for (JsonNode day : dailyDays) {
                    JsonNode searches = day.path("trendingSearches");
                    if (!searches.isArray()) {
                        continue;
                    }
                    for (JsonNode search : searches) {
                        String query = clean(search.path("title").path("query").asText());
                        addIfValidTrend(query, trends, maxTrends);
                        if (trends.size() >= maxTrends) {
                            return new ArrayList<>(trends);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed parsing Google Trends API payload: {}", ex.getMessage());
        }

        return new ArrayList<>(trends);
    }

    List<TrendNewsItem> gatherDetailsForTrends(List<String> trends, int maxArticlesPerTrend, String geo, String language) {
        List<TrendNewsItem> items = new ArrayList<>();

        for (String trend : trends) {
            try {
                String rssUrl = googleNewsRssUrl
                        + "?q=" + urlEncode(trend + " jobs education " + geo)
                        + "&hl=" + urlEncode(language)
                        + "&gl=" + urlEncode(geo)
                        + "&ceid=" + urlEncode(geo + ":en");

                Document rssDoc = Jsoup.connect(rssUrl)
                        .ignoreContentType(true)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .get();

                items.addAll(parseNewsRssDocument(rssDoc, trend, maxArticlesPerTrend));
            } catch (Exception ex) {
                log.warn("Failed to gather article details from Google News RSS for trend {}: {}", trend, ex.getMessage());
            }
        }

        return items;
    }

    List<TrendNewsItem> parseNewsRssDocument(Document rssDoc, String trend, int maxArticlesPerTrend) {
        List<TrendNewsItem> parsedItems = new ArrayList<>();
        Elements rssItems = rssDoc.select("item");

        for (Element item : rssItems) {
            if (parsedItems.size() >= maxArticlesPerTrend) {
                break;
            }

            String title = clean(textOf(item, "title"));
            String link = clean(textOf(item, "link"));
            String source = clean(textOf(item, "source"));
            String publishedAt = clean(textOf(item, "pubDate"));
            String description = clean(Jsoup.parse(textOf(item, "description")).text());

            if (title.isBlank() || link.isBlank()) {
                continue;
            }

            String snippet = description;
            if (snippet.isBlank()) {
                snippet = fetchArticleSnippet(link);
            }

            parsedItems.add(TrendNewsItem.builder()
                    .trend(trend)
                    .title(title)
                    .link(link)
                    .source(source.isBlank() ? "Google News RSS" : source)
                    .publishedAt(publishedAt)
                    .snippet(snippet)
                    .build());
        }

        return parsedItems;
    }

    String fetchArticleSnippet(String link) {
        try {
            Document articleDoc = Jsoup.connect(link)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            String metaDescription = firstNonBlank(
                    articleDoc.select("meta[name=description]").attr("content"),
                    articleDoc.select("meta[property=og:description]").attr("content")
            );
            if (!metaDescription.isBlank()) {
                return clean(metaDescription);
            }

            Element firstParagraph = articleDoc.selectFirst("article p, main p, p");
            return clean(firstParagraph != null ? firstParagraph.text() : "");
        } catch (Exception ex) {
            log.debug("Could not fetch article snippet for {}: {}", link, ex.getMessage());
            return "";
        }
    }

    private String textOf(Element parent, String selector) {
        Element found = parent.selectFirst(selector);
        return found != null ? found.text() : "";
    }

    Map<String, String> generateArticleWithGoogleAi(List<String> trends, List<TrendNewsItem> newsItems) {
        if (googleAiApiKey == null || googleAiApiKey.isBlank()) {
            throw new IllegalStateException("Google AI API key is missing. Set careerpolitics.content.ai.google.api-key");
        }

        String scrapedDetails = newsItems.stream()
                .map(item -> String.format("- Trend: %s | Title: %s | Source: %s | Link: %s | Snippet: %s",
                        item.getTrend(), item.getTitle(), item.getSource(), item.getLink(), item.getSnippet()))
                .collect(Collectors.joining("\n"));

        String prompt = "Using the scraped trend topics and article details, write a Forem-ready markdown post for CareerPolitics. " +
                "Focus on jobs and education opportunities in India. " +
                "Structure: title, intro, trend-wise analysis, implications for students/job seekers, actionable steps, conclusion, and sources. " +
                "Use factual tone and do not fabricate statistics. " +
                "Return strict JSON with keys: title, markdown.\n\n" +
                "Date: " + LocalDate.now() + "\n" +
                "Trends: " + String.join(", ", trends) + "\n" +
                "Scraped details:\n" + scrapedDetails;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        WebClient client = WebClient.builder()
                .baseUrl(googleAiBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        String raw = client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .queryParam("key", googleAiApiKey)
                        .build(googleAiModel))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
            JsonNode article = objectMapper.readTree(content);
            Map<String, String> result = new HashMap<>();
            result.put("title", article.path("title").asText("Weekly Trends: Jobs & Education in India"));
            result.put("markdown", article.path("markdown").asText(""));
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Google AI response", ex);
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
                "source", "google-trends-page-scrape",
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

    private boolean isLikelyTrendTerm(String value) {
        if (value == null) {
            return false;
        }

        String normalized = clean(value).toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() < 3 || normalized.length() > 120) {
            return false;
        }
        if (normalized.startsWith("http") || normalized.contains("/") || normalized.contains("@")) {
            return false;
        }
        if (INVALID_TREND_TOKENS.stream().anyMatch(normalized::equals)) {
            return false;
        }
        return INVALID_TREND_TOKENS.stream().noneMatch(normalized::contains);
    }

    private void addIfValidTrend(String candidate, LinkedHashSet<String> trends, int maxTrends) {
        String cleaned = clean(candidate);
        if (isLikelyTrendTerm(cleaned)) {
            trends.add(cleaned);
        }
        while (trends.size() > maxTrends) {
            trends.remove(trends.iterator().next());
        }
    }

    private Element firstElement(Element base, String... selectors) {
        for (String selector : selectors) {
            Element found = base.selectFirst(selector);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
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
