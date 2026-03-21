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

    private String apiKey;
    private String environment = "default";
    private String serviceName = "careerpolitics-trending-service";
    private HoneybadgerApiClient apiClient;

    @Override
    public void start() {
        if (apiKey != null && !apiKey.isBlank()) {
            this.apiClient = new HoneybadgerApiClient(apiKey, environment, serviceName);
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (apiClient == null || eventObject.getLevel().toInt() < Level.WARN_INT) {
            return;
        }

        ThrowableProxyDetails throwableDetails = extractThrowableDetails(eventObject);
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

        if (throwableDetails != null) {
            event.put("error_class", throwableDetails.errorClass());
            event.put("error_message", throwableDetails.message());
            event.put("backtrace", throwableDetails.backtrace());
        }

        apiClient.sendLogEvent(event);

        if (eventObject.getLevel().toInt() >= Level.ERROR_INT) {
            apiClient.notifyError(
                    throwableDetails == null ? eventObject.getLoggerName() : throwableDetails.errorClass(),
                    throwableDetails == null ? eventObject.getFormattedMessage() : throwableDetails.message(),
                    throwableDetails == null ? HoneybadgerApiClient.buildBacktrace(eventObject.getCallerData()) : throwableDetails.backtrace(),
                    Map.of("component", eventObject.getLoggerName())
            );
        }
    }

    private ThrowableProxyDetails extractThrowableDetails(ILoggingEvent eventObject) {
        if (eventObject.getThrowableProxy() == null) {
            return null;
        }
        IThrowableProxy proxy = eventObject.getThrowableProxy();
        return new ThrowableProxyDetails(proxy.getClassName(), proxy.getMessage(), buildBacktrace(proxy));
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

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    private record ThrowableProxyDetails(String errorClass, String message, List<Map<String, Object>> backtrace) {
    }
}
