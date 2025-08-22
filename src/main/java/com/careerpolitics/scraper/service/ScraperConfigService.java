package com.careerpolitics.scraper.service;


import com.careerpolitics.scraper.model.ScrapingConfig;
import com.careerpolitics.scraper.model.WebsiteConfig;
import com.careerpolitics.scraper.repository.ScrapeConfigRepository;
import com.careerpolitics.scraper.repository.WebsiteConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ScraperConfigService {

    private final WebsiteConfigRepository websiteConfigRepository;
    private final ScrapeConfigRepository scrapeConfigRepository;

    public List<WebsiteConfig> getWebsiteConfigs() {
        return websiteConfigRepository.findAll();
    }

    public Optional<WebsiteConfig> getWebsiteConfig(String name) {
        return websiteConfigRepository.findById(name);
    }

    public WebsiteConfig addWebsiteConfig(WebsiteConfig config) {
        return websiteConfigRepository.save(config);
    }

    public WebsiteConfig updateWebsiteConfig(String name, WebsiteConfig config) {
        config.setName(name);
        return websiteConfigRepository.save(config);
    }

    public void deleteWebsiteConfig(String name) {
        websiteConfigRepository.deleteById(name);
    }

    public ScrapingConfig getScrapingConfig() {
        return scrapeConfigRepository.findById("default").orElseGet(() -> {
            ScrapingConfig cfg = new ScrapingConfig();
            cfg.setId("default");
            return scrapeConfigRepository.save(cfg);
        });
    }

    public ScrapingConfig updateScrapingConfig(ScrapingConfig config) {
        if (config.getId() == null || config.getId().isBlank()) {
            config.setId("default");
        }
        return scrapeConfigRepository.save(config);
    }

    public ScrapingConfig resetScrapingConfig() {
        scrapeConfigRepository.deleteById("default");
        ScrapingConfig cfg = new ScrapingConfig();
        cfg.setId("default");
        return scrapeConfigRepository.save(cfg);
    }

    public Map<String, Object> getConfigHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("websiteConfigs", websiteConfigRepository.count());
        health.put("scrapingConfigPresent", scrapeConfigRepository.existsById("default"));
        return health;
    }
}
