package com.careerpolitics.scraper.application;

import com.careerpolitics.scraper.domain.model.GeneratedArticle;
import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.PublishingResult;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.ArticleGenerator;
import com.careerpolitics.scraper.domain.port.ArticlePublisher;
import com.careerpolitics.scraper.domain.port.TrendDiscoveryClient;
import com.careerpolitics.scraper.domain.port.TrendHeadlineDetailClient;
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
    private final TrendHeadlineDetailClient trendHeadlineDetailClient;

    public TrendingWorkflowService(TrendDiscoveryClient trendDiscoveryClient,
                                   TrendNewsClient trendNewsClient,
                                   TrendSelectionService trendSelectionService,
                                   ArticleGenerator articleGenerator,
                                   ArticlePublisher articlePublisher,
                                   TrendHeadlineDetailClient trendHeadlineDetailClient) {
        this.trendDiscoveryClient = trendDiscoveryClient;
        this.trendNewsClient = trendNewsClient;
        this.trendSelectionService = trendSelectionService;
        this.articleGenerator = articleGenerator;
        this.articlePublisher = articlePublisher;
        this.trendHeadlineDetailClient = trendHeadlineDetailClient;
    }

    public TrendingArticleResponse generate(TrendingArticleRequest request) {
        log.info("Starting trending workflow geo={} language={} maxTrends={} maxNewsPerTrend={} publish={}",
                request.getGeo(),
                request.getLanguage(),
                request.getMaxTrends(),
                request.getMaxNewsPerTrend(),
                request.shouldPublish());
        List<String> discoveredTrends = discoverCandidateTrends(request);
        log.info("Trending workflow discovered {} candidate trends.", discoveredTrends.size());
        List<String> selectedTrends = trendSelectionService.pickFreshTrends(
                discoveredTrends,
                request.getMaxTrends(),
                request.getTrendCooldownHours()
        );
        log.info("Trending workflow selected {} fresh trends after cooldown filtering: {}",
                selectedTrends.size(),
                selectedTrends);

        if (selectedTrends.isEmpty()) {
            log.warn("No fresh trends available after cooldown filtering.");
            throw new EntityNotFoundException("No fresh trends were available after applying the cooldown window.");
        }

        List<GeneratedArticle> articles = new ArrayList<>();
        List<TrendHeadline> allHeadlines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String trend : selectedTrends) {
            log.info("Collecting news for trend='{}' with maxNewsPerTrend={}", trend, request.getMaxNewsPerTrend());
            List<TrendHeadline> headlines = trendNewsClient.discover(
                    trend,
                    request.getGeo(),
                    request.getLanguage(),
                    request.getMaxNewsPerTrend()
            );
            log.info("Collected {} headlines for trend='{}'.", headlines.size(), trend);
            List<TrendHeadline> enrichedHeadlines = trendHeadlineDetailClient.enrich(headlines);
            log.info("Enriched {} headlines with article details for trend='{}'.", enrichedHeadlines.size(), trend);
            allHeadlines.addAll(enrichedHeadlines);

            List<String> articleWarnings = new ArrayList<>();
            if (enrichedHeadlines.isEmpty()) {
                articleWarnings.add("No headlines found for trend '" + trend + "'. Generated article uses a template fallback.");
                log.warn("No headlines found for trend='{}'. Falling back to template-driven article generation.", trend);
            }

            GeneratedArticleDraft draft = articleGenerator.generate(trend, request.getLanguage(), enrichedHeadlines);
            log.info("Generated article for trend='{}' using strategy='{}' title='{}'.",
                    trend,
                    draft.strategy(),
                    draft.title());
            PublishingResult publishingResult = articlePublisher.publish(
                    draft.title(),
                    draft.markdown(),
                    draft.tags(),
                    trend,
                    enrichedHeadlines,
                    request
            );
            boolean published = request.shouldPublish() && publishingResult.success();

            if (!publishingResult.success()) {
                warnings.add("Publishing failed for trend '" + trend + "': " + publishingResult.message());
                log.warn("Publishing did not succeed for trend='{}': {}", trend, publishingResult.message());
            }

            if (published) {
                trendSelectionService.remember(trend, true);
                log.info("Recorded trend='{}' in history after successful publishing.", trend);
            } else {
                log.info("Skipping cooldown history for trend='{}' because the article was not published successfully.", trend);
            }

            articles.add(new GeneratedArticle(
                    trend,
                    draft.title(),
                    draft.markdown(),
                    draft.tags(),
                    draft.keywords(),
                    enrichedHeadlines,
                    published,
                    publishingResult,
                    articleWarnings,
                    draft.strategy()
            ));
        }

        if (allHeadlines.isEmpty()) {
            warnings.add("No headlines were returned from the Selenium Google News workflow. Articles were generated using trend-only fallback content.");
            log.warn("Workflow completed without any Selenium news headlines. Generated articles use trend-only fallback content.");
        }

        TrendingArticleResponse response = new TrendingArticleResponse(selectedTrends, deduplicateHeadlines(allHeadlines), articles, warnings);
        log.info("Trending workflow completed with {} articles, {} unique headlines, {} warnings.",
                response.articles().size(),
                response.headlines().size(),
                response.warnings().size());
        return response;
    }

    public List<String> discoverTrends(String geo, String language, int maxTrends, List<String> requestedTrends) {
        TrendingArticleRequest request = new TrendingArticleRequest();
        request.setGeo(geo);
        request.setLanguage(language);
        request.setMaxTrends(maxTrends);
        request.setRequestedTrends(requestedTrends);
        return discoverCandidateTrends(request);
    }

    public List<TrendHeadline> discoverNews(String trend, String geo, String language, int maxNewsPerTrend) {
        log.info("Discovering news for trend='{}' geo={} language={} maxNewsPerTrend={}",
                trend,
                geo,
                language,
                maxNewsPerTrend);
        List<TrendHeadline> headlines = trendNewsClient.discover(trend, geo, language, maxNewsPerTrend);
        log.info("Discovered {} news headlines for trend='{}' via direct request.", headlines.size(), trend);
        return headlines;
    }

    private List<String> discoverCandidateTrends(TrendingArticleRequest request) {
        if (request.getRequestedTrends() != null && !request.getRequestedTrends().isEmpty()) {
            List<String> requestedTrends = request.getRequestedTrends().stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .limit(request.getMaxTrends())
                    .toList();
            log.info("Using {} requested trends from request instead of Selenium discovery.", requestedTrends.size());
            return requestedTrends;
        }
        return trendDiscoveryClient.discover(request.getGeo(), request.getLanguage(), request.getMaxTrends());
    }

    private List<TrendHeadline> deduplicateHeadlines(List<TrendHeadline> headlines) {
        return new ArrayList<>(new LinkedHashSet<>(headlines));
    }
}
