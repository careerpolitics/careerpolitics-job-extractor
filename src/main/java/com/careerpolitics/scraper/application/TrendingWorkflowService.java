package com.careerpolitics.scraper.application;

import com.careerpolitics.scraper.domain.model.GeneratedArticle;
import com.careerpolitics.scraper.domain.model.GeneratedArticleDraft;
import com.careerpolitics.scraper.domain.model.PublishingResult;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.model.TrendTopic;
import com.careerpolitics.scraper.domain.port.ArticleGenerator;
import com.careerpolitics.scraper.domain.port.ArticlePublisher;
import com.careerpolitics.scraper.domain.port.TrendDiscoveryClient;
import com.careerpolitics.scraper.domain.port.TrendHeadlineDetailClient;
import com.careerpolitics.scraper.domain.port.TrendNewsClient;
import com.careerpolitics.scraper.domain.port.TrendTopicCleaner;
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
    private final TrendTopicCleaner trendTopicCleaner;

    public TrendingWorkflowService(TrendDiscoveryClient trendDiscoveryClient,
                                   TrendNewsClient trendNewsClient,
                                   TrendSelectionService trendSelectionService,
                                   ArticleGenerator articleGenerator,
                                   ArticlePublisher articlePublisher,
                                   TrendHeadlineDetailClient trendHeadlineDetailClient,
                                   TrendTopicCleaner trendTopicCleaner) {
        this.trendDiscoveryClient = trendDiscoveryClient;
        this.trendNewsClient = trendNewsClient;
        this.trendSelectionService = trendSelectionService;
        this.articleGenerator = articleGenerator;
        this.articlePublisher = articlePublisher;
        this.trendHeadlineDetailClient = trendHeadlineDetailClient;
        this.trendTopicCleaner = trendTopicCleaner;
    }

    public TrendingArticleResponse generate(TrendingArticleRequest request) {
        log.info("Starting trending workflow geo={} language={} maxTrends={} maxNewsPerTrend={} publish={}",
                request.getGeo(),
                request.getLanguage(),
                request.getMaxTrends(),
                request.getMaxNewsPerTrend(),
                request.shouldPublish());
        List<TrendTopic> discoveredTrends = discoverCandidateTrends(request);
        log.info("Trending workflow discovered {} candidate trends.", discoveredTrends.size());
        List<TrendTopic> selectedTrends = trendSelectionService.pickFreshTrends(
                discoveredTrends,
                request.getMaxTrends(),
                request.getTrendCooldownHours()
        );
        log.info("Trending workflow selected {} fresh trends after cooldown filtering: {}",
                selectedTrends.size(),
                selectedTrends.stream().map(TrendTopic::name).toList());

        if (selectedTrends.isEmpty()) {
            log.warn("No fresh trends available after cooldown filtering.");
            throw new EntityNotFoundException("No fresh trends were available after applying the cooldown window.");
        }

        List<GeneratedArticle> articles = new ArrayList<>();
        List<TrendHeadline> allHeadlines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (TrendTopic trend : selectedTrends) {
            log.info("Collecting news for trend='{}' with maxNewsPerTrend={}", trend.name(), request.getMaxNewsPerTrend());
            List<TrendHeadline> headlines = trendNewsClient.discover(
                    trend.name(),
                    request.getGeo(),
                    request.getLanguage(),
                    request.getMaxNewsPerTrend()
            );
            log.info("Collected {} headlines for trend='{}'.", headlines.size(), trend.name());
            List<TrendHeadline> enrichedHeadlines = trendHeadlineDetailClient.enrich(headlines);
            log.info("Enriched {} headlines with article details for trend='{}'.", enrichedHeadlines.size(), trend.name());
            allHeadlines.addAll(enrichedHeadlines);

            List<String> articleWarnings = new ArrayList<>();
            if (enrichedHeadlines.isEmpty()) {
                articleWarnings.add("No headlines found for trend '" + trend.name() + "'. AI article generation will rely on limited source input.");
                log.warn("No headlines found for trend='{}'. AI article generation will run with limited source input.", trend.name());
            }

            GeneratedArticleDraft draft = articleGenerator.generate(trend.name(), request.getLanguage(), enrichedHeadlines);
            log.info("Generated article for trend='{}' using strategy='{}' title='{}'.",
                    trend.name(),
                    draft.strategy(),
                    draft.title());
            PublishingResult publishingResult = articlePublisher.publish(
                    draft.title(),
                    draft.markdown(),
                    draft.description(),
                    draft.tags(),
                    trend.name(),
                    enrichedHeadlines,
                    request
            );
            boolean published = request.shouldPublish() && publishingResult.success();

            if (!publishingResult.success()) {
                warnings.add("Publishing failed for trend '" + trend.name() + "': " + publishingResult.message());
                log.warn("Publishing did not succeed for trend='{}': {}", trend.name(), publishingResult.message());
            }

            if (published) {
                trendSelectionService.remember(trend, true);
                log.info("Recorded trend='{}' in history after successful publishing.", trend.name());
            } else {
                log.info("Skipping cooldown history for trend='{}' because the article was not published successfully.", trend.name());
            }

            articles.add(new GeneratedArticle(
                    trend.name(),
                    draft.title(),
                    draft.markdown(),
                    draft.tags(),
                    draft.description(),
                    enrichedHeadlines,
                    published,
                    publishingResult,
                    articleWarnings,
                    draft.strategy()
            ));
        }

        if (allHeadlines.isEmpty()) {
            warnings.add("No headlines were returned from the Selenium Google News workflow. AI article generation ran without source headlines.");
            log.warn("Workflow completed without any Selenium news headlines. AI article generation ran without source headlines.");
        }

        TrendingArticleResponse response = new TrendingArticleResponse(
                selectedTrends.stream().map(TrendTopic::name).toList(),
                deduplicateHeadlines(allHeadlines),
                articles,
                warnings
        );
        log.info("Trending workflow completed with {} articles, {} warnings.",
                response.articles().size(),
                response.warnings().size());
        return response;
    }

    public List<String> discoverTrends(String geo, String language, int maxTrends, List<String> requestedTrends) {
        TrendingArticleRequest request = new TrendingArticleRequest();
        request.setGeo(geo);
        request.setLanguage(language);
        request.setMaxTrends(maxTrends);
        request.setRequestedTrends(requestedTrends);
        return discoverCandidateTrends(request).stream().map(TrendTopic::name).toList();
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

    private List<TrendTopic> discoverCandidateTrends(TrendingArticleRequest request) {
        if (request.getRequestedTrends() != null && !request.getRequestedTrends().isEmpty()) {
            List<String> requestedTrendNames = request.getRequestedTrends().stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .limit(request.getMaxTrends())
                    .toList();
            List<TrendTopic> normalizedRequestedTrends = trendTopicCleaner.cleanTopics(
                    buildRequestedTrendsTable(requestedTrendNames),
                    request.getMaxTrends()
            );
            if (!normalizedRequestedTrends.isEmpty()) {
                log.info("Normalized {} requested trends into {} AI topics for article generation.", requestedTrendNames.size(), normalizedRequestedTrends.size());
                return normalizedRequestedTrends;
            }
            List<TrendTopic> requestedTrends = requestedTrendNames.stream()
                    .map(value -> new TrendTopic(value, value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""), List.of(value)))
                    .toList();
            log.info("Using {} requested trends from request instead of Selenium discovery.", requestedTrends.size());
            return requestedTrends;
        }
        return trendDiscoveryClient.discover(request.getGeo(), request.getLanguage(), request.getMaxTrends());
    }

    private List<TrendHeadline> deduplicateHeadlines(List<TrendHeadline> headlines) {
        return new ArrayList<>(new LinkedHashSet<>(headlines));
    }

    private String buildRequestedTrendsTable(List<String> requestedTrendNames) {
        StringBuilder table = new StringBuilder("<table><tbody>");
        for (String requestedTrendName : requestedTrendNames) {
            table.append("<tr><td>")
                    .append(escapeHtml(requestedTrendName))
                    .append("</td></tr>");
        }
        table.append("</tbody></table>");
        return table.toString();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
