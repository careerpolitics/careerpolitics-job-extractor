package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.ArticleDetails;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.TrendNewsClient;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

@Slf4j
@Component
public class GoogleNewsSeleniumClient implements TrendNewsClient {

    private final SeleniumBrowserClient browserClient;
    private final TrendingProperties properties;
    private final TrendNormalizer trendNormalizer;

    public GoogleNewsSeleniumClient(SeleniumBrowserClient browserClient,
                                    TrendingProperties properties,
                                    TrendNormalizer trendNormalizer) {
        this.browserClient = browserClient;
        this.properties = properties;
        this.trendNormalizer = trendNormalizer;
    }

    @Override
    public List<TrendHeadline> discover(String trend, String geo, String language, int maxNewsPerTrend) {
        String url = properties.news().googleSearchUrl()
                + "?q=" + encode(trend)
                + "&tbm=nws&hl=" + encode(language)
                + "&gl=" + encode(geo)
                + "&num=" + encode(String.valueOf(Math.max(10, maxNewsPerTrend * 2)));
        String html = browserClient.fetchNewsPage(url, trend);
        if (html.isBlank()) {
            return List.of();
        }
        List<TrendHeadline> headlines = parse(html, url, trend, maxNewsPerTrend);
        log.info("Discovered {} Google Search news items using Selenium for trend='{}'", headlines.size(), trend);
        return headlines;
    }

    List<TrendHeadline> parse(String html, String baseUri, String trend, int maxNewsPerTrend) {
        Document document = Jsoup.parse(html, baseUri);
        LinkedHashMap<String, TrendHeadline> unique = new LinkedHashMap<>();
        for (Element card : document.select("div.SoaBEf, div.dbsr, div.MjjYud, article, g-card, a.WlydOe")) {
            String title = extractText(card, ".n0jPhd, .mCBkyc, .JheGif, h3");
            String link = resolveOriginalNewsUrl(extractLink(card));
            String source = extractText(card, ".CEMjEf span, .NUnG9d span, .XTjFC, cite");
            String publishedAt = extractText(card, "time, .OSrXXb, .ZE0LJd span");
            String summary = extractText(card, ".GI74Re, .Y3v8qd, .st");
            String media = extractMediaUrl(card);
            if (title.isBlank() || link.isBlank()) {
                continue;
            }
            unique.putIfAbsent(link, new TrendHeadline(
                    trend,
                    trendNormalizer.clean(title),
                    link,
                    source.isBlank() ? "Google News" : trendNormalizer.clean(source),
                    publishedAt.isBlank() ? null : trendNormalizer.clean(publishedAt),
                    summary.isBlank() ? null : trendNormalizer.clean(summary),
                    new ArticleDetails(
                            summary.isBlank() ? null : trendNormalizer.clean(summary),
                            null,
                            media,
                            inferMediaType(media)
                    )
            ));
        }
        return unique.values().stream().limit(Math.max(1, maxNewsPerTrend)).toList();
    }

    String resolveOriginalNewsUrl(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            return "";
        }
        String decoded = URLDecoder.decode(rawLink, StandardCharsets.UTF_8);
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

    private String extractText(Element root, String selector) {
        Element element = root.selectFirst(selector);
        return element == null ? "" : trendNormalizer.clean(element.text());
    }

    private String extractLink(Element root) {
        Element anchor = root.selectFirst("a[href]");
        return anchor == null ? "" : anchor.absUrl("href");
    }

    private String extractMediaUrl(Element root) {
        Element image = root.selectFirst("img[src], img[data-src], img[data-iurl], img[srcset]");
        if (image == null) {
            return null;
        }
        for (String attribute : List.of("src", "data-src", "data-iurl")) {
            String value = image.absUrl(attribute);
            if (!value.isBlank()) {
                return value;
            }
            value = image.attr(attribute);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        String srcSet = image.attr("srcset");
        if (srcSet == null || srcSet.isBlank()) {
            return null;
        }
        String candidate = srcSet.split(",")[0].trim().split("\\s+")[0];
        return candidate.isBlank() ? null : candidate;
    }

    private String inferMediaType(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return null;
        }
        String lower = mediaUrl.toLowerCase();
        if (lower.endsWith(".gif") || lower.contains(".gif?")) {
            return "gif";
        }
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
            return "youtube";
        }
        if (lower.endsWith(".mp4") || lower.contains(".mp4?")) {
            return "video";
        }
        return "image";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
