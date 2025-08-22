package com.careerpolitics.scraper.controller;

import com.careerpolitics.scraper.model.JobDetail;
import com.careerpolitics.scraper.service.DetailScraperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/careerpolitics/detail-scraping")
@Tag(name = "Detail Scraping", description = "APIs for scraping detailed job information")
@RequiredArgsConstructor
public class DetailScrapingController {

    private final DetailScraperService detailScraperService;

    @PostMapping("/scrape/batch")
    @Operation(summary = "Scrape details for a batch of URLs")
    public ResponseEntity<ScrapeBatchResponse> scrapeBatch(
            @RequestParam(defaultValue = "5") int batchSize,
            @RequestParam(defaultValue = "false") boolean forceRetry) {

        ScrapeBatchResponse response = detailScraperService.scrapeBatch(batchSize, forceRetry);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/scrape/url/{id}")
    @Operation(summary = "Scrape details for a specific URL")
    public ResponseEntity<JobDetail> scrapeSingleUrl(@PathVariable Long id) {
        JobDetail detail = detailScraperService.scrapeSingleUrl(id);
        return ResponseEntity.ok(detail);
    }

    @PostMapping("/scrape/urls")
    @Operation(summary = "Scrape details for multiple specific URLs")
    public ResponseEntity<List<JobDetail>> scrapeMultipleUrls(@RequestBody List<Long> urlIds) {
        List<JobDetail> details = detailScraperService.scrapeMultipleUrls(urlIds);
        return ResponseEntity.ok(details);
    }

    @GetMapping("/status/{urlId}")
    @Operation(summary = "Get scraping status for a URL")
    public ResponseEntity<ScrapeStatus> getScrapeStatus(@PathVariable Long urlId) {
        return ResponseEntity.ok(detailScraperService.getScrapeStatus(urlId));
    }

    @PostMapping("/retry/failed")
    @Operation(summary = "Retry failed scraping attempts")
    public ResponseEntity<?> retryFailedScrapes(
            @RequestParam(defaultValue = "10") int maxRetries) {
        return ResponseEntity.ok(detailScraperService.retryFailedScrapes(maxRetries));
    }

    @GetMapping("/progress")
    @Operation(summary = "Get scraping progress")
    public ResponseEntity<?> getScrapingProgress() {
        return ResponseEntity.ok(detailScraperService.getScrapingProgress());
    }
}
