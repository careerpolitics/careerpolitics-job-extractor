package com.careerpolitics.scraper.infrastructure.publisher;

import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.PublishingResult;
import com.careerpolitics.scraper.domain.port.ArticlePublisher;
import com.careerpolitics.scraper.domain.request.TrendingArticleRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            return new PublishingResult(false, "Article API URL or token is missing.", null);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("content", markdown);
        payload.put("tags", tags);
        payload.put("trend", trend);
        if (organizationId != null) {
            payload.put("organization_id", organizationId);
        }

        try {
            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return new PublishingResult(true, "Published successfully.", null);
        } catch (Exception exception) {
            return new PublishingResult(false, exception.getMessage(), null);
        }
    }
}
