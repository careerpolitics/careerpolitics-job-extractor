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

            List<TrendMediaItem> mediaItems = gatherMediaForTrend(trend, request.getGeo(), request.getLanguage());
            Map<String, Object> articleData = generateArticleWithGoogleAi(trend, newsItems, mediaItems);

            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) articleData.getOrDefault("tags", defaultTagsForTrend(trend));
            @SuppressWarnings("unchecked")
            List<String> keywords = (List<String>) articleData.getOrDefault("keywords", defaultKeywordsForTrend(trend));
            String title = String.valueOf(articleData.getOrDefault("title", trend + " - Latest Jobs & Education Update"));
            String markdown = String.valueOf(articleData.getOrDefault("markdown", ""));

            Map<String, Object> publishResponse = null;
            boolean published = false;
            if (request.isPublish()) {
                publishResponse = publishToCareerPolitics(title, markdown, tags, trend, newsItems);
                published = true;
            }

            generatedArticles.add(TrendGeneratedArticle.builder()
                    .trend(trend)
                    .title(title)
                    .markdown(markdown)
                    .tags(tags)
                    .keywords(keywords)
                    .sources(newsItems)
                    .media(mediaItems)
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

    Map<String, Object> generateArticleWithGoogleAi(String trend, List<TrendNewsItem> newsItems, List<TrendMediaItem> mediaItems) {
        if (googleAiApiKey == null || googleAiApiKey.isBlank()) {
            throw new IllegalStateException("Google AI API key is missing. Set careerpolitics.content.ai.google.api-key");
        }

        String sourcesText = newsItems.stream().map(n -> "- " + n.getTitle() + " | " + n.getSource() + " | " + n.getLink() + " | " + n.getSnippet())
                .collect(Collectors.joining("\n"));
        String mediaText = mediaItems.stream().map(m -> "- " + m.getType() + " | " + m.getTitle() + " | " + m.getUrl())
                .collect(Collectors.joining("\n"));

        String prompt = "Create ONE detailed SEO-friendly article in markdown for trend: " + trend + ". " +
                "Use clear human-readable language with headings, subheadings, bullets, and short paragraphs. " +
                "Must include: what happened, why trending, timeline, important statements/reactions, and impact. " +
                "Must include an intro and conclusion. Must include at least 3 source references in a '## Sources' section. " +
                "Include media embeds where relevant using markdown links for images/videos/social posts under '## Media'. " +
                "Return strict JSON with keys: title, markdown, tags(array), keywords(array).\n\n" +
                "News sources:\n" + sourcesText + "\n\nMedia candidates:\n" + mediaText;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        String raw = WebClient.builder().baseUrl(googleAiBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri(uriBuilder -> uriBuilder.path("/models/{model}:generateContent").queryParam("key", googleAiApiKey).build(googleAiModel))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            String content = objectMapper.readTree(raw).path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
            JsonNode parsed = objectMapper.readTree(content);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("title", parsed.path("title").asText(trend + " - Latest Jobs & Education Update"));
            result.put("markdown", parsed.path("markdown").asText(""));
            result.put("tags", toStringList(parsed.path("tags"), defaultTagsForTrend(trend)));
            result.put("keywords", toStringList(parsed.path("keywords"), defaultKeywordsForTrend(trend)));
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Google AI response", ex);
        }
    }

    Map<String, Object> publishToCareerPolitics(String title, String markdown, List<String> tags, String trend, List<TrendNewsItem> newsItems) {
        if (articleApiUrl == null || articleApiUrl.isBlank()) {
            throw new IllegalStateException("Article API URL is missing. Set careerpolitics.content.article-api.url");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("article", Map.of(
                "title", title,
                "body_markdown", markdown,
                "published", true,
                "tags", tags
        ));
        payload.put("meta", Map.of(
                "source", "google-trends-plus-news-rss",
                "trend", trend,
                "headlines", newsItems.stream().map(TrendNewsItem::getTitle).toList()
        ));

        WebClient.Builder builder = WebClient.builder();
        if (articleApiToken != null && !articleApiToken.isBlank()) builder.defaultHeader("api-key", articleApiToken);
        String response = builder.build().post().uri(articleApiUrl).contentType(MediaType.APPLICATION_JSON).bodyValue(payload).retrieve().bodyToMono(String.class).block();

        try {
            return objectMapper.readValue(response, Map.class);
        } catch (Exception ex) {
            return Map.of("raw", response);
        }
    }

    private List<String> defaultTagsForTrend(String trend) {
        return List.of("trending", "jobs", "education", "india", slug(trend));
    }

    private List<String> defaultKeywordsForTrend(String trend) {
        return List.of(trend, trend + " jobs", trend + " education", "India trending news");
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

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
