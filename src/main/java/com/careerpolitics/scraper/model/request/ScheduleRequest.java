package com.careerpolitics.scraper.model.request;

import lombok.Data;

@Data
public class ScheduleRequest {
    private String cronExpression;
    private String website;
    private int batchSize;
    private boolean generateMarkdown;
}
