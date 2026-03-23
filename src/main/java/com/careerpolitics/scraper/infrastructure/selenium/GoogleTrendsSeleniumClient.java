package com.careerpolitics.scraper.infrastructure.selenium;

import com.careerpolitics.scraper.application.TrendNormalizer;
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
import java.util.List;

@Slf4j
@Component
public class GoogleTrendsSeleniumClient implements TrendDiscoveryClient {

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
    public List<TrendTopic> discover(String geo, String language, int maxTrends) {
        String url = properties.discovery().googleTrendsUrl()
                + "?geo=" + encode(geo)
                + "&hl=" + encode(language)
                + "&category=9&status=active";

        String html = browserClient.fetchTrendsPage(url);
        if (html.isBlank()) {
            return fallbackTopics();
        }
        List<TrendTopic> trends = parse(html, maxTrends);
        if (!trends.isEmpty()) {
            log.info("Discovered {} trends by extracting the trends table HTML and sending it to AI for geo={} language={}", trends.size(), geo, language);
            return trends;
        }
        return fallbackTopics();
    }

    List<TrendTopic> parse(String html, int maxTrends) {
        Document document = Jsoup.parse(html);
        String tableHtml = document.select("table").stream()
                .filter(this::isTrendTable)
                .map(Element::outerHtml)
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
        if (tableHtml.isBlank()) {
            return List.of();
        }
        return trendTopicCleaner.cleanTopics(tableHtml, maxTrends).stream()
                .limit(Math.max(1, maxTrends))
                .toList();
    }

    private boolean isTrendTable(Element table) {
        return table != null
                && !table.select("tbody tr").isEmpty()
                && (!table.select("[data-term], .mZ3RIc, .QNIh4d, a[title]").isEmpty()
                || table.text().toLowerCase().contains("searches"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private List<TrendTopic> fallbackTopics() {
        if (properties.discovery().fallbackTrends() == null) {
            return List.of();
        }
        return properties.discovery().fallbackTrends().stream()
                .map(name -> trendNormalizer.clean(name))
                .filter(name -> !name.isBlank())
                .map(name -> new TrendTopic(name, trendNormalizer.slug(name), List.of(name)))
                .toList();
    }
}
