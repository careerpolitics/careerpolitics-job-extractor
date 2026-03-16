package com.careerpolitics.scraper.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TrendMediaResponse {
    private String trend;
    private String geo;
    private String language;
    private int newsItemsConsidered;
    private int maxMediaItems;
    private String coverImage;
    private List<TrendMediaItem> media;
}
