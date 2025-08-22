package com.careerpolitics.scraper.model.response;


import com.careerpolitics.scraper.model.JobDetail;
import lombok.Data;
import lombok.Builder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ScrapeBatchResponse {
    private boolean success;
    private int urlsProcessed;
    private int successfulScrapes;
    private int failedScrapes;
    private List<JobDetail> scrapedDetails;
    private Map<Long, String> failures;
    private Duration processingTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public static ScrapeBatchResponse success(int processed, int successful,
                                              List<JobDetail> details,
                                              Map<Long, String> failures,
                                              Duration time) {
        return ScrapeBatchResponse.builder()
                .success(true)
                .urlsProcessed(processed)
                .successfulScrapes(successful)
                .failedScrapes(failures.size())
                .scrapedDetails(details)
                .failures(failures)
                .processingTime(time)
                .startTime(LocalDateTime.now().minus(time))
                .endTime(LocalDateTime.now())
                .build();
    }
}
