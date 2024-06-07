package ru.tinkoff.kora.camunda.rest.telemetry;

import io.undertow.util.HeaderMap;
import jakarta.annotation.Nullable;

public final class DefaultCamundaRestTelemetry implements CamundaRestTelemetry {

    private static final String UNMATCHED_ROUTE_TEMPLATE = "UNKNOWN_ROUTE";

    private final CamundaRestMetrics metrics;
    private final CamundaRestLogger logger;
    private final CamundaRestTracer tracer;

    public DefaultCamundaRestTelemetry(@Nullable CamundaRestMetrics metrics,
                                       @Nullable CamundaRestLogger logger,
                                       @Nullable CamundaRestTracer tracer) {
        this.metrics = metrics;
        this.logger = logger;
        this.tracer = tracer;
    }

    @Override
    public CamundaRestTelemetryContext get(String method, String path, HeaderMap headerMap) {
        var start = System.nanoTime();
        String routeTelemetry = path != null ? path : UNMATCHED_ROUTE_TEMPLATE;
        if (metrics != null) {
            metrics.requestStarted(method, routeTelemetry);
        }

        final CamundaRestTracer.CamundaRestSpan span;
        if (path != null) {
            if (tracer != null) {
                span = tracer.createSpan(method, path, headerMap);
            } else {
                span = null;
            }
            if (logger != null) {
                logger.logStart(method, path);
            }
        } else {
            span = null;
        }

        return (statusCode, exception) -> {
            var end = System.nanoTime();
            var processingTime = end - start;
            if (metrics != null) {
                metrics.requestFinished(method, routeTelemetry, statusCode, processingTime, exception);
            }
            if (logger != null) {
                logger.logEnd(method, routeTelemetry, statusCode, processingTime, exception);
            }
            if (span != null) {
                span.close(statusCode, exception);
            }
        };
    }
}
