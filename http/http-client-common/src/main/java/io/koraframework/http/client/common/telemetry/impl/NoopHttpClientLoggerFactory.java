package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

import java.nio.ByteBuffer;
import java.util.Set;

public final class NoopHttpClientLoggerFactory extends DefaultHttpClientLoggerFactory {

    public static final NoopHttpClientLoggerFactory INSTANCE = new NoopHttpClientLoggerFactory();

    private NoopHttpClientLoggerFactory() {}

    @Override
    public DefaultHttpClientLogger create(DefaultHttpClientTelemetry.TelemetryContext context) {
        return NoopHttpClientLogger.INSTANCE;
    }

    public static class NoopHttpClientLogger extends DefaultHttpClientLogger {

        public static final NoopHttpClientLogger INSTANCE = new NoopHttpClientLogger();

        private NoopHttpClientLogger() {
            super(NOPLogger.NOP_LOGGER, NOPLogger.NOP_LOGGER, Set.of(), Set.of(), DefaultHttpClientTelemetry.TelemetryContext.EMPTY);
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
}
