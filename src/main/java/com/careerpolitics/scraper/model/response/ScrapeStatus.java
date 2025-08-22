package com.careerpolitics.scraper.model.response;


import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ScrapeStatus {
    private Long urlId;
    private String url;
    private boolean processed;
    private int retryCount;
    private String lastError;
    private LocalDateTime lastUpdated;
}