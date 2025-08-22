package com.careerpolitics.scraper.model;


import jakarta.persistence.*;
import lombok.Data;
import java.util.Map;

@Data
@Entity
@Table(name = "website_configs")
public class WebsiteConfig {
    @Id
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "url_pattern")
    private String urlPattern;

    @Column(name = "title_selector")
    private String titleSelector;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "priority")
    private int priority = 1;

    @ElementCollection
    @CollectionTable(name = "website_selectors", joinColumns = @JoinColumn(name = "website_name"))
    @MapKeyColumn(name = "selector_key")
    @Column(name = "selector_value")
    private Map<String, String> selectors;

    @Column(name = "request_delay_ms")
    private int requestDelayMs = 1000;

    @Column(name = "timeout_ms")
    private int timeoutMs = 10000;

    @Column(name = "max_pages_to_scrape")
    private int maxPagesToScrape = 10;

    @Column(name = "last_scraped")
    private java.time.LocalDateTime lastScraped;

    @Column(name = "success_rate")
    private double successRate = 0.0;
}
