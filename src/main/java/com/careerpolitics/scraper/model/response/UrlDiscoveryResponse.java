package com.careerpolitics.scraper.model.response;


import com.careerpolitics.scraper.model.JobSummary;
import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UrlDiscoveryResponse {
    private boolean success;
    private int newUrlsDiscovered;
    private int totalUrls;
    private List<JobSummary> discoveredUrls;
    private String message;
    private LocalDateTime timestamp;

    public static UrlDiscoveryResponse success(int newUrls, int total, List<JobSummary> urls) {
        return UrlDiscoveryResponse.builder()
                .success(true)
                .newUrlsDiscovered(newUrls)
                .totalUrls(total)
                .discoveredUrls(urls)
                .message("URL discovery completed successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static UrlDiscoveryResponse error(String errorMessage) {
        return UrlDiscoveryResponse.builder()
                .success(false)
                .newUrlsDiscovered(0)
                .totalUrls(0)
                .message("URL discovery failed: " + errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
