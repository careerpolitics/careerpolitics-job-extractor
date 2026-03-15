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

    @Value("${careerpolitics.content.google-search-url:https://www.google.com/search}")
    private String googleSearchUrl;

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
        List<String> trends = new ArrayList<>();

        try {
            String url = googleTrendsUrl + "?geo=" + urlEncode(geo) + "&hl=" + urlEncode(language) + "&category=9&status=active";
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();

            // Prefer actual trend cells from Google Trends page.
            Elements trendCandidates = doc.select(
                    "div[aria-label] a, a[title], div[title], [data-term], [data-row-id], .mZ3RIc, .QNIh4d, .mM5pbd"
            );

            for (Element element : trendCandidates) {
                String text = firstNonBlank(
                        element.attr("data-term"),
                        element.attr("title"),
                        element.attr("aria-label"),
                        element.text()
                );

                String cleaned = clean(text);
                if (isLikelyTrendTerm(cleaned) && !trends.contains(cleaned)) {
                    trends.add(cleaned);
                }
                if (trends.size() >= maxTrends) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to scrape Google Trends page: {}", ex.getMessage());
        }

        return trends.stream().limit(maxTrends).toList();
    }

    List<TrendNewsItem> gatherDetailsForTrends(List<String> trends, int maxArticlesPerTrend, String geo, String language) {
        List<TrendNewsItem> items = new ArrayList<>();

        for (String trend : trends) {
            try {
                String query = trend + " jobs education " + geo;
                String url = googleSearchUrl + "?q=" + urlEncode(query) + "&tbm=nws&hl=" + urlEncode(language) + "&gl=" + urlEncode(geo);

                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .referrer("https://www.google.com/")
                        .timeout(15000)
                        .get();

                // Google News search result cards (best-effort selectors)
                Elements cards = doc.select("div.SoaBEf, div.dbsr, g-card");
                int count = 0;

                for (Element card : cards) {
                    if (count >= maxArticlesPerTrend) {
                        break;
                    }

                    Element titleEl = firstElement(card, "div.n0jPhd", "div.JheGif", "h3", "a > div");
                    Element linkEl = firstElement(card, "a[href]");
                    Element sourceEl = firstElement(card, "div.CEMjEf span", "span.WG9SHc", "div.CEMjEf");
                    Element timeEl = firstElement(card, "div.OSrXXb span", "span.OSrXXb", "time");
                    Element snippetEl = firstElement(card, "div.GI74Re", "div.Y3v8qd", "div", "span");

                    String title = clean(titleEl != null ? titleEl.text() : "");
                    String link = linkEl != null ? linkEl.absUrl("href") : "";
                    String source = clean(sourceEl != null ? sourceEl.text() : "Google Search");
                    String publishedAt = clean(timeEl != null ? timeEl.text() : "");
                    String snippet = clean(snippetEl != null ? snippetEl.text() : "");

                    if (!title.isBlank() && !link.isBlank()) {
                        items.add(TrendNewsItem.builder()
                                .trend(trend)
                                .title(title)
                                .link(link)
                                .source(source)
                                .publishedAt(publishedAt)
                                .snippet(snippet)
                                .build());
                        count++;
                    }
                }

                // Fallback generic extraction if Google markup changes.
                if (count == 0) {
                    Elements links = doc.select("a[href]");
                    for (Element link : links) {
                        if (count >= maxArticlesPerTrend) {
                            break;
                        }

                        String href = link.absUrl("href");
                        String title = clean(link.text());
                        if (!href.isBlank() && href.startsWith("http") && title.length() > 20) {
                            items.add(TrendNewsItem.builder()
                                    .trend(trend)
                                    .title(title)
                                    .link(href)
                                    .source("Google Search")
                                    .publishedAt("")
                                    .snippet("")
                                    .build());
                            count++;
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to gather article details for trend {}: {}", trend, ex.getMessage());
            }
        }

        return items;
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
        return value != null
                && !value.isBlank()
                && value.length() > 2
                && value.length() < 120
                && !value.equalsIgnoreCase("trending now")
                && !value.equalsIgnoreCase("google trends")
                && !value.toLowerCase(Locale.ROOT).startsWith("http");
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
