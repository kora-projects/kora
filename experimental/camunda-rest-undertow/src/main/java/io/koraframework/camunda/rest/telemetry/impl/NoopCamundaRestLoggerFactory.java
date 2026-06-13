package io.koraframework.camunda.rest.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.HttpServerRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

import java.util.Set;

public final class NoopCamundaRestLoggerFactory extends DefaultCamundaRestLoggerFactory {

    public static final NoopCamundaRestLoggerFactory INSTANCE = new NoopCamundaRestLoggerFactory();

    private NoopCamundaRestLoggerFactory() {}

    @Override
    public DefaultCamundaRestLogger create(DefaultCamundaRestTelemetry.TelemetryContext context) {
        return NoopCamundaRestLogger.INSTANCE;
    }

    public static final class NoopCamundaRestLogger extends DefaultCamundaRestLogger {

        public static final NoopCamundaRestLogger INSTANCE = new NoopCamundaRestLogger();

        private NoopCamundaRestLogger() {
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
