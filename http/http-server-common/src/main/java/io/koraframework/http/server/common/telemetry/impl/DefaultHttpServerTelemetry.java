package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;

public class DefaultHttpServerTelemetry implements HttpServerTelemetry {

    public record TelemetryContext(HttpServerTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   DefaultHttpServerBodyConverter bodyLogger) {

        public static final TelemetryContext EMPTY = new TelemetryContext(new HttpServerTelemetryConfig() {
            @Override
            public HttpServerLoggingConfig logging() {
                return () -> null;
            }

            @Override
            public HttpServerMetricsConfig metrics() {
                return new HttpServerMetricsConfig() {};
            }

            @Override
            public HttpServerTracingConfig tracing() {
                return new HttpServerTracingConfig() {};
            }
        }, false, false, DefaultHttpServerTelemetryFactory.NOOP_METER_REGISTRY, DefaultHttpServerTelemetryFactory.NOOP_TRACER, new DefaultHttpServerBodyConverter());
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
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultHttpServerTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultHttpServerTelemetryFactory.NOOP_METER_REGISTRY;

        this.context = new TelemetryContext(config,
            isTraceEnabled,
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
        var span = context.config().tracing().enabled() && request.pathTemplate() != null
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
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.method())
            .setAttribute(UrlAttributes.URL_SCHEME, request.scheme())
            .setAttribute(ServerAttributes.SERVER_ADDRESS, request.host())
            .setAttribute(UrlAttributes.URL_PATH, request.path())
            .setAttribute(HttpAttributes.HTTP_ROUTE, request.pathTemplate()); // if unknown tracing is disabled
        for (var attribute : context.config().tracing().attributes().entrySet()) {
            span.setAttribute(attribute.getKey(), attribute.getValue());
        }
        return span;
    }
}
