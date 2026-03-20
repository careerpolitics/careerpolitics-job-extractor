package com.careerpolitics.scraper.infrastructure.publisher;

import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.PublishingResult;
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
                                    TrendingArticleRequest request) {
        if (!properties.publishing().enabled()) {
            log.info("Skipping publishing for trend='{}' because publishing is disabled.", trend);
            return PublishingResult.skipped("Publishing is disabled by configuration.");
        }

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
        articlePayload.put("published", false);
        articlePayload.put("series", "Trending");
        articlePayload.put("main_image", "");
        articlePayload.put("canonical_url", "");
        articlePayload.put("description", buildDescription(title, trend));
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

    private String buildDescription(String title, String trend) {
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
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}
