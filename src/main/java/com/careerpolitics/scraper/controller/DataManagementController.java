package com.careerpolitics.scraper.controller;

import com.careerpolitics.scraper.model.JobDetail;
import com.careerpolitics.scraper.service.JobDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/careerpolitics/data")
@Tag(name = "Data Management", description = "APIs for managing scraped data")
@RequiredArgsConstructor
public class DataManagementController {

    private final JobDataService jobDataService;

    @GetMapping("/jobs")
    @Operation(summary = "Get all job details with filtering")
    public ResponseEntity<Page<JobDetail>> getJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String website,
            @RequestParam(required = false) Boolean activeOnly) {

        return ResponseEntity.ok(jobDataService.getJobs(page, size, department, website, activeOnly));
    }

    @GetMapping("/jobs/{id}")
    @Operation(summary = "Get a specific job by ID")
    public ResponseEntity<JobDetail> getJobById(@PathVariable Long id) {
        return jobDataService.getJobById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs/{id}/markdown")
    @Operation(summary = "Get job details in markdown format")
    public ResponseEntity<String> getJobMarkdown(@PathVariable Long id) {
        return jobDataService.getJobMarkdown(id)
                .map(markdown -> ResponseEntity.ok()
                        .header("Content-Type", "text/markdown")
                        .body(markdown))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/jobs/{id}")
    @Operation(summary = "Delete a job entry")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        jobDataService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/export")
    @Operation(summary = "Export jobs data")
    public ResponseEntity<?> exportJobs(
            @RequestParam String format,
            @RequestParam(required = false) String department) {

        return ResponseEntity.ok(jobDataService.exportJobs(format, department));
    }

    @GetMapping("/departments")
    @Operation(summary = "Get all unique departments")
    public ResponseEntity<List<String>> getDepartments() {
        return ResponseEntity.ok(jobDataService.getDepartments());
    }

    @GetMapping("/websites")
    @Operation(summary = "Get all unique websites")
    public ResponseEntity<List<String>> getWebsites() {
        return ResponseEntity.ok(jobDataService.getWebsites());
    }

    @PostMapping("/jobs/{id}/toggle-active")
    @Operation(summary = "Toggle job active status")
    public ResponseEntity<JobDetail> toggleJobActiveStatus(@PathVariable Long id) {
        return ResponseEntity.ok(jobDataService.toggleJobActiveStatus(id));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get data statistics")
    public ResponseEntity<?> getDataStats() {
        return ResponseEntity.ok(jobDataService.getDataStats());
    }
}
