package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.domain.model.TrendDiscoveryCandidate;
import com.careerpolitics.scraper.domain.model.TrendTopic;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.port.TrendDiscoveryClient;
import com.careerpolitics.scraper.domain.port.TrendTopicCleaner;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private final TrendTopicCleaner trendTopicCleaner;

    public GoogleTrendsSeleniumClient(SeleniumBrowserClient browserClient,
                                      TrendingProperties properties,
                                      TrendNormalizer trendNormalizer,
                                      TrendTopicCleaner trendTopicCleaner) {
        this.browserClient = browserClient;
        this.properties = properties;
        this.trendNormalizer = trendNormalizer;
        this.trendTopicCleaner = trendTopicCleaner;
    }

    @Override
    public List<String> discover(String geo, String language, int maxTrends) {
        String url = properties.discovery().googleTrendsUrl()
                + "?geo=" + encode(geo)
                + "&hl=" + encode(language)
                + "&category=9&status=active";

        String html = browserClient.fetchTrendsPage(url);
        if (html.isBlank()) {
            return properties.discovery().fallbackTrends() == null ? List.of() : properties.discovery().fallbackTrends();
        }
        List<String> trends = parse(html, maxTrends);
        log.info("Discovered {} trends using Selenium HTML + AI topic cleaning for geo={} language={}", trends.size(), geo, language);
        return trends;
    }

    List<String> parse(String html, int maxTrends) {
        Document document = Jsoup.parse(html);
        List<TrendDiscoveryCandidate> candidates = new ArrayList<>();

        for (Element row : document.select(HEADLINE_ROW_SELECTOR)) {
            TrendDiscoveryCandidate candidate = extractCandidateFromRow(row);
            if (isUsableCandidate(candidate)) {
                candidates.add(candidate);
            }
        }

        if (candidates.isEmpty()) {
            for (Element element : document.select(HEADLINE_TEXT_SELECTOR + ", td:nth-of-type(2)")) {
                String normalized = normalizeCandidate(extractRawText(element));
                if (isValidTrend(normalized)) {
                    candidates.add(new TrendDiscoveryCandidate(normalized, List.of(), normalized));
                }
            }
        }

        List<TrendTopic> topics = trendTopicCleaner.cleanTopics(candidates, maxTrends);
        if (!topics.isEmpty()) {
            return topics.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            TrendTopic::slug,
                            TrendTopic::name,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ))
                    .values().stream()
                    .limit(Math.max(1, maxTrends))
                    .toList();
        }

        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (TrendDiscoveryCandidate candidate : candidates) {
            addIfValid(unique, trendNormalizer.clean(candidate.title()));
            if (candidate.breakdowns() != null) {
                for (String breakdown : candidate.breakdowns()) {
                    addIfValid(unique, trendNormalizer.clean(breakdown));
                }
            }
        }
        return unique.values().stream().limit(Math.max(1, maxTrends)).toList();
    }

    private TrendDiscoveryCandidate extractCandidateFromRow(Element row) {
        String headline = "";
        for (Element candidate : row.select(HEADLINE_TEXT_SELECTOR)) {
            String normalized = normalizeCandidate(extractRawText(candidate));
            if (isValidTrend(normalized)) {
                headline = normalized;
                break;
            }
        }

        List<String> breakdowns = row.select("a[title], a, [data-term], .trend-breakdown *").stream()
                .map(this::extractRawText)
                .map(this::normalizeCandidate)
                .filter(this::isValidTrend)
                .filter(value -> !value.equalsIgnoreCase(headline))
                .distinct()
                .limit(8)
                .toList();

        String rawText = normalizeCandidate(row.text());
        return new TrendDiscoveryCandidate(headline, breakdowns, rawText);
    }

    private boolean isUsableCandidate(TrendDiscoveryCandidate candidate) {
        return candidate != null && (
                isValidTrend(candidate.title())
                        || (candidate.breakdowns() != null && candidate.breakdowns().stream().anyMatch(this::isValidTrend))
                        || isValidTrend(candidate.rawText())
        );
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
