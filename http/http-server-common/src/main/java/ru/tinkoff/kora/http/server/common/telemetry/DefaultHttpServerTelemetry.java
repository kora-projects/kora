package ru.tinkoff.kora.http.server.common.telemetry;

import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;

import javax.annotation.Nullable;

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
    public HttpServerTelemetryContext get(PublicApiHandler.PublicApiRequest request, @Nullable String routeTemplate) {
        var start = System.nanoTime();
        var method = request.method();
        var scheme = request.scheme();
        var host = request.hostName();

        if (this.metrics != null) this.metrics.requestStarted(method, routeTemplate != null ? routeTemplate : UNMATCHED_ROUTE_TEMPLATE, host, scheme);

        final HttpServerTracer.HttpServerSpan span;
        final String operation;
        if (routeTemplate != null) {
            operation = method + " " + routeTemplate;
            span = this.tracer != null
                ? this.tracer.createSpan(routeTemplate, request)
                : null;
            if (this.logger != null) this.logger.logStart(operation);
        } else {
            span = null;
            operation = null;
        }

        return (statusCode, resultCode, exception) -> {
            var end = System.nanoTime();
            var processingTime = end - start;

            if (this.metrics != null) this.metrics.requestFinished(method, routeTemplate != null ? routeTemplate : UNMATCHED_ROUTE_TEMPLATE, host, scheme, statusCode, processingTime);

            if (routeTemplate != null) {
                if (this.logger != null) this.logger.logEnd(operation, statusCode, resultCode, processingTime, exception);

                if (span != null) span.close(statusCode, resultCode, processingTime, exception);
            }
        };
    }
}
