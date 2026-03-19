package com.careerpolitics.scraper.domain.response;

import com.careerpolitics.scraper.domain.model.GeneratedArticle;
import com.careerpolitics.scraper.domain.model.TrendHeadline;

import java.util.List;

public record TrendingArticleResponse(
        List<String> trends,
        List<TrendHeadline> news,
        List<GeneratedArticle> articles,
        List<String> warnings
) {
}
