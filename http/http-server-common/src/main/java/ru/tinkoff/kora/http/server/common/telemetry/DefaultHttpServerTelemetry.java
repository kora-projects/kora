package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.router.PublicApiRequest;

public final class DefaultHttpServerTelemetry implements HttpServerTelemetry {
    private static final String UNMATCHED_ROUTE_TEMPLATE = "UNKNOWN_ROUTE";

    @Nullable
    private final HttpServerMetrics metrics;
    @Nullable
    private final HttpServerLogger logger;
    @Nullable
    private final HttpServerTracer tracer;

    public DefaultHttpServerTelemetry(@Nullable HttpServerMetrics metrics, @Nullable HttpServerLogger logger, @Nullable HttpServerTracer tracer) {
        this.metrics = metrics;
        this.logger = logger;
        this.tracer = tracer;
    }

    @Override
    public HttpServerTelemetryContext get(PublicApiRequest request, @Nullable String routeTemplate) {
        var metrics = this.metrics;
        var logger = this.logger;
        var tracer = this.tracer;
        if (metrics == null && tracer == null && (logger == null || !logger.isEnabled())) {
            return EMPTY_CTX;
        }

        var start = System.nanoTime();
        var method = request.method();
        var scheme = request.scheme();
        var host = request.hostName();
        if (metrics != null) {
            var pathTemplate = routeTemplate != null ? routeTemplate : UNMATCHED_ROUTE_TEMPLATE;
            metrics.requestStarted(method, pathTemplate, host, scheme);
        }

        final HttpServerTracer.HttpServerSpan span;
        if (routeTemplate != null) {
            if (logger != null) {
                logger.logStart(method, request.path(), routeTemplate, request.queryParams(), request.headers());
            }
            if (tracer != null) {
                span = tracer.createSpan(routeTemplate, request);
            } else {
                span = null;
            }
        } else {
            span = null;
        }

        return (statusCode, resultCode, httpHeaders, exception) -> {
            var end = System.nanoTime();
            var processingTime = end - start;
            if (metrics != null) {
                var pathTemplate = routeTemplate != null ? routeTemplate : UNMATCHED_ROUTE_TEMPLATE;
                metrics.requestFinished(statusCode, resultCode, scheme, host, method, pathTemplate, httpHeaders, processingTime, exception);
            }

            if (routeTemplate != null) {
                if (logger != null) {
                    logger.logEnd(statusCode, resultCode, method, request.path(), routeTemplate, processingTime, request.queryParams(), httpHeaders, exception);
                }
                if (span != null) {
                    span.close(statusCode, resultCode, exception);
                }
            }
        };
    }
}
