package com.careerpolitics.scraper.infrastructure.observability;

import com.careerpolitics.scraper.config.HoneybadgerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HoneybadgerNotifier {

    private final HoneybadgerProperties properties;
    private final HoneybadgerApiClient apiClient;

    public HoneybadgerNotifier(HoneybadgerProperties properties,
                               @Value("${info.app.version:${spring.application.name}}") String version) {
        this.properties = properties;
        this.apiClient = new HoneybadgerApiClient(properties, version);
    }

    public void notify(Throwable exception, Map<String, Object> requestContext) {
        if (!properties.isConfigured()) {
            return;
        }
        apiClient.notifyError(exception, requestContext);
    }

    public Map<String, Object> buildRequestContext(String path, String method, String requestUrl, Map<String, Object> context) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("url", HoneybadgerApiClient.sanitizeUrl(requestUrl));
        request.put("component", method + " " + path);
        request.put("context", context);
        return request;
    }
}
