package com.careerpolitics.scraper.infrastructure.news;

import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.TrendNewsClient;
import com.careerpolitics.scraper.infrastructure.selenium.GoogleNewsSeleniumClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class CompositeNewsClient implements TrendNewsClient {

    private final GoogleNewsRssClient rssClient;
    private final GoogleNewsSeleniumClient seleniumClient;
    private final TrendingProperties properties;

    public CompositeNewsClient(GoogleNewsRssClient rssClient,
                               GoogleNewsSeleniumClient seleniumClient,
                               TrendingProperties properties) {
        this.rssClient = rssClient;
        this.seleniumClient = seleniumClient;
        this.properties = properties;
    }

    @Override
    public List<TrendHeadline> discover(String trend, String geo, String language, int maxNewsPerTrend) {
        if (properties.news().rssEnabled()) {
            List<TrendHeadline> rssResults = rssClient.discover(trend, geo, language, maxNewsPerTrend);
            if (!rssResults.isEmpty()) {
                log.info("Google News RSS returned {} results for trend='{}'. Skipping Selenium.", rssResults.size(), trend);
                return rssResults;
            }
            log.warn("Google News RSS returned 0 results for trend='{}'. Falling back to Selenium.", trend);
        } else {
            log.debug("Google News RSS is disabled. Using Selenium directly for trend='{}'.", trend);
        }

        return seleniumClient.discover(trend, geo, language, maxNewsPerTrend);
    }
}