package com.careerpolitics.scraper.infrastructure.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class HoneybadgerApiClient {

    private static final String API_BASE_URL = "https://api.honeybadger.io";
    private static final DateTimeFormatter RFC_3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String environment;
    private final String serviceName;

    public HoneybadgerApiClient(String apiKey, String environment, String serviceName) {
        this(HttpClient.newHttpClient(), API_BASE_URL, apiKey, environment, serviceName);
    }

    public HoneybadgerApiClient(HttpClient httpClient,
                                String apiKey,
                                String environment,
                                String serviceName) {
        this(httpClient, API_BASE_URL, apiKey, environment, serviceName);
    }

    HoneybadgerApiClient(HttpClient httpClient,
                         String baseUrl,
                         String apiKey,
                         String environment,
                         String serviceName) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.environment = environment;
        this.serviceName = serviceName;
    }

    public void notifyError(Throwable exception, Map<String, Object> requestContext) {
        sendJson("/v1/notices", "application/json", buildNoticePayload(exception, requestContext), "error notice");
    }

    public void sendLogEvent(Map<String, Object> event) {
        sendJson("/v1/events", "application/x-ndjson", event, "log event", true);
    }

    public static Map<String, Object> buildLogEvent(String serviceName,
                                                    String environment,
                                                    String level,
                                                    String logger,
                                                    String threadName,
                                                    String message,
                                                    Throwable throwable,
                                                    Instant timestamp) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("ts", RFC_3339.format(timestamp));
        event.put("service", serviceName);
        event.put("environment", environment);
        event.put("level", level.toLowerCase());
        event.put("logger", logger);
        event.put("thread", threadName);
        event.put("message", message);
        if (throwable != null) {
            event.put("error_class", throwable.getClass().getName());
            event.put("error_message", Optional.ofNullable(throwable.getMessage()).orElse(throwable.getClass().getSimpleName()));
            event.put("backtrace", buildBacktrace(throwable));
        }
        return event;
    }

    private Map<String, Object> buildNoticePayload(Throwable exception, Map<String, Object> requestContext) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notifier", Map.of("name", serviceName));
        payload.put("error", Map.of(
                "class", exception.getClass().getName(),
                "message", Optional.ofNullable(exception.getMessage()).orElse(exception.getClass().getSimpleName()),
                "backtrace", buildBacktrace(exception)
        ));
        payload.put("server", Map.of(
                "environment_name", environment,
                "hostname", resolveHostname()
        ));
        if (requestContext != null && !requestContext.isEmpty()) {
            payload.put("request", requestContext);
        }
        return payload;
    }

    private static List<Map<String, Object>> buildBacktrace(Throwable throwable) {
        return Arrays.stream(throwable.getStackTrace())
                .map(frame -> {
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("file", frame.getFileName() == null ? frame.getClassName() : frame.getFileName());
                    line.put("number", frame.getLineNumber());
                    line.put("method", frame.getClassName() + "." + frame.getMethodName());
                    return line;
                })
                .toList();
    }

    private void sendJson(String path, String contentType, Object payload, String payloadType) {
        sendJson(path, contentType, payload, payloadType, false);
    }

    private void sendJson(String path, String contentType, Object payload, String payloadType, boolean ndjson) {
        try {
            String serialized = objectMapper.writeValueAsString(payload);
            if (ndjson) {
                serialized = serialized + "\n";
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("X-API-Key", apiKey)
                    .header("Content-Type", contentType)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(serialized, StandardCharsets.UTF_8))
                    .build();
            send(request, payloadType);
        } catch (JsonProcessingException jsonProcessingException) {
            log.warn("Unable to serialize Honeybadger {} payload: {}", payloadType, jsonProcessingException.getMessage());
        }
    }

    private void send(HttpRequest request, String payloadType) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Honeybadger rejected {} with status={} body={}", payloadType, response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Unable to send {} to Honeybadger: {}", payloadType, exception.getMessage());
        }
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException exception) {
            return "unknown";
        }
    }

    public static String sanitizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return rawUrl;
        }
        try {
            URI uri = new URI(rawUrl);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (URISyntaxException exception) {
            return rawUrl;
        }
    }
}
