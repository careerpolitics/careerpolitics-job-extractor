package com.careerpolitics.scraper.service;


import com.careerpolitics.scraper.model.response.FullCycleResponse;
import com.careerpolitics.scraper.model.response.ScrapeBatchResponse;
import com.careerpolitics.scraper.model.response.ScrapingMetrics;
import com.careerpolitics.scraper.model.response.UrlDiscoveryResponse;
import com.careerpolitics.scraper.repository.JobDetailRepository;
import com.careerpolitics.scraper.repository.JobSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class JobOrchestrationService {

    private final UrlCollectorService urlCollectorService;
    private final DetailScraperService detailScraperService;
    private final JobSummaryRepository jobSummaryRepository;
    private final JobDetailRepository jobDetailRepository;

    private final Map<String, Object> schedules = new ConcurrentHashMap<>();

    public FullCycleResponse executeFullCycle(String website, int batchSize) {
        UrlDiscoveryResponse discovery = urlCollectorService.discoverUrls(website, false);
        ScrapeBatchResponse batch = detailScraperService.scrapeBatch(batchSize, false);
        return FullCycleResponse.create(UUID.randomUUID().toString(), discovery, batch);
    }

    public Map<String, Object> scheduleScraping(com.careerpolitics.scraper.model.request.ScheduleRequest request) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", id);
        info.put("cron", request.getCronExpression());
        info.put("website", request.getWebsite());
        info.put("batchSize", request.getBatchSize());
        schedules.put(id, info);
        return info;
    }

    public void cancelSchedule(String id) {
        schedules.remove(id);
    }

    public ScrapingMetrics getMetrics() {
        int totalUrls = (int) jobSummaryRepository.count();
        int totalDetails = (int) jobDetailRepository.count();
        int failed = jobSummaryRepository.findByProcessedFalse().size();
        return ScrapingMetrics.builder()
                .totalUrlsDiscovered(totalUrls)
                .totalDetailsScraped(totalDetails)
                .failedScrapes(failed)
                .urlsByWebsite(Map.of())
                .detailsByWebsite(Map.of())
                .lastSuccessfulRun(LocalDateTime.now())
                .averageProcessingTime(Duration.ofSeconds(1))
                .activeJobs(jobDetailRepository.countActiveJobs())
                .expiredJobs(jobDetailRepository.countExpiredJobs())
                .build();
    }

    public Collection<Object> getActiveSchedules() {
        return schedules.values();
    }

    public Map<String, Object> stopScrapingJob(String id) {
        return Map.of("stopped", id);
    }
}
