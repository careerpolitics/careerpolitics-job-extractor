package com.careerpolitics.scraper.domain.port;

import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.TrendHeadline;

import java.util.List;

public interface ArticleGenerator {
    boolean supportsAi();

    GeneratedArticleDraft generate(String trend, String language, List<TrendHeadline> headlines);
}
