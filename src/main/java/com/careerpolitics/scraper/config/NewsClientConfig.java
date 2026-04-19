package com.careerpolitics.scraper.config;

import com.careerpolitics.scraper.application.TrendNormalizer;
import com.careerpolitics.scraper.domain.port.TrendNewsClient;
import com.careerpolitics.scraper.infrastructure.news.CompositeNewsClient;
import com.careerpolitics.scraper.infrastructure.news.GoogleNewsRssClient;
import com.careerpolitics.scraper.infrastructure.selenium.GoogleNewsSeleniumClient;
import com.careerpolitics.scraper.infrastructure.selenium.SeleniumBrowserClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class NewsClientConfig {

    @Bean
    GoogleNewsRssClient googleNewsRssClient(RestClient restClient,
                                            TrendingProperties properties,
                                            TrendNormalizer trendNormalizer) {
        return new GoogleNewsRssClient(restClient, properties, trendNormalizer);
    }

    @Bean
    GoogleNewsSeleniumClient googleNewsSeleniumClient(SeleniumBrowserClient browserClient,
                                                      TrendingProperties properties,
                                                      TrendNormalizer trendNormalizer) {
        return new GoogleNewsSeleniumClient(browserClient, properties, trendNormalizer);
    }

    @Bean
    TrendNewsClient trendNewsClient(GoogleNewsRssClient rssClient,
                                    GoogleNewsSeleniumClient seleniumClient,
                                    TrendingProperties properties) {
        return new CompositeNewsClient(rssClient, seleniumClient, properties);
    }
}