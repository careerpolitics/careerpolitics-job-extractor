package com.careerpolitics.scraper.application;

import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.request.TrendingArticleRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class TrendingScheduler {

    private final TrendingWorkflowService workflowService;
    private final TrendingProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TrendingScheduler(TrendingWorkflowService workflowService, TrendingProperties properties) {
        this.workflowService = workflowService;
        this.properties = properties;
    }

    @Scheduled(cron = "${careerpolitics.trending.scheduler.cron:0 0 */6 * * *}")
    public void run() {
        if (!properties.scheduler().enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("Skipping scheduled run because the previous execution is still in progress.");
            return;
        }

        try {
            TrendingArticleRequest request = new TrendingArticleRequest();
            request.setGeo(properties.scheduler().geo());
            request.setLanguage(properties.scheduler().language());
            request.setMaxTrends(properties.scheduler().maxTrends());
            request.setMaxNewsPerTrend(properties.scheduler().maxNewsPerTrend());
            request.setTrendCooldownHours(properties.scheduler().trendCooldownHours());
            request.setPublish(properties.scheduler().publish());
            request.setFallbackTrends(properties.discovery().fallbackTrends());
            workflowService.generate(request);
            log.info("Scheduled trending article run finished successfully.");
        } catch (Exception exception) {
            log.error("Scheduled trending article run failed.", exception);
        } finally {
            running.set(false);
        }
    }
}
