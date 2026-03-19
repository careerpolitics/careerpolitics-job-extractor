package com.careerpolitics.scraper.infrastructure.google;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.port.TrendDiscoveryClient;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

@Slf4j
@Component
public class GoogleTrendsRssClient implements TrendDiscoveryClient {

    private final RestClient restClient;
    private final TrendingProperties properties;
    private final TrendNormalizer trendNormalizer;

    public GoogleTrendsRssClient(RestClient restClient, TrendingProperties properties, TrendNormalizer trendNormalizer) {
        this.restClient = restClient;
        this.properties = properties;
        this.trendNormalizer = trendNormalizer;
    }

    @Override
    public List<String> discover(String geo, String language, int maxTrends) {
        String url = properties.discovery().googleTrendsRssUrl() + "?geo=" + urlEncode(geo) + "&hl=" + urlEncode(language);
        try {
            String xml = restClient.get().uri(url).retrieve().body(String.class);
            List<String> trends = parse(xml, maxTrends);
            log.info("Discovered {} trends from Google Trends RSS for geo={} language={}", trends.size(), geo, language);
            return trends;
        } catch (Exception exception) {
            log.warn("Unable to fetch Google Trends RSS from {}: {}", url, exception.getMessage());
            return properties.discovery().fallbackTrends() == null ? List.of() : properties.discovery().fallbackTrends();
        }
    }

    List<String> parse(String xml, int maxTrends) {
        Document document = Jsoup.parse(xml, "", Parser.xmlParser());
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        document.select("item > title").forEach(title -> {
            String cleaned = trendNormalizer.clean(title.text());
            String slug = trendNormalizer.slug(cleaned);
            if (!cleaned.isBlank() && !slug.isBlank()) {
                unique.putIfAbsent(slug, cleaned);
            }
        });
        return unique.values().stream().limit(Math.max(1, maxTrends)).toList();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
