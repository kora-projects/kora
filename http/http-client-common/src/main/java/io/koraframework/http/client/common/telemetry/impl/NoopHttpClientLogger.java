package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.telemetry.$HttpClientTelemetryConfig_HttpClientLoggerConfig_ConfigValueExtractor;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

public final class NoopHttpClientLogger extends DefaultHttpClientLogger {

    public static final NoopHttpClientLogger INSTANCE = new NoopHttpClientLogger();

    private NoopHttpClientLogger() {
        super("noop", "noop", new $HttpClientTelemetryConfig_HttpClientLoggerConfig_ConfigValueExtractor.HttpClientLoggerConfig_Defaults());
    }

    @Override
    public boolean logRequestBody() {
        return false;
    }

    @Override
    public boolean logResponseBody() {
        return false;
    }

    @Override
    public void logRequest(HttpClientRequest rq, @Nullable ByteBuffer body, @Nullable String contentType) {

    }

    @Override
    public void logResponse(HttpClientRequest rq, HttpClientResponse rs, long processingTookNanos, @Nullable ByteBuffer body, @Nullable String contentType) {

    }

    @Override
    public void logError(HttpClientRequest rq, long processingTime, Throwable exception) {

    }
}
