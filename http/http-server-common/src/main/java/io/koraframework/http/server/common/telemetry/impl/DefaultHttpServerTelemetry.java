package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;

import java.util.List;
import java.util.Objects;

public class DefaultHttpServerTelemetry implements HttpServerTelemetry {

    public record TelemetryContext(HttpServerTelemetryConfig config,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   DefaultHttpServerBodyConverter bodyLogger) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            new $HttpServerTelemetryConfig_ConfigValueMapper.HttpServerTelemetryConfig_Impl(
                new $HttpServerTelemetryConfig_HttpServerLoggingConfig_ConfigValueMapper.HttpServerLoggingConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerMetricsConfig_ConfigValueMapper.HttpServerMetricsConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerTracingConfig_ConfigValueMapper.HttpServerTracingConfig_Defaults()
            )
            , false, false, DefaultHttpServerTelemetryFactory.NOOP_METER_REGISTRY, DefaultHttpServerTelemetryFactory.NOOP_TRACER, new DefaultHttpServerBodyConverter());
    }

    protected final TelemetryContext context;
    protected final DefaultHttpServerLoggerFactory.DefaultHttpServerLogger logger;
    protected final DefaultHttpServerMetricsFactory.DefaultHttpServerMetrics metrics;

    public DefaultHttpServerTelemetry(HttpServerTelemetryConfig config,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultHttpServerMetricsFactory metricsFactory,
                                      DefaultHttpServerLoggerFactory loggerFactory,
                                      DefaultHttpServerBodyConverter bodyLogger) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultHttpServerTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultHttpServerTelemetryFactory.NOOP_METER_REGISTRY;

        this.context = new TelemetryContext(config,
            isTracingEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            bodyLogger
        );

        this.metrics = metricsFactory.create(this.context);
        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public HttpServerObservation observe(HttpServerRequest request) {
        var span = context.isTracingEnabled && request.pathTemplate() != null
            ? startSpan(request).startSpan()
            : Span.getInvalid();
        return new DefaultHttpServerObservation(context, logger, metrics, request, request.requestStartTimeInNanos(), span);
    }

    protected SpanBuilder startSpan(HttpServerRequest request) {
        @SuppressWarnings("DataFlowIssue")
        var span = this.context.tracer()
            .spanBuilder(request.method() + " " + request.pathTemplate())
            .setSpanKind(SpanKind.SERVER)
            .setParent(io.opentelemetry.context.Context.current())
            .setAttribute(UrlAttributes.URL_SCHEME, request.scheme())
            .setAttribute(HttpAttributes.HTTP_ROUTE, request.pathTemplate())
            .setAttribute(ServerAttributes.SERVER_ADDRESS, request.host())
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.method()); // if unknown tracing is disabled

        if (request.body().contentType() != null) {
            var contentType = Objects.requireNonNullElse(request.body().contentType(), "UNKNOWN");
            span.setAttribute(HttpAttributes.HTTP_REQUEST_HEADER.getAttributeKey("content-type"), List.of(contentType));
        }

        if (request.body().contentLength() != -1) {
            var contentLength = String.valueOf(request.body().contentLength());
            span.setAttribute(HttpAttributes.HTTP_REQUEST_HEADER.getAttributeKey("content-length"), List.of(contentLength));
        }

        if (context.config().tracing().tracePathFull()) {
            span.setAttribute(UrlAttributes.URL_PATH, request.path());
        }

        for (var attribute : context.config().tracing().attributes().entrySet()) {
            span.setAttribute(attribute.getKey(), attribute.getValue());
        }
        return span;
    }
}
