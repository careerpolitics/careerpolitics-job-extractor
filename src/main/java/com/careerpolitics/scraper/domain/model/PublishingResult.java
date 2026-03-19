package com.careerpolitics.scraper.domain.model;

public record PublishingResult(
        boolean success,
        String message,
        String externalId
) {

    public static PublishingResult skipped(String message) {
        return new PublishingResult(false, message, null);
    }
}
