package com.careerpolitics.scraper.infrastructure.observability;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HoneybadgerNotifier {

    private final String apiKey;
    private final HoneybadgerApiClient apiClient;

    public HoneybadgerNotifier(@Value("${honeybadger.api-key:}") String apiKey,
                               @Value("${honeybadger.environment:${SPRING_PROFILES_ACTIVE:production}}") String environment,
                               @Value("${spring.application.name}") String serviceName) {
        this.apiKey = apiKey;
        this.apiClient = new HoneybadgerApiClient(apiKey, environment, serviceName);
    }

    public void notify(Throwable exception, HttpServletRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        Map<String, Object> requestContext = new LinkedHashMap<>();
        requestContext.put("url", HoneybadgerApiClient.sanitizeUrl(request.getRequestURL().toString()));
        requestContext.put("component", request.getMethod() + " " + request.getRequestURI());
        apiClient.notifyError(exception, requestContext);
    }
}
