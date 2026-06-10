package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.telemetry.$HttpServerTelemetryConfig_HttpServerLoggingConfig_ConfigValueExtractor;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

import java.nio.ByteBuffer;

public final class NoopHttpServerLogger extends DefaultHttpServerLogger {

    public static final NoopHttpServerLogger INSTANCE = new NoopHttpServerLogger();

    private NoopHttpServerLogger() {
        super(NOPLogger.NOP_LOGGER, NOPLogger.NOP_LOGGER, new DefaultHttpServerBodyConverter(), new $HttpServerTelemetryConfig_HttpServerLoggingConfig_ConfigValueExtractor.HttpServerLoggingConfig_Defaults());
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
