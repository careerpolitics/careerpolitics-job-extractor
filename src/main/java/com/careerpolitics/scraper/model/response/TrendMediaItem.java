package com.careerpolitics.scraper.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrendMediaItem {
    private String trend;
    private String type;
    private String title;
    private String url;
    private String embed;
}
