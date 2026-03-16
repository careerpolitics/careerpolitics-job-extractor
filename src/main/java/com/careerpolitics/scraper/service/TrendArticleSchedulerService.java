package com.careerpolitics.scraper.service;

import com.careerpolitics.scraper.model.request.TrendArticleRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class TrendArticleSchedulerService {

    private final TrendArticleWorkflowService trendArticleWorkflowService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${careerpolitics.content.trends.scheduler.enabled:false}")
    private boolean enabled;

    @Value("${careerpolitics.content.trends.scheduler.geo:IN}")
    private String geo;

    @Value("${careerpolitics.content.trends.scheduler.language:en-US}")
    private String language;

    @Value("${careerpolitics.content.trends.scheduler.max-trends:3}")
    private int maxTrends;

    @Value("${careerpolitics.content.trends.scheduler.max-news-per-trend:3}")
    private int maxNewsPerTrend;

    @Value("${careerpolitics.content.trends.scheduler.publish:true}")
    private boolean publish;

    @Value("${careerpolitics.content.trends.scheduler.trend-cooldown-hours:24}")
    private int trendCooldownHours;

    @Value("${careerpolitics.content.trends.scheduler.fallback-trends:}")
    private List<String> fallbackTrends;

    public TrendArticleSchedulerService(TrendArticleWorkflowService trendArticleWorkflowService) {
        this.trendArticleWorkflowService = trendArticleWorkflowService;
    }

    @Scheduled(cron = "${careerpolitics.content.trends.scheduler.cron:0 0 * * * *}")
    public void runScheduledTrendingArticlePipeline() {
        if (!enabled) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn(
                    "Skipping scheduled trend article run because previous execution is still running"
            );
            return;
        }

        try {
            TrendArticleRequest request = new TrendArticleRequest();
            request.setGeo(geo);
            request.setLanguage(language);
            request.setMaxTrends(Math.max(1, maxTrends));
            request.setMaxNewsPerTrend(Math.max(1, maxNewsPerTrend));
            request.setPublish(publish);
            request.setTrendCooldownHours(Math.max(1, trendCooldownHours));
            request.setFallbackTrends(
                    fallbackTrends == null || fallbackTrends.isEmpty() ? null : fallbackTrends
            );

            var response = trendArticleWorkflowService.createAndOptionallyPublish(request);
            log.info(
                    "Scheduled trend article run completed: trendsProcessed={}, generatedArticles={}",
                    response.getTrends() != null ? response.getTrends().size() : 0,
                    response.getArticles() != null ? response.getArticles().size() : 0
            );
        } catch (Exception ex) {
            log.error("Scheduled trend article run failed", ex);
        } finally {
            running.set(false);
        }
    }
}
