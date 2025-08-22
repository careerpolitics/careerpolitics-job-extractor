package com.careerpolitics.scraper.controller;

import com.careerpolitics.scraper.service.JobOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/careerpolitics/orchestration")
@Tag(name = "Orchestration", description = "APIs for managing the complete scraping workflow")
@RequiredArgsConstructor
public class OrchestrationController {

    private final JobOrchestrationService orchestrationService;

    @PostMapping("/full-cycle")
    @Operation(summary = "Execute full scraping cycle")
    public ResponseEntity<FullCycleResponse> executeFullCycle(
            @RequestParam(defaultValue = "sarkariexam") String website,
            @RequestParam(defaultValue = "10") int batchSize) {

        FullCycleResponse response = orchestrationService.executeFullCycle(website, batchSize);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/schedule")
    @Operation(summary = "Schedule automated scraping")
    public ResponseEntity<?> scheduleScraping(
            @RequestBody ScheduleRequest scheduleRequest) {
        return ResponseEntity.ok(orchestrationService.scheduleScraping(scheduleRequest));
    }

    @DeleteMapping("/schedule/{id}")
    @Operation(summary = "Cancel scheduled scraping")
    public ResponseEntity<Void> cancelSchedule(@PathVariable String id) {
        orchestrationService.cancelSchedule(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get scraping metrics and statistics")
    public ResponseEntity<ScrapingMetrics> getMetrics() {
        return ResponseEntity.ok(orchestrationService.getMetrics());
    }

    @GetMapping("/schedules")
    @Operation(summary = "Get all active schedules")
    public ResponseEntity<?> getActiveSchedules() {
        return ResponseEntity.ok(orchestrationService.getActiveSchedules());
    }

    @PostMapping("/stop/{id}")
    @Operation(summary = "Stop a running scraping job")
    public ResponseEntity<?> stopScrapingJob(@PathVariable String id) {
        return ResponseEntity.ok(orchestrationService.stopScrapingJob(id));
    }
}