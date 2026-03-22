package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.port.TrendDiscoveryClient;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class GoogleTrendsSeleniumClient implements TrendDiscoveryClient {

    private static final List<String> INVALID_TOKENS = List.of(
            "google trends", "trending now", "home", "explore", "year in search", "go back", "searches",
            "trending_up", "active"
    );

    private static final String HEADLINE_ROW_SELECTOR = "tbody tr, [role=row], [data-row-id]";
    private static final String HEADLINE_TEXT_SELECTOR = "[data-term], .mZ3RIc, .QNIh4d, a[title]";

    private final SeleniumBrowserClient browserClient;
    private final TrendingProperties properties;
    private final TrendNormalizer trendNormalizer;

    public GoogleTrendsSeleniumClient(SeleniumBrowserClient browserClient,
                                      TrendingProperties properties,
                                      TrendNormalizer trendNormalizer) {
        this.browserClient = browserClient;
        this.properties = properties;
        this.trendNormalizer = trendNormalizer;
    }

    @Override
    public List<String> discover(String geo, String language, int maxTrends) {
        String url = properties.discovery().googleTrendsUrl()
                + "?geo=" + encode(geo)
                + "&hl=" + encode(language)
                + "&category=9&status=active";

        List<String> trends = browserClient.fetchTrendTitles(url, maxTrends);
        if (!trends.isEmpty()) {
            log.info("Discovered {} headline trends using Selenium DOM extraction for geo={} language={}", trends.size(), geo, language);
            return trends;
        }

        String html = browserClient.fetchTrendsPage(url);
        if (html.isBlank()) {
            return properties.discovery().fallbackTrends() == null ? List.of() : properties.discovery().fallbackTrends();
        }
        trends = parse(html, maxTrends);
        log.info("Discovered {} trends using Selenium HTML fallback for geo={} language={}", trends.size(), geo, language);
        return trends;
    }

    List<String> parse(String html, int maxTrends) {
        Document document = Jsoup.parse(html);
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();

        for (Element row : document.select(HEADLINE_ROW_SELECTOR)) {
            addIfValid(unique, extractHeadlineFromRow(row));
        }

        if (unique.isEmpty()) {
            for (Element element : document.select(HEADLINE_TEXT_SELECTOR + ", td:nth-of-type(2)")) {
                addIfValid(unique, normalizeCandidate(extractRawText(element)));
            }
        }

        return unique.values().stream().limit(Math.max(1, maxTrends)).toList();
    }

    private String extractHeadlineFromRow(Element row) {
        for (Element candidate : row.select(HEADLINE_TEXT_SELECTOR)) {
            String normalized = normalizeCandidate(extractRawText(candidate));
            if (isValidTrend(normalized)) {
                return normalized;
            }
        }
        return "";
    }

    private String extractRawText(Element element) {
        if (element == null) {
            return "";
        }
        if (!element.attr("data-term").isBlank()) {
            return element.attr("data-term");
        }
        if (!element.attr("title").isBlank()) {
            return element.attr("title");
        }
        return element.text();
    }

    private void addIfValid(LinkedHashMap<String, String> unique, String candidate) {
        if (isValidTrend(candidate)) {
            unique.putIfAbsent(trendNormalizer.slug(candidate), candidate);
        }
    }

    private boolean isValidTrend(String value) {
        if (value.isBlank() || value.length() < 3 || value.length() > 120) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return INVALID_TOKENS.stream().noneMatch(normalized::contains);
    }

    private String normalizeCandidate(String raw) {
        return trendNormalizer.clean(raw)
                .replaceAll("(?i)\\b(?:trending_up|arrow_upward|timelapse)\\b", "")
                .replaceAll("(?i)\\bactive\\b", "")
                .replaceAll("(?i)\\b\\d+(?:[.,]\\d+)?(?:[KMB])?\\+?\\s*searches\\b", "")
                .replaceAll("(?i)\\b(?:\\d+\\s*(?:sec(?:ond)?s?|min(?:ute)?s?|hr|hour|day|week|month)s?\\s+ago|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+\\d{1,2}|\\d{1,2}:\\d{2}\\s*(?:am|pm))\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
