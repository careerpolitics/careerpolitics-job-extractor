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
import java.util.Arrays;
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
    private static final List<String> ROW_METADATA_MARKERS = List.of(
            "search volume", "started", "ended", "trend breakdown", "explore link",
            "copy to clipboard", "download csv"
    );

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
            for (Element element : document.select(HEADLINE_TEXT_SELECTOR)) {
                addIfValid(unique, normalizeCandidate(extractRawText(element)));
            }
        }

        if (unique.isEmpty()) {
            for (Element row : document.select(HEADLINE_ROW_SELECTOR)) {
                for (String line : extractLineCandidates(extractRawText(row))) {
                    addIfValid(unique, line);
                    break;
                }
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

        return extractLineCandidates(extractRawText(row)).stream()
                .findFirst()
                .orElse("");
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
        return element.wholeText();
    }

    private void addIfValid(LinkedHashMap<String, String> unique, String candidate) {
        if (isValidTrend(candidate)) {
            unique.putIfAbsent(trendNormalizer.slug(candidate), candidate);
        }
    }

    private List<String> extractLineCandidates(String raw) {
        return Arrays.stream(raw.split("\\R+"))
                .map(this::normalizeCandidate)
                .filter(this::isLikelyHeadlineLine)
                .toList();
    }

    private boolean isLikelyHeadlineLine(String value) {
        if (!isValidTrend(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (ROW_METADATA_MARKERS.stream().anyMatch(normalized::contains)) {
            return false;
        }
        if (normalized.startsWith("./explore?") || normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return false;
        }
        if (value.contains(",") && value.split(",").length >= 3) {
            return false;
        }
        return true;
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
                .replaceAll("(?i)\\b(?:search volume|started|ended|trend breakdown|explore link|copy to clipboard|download csv)\\b", "")
                .replaceAll("(?i)\\bat\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*(?:am|pm)?\\s*utc(?:[+-]\\d{1,2}(?::\\d{2})?)?", "")
                .replaceAll("(?i)\\butc(?:[+-]\\d{1,2}(?::\\d{2})?)?\\b", "")
                .replaceAll("\\s*[|•·]+\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
