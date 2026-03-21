package com.careerpolitics.scraper.infrastructure.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HoneybadgerLogAppender extends AppenderBase<ILoggingEvent> {

    private boolean enabled;
    private String apiKey;
    private String environment = "production";
    private String serviceName = "careerpolitics-trending-service";
    private HoneybadgerApiClient apiClient;

    @Override
    public void start() {
        if (enabled && apiKey != null && !apiKey.isBlank()) {
            this.apiClient = new HoneybadgerApiClient(apiKey, environment, serviceName);
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (apiClient == null || eventObject.getLevel().toInt() < Level.WARN_INT) {
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

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
