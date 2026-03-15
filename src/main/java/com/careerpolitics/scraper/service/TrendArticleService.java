package com.careerpolitics.scraper.service;

import com.careerpolitics.scraper.model.request.TrendArticleRequest;
import com.careerpolitics.scraper.model.response.*;
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
import org.springframework.http.HttpStatus;
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

    @Value("${careerpolitics.content.youtube-rss-url:https://www.youtube.com/feeds/videos.xml}")
    private String youtubeRssUrl;

    @Value("${careerpolitics.content.ai.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    @Value("${careerpolitics.content.ai.openrouter.model:anthropic/claude-3.5-sonnet}")
    private String openRouterModel;

    @Value("${careerpolitics.content.ai.openrouter.api-key:}")
    private String openRouterApiKey;

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
            throw new IllegalStateException("Could not scrape trending topics from Google Trends and no fallbackTrends were provided");
        }

        List<TrendGeneratedArticle> generatedArticles = new ArrayList<>();
        List<TrendNewsItem> allNews = new ArrayList<>();

        for (String trend : trends) {
            List<TrendNewsItem> newsItems = gatherDetailsForTrend(trend, request.getMaxNewsPerTrend(), request.getGeo(), request.getLanguage());
            allNews.addAll(newsItems);

            List<TrendMediaItem> mediaItems = new ArrayList<>();
            mediaItems.addAll(extractMediaFromNewsLinks(trend, newsItems));
            mediaItems.addAll(gatherMediaForTrend(trend, request.getGeo(), request.getLanguage()));
            mediaItems = mediaItems.stream().distinct().limit(8).toList();
            String coverImage = pickCoverImage(mediaItems);

            Map<String, Object> articleData = generateArticleWithClaudeViaOpenRouter(trend, newsItems, mediaItems, coverImage);

            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) articleData.getOrDefault("tags", defaultTagsForTrend(trend));
            @SuppressWarnings("unchecked")
            List<String> keywords = (List<String>) articleData.getOrDefault("keywords", defaultKeywordsForTrend(trend));
            String title = String.valueOf(articleData.getOrDefault("title", trend + " - Latest Jobs & Education Update"));
            String markdown = String.valueOf(articleData.getOrDefault("markdown", ""));

            Map<String, Object> publishResponse = null;
            boolean published = false;
            if (request.isPublish()) {
                publishResponse = publishToCareerPolitics(title, markdown, tags, trend, newsItems, coverImage);
                published = Boolean.TRUE.equals(publishResponse.get("success"));
            }

            generatedArticles.add(TrendGeneratedArticle.builder()
                    .trend(trend)
                    .title(title)
                    .markdown(markdown)
                    .tags(tags)
                    .keywords(keywords)
                    .sources(newsItems)
                    .media(mediaItems)
                    .coverImage(coverImage)
                    .published(published)
                    .publishResponse(publishResponse)
                    .build());
        }

        TrendGeneratedArticle first = generatedArticles.isEmpty() ? null : generatedArticles.get(0);
        return TrendArticleResponse.builder()
                .trends(trends)
                .news(allNews)
                .articles(generatedArticles)
                .generatedTitle(first != null ? first.getTitle() : null)
                .generatedMarkdown(first != null ? first.getMarkdown() : null)
                .published(first != null && first.isPublished())
                .publishResponse(first != null ? first.getPublishResponse() : null)
                .build();
    }

    List<String> fetchGoogleTrends(String geo, String language, int maxTrends) {
        String url = googleTrendsUrl + "?geo=" + urlEncode(geo) + "&hl=" + urlEncode(language) + "&category=9&status=active";
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(15000).get();
                List<String> trends = extractTrendsFromDocument(doc, maxTrends);
                if (!trends.isEmpty()) return trends;
                if (attempt < 3) Thread.sleep(1200);
            } catch (Exception ex) {
                log.warn("Failed to scrape Google Trends page (attempt {}): {}", attempt, ex.getMessage());
            }
        }
        List<String> apiTrends = fetchTrendsFromApiEndpoints(geo, language, maxTrends);
        return apiTrends.isEmpty() ? List.of() : apiTrends;
    }

    List<String> extractTrendsFromDocument(Document doc, int maxTrends) {
        LinkedHashSet<String> trends = new LinkedHashSet<>();
        Elements tableRows = doc.select("table tbody tr, table tr");
        for (Element row : tableRows) {
            Element titleElement = firstElement(row, "td:nth-of-type(2)", "td:nth-of-type(1)", "a[title]", "a", "div[title]", "span");
            addIfValidTrend(clean(titleElement != null ? titleElement.text() : row.text()), trends, maxTrends);
            if (trends.size() >= maxTrends) return new ArrayList<>(trends);
        }
        Elements trendCandidates = doc.select("main [data-row-id], main [data-term], main a[title], main div[title], main .mZ3RIc, main .QNIh4d, main .mM5pbd");
        for (Element element : trendCandidates) {
            String candidate = firstNonBlank(element.attr("data-term"), element.attr("title"), element.attr("aria-label"), element.text());
            addIfValidTrend(candidate, trends, maxTrends);
            if (trends.size() >= maxTrends) break;
        }
        return new ArrayList<>(trends);
    }

    List<String> fetchTrendsFromApiEndpoints(String geo, String language, int maxTrends) {
        LinkedHashSet<String> trends = new LinkedHashSet<>();
        List<String> endpoints = List.of(
                googleTrendsApiUrl + "/realtimetrends?hl=" + urlEncode(language) + "&tz=-330&cat=all&fi=0&fs=0&geo=" + urlEncode(geo) + "&ri=300&rs=20&sort=0",
                googleTrendsApiUrl + "/dailytrends?hl=" + urlEncode(language) + "&tz=-330&geo=" + urlEncode(geo) + "&ns=15"
        );
        for (String endpoint : endpoints) {
            try {
                String raw = Jsoup.connect(endpoint).ignoreContentType(true).userAgent("Mozilla/5.0").timeout(15000).execute().body();
                for (String trend : parseGoogleTrendsApiPayload(raw, maxTrends)) {
                    addIfValidTrend(trend, trends, maxTrends);
                    if (trends.size() >= maxTrends) return new ArrayList<>(trends);
                }
            } catch (Exception ex) {
                log.warn("Failed trends API fallback call {}: {}", endpoint, ex.getMessage());
            }
        }
        return new ArrayList<>(trends);
    }

    List<String> parseGoogleTrendsApiPayload(String raw, int maxTrends) {
        if (raw == null || raw.isBlank()) return List.of();
        String sanitized = raw.startsWith(")]}'") ? raw.substring(4) : raw;
        LinkedHashSet<String> trends = new LinkedHashSet<>();
        try {
            JsonNode root = objectMapper.readTree(sanitized).path("default");
            JsonNode realtimeStories = root.path("trendingStories");
            if (realtimeStories.isArray()) {
                for (JsonNode story : realtimeStories) {
                    addIfValidTrend(clean(story.path("title").path("query").asText()), trends, maxTrends);
                }
            }
            JsonNode dailyDays = root.path("trendingSearchesDays");
            if (dailyDays.isArray()) {
                for (JsonNode day : dailyDays) {
                    JsonNode searches = day.path("trendingSearches");
                    if (searches.isArray()) {
                        for (JsonNode search : searches) {
                            addIfValidTrend(clean(search.path("title").path("query").asText()), trends, maxTrends);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed parsing Google Trends API payload: {}", ex.getMessage());
        }
        return new ArrayList<>(trends);
    }

    List<TrendNewsItem> gatherDetailsForTrend(String trend, int maxArticlesPerTrend, String geo, String language) {
        String rssUrl = googleNewsRssUrl + "?q=" + urlEncode(trend + " jobs education " + geo)
                + "&hl=" + urlEncode(language) + "&gl=" + urlEncode(geo) + "&ceid=" + urlEncode(geo + ":en");

        try {
            Document rssDoc = Jsoup.connect(rssUrl).ignoreContentType(true).userAgent("Mozilla/5.0").timeout(15000).get();
            List<TrendNewsItem> items = parseNewsRssDocument(rssDoc, trend, Math.max(6, maxArticlesPerTrend));

            // Keep at least 3 distinct reliable sources when possible.
            LinkedHashMap<String, TrendNewsItem> bySource = new LinkedHashMap<>();
            for (TrendNewsItem item : items) {
                String sourceKey = clean(item.getSource()).toLowerCase(Locale.ROOT);
                bySource.putIfAbsent(sourceKey, item);
            }

            List<TrendNewsItem> selected = new ArrayList<>();
            for (TrendNewsItem uniqueSourceItem : bySource.values()) {
                selected.add(uniqueSourceItem);
                if (selected.size() >= 3) break;
            }
            for (TrendNewsItem item : items) {
                if (selected.size() >= maxArticlesPerTrend) break;
                if (!selected.contains(item)) selected.add(item);
            }
            return selected.stream().limit(maxArticlesPerTrend).toList();
        } catch (Exception ex) {
            log.warn("Failed to gather article details from Google News RSS for trend {}: {}", trend, ex.getMessage());
            return List.of();
        }
    }

    List<TrendMediaItem> extractMediaFromNewsLinks(String trend, List<TrendNewsItem> newsItems) {
        List<TrendMediaItem> media = new ArrayList<>();
        for (TrendNewsItem item : newsItems) {
            if (media.size() >= 6) {
                break;
            }
            media.addAll(extractMediaFromArticleLink(trend, item.getLink(), item.getTitle()));
        }
        return media;
    }

    List<TrendMediaItem> extractMediaFromArticleLink(String trend, String link, String fallbackTitle) {
        try {
            Document doc = Jsoup.connect(link).userAgent("Mozilla/5.0").timeout(12000).get();
            List<TrendMediaItem> media = new ArrayList<>();

            String image = firstNonBlank(
                    doc.select("meta[property=og:image]").attr("content"),
                    doc.select("meta[name=twitter:image]").attr("content"),
                    doc.select("article img[src]").attr("abs:src"),
                    doc.select("img[src]").attr("abs:src")
            );
            if (!image.isBlank()) {
                media.add(TrendMediaItem.builder().trend(trend).type("image").title(fallbackTitle).url(image).embed(image).build());
            }

            String video = firstNonBlank(
                    doc.select("meta[property=og:video]").attr("content"),
                    doc.select("video source[src]").attr("abs:src"),
                    doc.select("iframe[src*='youtube.com'], iframe[src*='youtu.be']").attr("abs:src")
            );
            if (!video.isBlank()) {
                media.add(TrendMediaItem.builder().trend(trend).type("video").title(fallbackTitle).url(video).embed(video).build());
            }

            return media;
        } catch (Exception ex) {
            return List.of();
        }
    }

    String pickCoverImage(List<TrendMediaItem> mediaItems) {
        for (TrendMediaItem item : mediaItems) {
            if ("image".equalsIgnoreCase(item.getType()) && item.getUrl() != null && !item.getUrl().isBlank()) {
                return item.getUrl();
            }
        }
        return "";
    }

    List<TrendMediaItem> gatherMediaForTrend(String trend, String geo, String language) {
        List<TrendMediaItem> media = new ArrayList<>();
        media.addAll(fetchYoutubeMedia(trend));
        media.addAll(fetchSocialMediaFromNewsRss(trend, geo, language));
        return media.stream().limit(4).toList();
    }

    List<TrendMediaItem> fetchYoutubeMedia(String trend) {
        try {
            String url = youtubeRssUrl + "?search_query=" + urlEncode(trend + " jobs education");
            Document doc = Jsoup.connect(url).ignoreContentType(true).userAgent("Mozilla/5.0").timeout(15000).get();
            Elements entries = doc.select("entry");
            List<TrendMediaItem> media = new ArrayList<>();
            for (Element entry : entries) {
                if (media.size() >= 2) break;
                String title = clean(textOf(entry, "title"));
                String link = entry.select("link[rel=alternate]").attr("href");
                String embed = youtubeEmbedFromUrl(link);
                if (!title.isBlank() && !link.isBlank()) {
                    media.add(TrendMediaItem.builder().trend(trend).type("youtube").title(title).url(link).embed(embed).build());
                }
            }
            return media;
        } catch (Exception ex) {
            log.debug("Failed to fetch YouTube media for {}: {}", trend, ex.getMessage());
            return List.of();
        }
    }

    List<TrendMediaItem> fetchSocialMediaFromNewsRss(String trend, String geo, String language) {
        String rssUrl = googleNewsRssUrl + "?q=" + urlEncode(trend + " (twitter OR x.com)")
                + "&hl=" + urlEncode(language) + "&gl=" + urlEncode(geo) + "&ceid=" + urlEncode(geo + ":en");
        try {
            Document rssDoc = Jsoup.connect(rssUrl).ignoreContentType(true).userAgent("Mozilla/5.0").timeout(15000).get();
            Elements items = rssDoc.select("item");
            List<TrendMediaItem> media = new ArrayList<>();
            for (Element item : items) {
                if (media.size() >= 2) break;
                String link = clean(textOf(item, "link"));
                if (link.contains("twitter.com") || link.contains("x.com")) {
                    media.add(TrendMediaItem.builder()
                            .trend(trend)
                            .type("social")
                            .title(clean(textOf(item, "title")))
                            .url(link)
                            .embed(link)
                            .build());
                }
            }
            return media;
        } catch (Exception ex) {
            return List.of();
        }
    }

    List<TrendNewsItem> parseNewsRssDocument(Document rssDoc, String trend, int maxArticlesPerTrend) {
        List<TrendNewsItem> parsedItems = new ArrayList<>();
        Elements rssItems = rssDoc.select("item");

        for (Element item : rssItems) {
            if (parsedItems.size() >= maxArticlesPerTrend) break;
            String title = clean(textOf(item, "title"));
            String link = clean(textOf(item, "link"));
            String source = clean(textOf(item, "source"));
            String publishedAt = clean(textOf(item, "pubDate"));
            String description = clean(Jsoup.parse(textOf(item, "description")).text());
            if (title.isBlank() || link.isBlank()) continue;

            String snippet = description.isBlank() ? fetchArticleSnippet(link) : description;
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

    Map<String, Object> generateArticleWithClaudeViaOpenRouter(String trend, List<TrendNewsItem> newsItems, List<TrendMediaItem> mediaItems, String coverImage) {
        if (openRouterApiKey == null || openRouterApiKey.isBlank()) {
            throw new IllegalStateException("OpenRouter API key is missing. Set careerpolitics.content.ai.openrouter.api-key");
        }

        String sourcesText = newsItems.stream().map(n -> "- " + n.getTitle() + " | " + n.getSource() + " | " + n.getLink() + " | " + n.getSnippet())
                .collect(Collectors.joining("\n"));
        String mediaText = mediaItems.stream().map(m -> "- " + m.getType() + " | " + m.getTitle() + " | " + m.getUrl())
                .collect(Collectors.joining("\n"));

        String prompt = "Create ONE detailed, engaging, SEO-friendly markdown article for trend: " + trend + ". " +
                "Decide the best article structure dynamically based on the provided facts. " +
                "Write in clear human language with compelling intro and strong narrative flow. " +
                "Must include: what happened, why trending, timeline, key statements/reactions, context, and practical impact. " +
                "Use headings/subheadings, bullet points, and short readable paragraphs. " +
                "Must include '## Sources' with at least 3 references, and '## Media' with relevant embeds/links. " +
                "If cover image is provided, reference it in markdown at top using a standard image markdown line. " +
                "Return strict JSON with keys: title, markdown, tags(array), keywords(array).\n\n" +
                "Cover image: " + (coverImage == null ? "" : coverImage) + "\n\n" +
                "News sources:\n" + sourcesText + "\n\nMedia candidates:\n" + mediaText;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openRouterModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "You are an expert journalist and SEO editor. Always return valid JSON only."),
                Map.of("role", "user", "content", prompt)
        ));

        String raw = WebClient.builder().baseUrl(openRouterBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterApiKey)
                .defaultHeader("HTTP-Referer", "https://careerpolitics.com")
                .defaultHeader("X-Title", "CareerPolitics Trend Article Writer")
                .build()
                .post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            String content = objectMapper.readTree(raw).path("choices").path(0).path("message").path("content").asText();
            JsonNode parsed = objectMapper.readTree(extractJsonObject(content));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("title", parsed.path("title").asText(trend + " - Latest Jobs & Education Update"));
            result.put("markdown", parsed.path("markdown").asText(""));
            result.put("tags", toStringList(parsed.path("tags"), defaultTagsForTrend(trend)));
            result.put("keywords", toStringList(parsed.path("keywords"), defaultKeywordsForTrend(trend)));
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Claude/OpenRouter response", ex);
        }
    }

    Map<String, Object> publishToCareerPolitics(String title, String markdown, List<String> tags, String trend, List<TrendNewsItem> newsItems, String coverImage) {
        if (articleApiUrl == null || articleApiUrl.isBlank()) {
            return Map.of(
                    "success", false,
                    "status", 500,
                    "error", "Article API URL is missing. Set careerpolitics.content.article-api.url"
            );
        }

        List<String> safeTags = sanitizeTags(tags);
        String safeMarkdown = markdown == null ? "" : markdown;
        String description = buildDescription(trend, newsItems);

        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> articlePayload = new LinkedHashMap<>();
        articlePayload.put("title", title);
        articlePayload.put("description", description);
        articlePayload.put("body_markdown", safeMarkdown);
        articlePayload.put("published", true);
        articlePayload.put("tags", safeTags);
        if (coverImage != null && !coverImage.isBlank()) {
            articlePayload.put("main_image", coverImage);
        }
        payload.put("article", articlePayload);
        payload.put("meta", Map.of(
                "source", "google-trends-plus-news-rss",
                "trend", trend,
                "headlines", newsItems.stream().map(TrendNewsItem::getTitle).toList()
        ));

        WebClient.Builder builder = WebClient.builder();
        if (articleApiToken != null && !articleApiToken.isBlank()) {
            builder.defaultHeader("api-key", articleApiToken);
        }

        try {
            Map<String, Object> response = builder.build().post()
                    .uri(articleApiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .exchangeToMono(clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> {
                                int status = clientResponse.statusCode().value();
                                if (clientResponse.statusCode().is2xxSuccessful()) {
                                    return parseResponseOrRaw(status, true, body, null);
                                }
                                String error = body.isBlank() ? "Publishing failed with status " + status : body;
                                return parseResponseOrRaw(status, false, body, error);
                            }))
                    .block();

            return response != null ? response : Map.of("success", false, "status", 500, "error", "Empty response from article API");
        } catch (Exception ex) {
            log.error("Article publish request failed for trend {}", trend, ex);
            return Map.of(
                    "success", false,
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "error", ex.getMessage()
            );
        }
    }

    private Map<String, Object> parseResponseOrRaw(int status, boolean success, String body, String error) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            parsed.put("success", success);
            parsed.put("status", status);
            if (!success && error != null) {
                parsed.putIfAbsent("error", error);
            }
            return parsed;
        } catch (Exception ex) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("success", success);
            raw.put("status", status);
            if (error != null) {
                raw.put("error", error);
            }
            raw.put("raw", body);
            return raw;
        }
    }

    private List<String> defaultTagsForTrend(String trend) {
        return List.of("trending", "jobs", "education", "india", slug(trend));
    }

    private List<String> defaultKeywordsForTrend(String trend) {
        return List.of(trend, trend + " jobs", trend + " education", "India trending news");
    }

    private List<String> sanitizeTags(List<String> tags) {
        List<String> source = (tags == null || tags.isEmpty()) ? List.of("trending", "jobs", "education", "india") : tags;
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String tag : source) {
            String normalized = normalizeTag(tag);
            if (!normalized.isBlank()) {
                cleaned.add(normalized.length() > 30 ? normalized.substring(0, 30) : normalized);
            }
            if (cleaned.size() >= 4) {
                break;
            }
        }
        if (cleaned.isEmpty()) {
            cleaned.addAll(List.of("trending", "jobs", "education", "india"));
        }
        return new ArrayList<>(cleaned);
    }

    String normalizeTag(String tag) {
        return clean(tag)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private String buildDescription(String trend, List<TrendNewsItem> newsItems) {
        String base = "Latest update on " + trend + " with context on jobs and education impact in India.";
        if (newsItems == null || newsItems.isEmpty()) {
            return base;
        }
        String first = clean(newsItems.get(0).getSnippet());
        if (first.isBlank()) {
            first = clean(newsItems.get(0).getTitle());
        }
        String merged = (base + " " + first).trim();
        return merged.length() > 200 ? merged.substring(0, 200) : merged;
    }

    private List<String> toStringList(JsonNode arrayNode, List<String> fallback) {
        if (!arrayNode.isArray()) return fallback;
        List<String> values = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            String value = clean(node.asText());
            if (!value.isBlank()) values.add(value);
        }
        return values.isEmpty() ? fallback : values;
    }

    String fetchArticleSnippet(String link) {
        try {
            Document articleDoc = Jsoup.connect(link).userAgent("Mozilla/5.0").timeout(10000).get();
            String metaDescription = firstNonBlank(articleDoc.select("meta[name=description]").attr("content"), articleDoc.select("meta[property=og:description]").attr("content"));
            if (!metaDescription.isBlank()) return clean(metaDescription);
            Element firstParagraph = articleDoc.selectFirst("article p, main p, p");
            return clean(firstParagraph != null ? firstParagraph.text() : "");
        } catch (Exception ex) {
            return "";
        }
    }

    private String textOf(Element parent, String selector) {
        Element found = parent.selectFirst(selector);
        return found != null ? found.text() : "";
    }

    private boolean isLikelyTrendTerm(String value) {
        if (value == null) return false;
        String normalized = clean(value).toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() < 3 || normalized.length() > 120) return false;
        if (normalized.startsWith("http") || normalized.contains("/") || normalized.contains("@")) return false;
        if (INVALID_TREND_TOKENS.stream().anyMatch(normalized::equals)) return false;
        return INVALID_TREND_TOKENS.stream().noneMatch(normalized::contains);
    }

    private void addIfValidTrend(String candidate, LinkedHashSet<String> trends, int maxTrends) {
        String cleaned = clean(candidate);
        if (isLikelyTrendTerm(cleaned)) trends.add(cleaned);
        while (trends.size() > maxTrends) trends.remove(trends.iterator().next());
    }

    private Element firstElement(Element base, String... selectors) {
        for (String selector : selectors) {
            Element found = base.selectFirst(selector);
            if (found != null) return found;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isBlank()) return cleaned;
        }
        return "";
    }

    private String clean(String value) {
        if (value == null) return "";
        return CLEANER_PATTERN.matcher(value.replace('\u00A0', ' ').trim()).replaceAll(" ");
    }

    private String slug(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String youtubeEmbedFromUrl(String url) {
        if (url == null || url.isBlank()) return "";
        String videoId = "";
        if (url.contains("watch?v=")) {
            videoId = url.substring(url.indexOf("watch?v=") + 8);
            int amp = videoId.indexOf('&');
            if (amp > -1) videoId = videoId.substring(0, amp);
        } else if (url.contains("youtu.be/")) {
            videoId = url.substring(url.indexOf("youtu.be/") + 9);
            int q = videoId.indexOf('?');
            if (q > -1) videoId = videoId.substring(0, q);
        }
        return videoId.isBlank() ? url : "https://www.youtube.com/embed/" + videoId;
    }

    private String extractJsonObject(String text) {
        if (text == null) {
            return "{}";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```json", "").replaceAll("^```", "").replaceAll("```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
