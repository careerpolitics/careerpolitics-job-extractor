package com.careerpolitics.scraper.service;


import com.careerpolitics.scraper.model.JobDetail;
import com.careerpolitics.scraper.model.JobSummary;
import com.careerpolitics.scraper.model.response.ScrapeBatchResponse;
import com.careerpolitics.scraper.model.response.ScrapeStatus;
import com.careerpolitics.scraper.repository.JobDetailRepository;
import com.careerpolitics.scraper.repository.JobSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetailScraperService {

    private final JobSummaryRepository jobSummaryRepository;
    private final JobDetailRepository jobDetailRepository;
    private final MarkdownGenerator markdownGenerator;

    public ScrapeBatchResponse scrapeBatch(int batchSize, boolean forceRetry) {
        LocalDateTime start = LocalDateTime.now();
        List<JobSummary> candidates = jobSummaryRepository
                .findByProcessedFalseOrderByDiscoveredAtDesc(PageRequest.of(0, Math.max(1, batchSize)))
                .getContent();

        List<JobDetail> successes = new ArrayList<>();
        Map<Long, String> failures = new LinkedHashMap<>();

        for (JobSummary summary : candidates) {
            try {
                JobDetail detail = scrapeDetail(summary);
                successes.add(detail);
                summary.setProcessed(true);
                summary.setLastError(null);
                jobSummaryRepository.save(summary);
            } catch (Exception ex) {
                log.warn("Failed scraping {}", summary.getUrl(), ex);
                summary.setRetryCount(summary.getRetryCount() + 1);
                summary.setLastError(ex.getMessage());
                jobSummaryRepository.save(summary);
                failures.put(summary.getId(), ex.getMessage());
            }
        }

        Duration time = Duration.between(start, LocalDateTime.now());
        return ScrapeBatchResponse.success(candidates.size(), successes.size(), successes, failures, time);
    }

    public JobDetail scrapeSingleUrl(Long id) {
        JobSummary summary = jobSummaryRepository.findById(id).orElseThrow();
        JobDetail detail = scrapeDetail(summary);
        summary.setProcessed(true);
        summary.setLastError(null);
        jobSummaryRepository.save(summary);
        return detail;
    }

    public List<JobDetail> scrapeMultipleUrls(List<Long> urlIds) {
        return urlIds.stream().map(this::scrapeSingleUrl).collect(Collectors.toList());
    }

    public ScrapeStatus getScrapeStatus(Long urlId) {
        JobSummary s = jobSummaryRepository.findById(urlId).orElseThrow();
        return ScrapeStatus.builder()
                .urlId(s.getId())
                .url(s.getUrl())
                .processed(s.isProcessed())
                .retryCount(s.getRetryCount())
                .lastError(s.getLastError())
                .lastUpdated(s.getDiscoveredAt())
                .build();
    }

    public Map<String, Object> retryFailedScrapes(int maxRetries) {
        List<JobSummary> retryable = jobSummaryRepository.findRetryableJobs(maxRetries);
        int attempted = 0;
        int succeeded = 0;
        for (JobSummary s : retryable) {
            attempted++;
            try {
                scrapeSingleUrl(s.getId());
                succeeded++;
            } catch (Exception ignored) {
            }
        }
        return Map.of("attempted", attempted, "succeeded", succeeded);
    }

    public Map<String, Object> getScrapingProgress() {
        long total = jobSummaryRepository.count();
        long pending = jobSummaryRepository.countByProcessedFalse();
        return Map.of("total", total, "pending", pending, "completed", Math.max(0, total - pending));
    }

    private JobDetail scrapeDetail(JobSummary summary) {
        try {
            Document doc = Jsoup.connect(summary.getUrl())
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();
            String title = Optional.ofNullable(doc.title()).filter(t -> !t.isBlank()).orElse(summary.getTitle());

            JobDetail detail = new JobDetail();
            detail.setTitle(title);
            detail.setUrl(summary.getUrl());
            detail.setSourceWebsite(summary.getSourceWebsite());
            detail.setDescription(title);
            String md = markdownGenerator.generate(detail);
            detail.setMarkdownContent(md);
            return jobDetailRepository.save(detail);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }
}
