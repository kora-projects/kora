package io.koraframework.bpmn.operaton.rest.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.HttpServerRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

import java.util.Set;

public final class NoopOperatonRestLoggerFactory extends DefaultOperatonRestLoggerFactory {

    public static final NoopOperatonRestLoggerFactory INSTANCE = new NoopOperatonRestLoggerFactory();

    private NoopOperatonRestLoggerFactory() {}

    @Override
    public DefaultOperatonRestLogger create(DefaultOperatonRestTelemetry.TelemetryContext context) {
        return NoopOperatonRestLogger.INSTANCE;
    }

    public static final class NoopOperatonRestLogger extends DefaultOperatonRestLogger {

        public static final NoopOperatonRestLogger INSTANCE = new NoopOperatonRestLogger();

        private NoopOperatonRestLogger() {
            super(null, NOPLogger.NOP_LOGGER, Set.of(), Set.of());
        }

        @Override
        public void logStart(HttpServerRequest request) {

        }

        @Override
        public void logEnd(HttpServerRequest request,
                           int statusCode,
                           HttpResultCode resultCode,
                           long processingTime,
                           @Nullable HttpHeaders headers,
                           @Nullable Throwable exception) {

        }
    }
}
