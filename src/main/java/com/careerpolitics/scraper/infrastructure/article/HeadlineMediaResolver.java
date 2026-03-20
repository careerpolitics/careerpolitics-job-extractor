package com.careerpolitics.scraper.infrastructure.article;

import com.careerpolitics.scraper.domain.model.ArticleDetails;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class HeadlineMediaResolver {

    public String resolveCoverMediaUrl(List<TrendHeadline> headlines) {
        return collectMedia(headlines).stream()
                .filter(media -> isCoverCompatible(media.type()))
                .map(ResolvedMedia::url)
                .findFirst()
                .orElse("");
    }

    public List<ResolvedMedia> resolveAdditionalMedia(List<TrendHeadline> headlines, int limit) {
        String coverMedia = resolveCoverMediaUrl(headlines);
        return collectMedia(headlines).stream()
                .filter(media -> coverMedia.isBlank() || !coverMedia.equals(media.url()))
                .limit(Math.max(0, limit))
                .toList();
    }

    private List<ResolvedMedia> collectMedia(List<TrendHeadline> headlines) {
        if (headlines == null || headlines.isEmpty()) {
            return List.of();
        }
        List<ResolvedMedia> resolved = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (TrendHeadline headline : headlines) {
            ArticleDetails details = headline.articleDetails();
            if (details == null || details.mediaUrls() == null) {
                continue;
            }
            for (String mediaUrl : details.mediaUrls()) {
                if (mediaUrl == null || mediaUrl.isBlank() || !seen.add(mediaUrl)) {
                    continue;
                }
                resolved.add(new ResolvedMedia(mediaUrl, details.mediaType()));
            }
        }
        return resolved;
    }

    private boolean isCoverCompatible(String mediaType) {
        return mediaType == null
                || "image".equalsIgnoreCase(mediaType)
                || "gif".equalsIgnoreCase(mediaType);
    }

    public record ResolvedMedia(String url, String type) {
    }
}
