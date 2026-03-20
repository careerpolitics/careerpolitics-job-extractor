package com.careerpolitics.scraper.infrastructure.publisher;

import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.ArticleDetails;
import com.careerpolitics.scraper.domain.model.PublishingResult;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.port.ArticlePublisher;
import com.careerpolitics.scraper.domain.request.TrendingArticleRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class CareerPoliticsArticlePublisher implements ArticlePublisher {

    private final RestClient restClient;
    private final TrendingProperties properties;

    public CareerPoliticsArticlePublisher(RestClient restClient, TrendingProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public PublishingResult publish(String title,
                                    String markdown,
                                    List<String> tags,
                                    String trend,
                                    List<TrendHeadline> headlines,
                                    TrendingArticleRequest request) {
        String endpoint = properties.publishing().articleApiUrl();
        String token = request.getArticleApiToken() != null && !request.getArticleApiToken().isBlank()
                ? request.getArticleApiToken()
                : properties.publishing().articleApiToken();
        Long organizationId = request.getOrganizationId() != null
                ? request.getOrganizationId()
                : properties.publishing().organizationId();

        if (endpoint == null || endpoint.isBlank() || token == null || token.isBlank()) {
            log.warn("Skipping publishing for trend='{}' because endpoint or token is missing.", trend);
            return new PublishingResult(false, "Article API URL or token is missing.", null);
        }

        Map<String, Object> articlePayload = new LinkedHashMap<>();
        articlePayload.put("title", title);
        articlePayload.put("body_markdown", markdown);
        articlePayload.put("published", request.shouldPublish());
        articlePayload.put("main_image", resolveMainImage(headlines));
        articlePayload.put("description", buildDescription(title, trend, headlines));
        articlePayload.put("tags", toTagString(tags));
        articlePayload.put("organization_id", organizationId == null ? 0L : organizationId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("article", articlePayload);

        try {
            log.info("Publishing trend='{}' to host='{}' organizationId={} tags={} contentLength={}",
                    trend,
                    resolveHost(endpoint),
                    organizationId,
                    tags == null ? 0 : tags.size(),
                    markdown == null ? 0 : markdown.length());
            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", token)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Publishing succeeded for trend='{}'.", trend);
            return new PublishingResult(true, "Published successfully.", null);
        } catch (HttpClientErrorException exception) {
            log.error("Publishing failed for trend='{}' with status={} responseBody={}",
                    trend,
                    exception.getStatusCode().value(),
                    sanitizeResponseBody(exception.getResponseBodyAsString()));
            return new PublishingResult(false, exception.getMessage(), null);
        } catch (Exception exception) {
            log.error("Publishing failed for trend='{}': {}", trend, exception.getMessage(), exception);
            return new PublishingResult(false, exception.getMessage(), null);
        }
    }

    private String resolveHost(String endpoint) {
        try {
            String host = URI.create(endpoint).getHost();
            return host == null || host.isBlank() ? "unknown" : host;
        } catch (Exception exception) {
            return "invalid-uri";
        }
    }

    private String sanitizeResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "<empty>";
        }
        String singleLine = responseBody.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 500 ? singleLine.substring(0, 500) + "..." : singleLine;
    }

    private String buildDescription(String title, String trend, List<TrendHeadline> headlines) {
        if (headlines != null) {
            for (TrendHeadline headline : headlines) {
                if (headline.articleDetails() != null
                        && headline.articleDetails().description() != null
                        && !headline.articleDetails().description().isBlank()) {
                    return headline.articleDetails().description();
                }
            }
        }
        if (title != null && !title.isBlank()) {
            return title;
        }
        return trend == null || trend.isBlank() ? "Trending article" : "Trending article about " + trend;
    }

    private String toTagString(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.stream()
                .map(this::sanitizeTag)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String sanitizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        return normalized;
    }

    private String resolveMainImage(List<TrendHeadline> headlines) {
        if (headlines == null || headlines.isEmpty()) {
            return "";
        }
        return headlines.stream()
                .map(TrendHeadline::articleDetails)
                .filter(details -> details != null && supportsForemMainImage(details))
                .flatMap(details -> details.mediaUrls().stream())
                .filter(media -> media != null && !media.isBlank())
                .findFirst()
                .orElse("");
    }

    private boolean supportsForemMainImage(ArticleDetails details) {
        if (details.mediaUrls() == null || details.mediaUrls().isEmpty()) {
            return false;
        }
        return details.mediaType() == null
                || "image".equalsIgnoreCase(details.mediaType())
                || "gif".equalsIgnoreCase(details.mediaType());
    }
}
