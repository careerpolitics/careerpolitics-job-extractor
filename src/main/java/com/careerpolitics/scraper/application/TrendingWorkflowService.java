package com.careerpolitics.scraper.application;

import com.careerpolitics.scraper.domain.model.GeneratedArticle;
import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.PublishingResult;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.ArticleGenerator;
import com.careerpolitics.scraper.domain.port.ArticlePublisher;
import com.careerpolitics.scraper.domain.port.TrendDiscoveryClient;
import com.careerpolitics.scraper.domain.port.TrendNewsClient;
import com.careerpolitics.scraper.domain.request.TrendingArticleRequest;
import com.careerpolitics.scraper.domain.response.TrendingArticleResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
public class TrendingWorkflowService {

    private final TrendDiscoveryClient trendDiscoveryClient;
    private final TrendNewsClient trendNewsClient;
    private final TrendSelectionService trendSelectionService;
    private final ArticleGenerator articleGenerator;
    private final ArticlePublisher articlePublisher;

    public TrendingWorkflowService(TrendDiscoveryClient trendDiscoveryClient,
                                   TrendNewsClient trendNewsClient,
                                   TrendSelectionService trendSelectionService,
                                   ArticleGenerator articleGenerator,
                                   ArticlePublisher articlePublisher) {
        this.trendDiscoveryClient = trendDiscoveryClient;
        this.trendNewsClient = trendNewsClient;
        this.trendSelectionService = trendSelectionService;
        this.articleGenerator = articleGenerator;
        this.articlePublisher = articlePublisher;
    }

    public TrendingArticleResponse generate(TrendingArticleRequest request) {
        List<String> discoveredTrends = discoverCandidateTrends(request);
        List<String> selectedTrends = trendSelectionService.pickFreshTrends(
                discoveredTrends,
                request.getMaxTrends(),
                request.getTrendCooldownHours()
        );

        if (selectedTrends.isEmpty()) {
            throw new EntityNotFoundException("No fresh trends were available after applying the cooldown window.");
        }

        List<GeneratedArticle> articles = new ArrayList<>();
        List<TrendHeadline> allHeadlines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String trend : selectedTrends) {
            List<TrendHeadline> headlines = trendNewsClient.discover(
                    trend,
                    request.getGeo(),
                    request.getLanguage(),
                    request.getMaxNewsPerTrend()
            );
            allHeadlines.addAll(headlines);

            List<String> articleWarnings = new ArrayList<>();
            if (headlines.isEmpty()) {
                articleWarnings.add("No headlines found for trend '" + trend + "'. Generated article uses a template fallback.");
            }

            GeneratedArticleDraft draft = articleGenerator.generate(trend, request.getLanguage(), headlines);
            PublishingResult publishingResult = request.shouldPublish()
                    ? articlePublisher.publish(draft.title(), draft.markdown(), draft.tags(), trend, request)
                    : PublishingResult.skipped("Publishing disabled for this request.");
            boolean published = request.shouldPublish() && publishingResult.success();

            if (request.shouldPublish() && !publishingResult.success()) {
                warnings.add("Publishing failed for trend '" + trend + "': " + publishingResult.message());
            }

            trendSelectionService.remember(trend, published);

            articles.add(new GeneratedArticle(
                    trend,
                    draft.title(),
                    draft.markdown(),
                    draft.tags(),
                    draft.keywords(),
                    headlines,
                    published,
                    publishingResult,
                    articleWarnings,
                    draft.strategy()
            ));
        }

        if (allHeadlines.isEmpty()) {
            warnings.add("No headlines were returned from the Selenium Google News workflow. Articles were generated using trend-only fallback content.");
        }

        return new TrendingArticleResponse(selectedTrends, deduplicateHeadlines(allHeadlines), articles, warnings);
    }

    public List<String> discoverTrends(String geo, String language, int maxTrends, List<String> fallbackTrends) {
        TrendingArticleRequest request = new TrendingArticleRequest();
        request.setGeo(geo);
        request.setLanguage(language);
        request.setMaxTrends(maxTrends);
        request.setFallbackTrends(fallbackTrends);
        return discoverCandidateTrends(request);
    }

    public List<TrendHeadline> discoverNews(String trend, String geo, String language, int maxNewsPerTrend) {
        return trendNewsClient.discover(trend, geo, language, maxNewsPerTrend);
    }

    private List<String> discoverCandidateTrends(TrendingArticleRequest request) {
        if (request.getFallbackTrends() != null && !request.getFallbackTrends().isEmpty()) {
            return request.getFallbackTrends().stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .limit(request.getMaxTrends())
                    .toList();
        }
        return trendDiscoveryClient.discover(request.getGeo(), request.getLanguage(), request.getMaxTrends());
    }

    private List<TrendHeadline> deduplicateHeadlines(List<TrendHeadline> headlines) {
        return new ArrayList<>(new LinkedHashSet<>(headlines));
    }
}
