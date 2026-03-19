package com.careerpolitics.scraper.infrastructure.google;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.TrendNewsClient;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

@Slf4j
@Component
public class GoogleNewsRssClient implements TrendNewsClient {

    private final RestClient restClient;
    private final TrendingProperties properties;
    private final TrendNormalizer trendNormalizer;

    public GoogleNewsRssClient(RestClient restClient, TrendingProperties properties, TrendNormalizer trendNormalizer) {
        this.restClient = restClient;
        this.properties = properties;
        this.trendNormalizer = trendNormalizer;
    }

    @Override
    public List<TrendHeadline> discover(String trend, String geo, String language, int maxNewsPerTrend) {
        String country = geo == null || geo.isBlank() ? "US" : geo.toUpperCase();
        String languageCode = language == null || language.isBlank() ? "en" : language.split("[-_]")[0];
        String ceid = country + ":" + languageCode;
        String url = properties.news().googleNewsRssUrl()
                + "?q=" + urlEncode(trend)
                + "&hl=" + urlEncode(language)
                + "&gl=" + urlEncode(country)
                + "&ceid=" + urlEncode(ceid);

        try {
            String xml = restClient.get().uri(url).retrieve().body(String.class);
            List<TrendHeadline> headlines = parse(xml, trend, maxNewsPerTrend);
            log.info("Discovered {} headlines for trend='{}'", headlines.size(), trend);
            return headlines;
        } catch (Exception exception) {
            log.warn("Unable to fetch Google News RSS for trend='{}': {}", trend, exception.getMessage());
            return List.of();
        }
    }

    List<TrendHeadline> parse(String xml, String trend, int maxNewsPerTrend) {
        Document document = Jsoup.parse(xml, "", Parser.xmlParser());
        LinkedHashMap<String, TrendHeadline> unique = new LinkedHashMap<>();
        for (Element item : document.select("item")) {
            String title = trendNormalizer.clean(item.selectFirst("title") != null ? item.selectFirst("title").text() : "");
            String link = resolveGoogleRedirect(item.selectFirst("link") != null ? item.selectFirst("link").text() : "");
            String source = item.selectFirst("source") != null ? trendNormalizer.clean(item.selectFirst("source").text()) : "Google News";
            String publishedAt = item.selectFirst("pubDate") != null ? trendNormalizer.clean(item.selectFirst("pubDate").text()) : null;
            String summary = item.selectFirst("description") != null ? trendNormalizer.clean(Jsoup.parse(item.selectFirst("description").text()).text()) : null;
            if (title.isBlank() || link.isBlank()) {
                continue;
            }
            unique.putIfAbsent(link, new TrendHeadline(trend, title, link, source, publishedAt, summary));
        }
        return unique.values().stream().limit(Math.max(1, maxNewsPerTrend)).toList();
    }

    String resolveGoogleRedirect(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            return "";
        }
        String decoded = URLDecoder.decode(rawLink, StandardCharsets.UTF_8);
        if (!decoded.contains("url=")) {
            return decoded;
        }
        for (String part : decoded.substring(decoded.indexOf('?') + 1).split("&")) {
            if (part.startsWith("url=")) {
                return URLDecoder.decode(part.substring(4), StandardCharsets.UTF_8);
            }
        }
        return decoded;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
