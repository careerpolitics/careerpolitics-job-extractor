package com.careerpolitics.scraper.model.response;


import lombok.Data;
import lombok.Builder;
import java.time.Duration;
import java.time.LocalDateTime;

@Data
@Builder
public class FullCycleResponse {
    private String workflowId;
    private UrlDiscoveryResponse urlDiscovery;
    private ScrapeBatchResponse detailScraping;
    private boolean completed;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Duration totalDuration;

    public static FullCycleResponse create(String workflowId,
                                           UrlDiscoveryResponse urlDiscovery,
                                           ScrapeBatchResponse detailScraping) {
        LocalDateTime endTime = LocalDateTime.now();
        Duration totalDuration = Duration.between(
                urlDiscovery.getTimestamp(), endTime
        );

        return FullCycleResponse.builder()
                .workflowId(workflowId)
                .urlDiscovery(urlDiscovery)
                .detailScraping(detailScraping)
                .completed(true)
                .startTime(urlDiscovery.getTimestamp())
                .endTime(endTime)
                .totalDuration(totalDuration)
                .build();
    }
}
