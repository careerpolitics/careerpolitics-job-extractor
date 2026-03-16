package com.careerpolitics.scraper.service;

import com.careerpolitics.scraper.exception.WorkflowStepException;
import com.careerpolitics.scraper.model.request.TrendArticleRequest;
import com.careerpolitics.scraper.model.response.TrendArticleResponse;
import com.careerpolitics.scraper.model.response.TrendGeneratedArticle;
import com.careerpolitics.scraper.model.response.TrendMediaBundle;
import com.careerpolitics.scraper.model.response.TrendMediaItem;
import com.careerpolitics.scraper.model.response.TrendNewsItem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrendArticleWorkflowService {

    private final TrendArticleService trendArticleService;
    private final TrendDiversityService trendDiversityService;

    public TrendArticleWorkflowService(TrendArticleService trendArticleService,
                                       TrendDiversityService trendDiversityService) {
        this.trendArticleService = trendArticleService;
        this.trendDiversityService = trendDiversityService;
    }

    public TrendArticleResponse createAndOptionallyPublish(TrendArticleRequest request) {
        List<String> discoveredTrends = trendArticleService.discoverTrends(
                request.getGeo(),
                request.getLanguage(),
                request.getMaxTrends(),
                request.getFallbackTrends()
        );
        List<String> trends = trendDiversityService.selectNonRepeatingTrends(
                discoveredTrends,
                request.getMaxTrends(),
                request.getTrendCooldownHours()
        );

        if (trends.isEmpty()) {
            throw new WorkflowStepException(
                    HttpStatus.NOT_FOUND,
                    "No trending items found from Google Trends and no fallbackTrends were provided",
                    List.of("trends_discovery_failed")
            );
        }

        List<TrendGeneratedArticle> generatedArticles = new ArrayList<>();
        List<TrendNewsItem> allNews = new ArrayList<>();
        List<String> workflowErrors = new ArrayList<>();

        for (String trend : trends) {
            List<String> stepErrors = new ArrayList<>();

            List<TrendNewsItem> newsItems = trendArticleService.discoverNewsForTrend(
                    trend,
                    request.getMaxNewsPerTrend(),
                    request.getGeo(),
                    request.getLanguage()
            );
            if (newsItems.isEmpty()) {
                stepErrors.add("news_fetch_failed: no news found for trend=" + trend);
                workflowErrors.add("No news found for trend: " + trend);
            }
            allNews.addAll(newsItems);

            TrendArticleService.TrendMediaBundle mediaBundle = trendArticleService.discoverMediaForTrend(
                    trend,
                    newsItems,
                    request.getGeo(),
                    request.getLanguage(),
                    8
            );
            List<TrendMediaItem> mediaItems = mediaBundle.items();
            if (mediaItems.isEmpty()) {
                stepErrors.add("media_fetch_warning: no media extracted for trend=" + trend);
            }
            String coverImage = mediaBundle.coverImage();

            Map<String, Object> articleData;
            try {
                articleData = trendArticleService.generateArticleData(
                        trend,
                        newsItems,
                        mediaItems,
                        coverImage,
                        request.getLanguage()
                );
            } catch (Exception ex) {
                stepErrors.add("article_generation_failed: " + ex.getMessage());
                workflowErrors.add("AI generation failed for trend " + trend + ": " + ex.getMessage());
                articleData = fallbackArticleData(trend, newsItems, coverImage);
            }

            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) articleData.getOrDefault(
                    "tags",
                    trendArticleService.pickDefaultTagsForTrend(trend)
            );
            @SuppressWarnings("unchecked")
            List<String> keywords = (List<String>) articleData.getOrDefault(
                    "keywords",
                    trendArticleService.pickDefaultKeywordsForTrend(trend)
            );
            String title = String.valueOf(
                    articleData.getOrDefault("title", trend + " - Latest Jobs & Education Update")
            );
            String markdown = String.valueOf(articleData.getOrDefault("markdown", ""));

            boolean requestedPublished = request.shouldPublish();
            Map<String, Object> publishResponse = trendArticleService.publishArticle(
                    title,
                    markdown,
                    tags,
                    trend,
                    newsItems,
                    coverImage,
                    requestedPublished
            );
            boolean published = requestedPublished && Boolean.TRUE.equals(publishResponse.get("success"));
            if (!Boolean.TRUE.equals(publishResponse.get("success"))) {
                String error = String.valueOf(publishResponse.getOrDefault("error", "unknown error"));
                stepErrors.add("publish_step_failed: " + error);
            }

            trendDiversityService.recordTrendHistory(trend, published);

            generatedArticles.add(TrendGeneratedArticle.builder()
                    .trend(trend)
                    .title(title)
                    .markdown(markdown)
                    .tags(tags)
                    .keywords(keywords)
                    .sources(newsItems)
                    .media(mediaItems)
                    .coverImage(coverImage)
                    .published(published)
                    .publishResponse(publishResponse)
                    .errors(stepErrors)
                    .build());
        }

        boolean allNewsMissing = generatedArticles.stream()
                .allMatch(a -> a.getSources() == null || a.getSources().isEmpty());
        if (allNewsMissing) {
            throw new WorkflowStepException(
                    HttpStatus.NOT_FOUND,
                    "News discovery failed for all trends",
                    workflowErrors.isEmpty() ? List.of("news_fetch_failed_for_all_trends") : workflowErrors
            );
        }

        TrendGeneratedArticle first = generatedArticles.get(0);
        return TrendArticleResponse.builder()
                .trends(trends)
                .news(allNews)
                .articles(generatedArticles)
                .generatedTitle(first != null ? first.getTitle() : null)
                .generatedMarkdown(first != null ? first.getMarkdown() : null)
                .published(first != null && first.isPublished())
                .publishResponse(first != null ? first.getPublishResponse() : null)
                .errors(workflowErrors)
                .build();
    }

    private Map<String, Object> fallbackArticleData(String trend,
                                                    List<TrendNewsItem> newsItems,
                                                    String coverImage) {
        StringBuilder markdown = new StringBuilder();
        if (coverImage != null && !coverImage.isBlank()) {
            markdown.append("![Cover](").append(coverImage).append(")\n\n");
        }
        markdown.append("## ").append(trend).append(" - Update\n\n");
        markdown.append("We could not generate full AI article content right now. Here are the latest collected details.\n\n");
        for (TrendNewsItem item : newsItems) {
            markdown.append("- [").append(item.getTitle()).append("](").append(item.getLink()).append(") - ")
                    .append(item.getSource()).append("\n");
        }
        if (newsItems.isEmpty()) {
            markdown.append("- No validated news items were available at generation time.\n");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", trend + " - Latest Jobs & Education Update");
        result.put("markdown", markdown.toString());
        result.put("tags", trendArticleService.pickDefaultTagsForTrend(trend));
        result.put("keywords", trendArticleService.pickDefaultKeywordsForTrend(trend));
        return result;
    }
}
