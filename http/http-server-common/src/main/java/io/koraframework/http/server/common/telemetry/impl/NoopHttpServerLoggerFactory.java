package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

import java.nio.ByteBuffer;
import java.util.Set;

public final class NoopHttpServerLoggerFactory extends DefaultHttpServerLoggerFactory {

    public static final NoopHttpServerLoggerFactory INSTANCE = new NoopHttpServerLoggerFactory();

    private NoopHttpServerLoggerFactory() {}

    @Override
    public DefaultHttpServerLogger create(DefaultHttpServerTelemetry.TelemetryContext context) {
        return NoopHttpServerLogger.INSTANCE;
    }

    public static final class NoopHttpServerLogger extends DefaultHttpServerLogger {

        public static final NoopHttpServerLogger INSTANCE = new NoopHttpServerLogger();

        private NoopHttpServerLogger() {
            super(NOPLogger.NOP_LOGGER, NOPLogger.NOP_LOGGER, Set.of(), Set.of(), DefaultHttpServerTelemetry.TelemetryContext.EMPTY);
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
        public void logRequest(HttpServerRequest request, @Nullable ByteBuffer body, @Nullable String contentType) {

        }

        @Override
        public void logResponse(HttpServerRequest request, HttpServerResponse response, HttpResultCode resultCode, long processingTime, @Nullable ByteBuffer body, @Nullable String contentType, @Nullable Throwable exception) {

        }
    }
}
