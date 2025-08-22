package com.careerpolitics.scraper.controller;

import com.careerpolitics.scraper.model.JobSummary;
import com.careerpolitics.scraper.service.UrlCollectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/careerpolitics/url-collection")
@Tag(name = "URL Collection", description = "APIs for collecting job URLs from websites")
@RequiredArgsConstructor
public class UrlCollectionController {

    private final UrlCollectorService urlCollectorService;

    @PostMapping("/discover/{website}")
    @Operation(summary = "Discover job URLs from a specific website")
    public ResponseEntity<UrlDiscoveryResponse> discoverUrls(
            @PathVariable String website,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {

        UrlDiscoveryResponse response = urlCollectorService.discoverUrls(website, forceRefresh);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/websites")
    @Operation(summary = "Get list of supported websites")
    public ResponseEntity<List<String>> getSupportedWebsites() {
        return ResponseEntity.ok(urlCollectorService.getSupportedWebsites());
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending URLs waiting for detail scraping")
    public ResponseEntity<List<JobSummary>> getPendingUrls(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(urlCollectorService.getPendingUrls(limit));
    }

    @DeleteMapping("/urls/{id}")
    @Operation(summary = "Delete a specific URL from collection")
    public ResponseEntity<Void> deleteUrl(@PathVariable Long id) {
        urlCollectorService.deleteUrl(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @Operation(summary = "Get URL collection statistics")
    public ResponseEntity<?> getCollectionStats() {
        return ResponseEntity.ok(urlCollectorService.getCollectionStats());
    }
}
