package com.careerpolitics.scraper.infrastructure.publisher;

import com.careerpolitics.scraper.config.TrendingProperties;
import com.careerpolitics.scraper.domain.model.ArticleDetails;
import com.careerpolitics.scraper.domain.model.PublishingResult;
import com.careerpolitics.scraper.domain.model.TrendHeadline;
import com.careerpolitics.scraper.domain.request.TrendingArticleRequest;
import com.careerpolitics.scraper.infrastructure.article.HeadlineMediaResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CareerPoliticsArticlePublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishWrapsRequestInsideArticleObjectAndUsesApiKeyHeader() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        AtomicReference<Map<String, Object>> capturedPayload = new AtomicReference<>();
        server.createContext("/api/articles", exchange -> handleSuccess(exchange, apiKeyHeader, capturedPayload));
        server.start();

        try {
            CareerPoliticsArticlePublisher publisher = new CareerPoliticsArticlePublisher(
                    RestClient.create(),
                    properties(false, "http://127.0.0.1:" + server.getAddress().getPort() + "/api/articles", "secret-token", 42L),
                    new HeadlineMediaResolver()
            );
            TrendingArticleRequest request = new TrendingArticleRequest();
            request.setPublish(true);

            PublishingResult result = publisher.publish(
                    "Example title",
                    "Example markdown",
                    "Example article description",
                    List.of("Tag One", "Tag@One", "tag2"),
                    "example-trend",
                    List.of(new TrendHeadline(
                            "example-trend",
                            "Example headline",
                            "https://example.com/story",
                            "Reuters",
                            "2 hours ago",
                            "Example summary",
                            new ArticleDetails("Example summary", "Long form content", List.of("https://example.com/story.jpg"), "image")
                    )),
                    request
            );

            assertThat(result.success()).isTrue();
            assertThat(apiKeyHeader.get()).isEqualTo("secret-token");
            assertThat(capturedPayload.get()).containsKey("article");
            assertThat(capturedPayload.get().get("article")).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> article = (Map<String, Object>) capturedPayload.get().get("article");
            assertThat(article)
                    .containsEntry("title", "Example title")
                    .containsEntry("body_markdown", "Example markdown")
                    .containsEntry("published", true)
                    .containsEntry("main_image", "https://example.com/story.jpg")
                    .containsEntry("series", "")
                    .containsEntry("canonical_url", "")
                    .containsEntry("description", "Example article description")
                    .containsEntry("tags", "tagone,tag2")
                    .containsEntry("organization_id", 42);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publishReturnsFailureMessageWhenRemoteApiRejectsPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/articles", exchange -> {
            byte[] body = "{\"error\":\"article param must be a JSON object\",\"status\":422}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(422, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            CareerPoliticsArticlePublisher publisher = new CareerPoliticsArticlePublisher(
                    RestClient.create(),
                    properties(true, "http://127.0.0.1:" + server.getAddress().getPort() + "/api/articles", "secret-token", null),
                    new HeadlineMediaResolver()
            );

            PublishingResult result = publisher.publish(
                    "Example title",
                    "Example markdown",
                    "Example article description",
                    List.of("tag1"),
                    "example-trend",
                    List.of(),
                    new TrendingArticleRequest()
            );

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("422 Unprocessable Entity");
        } finally {
            server.stop(0);
        }
    }

    private void handleSuccess(HttpExchange exchange,
                               AtomicReference<String> apiKeyHeader,
                               AtomicReference<Map<String, Object>> capturedPayload) throws IOException {
        apiKeyHeader.set(exchange.getRequestHeaders().getFirst("api-key"));
        try (InputStream requestBody = exchange.getRequestBody()) {
            capturedPayload.set(objectMapper.readValue(requestBody, Map.class));
        }
        byte[] body = "{}".getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(201, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private TrendingProperties properties(boolean enabled, String articleApiUrl, String token, Long organizationId) {
        return new TrendingProperties(
                new TrendingProperties.Discovery("https://trends.google.com/trending", 5, List.of()),
                new TrendingProperties.News("https://www.google.com/search", 4),
                new TrendingProperties.Selenium(true, true, true, false, 45, 20, 2, 750, "http://selenium:4444/wd/hub", "", List.of(), Duration.ofSeconds(2)),
                new TrendingProperties.Generation(false, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "", Duration.ofSeconds(20)),
                new TrendingProperties.Publishing(enabled, articleApiUrl, token, organizationId, Duration.ofSeconds(10)),
                new TrendingProperties.Scheduler(false, "0 0 */6 * * *", "US", "en-US", 3, 4, 24, false)
        );
    }
}
