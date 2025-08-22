package com.careerpolitics.scraper.controller;

import com.careerpolitics.scraper.model.WebsiteConfig;
import com.careerpolitics.scraper.service.ScraperConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/careerpolitics/config")
@Tag(name = "Configuration", description = "APIs for managing scraper configuration")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ScraperConfigService configService;

    @GetMapping("/websites")
    @Operation(summary = "Get website configurations")
    public ResponseEntity<List<WebsiteConfig>> getWebsiteConfigs() {
        return ResponseEntity.ok(configService.getWebsiteConfigs());
    }

    @GetMapping("/websites/{name}")
    @Operation(summary = "Get a specific website configuration")
    public ResponseEntity<WebsiteConfig> getWebsiteConfig(@PathVariable String name) {
        return configService.getWebsiteConfig(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/websites")
    @Operation(summary = "Add a new website configuration")
    public ResponseEntity<WebsiteConfig> addWebsiteConfig(@RequestBody WebsiteConfig config) {
        return ResponseEntity.ok(configService.addWebsiteConfig(config));
    }

    @PutMapping("/websites/{name}")
    @Operation(summary = "Update website configuration")
    public ResponseEntity<WebsiteConfig> updateWebsiteConfig(
            @PathVariable String name, @RequestBody WebsiteConfig config) {
        return ResponseEntity.ok(configService.updateWebsiteConfig(name, config));
    }

    @DeleteMapping("/websites/{name}")
    @Operation(summary = "Delete website configuration")
    public ResponseEntity<Void> deleteWebsiteConfig(@PathVariable String name) {
        configService.deleteWebsiteConfig(name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scraping")
    @Operation(summary = "Get scraping configuration")
    public ResponseEntity<ScrapingConfig> getScrapingConfig() {
        return ResponseEntity.ok(configService.getScrapingConfig());
    }

    @PutMapping("/scraping")
    @Operation(summary = "Update scraping configuration")
    public ResponseEntity<ScrapingConfig> updateScrapingConfig(@RequestBody ScrapingConfig config) {
        return ResponseEntity.ok(configService.updateScrapingConfig(config));
    }

    @PostMapping("/scraping/reset")
    @Operation(summary = "Reset scraping configuration to defaults")
    public ResponseEntity<ScrapingConfig> resetScrapingConfig() {
        return ResponseEntity.ok(configService.resetScrapingConfig());
    }

    @GetMapping("/health")
    @Operation(summary = "Get configuration health status")
    public ResponseEntity<?> getConfigHealth() {
        return ResponseEntity.ok(configService.getConfigHealth());
    }
}
