package com.careerpolitics.scraper.model;


import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "job_summaries")
public class JobSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true, length = 1000)
    private String url;

    @Column(name = "source_website", nullable = false)
    private String sourceWebsite;

    @Column(name = "discovered_at", nullable = false)
    private LocalDateTime discoveredAt;

    @Column(nullable = false)
    private boolean processed = false;

    @Column(name = "retry_count")
    private int retryCount = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "department")
    private String department;

    @Column(name = "vacancies")
    private Integer vacancies;

    @Column(name = "image_url")
    private String imageUrl;

    @PrePersist
    protected void onCreate() {
        discoveredAt = LocalDateTime.now();
    }
}
