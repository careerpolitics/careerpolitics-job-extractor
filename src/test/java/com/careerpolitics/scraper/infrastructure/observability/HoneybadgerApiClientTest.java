package com.careerpolitics.scraper.infrastructure.observability;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HoneybadgerApiClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsErrorNoticesToHoneybadger() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        server = startServer("/v1/notices", body, contentType);

        HoneybadgerApiClient client = new HoneybadgerApiClient(
                HttpClient.newHttpClient(),
                "http://localhost:" + server.getAddress().getPort(),
                "api-key",
                "production",
                "careerpolitics-trending-service"
        );

        client.notifyError(new IllegalStateException("boom"), Map.of("url", "http://localhost/api/trending/articles?token=secret"));

        assertThat(contentType.get()).isEqualTo("application/json");
        assertThat(body.get()).contains("IllegalStateException");
        assertThat(body.get()).contains("boom");
        assertThat(body.get()).contains("/api/trending/articles?token=secret");
    }

    @Test
    void sendsStructuredLogEventsAsNdjson() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        server = startServer("/v1/events", body, contentType);

        HoneybadgerApiClient client = new HoneybadgerApiClient(
                HttpClient.newHttpClient(),
                "http://localhost:" + server.getAddress().getPort(),
                "api-key",
                "production",
                "careerpolitics-trending-service"
        );

        client.sendLogEvent(HoneybadgerApiClient.buildLogEvent(
                "careerpolitics-trending-service",
                "production",
                "ERROR",
                "com.careerpolitics.scraper.application.TrendingScheduler",
                "main",
                "Scheduled run failed",
                new IllegalArgumentException("bad request"),
                Instant.parse("2026-03-21T00:00:00Z")
        ));

        assertThat(contentType.get()).isEqualTo("application/x-ndjson");
        assertThat(body.get()).contains("\"level\":\"error\"");
        assertThat(body.get()).contains("Scheduled run failed");
        assertThat(body.get()).contains("IllegalArgumentException");
    }

    private HttpServer startServer(String path, AtomicReference<String> body, AtomicReference<String> contentType) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext(path, exchange -> handle(exchange, body, contentType));
        httpServer.start();
        return httpServer;
    }

    private void handle(HttpExchange exchange, AtomicReference<String> body, AtomicReference<String> contentType) throws IOException {
        contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        body.set(readBody(exchange.getRequestBody()));
        exchange.sendResponseHeaders(201, 0);
        exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
        exchange.close();
    }

    private String readBody(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
