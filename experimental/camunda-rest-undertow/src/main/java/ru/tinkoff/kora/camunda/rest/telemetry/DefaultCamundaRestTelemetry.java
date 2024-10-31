package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.Map;

public final class DefaultCamundaRestTelemetry implements CamundaRestTelemetry {

    private static final String UNMATCHED_ROUTE_TEMPLATE = "UNKNOWN_ROUTE";

    @Nullable
    private final CamundaRestMetrics metrics;
    @Nullable
    private final CamundaRestLogger logger;
    @Nullable
    private final CamundaRestTracer tracer;

    public DefaultCamundaRestTelemetry(@Nullable CamundaRestMetrics metrics,
                                       @Nullable CamundaRestLogger logger,
                                       @Nullable CamundaRestTracer tracer) {
        this.metrics = metrics;
        this.logger = logger;
        this.tracer = tracer;
    }

    @Override
    public CamundaRestTelemetryContext get(String scheme,
                                           String host,
                                           String method,
                                           String path,
                                           @Nullable String routeTemplate,
                                           HttpHeaders headers,
                                           Map<String, ? extends Collection<String>> queryParams,
                                           HttpBodyInput body) {
        var metrics = this.metrics;
        var logger = this.logger;
        var tracer = this.tracer;
        if (metrics == null && tracer == null && (logger == null || !logger.isEnabled())) {
            return EMPTY_CTX;
        }

        var start = System.nanoTime();
        if (metrics != null) {
            var pathTemplate = routeTemplate != null ? routeTemplate : UNMATCHED_ROUTE_TEMPLATE;
            metrics.requestStarted(method, pathTemplate, host, scheme);
        }

        final CamundaRestTracer.CamundaRestSpan span;
        if (routeTemplate != null) {
            if (logger != null) {
                logger.logStart(method, path, routeTemplate, queryParams, headers);
            }
            if (tracer != null) {
                span = tracer.createSpan(scheme, host, method, path, routeTemplate, headers, queryParams, body);
            } else {
                span = null;
            }
        } else {
            span = null;
        }

        return (statusCode, resultCode, responseHeaders, exception) -> {
            var end = System.nanoTime();
            var processingTime = end - start;
            if (metrics != null) {
                var pathTemplate = routeTemplate != null ? routeTemplate : UNMATCHED_ROUTE_TEMPLATE;
                metrics.requestFinished(statusCode, resultCode, scheme, host, method, pathTemplate, responseHeaders, processingTime, exception);
            }

            if (routeTemplate != null) {
                if (logger != null) {
                    logger.logEnd(statusCode, resultCode, method, path, routeTemplate, processingTime, queryParams, responseHeaders, exception);
                }
                if (span != null) {
                    span.close(statusCode, resultCode, exception);
                }
            }
        };
    }
}
