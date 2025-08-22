package com.careerpolitics.scraper.model;


import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Data
@Entity
@Table(name = "scraping_configs")
public class ScrapingConfig {
    @Id
    @Column(name = "id", nullable = false)
    private String id = "default";

    @Column(name = "default_batch_size", nullable = false)
    private int defaultBatchSize = 5;

    @Column(name = "max_retry_attempts", nullable = false)
    private int maxRetryAttempts = 3;

    @Column(name = "retry_delay_ms", nullable = false)
    private int retryDelayMs = 5000;

    @Column(name = "request_delay_ms", nullable = false)
    private int requestDelayMs = 1000;

    @Column(name = "enable_caching", nullable = false)
    private boolean enableCaching = true;

    @Column(name = "cache_timeout_minutes", nullable = false)
    private int cacheTimeoutMinutes = 60;

    @ElementCollection
    @CollectionTable(name = "default_websites", joinColumns = @JoinColumn(name = "config_id"))
    @Column(name = "website_name")
    private List<String> defaultWebsites;

    @Column(name = "max_concurrent_scrapers")
    private int maxConcurrentScrapers = 3;

    @Column(name = "job_expiry_days")
    private int jobExpiryDays = 30;

    @Column(name = "enable_image_processing")
    private boolean enableImageProcessing = true;

    @Column(name = "image_quality")
    private int imageQuality = 85;

    @Column(name = "max_image_size_kb")
    private int maxImageSizeKb = 500;
}
