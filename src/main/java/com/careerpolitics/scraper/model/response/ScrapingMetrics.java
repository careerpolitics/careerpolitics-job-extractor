package com.careerpolitics.scraper.model.response;


import lombok.Data;
import lombok.Builder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ScrapingMetrics {
    private int totalUrlsDiscovered;
    private int totalDetailsScraped;
    private int failedScrapes;
    private Map<String, Integer> urlsByWebsite;
    private Map<String, Integer> detailsByWebsite;
    private LocalDateTime lastSuccessfulRun;
    private Duration averageProcessingTime;
    private int activeJobs;
    private int expiredJobs;

    public double getSuccessRate() {
        if (totalUrlsDiscovered == 0) return 0.0;
        return (double) totalDetailsScraped / totalUrlsDiscovered * 100;
    }
}
