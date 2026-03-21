package com.careerpolitics.scraper.infrastructure.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HoneybadgerLogAppender extends AppenderBase<ILoggingEvent> {

    private boolean enabled;
    private boolean loggingEnabled;
    private String apiKey;
    private String endpoint = "https://api.honeybadger.io";
    private String environment = "production";
    private String serviceName = "careerpolitics-trending-service";
    private String minimumLevel = Level.WARN.levelStr;
    private String version = "unknown";
    private HoneybadgerApiClient apiClient;
    private Level threshold;

    @Override
    public void start() {
        this.threshold = Level.toLevel(minimumLevel, Level.WARN);
        if (enabled && loggingEnabled && apiKey != null && !apiKey.isBlank()) {
            this.apiClient = new HoneybadgerApiClient(HttpClient.newHttpClient(), apiKey, endpoint, environment, serviceName, version);
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (apiClient == null || eventObject.getLevel().toInt() < threshold.toInt()) {
            return;
        }

        Map<String, Object> event = HoneybadgerApiClient.buildLogEvent(
                serviceName,
                environment,
                eventObject.getLevel().levelStr,
                eventObject.getLoggerName(),
                eventObject.getThreadName(),
                eventObject.getFormattedMessage(),
                null,
                Instant.ofEpochMilli(eventObject.getTimeStamp())
        );

        if (eventObject.getThrowableProxy() != null) {
            IThrowableProxy proxy = eventObject.getThrowableProxy();
            event.put("error_class", proxy.getClassName());
            event.put("error_message", proxy.getMessage());
            event.put("backtrace", buildBacktrace(proxy));
        }

        apiClient.sendLogEvent(event);
    }

    private List<Map<String, Object>> buildBacktrace(IThrowableProxy proxy) {
        return Arrays.stream(proxy.getStackTraceElementProxyArray())
                .map(StackTraceElementProxy::getStackTraceElement)
                .map(frame -> {
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("file", frame.getFileName() == null ? frame.getClassName() : frame.getFileName());
                    line.put("number", frame.getLineNumber());
                    line.put("method", frame.getClassName() + "." + frame.getMethodName());
                    return line;
                })
                .toList();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setMinimumLevel(String minimumLevel) {
        this.minimumLevel = minimumLevel;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
