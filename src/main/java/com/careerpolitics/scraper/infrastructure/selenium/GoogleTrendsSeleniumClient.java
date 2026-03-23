package com.careerpolitics.scraper.infrastructure.selenium;

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
    private final TrendTopicCleaner trendTopicCleaner;

    public GoogleTrendsSeleniumClient(SeleniumBrowserClient browserClient,
                                      TrendingProperties properties,
                                      TrendTopicCleaner trendTopicCleaner) {
        this.browserClient = browserClient;
        this.properties = properties;
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
        if (!trends.isEmpty()) {
            log.info("Discovered {} trends by extracting the trends table HTML and sending it to AI for geo={} language={}", trends.size(), geo, language);
            return trends;
        }
        return properties.discovery().fallbackTrends() == null ? List.of() : properties.discovery().fallbackTrends();
    }

    List<String> parse(String html, int maxTrends) {
        Document document = Jsoup.parse(html);
        String tableHtml = document.select("table").stream()
                .filter(this::isTrendTable)
                .map(Element::outerHtml)
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
        if (tableHtml.isBlank()) {
            return List.of();
        }

        List<TrendTopic> topics = trendTopicCleaner.cleanTopics(tableHtml, maxTrends);
        return topics.stream()
                .map(TrendTopic::name)
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
}
