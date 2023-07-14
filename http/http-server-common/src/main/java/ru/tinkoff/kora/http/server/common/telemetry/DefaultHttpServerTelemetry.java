package ru.tinkoff.kora.http.server.common.telemetry;

import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;

import javax.annotation.Nullable;

public final class DefaultHttpServerTelemetry implements HttpServerTelemetry {
    private static final String UNMATCHED_ROUTE_TEMPLATE = "UNKNOWN_ROUTE";
    private static final HttpServerTelemetryContext NOOP_CTX = (statusCode, resultCode, httpHeaders, exception) -> { };

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
    public HttpServerTelemetryContext get(PublicApiHandler.PublicApiRequest request, @Nullable String routeTemplate) {
        var metrics = this.metrics;
        var logger = this.logger;
        var tracer = this.tracer;
        if (metrics == null && tracer == null && (logger == null || !logger.isEnabled())) {
            return NOOP_CTX;
        }

        var start = System.nanoTime();
        var method = request.method();
        var scheme = request.scheme();
        var host = request.hostName();
        if (metrics != null) {
            metrics.requestStarted(method, routeTemplate != null ? routeTemplate : UNMATCHED_ROUTE_TEMPLATE, host, scheme);
        }

        final HttpServerTracer.HttpServerSpan span;
        final String operation;
        if (routeTemplate != null) {
            operation = method + " " + routeTemplate;
            if (tracer != null) {
                span = tracer.createSpan(routeTemplate, request);
            } else {
                span = null;
            }
            if (logger != null) {
                logger.logStart(operation, request.headers());
            }
        } else {
            span = null;
            operation = null;
        }

        return (statusCode, resultCode, httpHeaders, exception) -> {
            var end = System.nanoTime();
            var processingTime = end - start;
            if (metrics != null) {
                metrics.requestFinished(method, routeTemplate != null ? routeTemplate : UNMATCHED_ROUTE_TEMPLATE, host, scheme, statusCode, processingTime);
            }

            if (routeTemplate != null) {
                if (logger != null) {
                    logger.logEnd(operation, statusCode, resultCode, processingTime, httpHeaders, exception);
                }
                if (span != null) {
                    span.close(statusCode, resultCode, exception);
                }
            }
        };
    }
}
