package com.careerpolitics.scraper.domain.port;

import com.careerpolitics.scraper.domain.model.TrendTopic;

import java.util.List;

public interface TrendTopicCleaner {

    List<TrendTopic> cleanTopics(String tableHtml, int maxTopics);
}
