package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;

import java.util.Objects;

public class DefaultHttpServerTelemetry implements HttpServerTelemetry {

    protected final HttpServerTelemetryConfig config;
    protected final Tracer tracer;
    protected final DefaultHttpServerLogger logger;
    protected final DefaultHttpServerMetrics metrics;

    public DefaultHttpServerTelemetry(HttpServerTelemetryConfig config,
                                      Tracer tracer,
                                      DefaultHttpServerLogger logger,
                                      DefaultHttpServerMetrics metrics) {
        this.config = config;
        this.tracer = tracer;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public HttpServerObservation observe(HttpServerRequest request) {
        var span = config.tracing().enabled() && request.pathTemplate() != null
            ? startSpan(request).startSpan()
            : Span.getInvalid();
        return new DefaultHttpServerObservation(config, logger, metrics, request, request.requestStartTimeInNanos(), span);
    }

    protected SpanBuilder startSpan(HttpServerRequest request) {
        @SuppressWarnings("DataFlowIssue")
        var span = this.tracer
            .spanBuilder(request.method() + " " + request.pathTemplate())
            .setSpanKind(SpanKind.SERVER)
            .setParent(io.opentelemetry.context.Context.current())
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.method())
            .setAttribute(UrlAttributes.URL_SCHEME, request.scheme())
            .setAttribute(ServerAttributes.SERVER_ADDRESS, request.host())
            .setAttribute(UrlAttributes.URL_PATH, request.path())
            .setAttribute(HttpAttributes.HTTP_ROUTE, request.pathTemplate()); // if unknown tracing is disabled
        for (var attribute : config.tracing().attributes().entrySet()) {
            span.setAttribute(attribute.getKey(), attribute.getValue());
        }
        return span;
    }
}
