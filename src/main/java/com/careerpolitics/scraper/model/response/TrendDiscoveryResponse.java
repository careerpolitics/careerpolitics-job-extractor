package com.careerpolitics.scraper.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TrendDiscoveryResponse {
    private String geo;
    private String language;
    private int maxTrends;
    private List<String> trends;
}
