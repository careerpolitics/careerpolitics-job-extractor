package com.careerpolitics.scraper.domain.port;

import com.careerpolitics.scraper.domain.model.PublishingResult;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.request.TrendingArticleRequest;

import java.util.List;

public interface ArticlePublisher {
    PublishingResult publish(String title,
                             String markdown,
                             List<String> tags,
                             String trend,
                             List<TrendHeadline> headlines,
                             TrendingArticleRequest request);
}
