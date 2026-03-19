package com.careerpolitics.scraper.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "trend_run_history")
public class TrendRunHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String trend;

    @Column(name = "trend_slug", nullable = false, length = 160)
    private String trendSlug;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    public void setTrend(String trend) {
        this.trend = trend;
    }

    public void setTrendSlug(String trendSlug) {
        this.trendSlug = trendSlug;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    @PrePersist
    void beforeInsert() {
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
    }
}
