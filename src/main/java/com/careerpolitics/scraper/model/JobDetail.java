package com.careerpolitics.scraper.model;


import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

@Data
@Entity
@Table(name = "job_details")
public class JobDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true, length = 1000)
    private String url;

    @Column(name = "department")
    private String department;

    @Column(name = "vacancies")
    private Integer vacancies;

    @Column(name = "application_link", length = 1000)
    private String applicationLink;

    @Column(name = "notification_link", length = 1000)
    private String notificationLink;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "job_important_dates", joinColumns = @JoinColumn(name = "job_detail_id"))
    @MapKeyColumn(name = "event_name")
    @Column(name = "event_date")
    private Map<String, String> importantDates;

    @ElementCollection
    @CollectionTable(name = "job_eligibility_criteria", joinColumns = @JoinColumn(name = "job_detail_id"))
    @Column(name = "criterion", columnDefinition = "TEXT")
    private List<String> eligibilityCriteria;

    @Column(name = "exam_pattern", columnDefinition = "TEXT")
    private String examPattern;

    @Column(name = "application_fee")
    private String applicationFee;

    @Column(name = "selection_process", columnDefinition = "TEXT")
    private String selectionProcess;

    @Lob
    @Column(name = "markdown_content", columnDefinition = "TEXT")
    private String markdownContent;

    @Column(name = "scraped_at", nullable = false)
    private LocalDateTime scrapedAt;

    @Column(name = "has_image")
    private boolean hasImage = false;

    @Column(name = "banner_image_url", length = 1000)
    private String bannerImageUrl;

    @Column(name = "source_website")
    private String sourceWebsite;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @PrePersist
    protected void onCreate() {
        scrapedAt = LocalDateTime.now();
    }
}
