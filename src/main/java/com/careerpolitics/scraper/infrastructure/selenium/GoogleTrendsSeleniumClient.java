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
            "google trends", "trending now", "home", "explore", "year in search", "go back", "searches"
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
        String html = browserClient.fetchTrendsPage(url);
        if (html.isBlank()) {
            return properties.discovery().fallbackTrends() == null ? List.of() : properties.discovery().fallbackTrends();
        }
        List<String> trends = parse(html, maxTrends);
        log.info("Discovered {} trends using Selenium for geo={} language={}", trends.size(), geo, language);
        return trends;
    }

    List<String> parse(String html, int maxTrends) {
        Document document = Jsoup.parse(html);
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (Element element : document.select("[data-term], .mZ3RIc, .QNIh4d, td:nth-of-type(2), a[title]")) {
            String candidate = normalizeCandidate(element.attr("data-term").isBlank() ? element.text() : element.attr("data-term"));
            if (isValidTrend(candidate)) {
                unique.putIfAbsent(trendNormalizer.slug(candidate), candidate);
            }
        }
        return unique.values().stream().limit(Math.max(1, maxTrends)).toList();
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
                .replaceAll("(?i)\\b\\d+[+]?\\s*searches\\b", "")
                .replaceAll("(?i)\\barrow_upward\\b", "")
                .replaceAll("(?i)\\btimelapse\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
