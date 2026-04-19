package com.careerpolitics.scraper.infrastructure.news;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.ArticleDetails;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.TrendNewsClient;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Slf4j
public class GoogleNewsRssClient implements TrendNewsClient {

    private final RestClient restClient;
    private final TrendingProperties properties;
    private final TrendNormalizer trendNormalizer;

    public GoogleNewsRssClient(RestClient restClient,
                               TrendingProperties properties,
                               TrendNormalizer trendNormalizer) {
        this.restClient = restClient;
        this.properties = properties;
        this.trendNormalizer = trendNormalizer;
    }

    @Override
    public List<TrendHeadline> discover(String trend, String geo, String language, int maxNewsPerTrend) {
        String languagePrefix = language != null && language.contains("-")
                ? language.split("-")[0]
                : (language != null ? language : "en");
        String ceid = geo + ":" + languagePrefix;

        URI uri = UriComponentsBuilder
                .fromHttpUrl(properties.news().googleNewsRssUrl())
                .queryParam("q", trend)
                .queryParam("hl", language)
                .queryParam("gl", geo)
                .queryParam("ceid", ceid)
                .build(true)
                .toUri();

        try {
            String xml = restClient.get()
                    .uri(uri)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/rss+xml, application/xml;q=0.9, */*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .retrieve()
                    .body(String.class);

            if (xml == null || xml.isBlank()) {
                log.warn("Google News RSS returned empty response for trend='{}'", trend);
                return List.of();
            }

            List<TrendHeadline> headlines = parse(xml, trend, maxNewsPerTrend);
            log.info("Discovered {} news items via Google News RSS for trend='{}'", headlines.size(), trend);
            return headlines;
        } catch (Exception exception) {
            log.warn("Google News RSS fetch failed for trend='{}': {}", trend, exception.getMessage());
            return List.of();
        }
    }

    List<TrendHeadline> parse(String xml, String trend, int maxNewsPerTrend) {
        Document document = Jsoup.parse(xml, "", Parser.xmlParser());
        LinkedHashMap<String, TrendHeadline> unique = new LinkedHashMap<>();

        for (Element item : document.select("item")) {
            String rawTitle = elementText(item, "title");
            String link = resolveOriginalUrl(elementText(item, "link"));
            String pubDate = elementText(item, "pubDate");
            String descriptionHtml = elementText(item, "description");
            String source = extractSource(item, rawTitle);
            String title = stripSourceSuffix(rawTitle, source);
            String summary = descriptionHtml.isBlank() ? null : stripHtml(descriptionHtml);

            if (title.isBlank() || link.isBlank()) {
                continue;
            }

            unique.putIfAbsent(link, new TrendHeadline(
                    trend,
                    trendNormalizer.clean(title),
                    link,
                    source.isBlank() ? "Google News" : trendNormalizer.clean(source),
                    pubDate.isBlank() ? null : trendNormalizer.clean(pubDate),
                    summary == null ? null : trendNormalizer.clean(summary),
                    new ArticleDetails(
                            summary == null ? null : trendNormalizer.clean(summary),
                            null,
                            List.of(),
                            null
                    )
            ));

            if (unique.size() >= Math.max(1, maxNewsPerTrend)) {
                break;
            }
        }

        return new ArrayList<>(unique.values());
    }

    String resolveOriginalUrl(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            return "";
        }
        String decoded = URLDecoder.decode(rawLink.trim(), StandardCharsets.UTF_8);
        if (!decoded.contains("url=")) {
            return decoded;
        }
        String query = decoded.contains("?") ? decoded.substring(decoded.indexOf('?') + 1) : "";
        for (String part : query.split("&")) {
            if (part.startsWith("url=")) {
                return URLDecoder.decode(part.substring(4), StandardCharsets.UTF_8);
            }
        }
        return decoded;
    }

    private String extractSource(Element item, String rawTitle) {
        Element sourceElement = item.selectFirst("source");
        if (sourceElement != null) {
            String sourceText = sourceElement.text().trim();
            if (!sourceText.isBlank()) {
                return sourceText;
            }
        }
        if (rawTitle != null && rawTitle.contains(" - ")) {
            return rawTitle.substring(rawTitle.lastIndexOf(" - ") + 3).trim();
        }
        return "";
    }

    private String stripSourceSuffix(String title, String source) {
        if (title == null || title.isBlank()) {
            return "";
        }
        if (source != null && !source.isBlank() && title.endsWith(" - " + source)) {
            return title.substring(0, title.length() - (" - " + source).length()).trim();
        }
        return title.trim();
    }

    private String elementText(Element parent, String tagName) {
        Element child = parent.selectFirst(tagName);
        if (child == null) {
            return "";
        }
        return child.text().trim();
    }

    private String stripHtml(String html) {
        return Jsoup.parse(html).text().trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}